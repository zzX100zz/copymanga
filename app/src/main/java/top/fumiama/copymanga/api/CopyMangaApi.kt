package top.fumiama.copymanga.api

import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import top.fumiama.copymanga.activity.DlActivity
import top.fumiama.copymanga.activity.MainActivity.Companion.mh
import top.fumiama.copymanga.activity.MainActivity.Companion.wm
import top.fumiama.copymanga.data.ComicStructure
import top.fumiama.copymanga.data.CopyMangaApiModels.BaseResponse
import top.fumiama.copymanga.data.CopyMangaApiModels.BookResponse
import top.fumiama.copymanga.data.CopyMangaApiModels.Chapter
import top.fumiama.copymanga.data.CopyMangaApiModels.ChapterResponse
import top.fumiama.copymanga.data.CopyMangaApiModels.NetworkResponse
import top.fumiama.copymanga.data.CopyMangaApiModels.VolumeResponse
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * Minimal port of the native App API access used by the 2.x client.
 *
 * The 1.x UI remains intact. Only comic metadata, chapter lists and page URLs are
 * loaded through the App API so the reader and downloader no longer scrape H5 pages.
 */
object CopyMangaApi {
    private const val TAG = "CopyMangaApi"
    private const val SEED_HOST = "api.2024manga.com"
    private const val PLATFORM = "3"
    private const val API_VERSION = "2024.4.28"
    private const val REQUEST_SOURCE = "com.manga2020.app"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000

    private val gson = Gson()
    private val hostLock = Any()
    @Volatile private var hostsInitialized = false
    @Volatile private var apiHosts = arrayOf(SEED_HOST)

    data class ChapterReference(val comicPath: String, val uuid: String)

    data class ChapterData(
        val comicName: String,
        val chapterName: String,
        val uuid: String,
        val previousUrl: String?,
        val nextUrl: String?,
        val imageUrls: Array<String>
    ) {
        fun toLegacyPayload(): String {
            val header = "$chapterName $uuid\n${nextUrl ?: "null"}\n${previousUrl ?: "null"}"
            return if (imageUrls.isEmpty()) header else "$header\n${imageUrls.joinToString("\n")}"
        }
    }

    fun comicPathFromWebUrl(url: String): String? {
        val marker = when {
            url.contains("/details/comic/") -> "/details/comic/"
            url.contains("/comic/") -> "/comic/"
            else -> return null
        }
        return url.substringAfter(marker)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .substringBefore('/')
            .takeIf { it.isNotEmpty() }
    }

    fun chapterReferenceFromUrl(url: String): ChapterReference? {
        val marker = when {
            url.startsWith("copymanga://api/comic/") -> "copymanga://api/comic/"
            url.contains("/comicContent/") -> "/comicContent/"
            url.contains("/comic/") -> "/comic/"
            else -> return null
        }
        val parts = url.substringAfter(marker)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        return ChapterReference(parts.first(), parts.last())
    }

    fun loadComicForUi(url: String): Boolean {
        val path = comicPathFromWebUrl(url) ?: return false
        return try {
            val (title, groups) = fetchComic(path)
            DlActivity.comicName = title
            mh?.obtainMessage(4, gson.toJson(groups))?.sendToTarget()
            true
        } catch (e: Exception) {
            showError("读取漫画详情失败", e)
            false
        }
    }

    fun loadChapterForReader(url: String): Boolean {
        return try {
            val chapter = fetchChapter(url)
            mh?.obtainMessage(2, chapter.toLegacyPayload())?.sendToTarget()
            true
        } catch (e: Exception) {
            showError("读取完整章节失败", e)
            false
        }
    }

    fun loadChapterForWebReader(url: String): Boolean {
        return try {
            val chapter = fetchChapter(url)
            mh?.obtainMessage(6, gson.toJson(chapter))?.sendToTarget()
            true
        } catch (e: Exception) {
            showError("读取完整章节失败", e)
            false
        }
    }

    fun fetchChapter(url: String): ChapterData {
        val reference = chapterReferenceFromUrl(url)
            ?: throw IllegalArgumentException("无法识别章节地址: $url")
        return fetchChapter(reference.comicPath, reference.uuid)
    }

    private fun fetchComic(path: String): Pair<String, Array<ComicStructure>> {
        val encodedPath = encodePath(path)
        val book = gson.fromJson(
            getJson("/api/v3/comic2/$encodedPath?platform=$PLATFORM"),
            BookResponse::class.java
        )
        val results = book.results ?: throw IllegalStateException("漫画详情为空")
        val comic = results.comic ?: throw IllegalStateException("漫画资料为空")
        val groups = arrayListOf<ComicStructure>()
        results.groups?.values?.forEach { group ->
            val chapters = arrayListOf<ComicStructure.Chapters>()
            var offset = 0
            val expectedCount = if (group.count > 0) group.count else 1
            while (offset < expectedCount) {
                val groupPath = encodePath(group.path_word ?: "default")
                val volume = gson.fromJson(
                    getJson(
                        "/api/v3/comic/$encodedPath/group/$groupPath/chapters" +
                            "?limit=100&offset=$offset&platform=$PLATFORM"
                    ),
                    VolumeResponse::class.java
                )
                val list = volume.results?.list ?: emptyArray()
                list.forEach { chapter ->
                    if (!chapter.uuid.isNullOrEmpty()) {
                        val item = ComicStructure.Chapters()
                        item.name = chapter.name ?: chapter.uuid
                        item.url = chapterUrl(path, chapter.uuid)
                        chapters += item
                    }
                }
                if (list.isEmpty() || list.size < 100) break
                offset += list.size
            }
            if (chapters.isNotEmpty()) {
                val item = ComicStructure()
                item.name = group.name ?: group.path_word ?: "默认"
                item.chapters = chapters.toTypedArray()
                groups += item
            }
        }
        if (groups.isEmpty()) throw IllegalStateException("章节列表为空")
        return Pair(comic.name ?: path, groups.toTypedArray())
    }

