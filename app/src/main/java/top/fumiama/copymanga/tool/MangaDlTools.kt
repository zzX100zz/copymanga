package top.fumiama.copymanga.tool

import top.fumiama.copymanga.activity.DlActivity
import top.fumiama.copymanga.api.CopyMangaApi
import top.fumiama.copymanga.data.ComicStructure
import java.io.File
import java.lang.ref.WeakReference
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.Executors

class MangaDlTools(activity: DlActivity) {
    var exit = false
        set(value) {
            field = value
            if (value) apiExecutor.shutdownNow()
        }
    private val da = WeakReference(activity)
    private val d = da.get()
    private val p = PropertiesTools(File("${d?.filesDir}/chapters.hash"))
    private var imgUrlsList: Array<Array<String>?>? = null
    private var chaptersCount = 0
    private val chapterLock = Object()
    private val apiExecutor = Executors.newFixedThreadPool(4)
    private var pendingChapters = 0
    private val failedChapterHashes = hashSetOf<String>()

    init {
        wmdlt = WeakReference(this)
    }

    fun getImgsCountByHash(hash: String): Int?{
        return p[hash].toIntOrNull()?.let { imgUrlsList?.getOrNull(it)?.size }
    }

    fun allocateChapterUrls(count: Int){
        synchronized(chapterLock) {
            imgUrlsList = arrayOfNulls(count)
            chaptersCount = 0
            pendingChapters = 0
            failedChapterHashes.clear()
        }
    }

    fun dlChapterUrl(url: String){
        val hash = url.substringAfterLast("/")
        synchronized(chapterLock) {
            p[hash] = (chaptersCount++).toString()
            pendingChapters++
        }
        apiExecutor.execute {
            try {
                val images = CopyMangaApi.fetchChapter(url).imageUrls
                if (images.isEmpty()) throw IllegalStateException("章节图片为空")
                setChapterImgs(hash, images)
            } catch (e: Exception) {
                e.printStackTrace()
                synchronized(chapterLock) { failedChapterHashes += hash }
            } finally {
                synchronized(chapterLock) {
                    pendingChapters--
                    chapterLock.notifyAll()
                }
            }
        }
    }

    fun setChapterImgs(hash: String, imgUrls: Array<String>){
        synchronized(chapterLock) {
            p[hash].toIntOrNull()?.let { index -> imgUrlsList?.set(index, imgUrls) }
        }
    }

    fun awaitChapterUrls(timeoutMs: Long = 120000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(chapterLock) {
            while (pendingChapters > 0) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                chapterLock.wait(remaining)
            }
            return failedChapterHashes.isEmpty()
        }
    }

    fun dlChapterAndPackIntoZip(zipf: File, hash: String){
        val images = synchronized(chapterLock) {
            p[hash].toIntOrNull()?.let { imgUrlsList?.getOrNull(it) }
        }
        if (images.isNullOrEmpty() || synchronized(chapterLock) { hash in failedChapterHashes }) {
            onDownloadedListener?.handleMessage(false)
            return
        }
        images.let {
            val dl = DownloadTools()
            zipf.parentFile?.let { if (!it.exists()) it.mkdirs() }
            if (zipf.exists()) zipf.delete()
            zipf.createNewFile()
            val zip = ZipOutputStream(CheckedOutputStream(zipf.outputStream(), CRC32()))
            zip.setLevel(9)
            var succeed = true
            for (i in it.indices) {
                zip.putNextEntry(ZipEntry("$i.webp"))
                val s = dl.getHttpContent(it[i])?.let { zip.write(it); true } ?: false
                if (!s) succeed = s
                onDownloadedListener?.handleMessage(s, i + 1)
                zip.flush()
                if (exit) break
            }
            zip.close()
            onDownloadedListener?.handleMessage(succeed)
        }
    }

    var onDownloadedListener: OnDownloadedListener? = null

    interface OnDownloadedListener {
        fun handleMessage(succeed: Boolean)
        fun handleMessage(succeed: Boolean, pageNow: Int)
    }

    companion object {
        var wmdlt: WeakReference<MangaDlTools>? = null
        var comicStructure: Array<ComicStructure>? = null
    }
}
