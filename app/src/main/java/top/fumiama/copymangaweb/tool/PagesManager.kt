package top.fumiama.copymangaweb.tool

import android.content.Intent
import android.widget.Toast
import top.fumiama.copymangaweb.activity.MainActivity.Companion.wm
import top.fumiama.copymangaweb.activity.ViewMangaActivity
import java.lang.ref.WeakReference

class PagesManager(w: WeakReference<ViewMangaActivity>) {
    private val activity = w
    private val v get() = activity.get()
    private var isEndL = false
    private var isEndR = false
    fun toPreviousPage(){ toPage(v?.r2l==true) }
    fun toNextPage(){ toPage(v?.r2l!=true) }
    fun goBackward() = toPage(false)
    fun goForward() = toPage(true)
    private fun judgePrevious() = (v?.pageNum ?: 0) > 1
    private fun judgeNext() = (v?.pageNum ?: 0) < (v?.count ?: 0)
    private fun toPage(goNext:Boolean){
        if (v?.clicked == false) {
            if (if(goNext)judgeNext() else judgePrevious()) {
                if(goNext) {
                    v?.scrollForward()
                    isEndR = false
                } else {
                    v?.scrollBack()
                    isEndL = false
                }
            } else {
                if (v?.dlZip2View == true) {
                    switchZipChapter(goNext)
                } else {
                    val chapterUrl = if(goNext) ViewMangaActivity.nextChapterUrl else ViewMangaActivity.previousChapterUrl
                    if (chapterUrl == null) {
                        showReachedEnd()
                        return
                    }
                    if (if(goNext)isEndR else isEndL) {
                        setChapterStartPage(goNext)
                        wm?.get()?.mBinding?.w?.apply { post {
                            loadUrl("javascript:invoke.clickClass(\"comicControlBottomTopClick\",${if(goNext)1 else 0});")
                        } }
                        v?.finish()
                    } else doubleTapToast(goNext)
                }
            }
        } else v?.hideSettings()
    }

    private fun switchZipChapter(goNext: Boolean) {
        val chapters = ViewMangaActivity.zipList.orEmpty()
        val newPosition = ViewMangaActivity.zipPosition + if (goNext) 1 else -1
        val chapter = chapters.getOrNull(newPosition)
        if (chapter == null) {
            showReachedEnd()
            return
        }
        if (!(if (goNext) isEndR else isEndL)) {
            doubleTapToast(goNext)
            return
        }

        val reader = v ?: return
        setChapterStartPage(goNext)
        ViewMangaActivity.zipPosition = newPosition
        ViewMangaActivity.titleText = chapter.nameWithoutExtension
        ViewMangaActivity.zipFile = chapter
        reader.startActivity(Intent(reader, ViewMangaActivity::class.java))
        reader.finish()
    }

    private fun showReachedEnd() {
        Toast.makeText(v?.applicationContext, "已经到头了~", Toast.LENGTH_SHORT).show()
    }

    private fun setChapterStartPage(goNext: Boolean) {
        ViewMangaActivity.pn = if (goNext) {
            ViewMangaActivity.FIRST_PAGE
        } else {
            ViewMangaActivity.LAST_PAGE
        }
    }

    fun manageInfo(){
        if (v?.clicked == false) v?.showSettings() else v?.hideSettings()
    }
    private fun doubleTapToast(goNext: Boolean){
        val hint = if(goNext) "下" else "上"
        Toast.makeText(
            v?.applicationContext,
            "再次按下加载${hint}一章",
            Toast.LENGTH_SHORT
        ).show()
        if(goNext) isEndR = true
        else isEndL = true
    }
}