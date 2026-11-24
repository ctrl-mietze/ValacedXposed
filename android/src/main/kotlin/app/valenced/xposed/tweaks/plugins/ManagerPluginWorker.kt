package app.valenced.xposed.tweaks.plugins

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Process
import app.valenced.xposed.manager.DiscordConnectionEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import app.valenced.plugins.PluginStatesStore
import app.valenced.plugins.external.requireValidPluginId
import app.valenced.plugins.repos.RepoStore
import app.valenced.plugins.repos.SourcesStore
import app.valenced.plugins.repos.UserRepo
import app.valenced.xposed.BuildConfig
import app.valenced.xposed.PluginHttps
import app.valenced.xposed.ETagFetchResult
import app.valenced.plugins.repos.parseRepoIndex
import app.valenced.plugins.repos.sha256Hex
import app.valenced.xposed.manager.analysis.VencordTranslator
import app.valenced.xposed.manager.analysis.VencordCompiledRules
import app.valenced.xposed.manager.HookPolicy
import app.valenced.xposed.manager.HookMode
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweaks.plugins.internal.InternalPluginFlags
import app.valenced.xposed.tweaks.plugins.repos.refreshRepo
import app.valenced.xposed.tweaks.bridge.ValencedBridgeRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Native manager uses exactly the same plugin operations as the in-Discord UI.
 * No access to Discord messages, account data, scripts, or arbitrary bridge calls. */
internal class ManagerPluginProgress(val report: (String, Int) -> Unit) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ManagerPluginProgress>
}

internal object ManagerPluginWorker {
    private const val PREFIX = "valenced.plugins."
    private val allow = setOf("planInstall", "install", "setEnabled", "uninstall", "installLegacy")
    private val handlers = ConcurrentHashMap<String, suspend (List<Any?>) -> Any?>()
    private val started = AtomicBoolean(false)
    private data class Preview(val token: String, val plan: Map<*, *>, val at: Long)
    private var preview: Preview? = null

    fun register(name: String, handler: suspend (List<Any?>) -> Any?) {
        if (name.startsWith(PREFIX) && name.removePrefix(PREFIX) in allow) handlers[name] = handler
    }

