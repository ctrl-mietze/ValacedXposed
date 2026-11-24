package app.valenced.xposed

import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginHttpsTest {
    @Test fun validatesHttpsAndRedirects() {
        val url=PluginHttps.url("https://community.valenced.app/pool/example@1.0.zip")
        assertEquals("https://community.valenced.app/pool/next.zip",PluginHttps.redirect(url,"next.zip").toString())
        assertEquals("https://other.example/a.zip",PluginHttps.redirect(url,"https://other.example/a.zip").toString())
        assertFailsWith<IllegalArgumentException>{PluginHttps.redirect(url,"http://other.example/a.zip")}
        assertFailsWith<IllegalArgumentException>{PluginHttps.url("https://name:pass@other.example/a")}
        assertFailsWith<IllegalArgumentException>{PluginHttps.redirect(url,null)}
        assertFailsWith<IllegalArgumentException>{PluginHttps.url("https:///a")}
    }
    @Test fun transientErrorsAreRetryableButNotTlsOrMissingFiles() {
        assertTrue(PluginHttps.retryable(UnresolvedAddressException()))
        assertTrue(PluginHttps.retryable(IOException("wrapper",UnknownHostException("host"))))
        assertTrue(PluginHttps.retryable(SocketTimeoutException()))
        assertTrue(PluginHttps.retryable(PluginHttps.StatusFailure(503,"host")))
        assertFalse(PluginHttps.retryable(PluginHttps.StatusFailure(404,"host")))
        assertFalse(PluginHttps.retryable(SSLHandshakeException("invalid certificate")))
        assertFalse(PluginHttps.retryable(IllegalArgumentException("invalid file")))
        assertTrue(PluginHttps.describe("example.org",UnknownHostException()).contains("example.org"))
    }
}
