package top.fumiama.copymanga.web

import android.util.Log
import android.webkit.JavascriptInterface
import top.fumiama.copymanga.activity.MainActivity.Companion.mh
import top.fumiama.copymanga.api.CopyMangaApi

class JS {
    @JavascriptInterface
    fun loadComic(url: String){
        Log.d("MyJS", "Load comic through App API: $url")
        Thread {
            when {
                url.contains("/details/comic/") -> CopyMangaApi.loadComicForUi(url)
                url.contains("/comicContent/") -> CopyMangaApi.loadChapterForReader(url)
            }
        }.start()
    }
    @JavascriptInterface
    fun hideFab(){
        Thread{mh?.sendEmptyMessage(5)}.start()
    }
}
