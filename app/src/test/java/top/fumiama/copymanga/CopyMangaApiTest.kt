package top.fumiama.copymanga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.fumiama.copymanga.api.CopyMangaApi

class CopyMangaApiTest {
    @Test
    fun parsesComicPathFromH5DetailsUrl() {
        assertEquals(
            "grandblue",
            CopyMangaApi.comicPathFromWebUrl(
                "https://www.copy4000.com/h5/details/comic/grandblue?from=index"
            )
        )
        assertNull(CopyMangaApi.comicPathFromWebUrl("https://www.copy4000.com/h5/index"))
    }

    @Test
    fun parsesChapterReferencesFromWebAndApiUrls() {
        val h5 = CopyMangaApi.chapterReferenceFromUrl(
            "https://www.copy4000.com/h5/comicContent/grandblue/chapter/chapter-uuid"
        )
        assertEquals("grandblue", h5?.comicPath)
        assertEquals("chapter-uuid", h5?.uuid)

        val api = CopyMangaApi.chapterReferenceFromUrl(
            "copymanga://api/comic/grandblue/chapter/chapter-uuid"
        )
        assertEquals(h5, api)

        val previewApi = CopyMangaApi.chapterReferenceFromUrl(
            "https://api.copymanga.site/api/v3/comic/grandblue/chapter2/chapter-uuid" +
                "?platform=1"
        )
        assertEquals(h5, previewApi)
    }

    @Test
    fun legacyPayloadKeepsNextThenPreviousOrder() {
        val chapter = CopyMangaApi.ChapterData(
            "漫画",
            "第1话",
            "chapter-uuid",
            "previous-url",
            "next-url",
            arrayOf("image-1", "image-2")
        )
        assertEquals(
            "第1话 chapter-uuid\nnext-url\nprevious-url\nimage-1\nimage-2",
            chapter.toLegacyPayload()
        )
    }
}
