package top.fumiama.copymanga

import android.app.Application
import top.fumiama.copymanga.api.ModernTls

class CopyMangaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModernTls.install(this)
    }
}
