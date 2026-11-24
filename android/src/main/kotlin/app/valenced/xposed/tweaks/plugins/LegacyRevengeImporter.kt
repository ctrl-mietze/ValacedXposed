package app.valenced.xposed.tweaks.plugins

import app.valenced.plugins.external.applyStagedPlugin
import app.valenced.plugins.external.discardStagedPlugin
import app.valenced.plugins.external.stagePluginZip
import app.valenced.plugins.repos.sha256Hex
import app.valenced.plugins.repos.repoInstallMutex
import app.valenced.xposed.ETagFetchResult
import app.valenced.xposed.PluginHttps
import app.valenced.xposed.tweaks.plugins.external.InstallResult
import app.valenced.xposed.tweaks.plugins.external.readExternalPluginDir
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** A deliberately limited compatibility port for old Revenge/Vendetta manifest + JS.
 * Fetches the actual script, previews its digest and stores it only on explicit confirmation.
 * It is never executed until the plugin is explicitly enabled. */
internal object LegacyRevengeImporter {
    data class Candidate(val token: String, val url: String, val manifest: JSONObject, val script: ByteArray, val digest: String,
        val created: Long = android.os.SystemClock.elapsedRealtime())
    private var pending: Candidate? = null

    private fun https(raw: String): URI {
        val url = URI(raw.trim())
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null &&
            url.fragment == null && raw.length <= 1200) { "Eine HTTPS-URL ist erforderlich." }
        return url
    }

    private suspend fun fetch(url: String, cap: Int): ByteArray {
        val result = PluginHttps.fetch(url, null, 15_000L, cap.toLong()) as? ETagFetchResult.Fetched
            ?: error("Keine Daten empfangen.")
        require(result.bytes.size in 1..cap) { "Datei ist leer oder zu groß." }
        return result.bytes
    }

    suspend fun preview(raw: String): JSONObject {
        val input = https(raw)
        val manifestUrl = if (input.path.endsWith("/manifest.json")) input
            else https(raw.trim().trimEnd('/') + "/manifest.json")
        val legacy = JSONObject(fetch(manifestUrl.toString(), 96_000).decodeToString())
        require(!legacy.has("format") && legacy.has("name") && legacy.has("main")) {
            "Nur Revenge/Vendetta-JS-Manifeste. Revenge Next ZIPs und native Plugins sind nicht unterstützt."
        }
        val main = legacy.getString("main")
        require(main.matches(Regex("[a-zA-Z0-9_./-]+\\.js")) && !main.startsWith("/") && ".." !in main.split('/')) {
            "Nur veröffentlichte JavaScript-Einstiegspunkte werden unterstützt."
        }
        val scriptUrl = manifestUrl.resolve(main)
        require(scriptUrl.host == manifestUrl.host && scriptUrl.scheme == "https") { "Ungültige Script-URL." }
        val bytes = fetch(scriptUrl.toString(), 1_500_000)
        val script = bytes.decodeToString().trim()
        require(script.startsWith("(")) { "Kein unterstütztes kompiliertes Revenge-JS-Bundle." }
        val digest = sha256Hex(bytes)
        val publishedHash=legacy.optString("hash").lowercase()
        if (publishedHash.matches(Regex("[0-9a-f]{64}"))) {
            require(digest==publishedHash) { "SHA-256 stimmt nicht mit dem veröffentlichten Manifest überein." }
        }
        val id = "veyra.legacy." + sha256Hex(manifestUrl.toString()).take(24)
        val authors = legacy.optJSONArray("authors")
        val author = (0 until (authors?.length() ?: 0)).mapNotNull { authors?.optJSONObject(it)?.optString("name") }
            .joinToString(", ").take(150).ifBlank { "Legacy community" }
        val manifest = JSONObject().put("format",1).put("id",id)
            .put("name",legacy.getString("name").take(100))
            .put("description",("[Revenge Legacy] "+legacy.optString("description")).take(500))
            .put("author",author).put("version","1.0.0")
            .put("dependencies",JSONObject().put("valenced.api",JSONObject()).put("discord",JSONObject()).put("veyra.legacy.compat",JSONObject()))
            .put("dist",JSONObject().put("script","index.js"))
        val candidate=Candidate(UUID.randomUUID().toString(),manifestUrl.toString(),manifest,bytes,digest)
        pending=candidate
        return JSONObject().put("legacyPreview",JSONObject().put("token",candidate.token)
            .put("name",manifest.getString("name")).put("author",author)
            .put("description",legacy.optString("description").take(500))
            .put("url",candidate.url).put("sha256",digest).put("bytes",bytes.size))
    }

    suspend fun install(token: String,dataDir: String): InstallResult = repoInstallMutex.withLock {
        val candidate=pending ?: error("Import-Vorschau abgelaufen.")
        require(candidate.token==token && android.os.SystemClock.elapsedRealtime()-candidate.created<120_000L) {
            "Import-Vorschau abgelaufen."
        }
        pending=null
        val id=candidate.manifest.getString("id")
        require(!pluginRegistry.knownManifests().containsKey(id)) {
            "Plugin bereits importiert. Für einen Neuimport zuerst deinstallieren."
        }
        val zip=ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            fun add(name:String,bytes:ByteArray) {
                out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()
            }
            add("manifest.json",candidate.manifest.toString().encodeToByteArray())
            add("index.js",candidate.script)
        }
        val paths=pluginPaths(dataDir)
        val staged=stagePluginZip(paths,".legacy-"+UUID.randomUUID()) { it.write(zip.toByteArray()) }
        val dir=try { applyStagedPlugin(paths,staged) }
            catch(e:Throwable) { discardStagedPlugin(paths,staged);throw e }
        try {
            InstallResult.New(readExternalPluginDir(dir.toFile(),pluginRegistry.installedManifests()))
        } catch(e:Throwable) {
            dir.toFile().deleteRecursively()
            throw e
        }
    }
}
