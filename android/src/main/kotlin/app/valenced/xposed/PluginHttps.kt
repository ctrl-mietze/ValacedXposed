package app.valenced.xposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

private const val DEFAULT_INDEX_LIMIT = 2_000_000L
private const val REDIRECT_LIMIT = 5
private const val ATTEMPTS = 3

/** Android platform HTTPS transport. Retains the device's DNS, proxy, VPN,
 * certificate validation and hostname verification; no custom trust manager.
 * Uses an independent transport from the embedded Ktor CIO selector. */
internal object PluginHttps {
    internal fun url(raw: String): URI {
        val uri = URI(raw)
        require(uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Nur gültige HTTPS-Download-URLs ohne Zugangsdaten."
        }
        return uri
    }
    internal fun redirect(from: URI, location: String?): URI {
        require(!location.isNullOrBlank()) { "HTTP-Weiterleitung ohne Ziel." }
        return url(from.resolve(location).toString())
    }
    internal fun retryable(error: Throwable): Boolean {
        // TLS and application-level errors must never be retried as DNS errors.
        val chain = generateSequence(error) { it.cause?.takeUnless { next -> next === it } }.take(12).toList()
        if (chain.any { it is SSLException }) return false
        return chain.any {
            it is UnknownHostException || it is UnresolvedAddressException ||
                it is ConnectException || it is SocketTimeoutException ||
                it is NoRouteToHostException || it is SocketException || it is EOFException ||
                (it is StatusFailure && it.status in setOf(408, 429, 500, 502, 503, 504))
        }
    }
    internal class StatusFailure(val status:Int, host:String):IOException("HTTP $status · $host")
    internal fun describe(host: String, error: Throwable): String {
        val chain = generateSequence(error) { it.cause?.takeUnless { next -> next === it } }.take(12).toList()
        return when {
            chain.any { it is SSLException } -> "TLS-Verbindung zu $host fehlgeschlagen. Zertifikat und Gerätezeit prüfen."
            chain.any { it is UnknownHostException || it is UnresolvedAddressException } ->
                "DNS-Auflösung für $host fehlgeschlagen. Netzwerk, Private DNS und VPN prüfen."
            chain.any { it is SocketTimeoutException } -> "Zeitüberschreitung beim Download von $host."
            chain.any { it is ConnectException || it is NoRouteToHostException || it is SocketException } ->
                "Verbindung zu $host fehlgeschlagen. Netzwerk prüfen und erneut versuchen."
            else -> error.message?.take(350) ?: "Download von $host fehlgeschlagen."
        }
    }
    private fun remaining(deadline:Long):Int =
        ((deadline-System.nanoTime())/1_000_000L).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()

    suspend fun fetch(raw:String, etag:String?=null, timeoutMillis:Long=30_000L,
        maxBytes:Long=DEFAULT_INDEX_LIMIT, onReceived:(Long)->Unit={}):ETagFetchResult = withContext(Dispatchers.IO) {
        val first=url(raw)
        require(maxBytes in 1..(128L*1024*1024)) { "Download-Größe nicht unterstützt." }
        val deadline=System.nanoTime()+timeoutMillis.coerceIn(1000L,120_000L)*1_000_000L
        var failure:Throwable?=null
        repeat(ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            if(remaining(deadline)<=0)throw IOException("Zeitüberschreitung beim Download von "+first.host,failure)
            try {
                onReceived(0)
                return@withContext once(first,etag,deadline,maxBytes,onReceived)
            } catch(e:Exception) {
                coroutineContext.ensureActive()
                failure=e
                if(!retryable(e)||attempt==ATTEMPTS-1||remaining(deadline)<1000)
                    throw IOException(describe(first.host,e),e)
                delay(if(attempt==0)350L else 900L)
            }
        }
        throw IOException(describe(first.host,failure ?: IOException("Download fehlgeschlagen.")),failure)
    }

    private suspend fun once(first:URI,etag:String?,deadline:Long,maxBytes:Long,
        onReceived:(Long)->Unit):ETagFetchResult {
        var current=first
        repeat(REDIRECT_LIMIT+1) { hop ->
            coroutineContext.ensureActive()
            val left=remaining(deadline)
            if(left<=0)throw SocketTimeoutException("Download-Zeitlimit erreicht.")
            // No forced IP, no forced network, no custom resolver or bypass.
            val connection=current.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=left.coerceIn(1,8000)
                connection.readTimeout=left.coerceIn(1,10000)
                connection.requestMethod="GET"
                connection.useCaches=false
                connection.setRequestProperty("User-Agent",ValencedConstants.USER_AGENT)
                connection.setRequestProperty("Accept-Encoding","identity")
                if(current.host.equals(first.host,ignoreCase=true)&&etag!=null)
                    connection.setRequestProperty("If-None-Match",etag)
                val code=connection.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    check(hop<REDIRECT_LIMIT){"Zu viele HTTPS-Weiterleitungen."}
                    current=redirect(current,connection.getHeaderField("Location"))
                    return@repeat
                }
                if(code==HttpURLConnection.HTTP_NOT_MODIFIED)
                    return ETagFetchResult.NotModified
                if(code!=HttpURLConnection.HTTP_OK)throw StatusFailure(code,current.host)
                val declared=connection.contentLengthLong
                require(declared<0||declared<=maxBytes){"Datei überschreitet das Größenlimit."}
                val out=ByteArrayOutputStream(minOf(maxBytes, if(declared>0) declared else 16384L,65536L).toInt())
                connection.inputStream.use { input ->
                    val buffer=ByteArray(16384)
                    var received=0L
                    while(true) {
                        coroutineContext.ensureActive()
                        if(remaining(deadline)<=0)throw SocketTimeoutException("Download-Zeitlimit erreicht.")
                        val n=input.read(buffer)
                        if(n<0)break
                        if(n==0)continue
                        received+=n
                        require(received<=maxBytes){"Datei überschreitet das Größenlimit."}
                        out.write(buffer,0,n)
                        onReceived(received)
                    }
                    if(declared>=0&&received!=declared)throw EOFException("Unvollständiger Download.")
                }
                return ETagFetchResult.Fetched(out.toByteArray(),connection.getHeaderField("ETag")?.takeIf{it.isNotBlank()})
            } finally {
                connection.disconnect()
            }
        }
        error("Zu viele HTTPS-Weiterleitungen.")
    }
}