    private suspend fun invoke(name: String, vararg args: Any?): Any? =
        (handlers[PREFIX + name] ?: error("Plugin-Bridge noch nicht bereit."))(args.toList())

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val ctx = context.applicationContext ?: context
        val uri = DiscordConnectionEndpoint.managerUri
        val visible=AtomicBoolean(false)
        val offered=AtomicBoolean(false)
        val wake=Channel<Unit>(Channel.CONFLATED)
        val heartbeatWake=Channel<Unit>(Channel.CONFLATED)
        fun signalWorker(){wake.trySend(Unit);heartbeatWake.trySend(Unit)}
        val observer=object:ContentObserver(null){
            override fun onChange(selfChange:Boolean){signalWorker()}
            override fun onChange(selfChange:Boolean,changed:Uri?){signalWorker()}
        }
        // The host's FileProvider deliberately does not map the lease URI to a
        // file. Grant only that exact URI; the manager never performs a file
        // operation. Its unstable client reference is held only while visible.
        pluginJobScope.launch(Dispatchers.IO){
            var lastGrant=0L
            while(true){
                try{
                    val config=ModuleSettings.current()
                    val enabled=HookPolicy.resolve(config.enabled,config.javascriptEnabled,config.safeMode)==HookMode.FULL
                    val state=ctx.contentResolver.call(uri,"plugin_heartbeat",null,Bundle().apply{
                        putInt("pid",Process.myPid())
                        putInt("manager_version",BuildConfig.VERSION_CODE)
                        putBoolean("theme_ready",ManagerThemeRequests.isReady())
                        putBoolean("enabled",enabled)
                        putBoolean("provider_offered",offered.get())
                    })
                    val nowVisible=state?.getBoolean("visible",false)==true
                    if(visible.getAndSet(nowVisible)!=nowVisible)wake.trySend(Unit)
                    val now=SystemClock.elapsedRealtime()
                    if(nowVisible&&enabled&&(!offered.get()||now-lastGrant>15000L)){
                        lastGrant=now
                        offered.set(runCatching{
                            val info=ctx.packageManager.resolveContentProvider(DiscordConnectionEndpoint.AUTHORITY,0)
                            check(info?.packageName==DiscordConnectionEndpoint.HOST &&
                                info.processName==DiscordConnectionEndpoint.HOST &&
                                info.name=="androidx.core.content.FileProvider" && info.grantUriPermissions)
                            ctx.grantUriPermission(BuildConfig.APPLICATION_ID,DiscordConnectionEndpoint.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            true
                        }.onFailure{android.util.Log.w("ValancedXLink","Provider lease unavailable",it)}.getOrDefault(false))
                        if(offered.get())heartbeatWake.trySend(Unit)
                    }
                }catch(e:Exception){visible.set(false)}
                withTimeoutOrNull(if(visible.get())900L else 4000L){heartbeatWake.receive()}
            }
        }
        pluginJobScope.launch(Dispatchers.IO) {
            var observing=false
            while (true) {
                if(!observing)observing=runCatching{
                    ctx.contentResolver.registerContentObserver(uri,false,observer)
                    true
                }.getOrDefault(false)
                try {
                    val job = ctx.contentResolver.call(uri, "plugin_claim", null, Bundle().apply { putInt("manager_version",BuildConfig.VERSION_CODE) })
                    val id = job?.getString("id")
                    if (id != null) {
                        val result = try {
                            val config = ModuleSettings.current()
                            check(HookPolicy.resolve(config.enabled, config.javascriptEnabled, config.safeMode) == HookMode.FULL) {
                                "Vexcord befindet sich im deaktivierten oder Recovery-Modus."
                            }
                            withTimeout(105_000L) {
                                withContext(ManagerPluginProgress { stage, percent ->
                                    runCatching {
                                        ctx.contentResolver.call(uri, "plugin_progress", null, Bundle().apply {
                                            putString("id", id)
                                            putString("stage", stage)
                                            putInt("percent", percent)
                                        })
                                    }
                                }) {
                                    handle(ctx, job.getString("operation").orEmpty(), JSONObject(job.getString("payload").orEmpty()))
                                }
                            }
                        } catch (e: Throwable) {
                            if(e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e
                            android.util.Log.w("ValancedX","Manager operation failed: "+job.getString("operation"),e)
                            JSONObject().put("error", (e.message ?: e.javaClass.simpleName).take(1500))
                        }
                        ctx.contentResolver.call(uri, "plugin_finish", null, Bundle().apply {
                            putString("id", id)
                            putString("result", result.toString())
                        })
                    }
                } catch (_: Exception) {
                    // Manager may be uninstalled or visibility not granted yet.
                }
                // Notification-driven normally, short bounded polling as fallback.
                withTimeoutOrNull(if(visible.get())600L else 4000L){wake.receive()}
            }
        }
    }

    private fun validateUrl(raw: String): String {
        val url = raw.trim().trimEnd('/')
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() &&
            parsed.userInfo == null && parsed.fragment == null && url.length <= 1000) { "Nur öffentliche HTTPS-Index-URLs." }
        return url
    }

    private var repoMeta: android.content.SharedPreferences? = null

    private suspend fun refreshTracked(url: String) {
        try {
            refreshRepo(url)
            repoMeta?.edit()?.putLong(sha256Hex(url)+"_at",System.currentTimeMillis())
                ?.remove(sha256Hex(url)+"_error")?.apply()
        } catch (e: Exception) {
            repoMeta?.edit()?.putLong(sha256Hex(url)+"_at",System.currentTimeMillis())
                ?.putString(sha256Hex(url)+"_error",(e.message ?: "Indexfehler").take(300))?.apply()
            throw e
        }
    }

