package top.fumiama.copymanga.api

import android.content.Context
import android.os.Build
import android.util.Log
import top.fumiama.copymanga.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Adds only the modern public roots missing from Android 5/6 to the platform trust store.
 * Hostname verification and normal certificate-chain validation remain enabled.
 */
object ModernTls {
    private const val TAG = "ModernTls"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || installed) return
        synchronized(this) {
            if (installed) return
            try {
                val systemTrust = trustManager(null)
                val bundledStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                }
                val certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509")
                arrayOf(
                    "usertrust_rsa" to R.raw.usertrust_rsa_certification_authority,
                    "gts_root_r4" to R.raw.gts_root_r4
                ).forEach { (alias, resourceId) ->
                    context.resources.openRawResource(resourceId).use { source ->
                        bundledStore.setCertificateEntry(
                            alias,
                            certificateFactory.generateCertificate(source)
                        )
                    }
                }
                val bundledTrust = trustManager(bundledStore)
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(
                    null,
                    arrayOf(CompositeTrustManager(systemTrust, bundledTrust)),
                    null
                )
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                installed = true
                Log.i(TAG, "Installed Android 5/6 public-root compatibility")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to install Android 5/6 TLS compatibility", e)
            }
        }
    }

    private fun trustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("No X509 trust manager available")
    }

    private class CompositeTrustManager(
        private val system: X509TrustManager,
        private val bundled: X509TrustManager
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            system.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
                return
            } catch (systemError: CertificateException) {
                try {
                    bundled.checkServerTrusted(chain, authType)
                    return
                } catch (bundledError: CertificateException) {
                    bundledError.addSuppressed(systemError)
                    throw bundledError
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + bundled.acceptedIssuers
    }
}