    private fun fetchChapter(comicPath: String, uuid: String): ChapterData {
        val encodedPath = encodePath(comicPath)
        val encodedUuid = encodePath(uuid)
        val response = gson.fromJson(
            getJson(
                "/api/v3/comic/$encodedPath/chapter/$encodedUuid?platform=$PLATFORM"
            ),
            ChapterResponse::class.java
        )
        val results = response.results ?: throw IllegalStateException("章节数据为空")
        val chapter = results.chapter ?: throw IllegalStateException("章节内容为空")
        val path = chapter.comic_path_word?.takeIf { it.isNotEmpty() } ?: comicPath
        val images = orderedImageUrls(chapter)
        if (images.isEmpty()) throw IllegalStateException("章节图片为空")
        return ChapterData(
            results.comic?.name ?: path,
            chapter.name ?: uuid,
            chapter.uuid ?: uuid,
            chapter.prev?.takeIf { it.isNotEmpty() }?.let { chapterUrl(path, it) },
            chapter.next?.takeIf { it.isNotEmpty() }?.let { chapterUrl(path, it) },
            images
        )
    }

    private fun orderedImageUrls(chapter: Chapter): Array<String> {
        val contents = chapter.contents ?: return emptyArray()
        val direct = contents.mapNotNull { it.url?.takeIf(String::isNotEmpty) }
        val words = chapter.words
        if (words == null || words.isEmpty()) return direct.toTypedArray()

        val positions = words.toMutableList()
        contents.indices.forEach { index ->
            if (!positions.contains(index)) positions += index
        }
        val ordered = arrayOfNulls<String>(contents.size)
        contents.forEachIndexed { index, content ->
            val position = positions.getOrNull(index) ?: index
            if (position in ordered.indices) ordered[position] = content.url
        }
        contents.forEachIndexed { index, content ->
            if (ordered[index].isNullOrEmpty()) ordered[index] = content.url
        }
        return ordered.mapNotNull { it?.takeIf(String::isNotEmpty) }.toTypedArray()
    }

    private fun chapterUrl(comicPath: String, uuid: String) =
        "copymanga://api/comic/$comicPath/chapter/$uuid"

    private fun encodePath(value: String) =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun getJson(path: String): String {
        ensureHosts()
        var lastError: Exception? = null
        val candidates = apiHosts
        candidates.forEach { host ->
            try {
                val body = request(host, path)
                val base = gson.fromJson(body, BaseResponse::class.java)
                if (base.code != 200) {
                    throw IllegalStateException("API ${base.code}: ${base.message ?: "未知错误"}")
                }
                return body
            } catch (e: Exception) {
                Log.w(TAG, "API host failed: $host", e)
                lastError = e
                synchronized(hostLock) {
                    if (apiHosts.size > 1) {
                        apiHosts = apiHosts.filterNot { it == host }.toTypedArray()
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("没有可用的 API 节点")
    }

    private fun ensureHosts() {
        if (hostsInitialized) return
        synchronized(hostLock) {
            if (hostsInitialized) return
            try {
                val body = request(
                    SEED_HOST,
                    "/api/v3/system/network2?platform=$PLATFORM"
                )
                val response = gson.fromJson(body, NetworkResponse::class.java)
                val discovered = arrayListOf<String>()
                response.results?.api?.forEach { row ->
                    row?.forEach { host ->
                        host?.takeIf { it.isNotEmpty() && it != "t66y.com" }?.let(discovered::add)
                    }
                }
                discovered += SEED_HOST
                apiHosts = discovered.distinct().toTypedArray()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to refresh API hosts; use seed", e)
                apiHosts = arrayOf(SEED_HOST)
            } finally {
                hostsInitialized = true
            }
            Log.d(TAG, "API hosts: ${apiHosts.joinToString()}")
        }
    }

    private fun request(host: String, path: String): String {
        val connection = URL("https://$host$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("user-agent", USER_AGENT)
        connection.setRequestProperty("x-requested-with", REQUEST_SOURCE)
        connection.setRequestProperty("webp", "1")
        connection.setRequestProperty("accept-encoding", "gzip")
        connection.setRequestProperty("authorization", "Token")
        connection.setRequestProperty("platform", PLATFORM)
        connection.setRequestProperty("accept", "application/json")
        connection.setRequestProperty("version", API_VERSION)
        return try {
            val code = connection.responseCode
            val source = if (code >= 400) connection.errorStream else connection.inputStream
            val body = decodeStream(source, connection.getHeaderField("Content-Encoding"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            if (code >= 400) throw IllegalStateException("HTTP $code: $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeStream(stream: InputStream, encoding: String?): InputStream =
        if (encoding.equals("gzip", ignoreCase = true)) GZIPInputStream(stream) else stream

    private fun showError(prefix: String, error: Exception) {
        Log.e(TAG, prefix, error)
        wm?.get()?.runOnUiThread {
            Toast.makeText(
                wm?.get(),
                "$prefix：${error.message ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