    private fun legacyEnabled():Boolean=PluginStatesStore.active.isPluginEnabled("veyra.legacy.compat")
    private fun requireLegacyEnabled(){
        check(legacyEnabled()) {"Revenge-Kompatibilitätsmodus zuerst aktivieren."}
    }
    private val portAlternatives=mapOf(
        "vexcord.stereo-call" to setOf("mxtiy.audiofix"),
        "veyra.port.no-typing" to setOf("palmdevs.silent-typing"),
        "veyra.port.char-counter" to setOf("thororen.character-counter"),
        "veyra.port.clean-urls" to setOf("hakuxyz.clear-urls"),
        "veyra.port.pastelize" to setOf("dxckie.pastelize","veyra.vencord.generated.irccolors"),
        "veyra.vencord.generated.gifpaste" to setOf("dev.benjii.gtci","dev.benjii.gif-to-chat-input"),
        "veyra.vencord.generated.irccolors" to setOf("dxckie.pastelize"),
    )
    private fun conflictingPort(id:String):String? {
        val matches=portAlternatives[id].orEmpty()+portAlternatives.filterValues{id in it}.keys
        return matches.firstOrNull{PluginStatesStore.active.isPluginEnabled(it)}
    }

    private fun snapshot(message: String = ""): JSONObject {
        val repos = JSONArray()
        val available = JSONArray()
        val installed = JSONArray()
        for (repo in RepoStore.list()) {
            val index = RepoStore.cachedIndex(repo.url)
            repos.put(JSONObject().put("url", repo.url).put("enabled", repo.enabled)
                 .put("name", index?.name?.take(100) ?: repo.url).put("count", index?.plugins?.size ?: 0)
                .put("description",index?.description?.take(240) ?: "")
                .put("cached",index != null)
                .put("checkedAt",repoMeta?.getLong(sha256Hex(repo.url)+"_at",0L) ?: 0L)
                .put("error",repoMeta?.getString(sha256Hex(repo.url)+"_error","") ?: ""))
            if (!repo.enabled || index == null) continue
            for ((id, plugin) in index.plugins) {
                if (available.length() >= 350) break
                val version = plugin.channels["latest"] ?: continue
                val artifact = plugin.versions[version] ?: continue
                available.put(JSONObject().put("id", id).put("name", plugin.name.take(100))
                    .put("description", plugin.description.take(180)).put("author", plugin.author.take(80))
                    .put("version", version).put("repo", repo.url)
                    .put("artifactUrl",artifact.url).put("artifactSize",artifact.size).put("artifactSha256",artifact.sha256)
                    .put("themePack", plugin.versions[version]?.dependencies?.containsKey("valenced.api.themes") == true &&
                        id !in setOf("valenced.settings.themes","valenced.themes.legacy"))
                    .put("installed", pluginRegistry.factories.containsKey(id))
                    .put("installedVersion", pluginRegistry.factories[id]?.manifest?.version?.toString() ?: "")
                    .put("pending", pluginRegistry.pendingUpdates[id]?.toString() ?: ""))
            }
        }
        val active = PluginStatesStore.active
        for ((id, manifest) in pluginRegistry.knownManifests().toSortedMap()) {
            val factory = pluginRegistry.factories[id]
            val flags = factory?.internalFlags.orEmpty()
            val essential = InternalPluginFlags.ESSENTIAL in flags
            val enabled = essential || active.isPluginEnabled(id) ||
                (!active.hasPlugin(id) && InternalPluginFlags.ENABLED_BY_DEFAULT in flags)
            installed.put(JSONObject().put("id", id).put("name", manifest.name.take(100))
                .put("description", manifest.description.take(180)).put("author", manifest.author.take(80))
                .put("version", manifest.version.toString())
                .put("essential", essential).put("internal", InternalPluginFlags.INTERNAL in flags)
                .put("enabled", enabled).put("failed", id in pluginRegistry.discoveryFailures || id in pluginRegistry.bootErrors)
                .put("pending", pluginRegistry.pendingUpdates[id]?.toString() ?: "")
                .put("compilerRuntime", CompilerRuntimeState.snapshot(id))
                .put("presenceRuntime",if(id=="vexcord.bitchord-presence")BitChordPresenceStatus.snapshot() else null)
                .put("artifactSha256",SourcesStore[id]?.hash ?: "")
                .put("repo", SourcesStore[id]?.repo ?: ""))
        }
        return JSONObject().put("message", message).put("legacyEnabled",legacyEnabled()).put("repos", repos)
            .put("available", available).put("installed", installed)
    }

