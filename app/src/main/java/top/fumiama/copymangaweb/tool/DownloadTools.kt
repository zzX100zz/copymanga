package top.fumiama.copymangaweb.tool

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class DownloadTools {
    fun getHttpContent(u: String, refer: String? = null, ua: String? = null): ByteArray? {
        Log.d("Mydl", "getHttp: $u")
        val connection = try {
            URL(u).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            Log.e("Mydl", "Unable to open connection: $u", e)
            return null
        }

        return try {
            connection.run {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                refer?.let { setRequestProperty("referer", it) }
                ua?.let { setRequestProperty("User-agent", it) }
                inputStream.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.e("Mydl", "Download failed: $u", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}