    /** The selected native equivalent is a real repository plugin, not
     * relabelled Desktop JS. Its root artifact is pinned to the reviewed ZIP.
     * Required libraries are resolved by the normal install dependency graph. */
    private suspend fun activateVencordRepository(rule:VencordTranslator.Rule){
        require(RepoStore.list().any{it.url==rule.repo&&it.enabled}){"Die zugeordnete Plugin-Quelle ist nicht aktiv: "+rule.repo}
        val root=RepoStore.cachedIndex(rule.repo)?.plugins?.get(rule.port)
            ?:error("Mobile-Gegenstück fehlt im Quellindex. Quellen synchronisieren.")
        val artifact=root.versions[rule.version]?:error("Geprüfte Mobile-Version nicht mehr im Index. Compiler-Inventar aktualisieren.")
        require(artifact.sha256==rule.artifactHash){"Mobile-Artefakt wurde geändert; erneut prüfen."}
        // Never replace an existing installation with a different artifact
        // through the translator. Normal plugin update UI remains available.
        val installed=pluginRegistry.factories[rule.port]
        if(installed!=null){
            require(installed.manifest.version.toString()==rule.version&&SourcesStore[rule.port]?.hash==rule.artifactHash) {
                "Andere Mobile-Version bereits installiert. Im Plugin Manager prüfen."
            }
        }else{
            val progress=kotlin.coroutines.coroutineContext[ManagerPluginProgress]
            progress?.report?.invoke("Mobile-Abhängigkeiten prüfen",-1)
            val plan=invoke("planInstall",rule.port,
                mapOf("targets" to mapOf(rule.port to mapOf("repo" to rule.repo,"compatible" to true)),
                    "skipMissingOptionals" to true)) as? Map<*,*> ?: error("Ungültiger Installationsplan.")
            val blockers=(plan["warnings"] as? List<*>).orEmpty().mapNotNull{it as? Map<*,*>}
                .filter{it["type"] in setOf("downgrade","breaks","conflict")||(it["type"]=="unresolved"&&it["optional"]!=true)}
            require(blockers.isEmpty()){blockers.joinToString("\n"){it["message"].toString()}}
            val actions=(plan["actions"] as? List<*>).orEmpty().mapNotNull{it as? Map<*,*>}
            val main=actions.firstOrNull{it["id"]==rule.port}?:error("Mobile-Hauptpaket fehlt im Installationsplan.")
            require(main["repo"]==rule.repo&&main["version"]==rule.version&&main["sha256"]==rule.artifactHash){"Resolver wählte eine ungeprüfte Mobile-Version."}
            for(action in actions){
                require(action["url"].toString().startsWith("https://")){"Nur HTTPS-Plugin-Dateien."}
                val actionId=action["id"].toString()
                require(pluginRegistry.factories[actionId]==null){"Vorhandene Abhängigkeit müsste geändert werden: "+actionId+". Bitte separat aktualisieren."}
                if(actionId!=rule.port){
                    val pin=VencordCompiledRules.dependencyPins[actionId]?:error("Nicht geprüfte Mobile-Abhängigkeit: "+actionId)
                    require(action["repo"]==pin.repo&&action["version"]==pin.version&&action["sha256"]==pin.hash){"Abhängigkeit hat sich geändert: "+actionId}
                }
            }
            progress?.report?.invoke("Mobile-Port installieren",0)
            invoke("install",plan)
        }
        val graph=pluginRegistry.dependencies
        val order=mutableListOf<String>()
        val visiting=mutableSetOf<String>()
        val done=mutableSetOf<String>()
        fun visit(id:String){
            if(id in done)return
            check(visiting.add(id)){"Zyklische Mobile-Abhängigkeit: "+id}
            require(pluginRegistry.factories.containsKey(id)){"Mobile-Abhängigkeit fehlt: "+id}
            require(id !in pluginRegistry.pendingUpdates){"Discord-Neustart für Abhängigkeit nötig: "+id}
            if(id!=rule.port&&id !in setOf("valenced.api","discord")){
                val pin=VencordCompiledRules.dependencyPins[id]?:error("Nicht geprüfte Mobile-Abhängigkeit: "+id)
                require(pluginRegistry.factories[id]?.manifest?.version?.toString()==pin.version&&SourcesStore[id]?.hash==pin.hash){"Andere Abhängigkeitsversion installiert: "+id}
            }
            for(dep in graph.requiredDependencies(id)){
                graph.problem(id,dep)?.let{error(it.reason)}
                visit(dep)
            }
            visiting.remove(id);done.add(id);order.add(id)
        }
        visit(rule.port)
        val changed=mutableListOf<String>()
        try{
            for(id in order){
                val flags=pluginRegistry.factories[id]?.internalFlags.orEmpty()
                if(InternalPluginFlags.ESSENTIAL in flags||PluginStatesStore.active.isPluginEnabled(id)||
                    (!PluginStatesStore.active.hasPlugin(id)&&InternalPluginFlags.ENABLED_BY_DEFAULT in flags))continue
                invoke("setEnabled",id,true,id==rule.port)
                changed.add(id)
            }
        }catch(e:Throwable){
            for(id in changed.asReversed())runCatching{invoke("setEnabled",id,false,false)}
            throw e
        }
    }

    private suspend fun handle(context: Context, op: String, data: JSONObject): JSONObject {
        // Seed the same official community source once, respecting any later
        // explicit removal. Third-party sources are always opt-in.
        val defaults = context.getSharedPreferences("valancedx_manager_plugins", Context.MODE_PRIVATE)
        repoMeta=defaults
        if (!defaults.getBoolean("seeded", false)) {
            val official = "https://community.valenced.app/index.json"
            if (RepoStore.list().none { it.url == official }) {
                RepoStore.set(listOf(UserRepo(official)) + RepoStore.list())
            }
            defaults.edit().putBoolean("seeded", true).apply()
            if (RepoStore.list().any { it.url == official && it.enabled } && RepoStore.cachedIndex(official) == null) {
                runCatching { refreshTracked(official) }
            }
        }
        // Add our first-party catalog once, preserving all existing source
        // records, ordering, enabled flags and any later explicit removal.
        if(!defaults.getBoolean("vexcord_catalog_seeded",false)){
            val official="https://raw.githubusercontent.com/ctrl-mietze/Vexcord/main/plugins/index.json"
            if(RepoStore.list().none{it.url==official})RepoStore.set(RepoStore.list()+UserRepo(official))
            defaults.edit().putBoolean("vexcord_catalog_seeded",true).apply()
            if(RepoStore.list().any{it.url==official&&it.enabled}&&RepoStore.cachedIndex(official)==null){
                runCatching{refreshTracked(official)}
            }
        }
        when (op) {
            "analysis_prepare" -> return JSONObject().put("analysisInput",AnalysisExport.prepare(context.applicationInfo.dataDir,data.getString("id")))
            "analysis_chunk" -> return JSONObject().put("analysisChunk",AnalysisExport.chunk(data.getString("token"),data.getInt("offset")))
            "vencord_activate","vencord_disable" -> {
                val id=data.getString("id")
                val rule=VencordTranslator.rules[id]?:error("Kein Mobile-Adapter vorhanden.")
                if(op=="vencord_activate"){
                    val configuration=context.contentResolver.call(Uri.parse("content://"+BuildConfig.APPLICATION_ID+".settings"),"configuration",null,null)
                    require(configuration?.getBoolean("vencord_integration",true)==true){"Vencord-Integration zuerst unter Module aktivieren."}
                    require(data.optString("sha256")==rule.sourceHash&&data.optString("treeHash")==rule.treeHash){"Vencord-Quellbaum nicht unterstützt."}
                    conflictingPort(rule.port)?.let{error("Gleichartiges Plugin aktiv: "+it+". Zuerst deaktivieren.")}
                    if(rule.kind=="repository"){
                        activateVencordRepository(rule)
                    }else{
                        require(pluginRegistry.factories.containsKey(rule.port)){"Mobile-Port fehlt."}
                        if(rule.kind=="bundled"&&!legacyEnabled())invoke("setEnabled","veyra.legacy.compat",true,true)
                        invoke("setEnabled",rule.port,true,true)
                    }
                    return snapshot(if(rule.kind=="repository")"Mobile-Gegenstück aktiviert. Falls nötig Discord neu starten."
                        else "Mobile-Adapter aktiviert. Falls nötig Discord neu starten.")
                }
                require(pluginRegistry.factories.containsKey(rule.port)){"Mobile-Port nicht installiert."}
                invoke("setEnabled",rule.port,false,false)
                return snapshot("Vencord-Mobile-Port deaktiviert.")
            }
            "legacy_mode" -> {
                val enabled=data.getBoolean("enabled")
                if(!enabled){
                    // Include older imports created before the compatibility dependency existed.
                    for(id in pluginRegistry.factories.keys.filter{it.startsWith("veyra.legacy.")&&it!="veyra.legacy.compat"}){
                        if(PluginStatesStore.active.isPluginEnabled(id))invoke("setEnabled",id,false,false)
                    }
                }
                invoke("setEnabled","veyra.legacy.compat",enabled,enabled)
                return snapshot(if(enabled)"Revenge-Modus aktiviert. Einzelne Ports auswählen." else "Revenge-Modus deaktiviert. Discord neu starten.")
            }
            "legacy_preview" -> {
                requireLegacyEnabled()
                return LegacyRevengeImporter.preview(data.getString("url"))
            }
            "legacy_install" -> {
                requireLegacyEnabled()
                invoke("installLegacy",data.getString("token"))
                return snapshot("Legacy-Plugin importiert. Unter Installiert gezielt aktivieren.")
            }
            "theme_snapshot", "theme_import", "theme_apply", "theme_reset", "theme_remove", "theme_font_import", "theme_background" -> {
                if(op=="theme_import"){
                    val url=validateUrl(data.getString("url"))
                    val fetched=PluginHttps.fetch(url,null,15_000L,250_000L) as? ETagFetchResult.Fetched
                        ?: error("Keine Theme-Daten empfangen.")
                    require(fetched.bytes.size in 1..250_000) {"Theme-JSON ist zu groß."}
                    data.put("url",url).put("theme",JSONObject(fetched.bytes.decodeToString()))
                }
                if(op=="theme_font_import"){
                    val url=validateUrl(data.getString("url"))
                    data.put("url",url).put("pack",ManagerThemeAssets.importFonts(context,url))
                }
                if(op=="theme_background"&&data.optString("source")=="custom"){
                    val motion=data.optString("motion")
                    val path=if(motion.isNotBlank())ManagerThemeAssets.motionPath(context,motion)
                        else validateUrl(data.getString("path"))
                    data.put("path",path)
                }
                kotlin.coroutines.coroutineContext[ManagerPluginProgress]?.report?.invoke("Discord-Theme-Bridge", -1)
                val result=ManagerThemeRequests.request(op,data)
                if(result.has("error"))return result
                return snapshot(if(op=="theme_snapshot")"" else if(result.optJSONObject("themeState")?.optBoolean("ready")==true) "Theme-Einstellung gespeichert." else "")
                    .put("themeState",result.getJSONObject("themeState"))
            }
            "snapshot" -> return snapshot()
            "refresh" -> {
                val errors = mutableListOf<String>()
                for (repo in RepoStore.list().filter { it.enabled }) {
                    runCatching { refreshTracked(repo.url) }.onFailure { errors.add(repo.url + ": " + it.message) }
                }
                return snapshot(if (errors.isEmpty()) "Quellen aktualisiert." else errors.joinToString("\n").take(1400))
            }
            "refresh_repo" -> {
                val url=data.getString("url")
                require(RepoStore.list().any { it.url==url }) { "Unbekannte Quelle." }
                val message=runCatching { refreshTracked(url); "Quelle aktualisiert." }.getOrElse { "Indexfehler: "+it.message }
                return snapshot(message.take(1200))
            }
            "preview_repo" -> {
                val url=validateUrl(data.getString("url"))
                val fetched=PluginHttps.fetch(url,null,15_000L,2_000_000L) as? ETagFetchResult.Fetched
                    ?: error("Keine Indexdaten empfangen.")
                require(fetched.bytes.size<=2_000_000) { "Repository-Index ist zu groß." }
                val index=parseRepoIndex(fetched.bytes.decodeToString(),url,RepoStore.log)
                return JSONObject().put("sourcePreview",JSONObject().put("url",url).put("name",index.name.take(100))
                    .put("description",index.description.take(240)).put("count",index.plugins.size))
            }
            "add_repo" -> {
                val url = validateUrl(data.getString("url"))
                val list = RepoStore.list()
                if (list.none { it.url == url }) {
                    require(list.size < 24) { "Maximal 24 Quellen." }
                    RepoStore.set(list + UserRepo(url))
                }
                val note = runCatching { refreshTracked(url); "Quelle hinzugefügt." }
                    .getOrElse { "Quelle gespeichert, Indexfehler: " + it.message }
                return snapshot(note.take(1500))
            }
            "toggle_repo", "remove_repo" -> {
                val url = data.getString("url")
                val repos = RepoStore.list()
                require(repos.any { it.url == url }) { "Unbekannte Quelle." }
                RepoStore.set(if (op == "remove_repo") repos.filterNot { it.url == url }
                    else repos.map { if (it.url == url) it.copy(enabled = !it.enabled) else it })
                return snapshot("Quellen gespeichert.")
            }
            "plan", "install_direct" -> {
                val id = data.getString("id")
                requireValidPluginId(id)
                val repo = data.getString("repo")
                require(RepoStore.list().any { it.url == repo && it.enabled }) { "Quelle nicht aktiv." }
                val progress = kotlin.coroutines.coroutineContext[ManagerPluginProgress]
                progress?.report?.invoke("Abhängigkeiten prüfen", -1)
                val plan = invoke("planInstall", id,
                    mapOf("targets" to mapOf(id to mapOf("repo" to repo, "compatible" to true)),
                        "skipMissingOptionals" to true)) as? Map<*, *> ?: error("Ungültiger Installationsplan.")
                val actions = plan["actions"] as? List<*> ?: emptyList<Any>()
                val summary = JSONArray()
                for (a in actions) {
                    val item = a as? Map<*, *> ?: continue
                    // Refuse non-HTTPS artifact URLs in native manager.
                    require(item["url"].toString().startsWith("https://")) { "Unsicherer Download." }
                    summary.put(JSONObject().put("id", item["id"].toString())
                        .put("version", item["version"].toString()).put("repo", item["repo"].toString()))
                }
                if (op == "install_direct") {
                    // A tap is the explicit install authorization. Do not silently
                    // downgrade or break existing plugins to satisfy an install.
                    val blockers = (plan["warnings"] as? List<*>).orEmpty()
                        .mapNotNull { it as? Map<*, *> }
                        .filter { it["type"] in setOf("downgrade", "breaks", "conflict") ||
                            (it["type"] == "unresolved" && it["optional"] != true) }
                    require(blockers.isEmpty()) {
                        blockers.joinToString("\n") { it["message"].toString() }
                    }
                    if (actions.isEmpty()) return snapshot("Bereits installiert und aktuell.")
                    progress?.report?.invoke("Download vorbereiten", 0)
                    invoke("install", plan)
                    progress?.report?.invoke("Installation abschließen", 100)
                    return snapshot("Installation abgeschlossen. Neue Plugins bleiben deaktiviert.")
                }
                val token = UUID.randomUUID().toString()
                preview = Preview(token, plan, SystemClock.elapsedRealtime())
                return JSONObject().put("token", token).put("actions", summary)
                    .put("warnings", JSONArray((plan["warnings"] as? List<*>).orEmpty().map { it.toString().take(300) }))
            }
            "install" -> {
                val pending = preview ?: error("Installationsplan abgelaufen.")
                require(pending.token == data.getString("token") && SystemClock.elapsedRealtime() - pending.at < 120_000L) {
                    "Installationsplan abgelaufen."
                }
                preview = null
                invoke("install", pending.plan)
                return snapshot("Installation abgeschlossen. Neue Plugins bleiben deaktiviert.")
            }
            "enable", "disable", "uninstall" -> {
                val id = data.getString("id")
                requireValidPluginId(id)
                require(pluginRegistry.factories.containsKey(id)) { "Plugin nicht verfügbar." }
                if(op=="enable"){
                    if(id.startsWith("veyra.port.")||(id.startsWith("veyra.legacy.")&&id!="veyra.legacy.compat"))requireLegacyEnabled()
                    conflictingPort(id)?.let{error("Ein gleichartiges Plugin ist bereits aktiv: "+it+". Erst deaktivieren.")}
                }
                if (op == "uninstall") invoke("uninstall", id)
                else invoke("setEnabled", id, op == "enable", op == "enable")
                return snapshot("Gespeichert. Discord neu starten, um Änderungen vollständig zu übernehmen.")
            }
            else -> error("Unbekannte Plugin-Aktion.")
        }
    }
}
