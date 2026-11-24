package app.valenced.xposed.manager

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import android.view.WindowInsets
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.valenced.xposed.BuildConfig
import app.valenced.xposed.R
import org.json.JSONArray
import org.json.JSONObject
import android.widget.EditText
import android.widget.ProgressBar
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** An offline-first native manager. No Discord APK patcher or telemetry backend. */
class ValancedXActivity : Activity() {
    private var currentTheme = VxThemes.all.first()
    private var cardRadius = 20
    private var glowEnabled = false
    private var compact = false
    private val backgroundColor get() = currentTheme.background
    private val surfaceColor get() = currentTheme.surface
    private val raisedColor get() = currentTheme.raised
    private val borderColor get() = currentTheme.border
    private val accentColor get() = currentTheme.accent
    private val mintColor get() = currentTheme.secondary
    private val warmColor = Color.rgb(247, 198, 107)
    private val errorColor = Color.rgb(255, 124, 145)
    private val whiteColor get() = currentTheme.text
    private val mutedColor get() = currentTheme.muted
    private val onAccentColor get() = VxThemes.onColor(accentColor)
    private val selectedSurface get() = VxThemes.blend(raisedColor,accentColor,.10f)
    private val selectedStroke get() = VxThemes.blend(borderColor,accentColor,.35f)

    private fun readTheme() {
        val base = VxThemes.get(prefs.getString("ui_theme_id","dark"))
        val intensity = (prefs.getInt("ui_card_intensity",50).coerceIn(0,100)-50)/200f
        fun tone(color:Int):Int = VxThemes.blend(color, if(intensity>=0) base.text else base.background, kotlin.math.abs(intensity))
        currentTheme = base.copy(
            background=if(prefs.getBoolean("ui_amoled_enabled",false)) Color.BLACK else base.background,
            surface=tone(base.surface), raised=tone(base.raised),
            accent=prefs.getInt("ui_accent_color",base.accent),
        )
        cardRadius=prefs.getInt("ui_card_radius",20).coerceIn(12,30)
        glowEnabled=prefs.getBoolean("ui_glow",base.id=="glass"||base.id=="neon")
        compact=prefs.getBoolean("ui_compact",false)
        window.statusBarColor=backgroundColor
        window.navigationBarColor=backgroundColor
    }

    private val tabs = arrayOf("Start", "Module", "Plugins", "Themes", "Diagnose", "Info")
    private var currentTab = 0
    private var scrollView: ScrollView? = null
    private var connectionGranted = false
    private val liveConnection by lazy {DiscordConnectionController(applicationContext)}
    private var connectionText:TextView?=null
    private var connectionDot:View?=null
    private val connectionSyncs=mutableSetOf<String>()
    private var connectionWasOnline=false
    private val connectionTick=object:Runnable{
        override fun run(){
            ManagerConnection.renew()
            updateConnectionStatus()
            val peer=ManagerConnection.status()
            val online=peer.online()
            if(!online&&connectionWasOnline)connectionSyncs.clear()
            connectionWasOnline=online
            // One fresh read per view/peer session, never interrupt a real operation.
            val request=when{
                currentTab==3&&themePage==0&&peer.themeReady->"theme_snapshot"
                currentTab==2->"snapshot"
                else->null
            }
            val key=peer.pid.toString()+"|"+request.orEmpty()
            if(online&&request!=null&&key !in connectionSyncs&&pluginJob==null&&!restartRunning&&analysisJob?.isActive!=true){
                connectionSyncs.add(key)
                pluginAction(request)
            }
            mainHandler.postDelayed(this,650L)
        }
    }
    private fun updateConnectionStatus(){
        val peer=ManagerConnection.status()
        val online=peer.online()
        val staleVersion=peer.fresh()&&peer.version!=BuildConfig.VERSION_CODE
        val text=when{
            online&&peer.themeReady->"Discord verbunden"
            online->"Discord · JS startet"
            staleVersion->"Discord neu starten"
            peer.fresh()&&!peer.enabled->"Modul deaktiviert"
            else->"Discord verbinden …"
        }
        val tint=if(online&&peer.themeReady)mintColor else warmColor
        connectionText?.let{v->if(v.text.toString()!=text||v.currentTextColor!=tint){v.text=text;v.setTextColor(tint)}}
        connectionDot?.let{v->if(v.tag!=tint){v.background=rounded(tint,tint,6);v.tag=tint}}
    }
    override fun onStart(){
        super.onStart()
        connectionGranted=grantConnection()
        connectionSyncs.clear()
        connectionWasOnline=false
        liveConnection.start()
        mainHandler.removeCallbacks(connectionTick)
        mainHandler.post(connectionTick)
    }
    override fun onStop(){
        mainHandler.removeCallbacks(connectionTick)
        analysisJob?.cancel()
        liveConnection.stop()
        super.onStop()
    }
    override fun onDestroy(){
        mainHandler.removeCallbacks(connectionTick)
        analysisScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        liveConnection.destroy()
        super.onDestroy()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { if (currentTab != 2 && currentTab != 3) render(preserveScroll = true) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("last_") == true || key?.startsWith("total_") == true ||
            key == "event_history" || key == "error_streak") {
            mainHandler.removeCallbacks(refresh)
            mainHandler.postDelayed(refresh, 180)
        }
    }

    private val prefs by lazy {
        getSharedPreferences(SettingsProvider.PREFS, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readTheme()
        if(pluginData==null)pluginData=runCatching {
            JSONObject(java.io.File(filesDir,"manager-catalog.json").readText())
        }.getOrNull()
        discordThemeState=pluginData?.optJSONObject("themeState")
        currentTab = (savedInstanceState?.getInt("tab") ?: prefs.getInt("manager_tab",0)).coerceIn(0, tabs.lastIndex)
        pluginPage = (savedInstanceState?.getInt("pluginPage") ?: prefs.getInt("manager_plugin_page",0)).coerceIn(0,5)
        themePage = (savedInstanceState?.getInt("themePage") ?: prefs.getInt("manager_theme_page",0)).coerceIn(0,1)
        themeKind = savedInstanceState?.getString("themeKind") ?: prefs.getString("manager_theme_kind","themes") ?: "themes"
        pluginQuery = savedInstanceState?.getString("pluginQuery").orEmpty()
        pluginJob = savedInstanceState?.getString("pluginJob")
        pluginTarget = savedInstanceState?.getString("pluginTarget")
        pluginNote = savedInstanceState?.getString("pluginNote").orEmpty()
        restoredScrollY = savedInstanceState?.getInt("scrollY") ?: 0
        if (!prefs.getBoolean("welcome_seen", false)) {
            prefs.edit().putBoolean("welcome_seen", true).apply()
            showWelcome()
        }
    }

    override fun onResume() {
        super.onResume()
        connectionGranted = grantConnection()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        render(preserveScroll = true)
        if (pluginJob != null) {mainHandler.removeCallbacks(pluginPoll);mainHandler.post(pluginPoll)}
        updateConnectionStatus()
    }

    override fun onPause() {
        prefs.edit().putInt("manager_tab",currentTab).putInt("manager_plugin_page",pluginPage).putInt("manager_theme_page",themePage).putString("manager_theme_kind",themeKind).apply()
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        mainHandler.removeCallbacks(refresh)
        mainHandler.removeCallbacks(pluginPoll)
        super.onPause()
    }

    // Grant access only to a harmless, dedicated URI. Android's documented
    // URI-grant visibility mechanism makes this provider discoverable to Discord.
    // The provider still authenticates every call using Binder's actual caller UID.
    private fun grantConnection(): Boolean = runCatching {
        if (discordVersion() == null) return false
        grantUriPermission(
            "com.discord",
            Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".settings/connection"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        true
    }.getOrElse { false }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", currentTab)
        outState.putInt("pluginPage", pluginPage)
        outState.putInt("themePage",themePage)
        outState.putString("themeKind",themeKind)
        outState.putString("pluginQuery", pluginQuery)
        outState.putString("pluginJob", pluginJob)
        outState.putString("pluginTarget", pluginTarget)
        outState.putString("pluginNote", pluginNote)
        outState.putInt("scrollY", scrollView?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + 0.5f).toInt()

    private fun rounded(fill: Int = surfaceColor, stroke: Int = borderColor, radius: Int = 18) =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(value: String, size: Float, color: Int = whiteColor, strong: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(
                if (strong) "sans-serif-medium" else "sans-serif",
                if (strong) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun interactive(fill: Int, stroke: Int, radius: Int = 14) =
        RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), rounded(fill, stroke, radius), null)

    private fun eyebrow(parent: LinearLayout, value: String) {
        add(parent, label(value.uppercase(Locale.ROOT), 11f, mutedColor, true).apply {
            letterSpacing = .12f
        }, top = 20, bottom = 2)
    }

    private fun statusRow(parent: LinearLayout, name: String, value: String, color: Int) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(name, 13f, mutedColor), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(value, 13f, color, true).apply { gravity = Gravity.END })
        add(parent, row, top = 6, bottom = 6)
    }

    private fun add(parent: LinearLayout, view: View, top: Int = 0, bottom: Int = 0) {
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(top), 0, dp(bottom))
        parent.addView(view, params)
    }

    private fun card(parent: LinearLayout, top: Int = 12, tint: Int = surfaceColor, build: LinearLayout.() -> Unit) {
        val child = column().apply {
            val padding=dp(if(compact) 12 else 16)
            setPadding(padding,padding,padding,padding)
            background = rounded(tint, if(glowEnabled) VxThemes.blend(borderColor,accentColor,.32f) else borderColor, cardRadius)
        }
        child.build()
        add(parent, child, top)
    }

    private fun heading(parent: LinearLayout, title: String) {
        add(parent, label(title, 18f, whiteColor, true), top = 25)
    }

    private fun line(parent: LinearLayout) {
        val view = View(this).apply { setBackgroundColor(borderColor) }
        val params = LinearLayout.LayoutParams(-1, dp(1))
        params.setMargins(0, dp(12), 0, dp(12))
        parent.addView(view, params)
    }

    private fun button(value: String, primary: Boolean = false, destructive: Boolean = false, action: () -> Unit) =
        label(
            value, 14f,
            if (primary) onAccentColor else if (destructive) errorColor else whiteColor,
            true,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(if(compact) 10 else 13), dp(12), dp(if(compact) 10 else 13))
            background = interactive(
                if (primary) accentColor else raisedColor,
                if (primary) accentColor else borderColor, 13,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun applySwitchPalette(toggle: Switch) {
        val states=arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf(android.R.attr.state_checked),intArrayOf())
        toggle.thumbTintList=ColorStateList(states,intArrayOf(borderColor,accentColor,mutedColor))
        toggle.trackTintList=ColorStateList(states,intArrayOf(surfaceColor,VxThemes.blend(raisedColor,accentColor,.36f),borderColor))
    }

    private fun setting(parent: LinearLayout, name: String, description: String, key: String, fallback: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val content = column()
        add(content, label(name, 15f, whiteColor, true))
        add(content, label(description, 12f, mutedColor), top = 5)
        row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
        val enabled = prefs.getBoolean(key, fallback)
        val toggle = Switch(this).apply {
            isChecked = enabled
            contentDescription = name
            applySwitchPalette(this)
            setOnCheckedChangeListener { _, checked ->
                val editor = prefs.edit().putBoolean(key, checked)
                if (key == "safe_mode" && !checked) {
                    editor.putBoolean("auto_recovery", false).putInt("error_streak", 0)
                }
                editor.apply()
                render(preserveScroll = true)
                Toast.makeText(
                    this@ValancedXActivity,
                    "Nach einem vollständigen Discord-Neustart wirksam",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        row.addView(toggle)
        add(parent, row, top = 7, bottom = 7)
    }

    private fun state(): String = prefs.getString("last_state", "").orEmpty()

    private fun statusLabel(value: String = state()): String = when (value) {
        "bundle_injected" -> "Hermes geladen"
        "hooks_ready" -> "Native Hooks registriert"
        "framework_attached" -> "Xposed-Einstieg erkannt"
        "module_path_missing" -> "Modul-Dateipfad nicht gefunden"
        "script_loader_missing" -> "React-Native-Loader nicht gefunden"
        "native_only" -> "Minimalmodus aktiv"
        "safe_mode" -> "Recovery-Modus aktiv"
        "disabled" -> "Beim letzten Start deaktiviert"
        "initializing" -> "Letzter Start: Initialisierung"
        "bundle_error" -> "Fehler beim Hermes-Bundle"
        "js_runtime_error" -> "JavaScript-Laufzeitfehler in Discord"
        "bootstrap_error" -> "Fehler bei der Hook-Initialisierung"
        else -> "Verbindung ausstehend"
    }

    private fun stateColor(): Int = when (state()) {
        "bundle_injected" -> mintColor
        "hooks_ready", "framework_attached" -> accentColor
        "script_loader_missing", "bundle_error", "bootstrap_error", "module_path_missing", "js_runtime_error" -> errorColor
        "safe_mode", "native_only" -> warmColor
        else -> mutedColor
    }

    private fun time(millis: Long): String {
        if (millis <= 0L) return "Noch nicht vorhanden"
        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT, DateFormat.SHORT, Locale.GERMANY,
        ).format(Date(millis))
    }

    private fun discordVersion(): String? = try {
        packageManager.getPackageInfo("com.discord", 0).versionName ?: "unbekannt"
    } catch (_: Exception) {
        null
    }

    private fun eventHistory(): JSONArray = runCatching {
        JSONArray(prefs.getString("event_history", "[]"))
    }.getOrElse { JSONArray() }

    private fun details(parent: LinearLayout, title: String, value: String, color: Int = whiteColor) {
        add(parent, label(title, 12f, mutedColor))
        add(parent, label(value, 14f, color, true), top = 5)
    }

    private fun stats(parent: LinearLayout) {
        val row = LinearLayout(this)
        val all = arrayOf(
            Triple("Starts", prefs.getInt("total_starts", 0), accentColor),
            Triple("Bundles", prefs.getInt("total_successes", 0), mintColor),
            Triple("Fehler", prefs.getInt("total_errors", 0), warmColor),
        )
        for ((i, item) in all.withIndex()) {
            val cell = column().apply {
                setPadding(dp(10), dp(13), dp(9), dp(13))
                background = rounded(raisedColor, borderColor, 14)
                add(this, label(item.second.toString(), 24f, item.third, true))
                add(this, label(item.first, 11f, mutedColor), top = 5)
            }
            row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 0) leftMargin = dp(7)
            })
        }
        add(parent, row, top = 12)
    }

    private fun dashboard(parent: LinearLayout) {
        val seen = prefs.getLong("last_time", 0L) > 0L
        eyebrow(parent, "Übersicht")
        card(parent, top = 8) {
            val top = LinearLayout(this@ValancedXActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(View(this@ValancedXActivity).apply {
                background = rounded(stateColor(), stateColor(), 8)
            }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
            top.addView(label(if (seen) "LETZTES EREIGNIS" else "KEIN LIVE-STATUS", 11f, stateColor(), true))
            add(this, top)
            add(this, label(statusLabel(), 24f, whiteColor, true), top = 10)
            add(this, label(if (seen) time(prefs.getLong("last_time", 0L))
                else "Öffne Discord nach der Modul-Aktivierung. Ein fehlendes Ereignis bedeutet nicht automatisch, dass LSPosed inaktiv ist.",
                13f, mutedColor), top = 7)
            line(this)
            statusRow(this, "Discord", discordVersion() ?: "Nicht installiert", if (discordVersion() != null) mintColor else warmColor)
            statusRow(this, "IPC-Zugriff", if (connectionGranted) "URI erteilt" else "Nicht erteilt",
                if (connectionGranted) accentColor else warmColor)
            statusRow(this, "Recovery", if (prefs.getBoolean("safe_mode", false)) "Aktiv" else "Aus",
                if (prefs.getBoolean("safe_mode", false)) warmColor else mutedColor)
            twoActions(this,button("Discord öffnen",true){openDiscord()},button("Neu starten"){restartDiscord()},top=12)
        }
        eyebrow(parent, "Aktivität")
        stats(parent)
        eyebrow(parent, "Einrichtung & Diagnose")
        card(parent, top = 8) {
            add(this, button("LSPosed-Scope einrichten") { showSetup() })
            add(this, button("Status aktualisieren") {
                connectionGranted = grantConnection()
                render(preserveScroll = true)
            }, top = 8)
            add(this, button("Diagnose kopieren") { copyDiagnostics() }, top = 8)
        }
        eyebrow(parent, "Installation")
        card(parent, top = 8) {
            statusRow(this, "Manager", BuildConfig.VERSION_NAME, accentColor)
            statusRow(this, "Android", android.os.Build.VERSION.RELEASE, whiteColor)
            statusRow(this, "Paket", "com.discord", mutedColor)
            add(this, label("Die Statusanzeige unterscheidet den erteilten IPC-Zugriff von tatsächlichen Hook-Ereignissen.", 12f, mutedColor), top = 8)
        }
    }

    private fun modules(parent: LinearLayout) {
        eyebrow(parent, "Hook-Konfiguration")
        card(parent) {
            setting(this, "ValancedX-Erweiterungen", "Steuert die Hermes-Injektion. Der native Xposed-Hook wird früh registriert.", "hook_enabled", true)
            line(this)
            setting(this, "Hermes-Bundle", "Lädt den ValancedX-Code vor Discords eigener JavaScript-Datei.", "javascript_enabled", true)
            line(this)
            setting(this, "Recovery-Modus", "Verhindert das Hermes-Bundle; die frühe native Hook-Registrierung bleibt erhalten.", "safe_mode", false)
        }
        if (prefs.getBoolean("auto_recovery", false)) {
            card(parent, tint = VxThemes.blend(surfaceColor,errorColor,.09f)) {
                add(this, label("Automatischer Schutz aktiv", 16f, warmColor, true))
                add(
                    this,
                    label(
                        "Nach drei gemeldeten Fehlern in Folge wurde Recovery aktiviert. " +
                        "Prüfe die Diagnose, bevor du den Modus wieder deaktivierst.",
                        13f, whiteColor,
                    ),
                    top = 9,
                )
            }
        }
        eyebrow(parent, "Datenschutz")
        card(parent) {
            setting(this, "Ereignishistorie", "Maximal 32 lokale Status-Ereignisse.", "history_enabled", true)
            line(this)
            setting(this, "Kurze Fehlerdetails", "Speichert Fehlermeldungen nur auf diesem Gerät.", "diagnostics_enabled", true)
            add(this, label("Kein eigener Telemetrie-Upload. Änderungen gelten beim nächsten Discord-Neustart.", 12f, mutedColor), top = 10)
        }
        eyebrow(parent, "Wiederherstellung")
        card(parent) {
            add(this, button("Recovery aktivieren") {
                prefs.edit().putBoolean("safe_mode", true).apply()
                render()
            })
            add(this, button("Einstellungen zurücksetzen", destructive = true) {
                AlertDialog.Builder(this@ValancedXActivity)
                    .setTitle("Konfiguration zurücksetzen?")
                    .setMessage("Setzt die Hook-Modi und Diagnose-Schalter zurück.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Zurücksetzen") { _, _ ->
                        prefs.edit()
                            .remove("hook_enabled").remove("javascript_enabled")
                            .remove("safe_mode").remove("auto_recovery")
                            .remove("error_streak").remove("history_enabled")
                            .remove("diagnostics_enabled").apply()
                        render()
                    }.show()
            }, top = 9)
        }
    }

    private fun diagnostics(parent: LinearLayout) {
        eyebrow(parent, "Diagnosezentrale")
        card(parent) {
            add(this, label(statusLabel(), 19f, stateColor(), true))
            add(this, label("Zuletzt: " + time(prefs.getLong("last_time", 0L)), 12f, mutedColor), top = 8)
            add(this, label("Fehlerfolge: " + prefs.getInt("error_streak", 0) + " / 3", 12f, mutedColor), top = 7)
            add(this, label("IPC-URI: " + if (connectionGranted) "erteilt (kein Live-Nachweis)" else "nicht erteilt", 12f, mutedColor), top = 7)
            val last = prefs.getString("last_details", "").orEmpty()
            if (last.isNotBlank()) add(this, label(last, 12f, warmColor), top = 10)
        }
        stats(parent)
        card(parent) {
            add(this, button("Bericht kopieren", true) { copyDiagnostics() })
            add(this, button("Bericht teilen") { shareDiagnostics() }, top = 9)
        }
        eyebrow(parent, "Ereignisverlauf")
        card(parent) {
            val events = eventHistory()
            if (events.length() == 0) {
                add(this, label("Noch keine gespeicherten Ereignisse.", 13f, mutedColor))
            }
            val first = (events.length() - 12).coerceAtLeast(0)
            for (i in events.length() - 1 downTo first) {
                val item = events.optJSONObject(i) ?: continue
                if (i != events.length() - 1) line(this)
                add(this, label(statusLabel(item.optString("state")), 14f, whiteColor, true))
                add(this, label(time(item.optLong("at")), 11f, mutedColor), top = 4)
                val note = item.optString("details")
                if (note.isNotBlank()) add(this, label(note, 12f, mutedColor), top = 5)
            }
        }
        card(parent) {
            add(this, button("Ereignishistorie löschen", destructive = true) {
                AlertDialog.Builder(this@ValancedXActivity)
                    .setTitle("Diagnose-Verlauf löschen?")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Löschen") { _, _ ->
                        prefs.edit().remove("event_history")
                            .remove("last_state").remove("last_time")
                            .remove("last_details").apply()
                        render()
                    }.show()
            })
        }
    }

    private fun about(parent: LinearLayout) {
        eyebrow(parent, "Über ValancedX")
        card(parent, tint = surfaceColor) {
            add(this, label("ValancedX", 25f, whiteColor, true))
            add(this, label("Release " + BuildConfig.VERSION_NAME, 13f, accentColor), top = 6)
            add(
                this,
                label(
                    "Native Android-Xposed-Erweiterung für Discord. Eigenständiger Manager " +
                    "mit Recovery-System, Diagnose und integrierter Hermes-Plugin-Bridge.",
                    14f, whiteColor,
                ),
                top = 16,
            )
            add(this, label("Paket: " + BuildConfig.APPLICATION_ID, 12f, mutedColor), top = 15)
        }
        eyebrow(parent, "Open Source")
        card(parent) {
            add(
                this,
                label(
                    "ValacedXposed basiert auf dem quelloffenen Valenced-Core (GPL-3.0). " +
                    "Die neue Manager-Oberfläche und Projekt-Bildmarke sind eigenständig gestaltet.",
                    13f, mutedColor,
                ),
            )
            add(this, button("Quellcode auf GitHub") {
                openWeb("https://github.com/ctrl-mietze/ValacedXposed")
            }, top = 13)
            add(this, button("Aktuelle Releases") {
                openWeb("https://github.com/ctrl-mietze/ValacedXposed/releases")
            }, top = 9)
            add(this, button("Einrichtungshilfe") { showSetup() }, top = 9)
        }
    }

    private fun report(): String {
        val values = mutableListOf(
            "ValancedX " + BuildConfig.VERSION_NAME,
            "Paket: " + BuildConfig.APPLICATION_ID,
            "Discord: " + (discordVersion() ?: "nicht installiert"),
            "Status: " + statusLabel(),
            "IPC-URI erteilt: " + connectionGranted,
            "Letzte Details: " + prefs.getString("last_details", "").orEmpty(),
            "Letztes Event: " + time(prefs.getLong("last_time", 0L)),
            "Hook: " + prefs.getBoolean("hook_enabled", true),
            "Hermes: " + prefs.getBoolean("javascript_enabled", true),
            "Recovery: " + prefs.getBoolean("safe_mode", false),
            "Starts: " + prefs.getInt("total_starts", 0),
            "Bundles: " + prefs.getInt("total_successes", 0),
            "Fehler: " + prefs.getInt("total_errors", 0),
            "Letzte Ereignisse:",
        )
        val history = eventHistory()
        val first = (history.length() - 12).coerceAtLeast(0)
        for (index in first until history.length()) {
            val entry = history.optJSONObject(index) ?: continue
            values += time(entry.optLong("at")) + " - " + statusLabel(entry.optString("state"))
        }
        return values.joinToString("\n")
    }

    private fun copyDiagnostics() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ValancedX Diagnose", report()))
        Toast.makeText(this, "Bericht kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun shareDiagnostics() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ValancedX Diagnostics")
            putExtra(Intent.EXTRA_TEXT, report())
        }, "Diagnose teilen"))
    }

    private fun openDiscord() {
        connectionGranted = grantConnection()
        val launch = packageManager.getLaunchIntentForPackage("com.discord")
        if (launch == null) {
            Toast.makeText(this, "Discord nicht gefunden", Toast.LENGTH_SHORT).show()
        } else runCatching { startActivity(launch) }.onFailure {
            Toast.makeText(this, "Discord konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWeb(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                Toast.makeText(this, "Kein Browser verfügbar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showWelcome() {
        AlertDialog.Builder(this)
            .setTitle("Willkommen bei ValancedX")
            .setMessage(
                "Discord bleibt unverändert installiert. ValancedX läuft über Xposed.\n\n" +
                "Aktiviere das Modul im Framework, wähle com.discord als Scope " +
                "und starte Discord neu. Die Diagnose basiert auf echten Ereignissen.",
            )
            .setNegativeButton("Später", null)
            .setPositiveButton("Einrichten") { _, _ -> showSetup() }
            .show()
    }

    private fun showSetup() {
        AlertDialog.Builder(this)
            .setTitle("Xposed-Einrichtung")
            .setMessage(
                "1. Im bereits installierten LSPosed-Modulmanager ValancedX öffnen.\n\n" +
                "2. ValancedX im Framework aktivieren.\n\n" +
                "3. Ausschließlich com.discord als Scope wählen.\n\n" +
                "4. Discord vollständig stoppen und neu starten.\n\n" +
                "5. Unter Diagnose den Hook-Status prüfen. Wenn nichts erscheint, die LSPosed-Framework-Logs nach ValancedX durchsuchen.\n\n" +
                "Bei Problemen Recovery aktivieren. Es werden keine Discord-APKs gepatcht.",
            )
            .setPositiveButton("Fertig", null)
            .show()
    }


    private var pluginPage = 0
    private var pluginQuery = ""
    private var pluginData: JSONObject? = ManagerPluginMailbox.cachedSnapshot()?.let { runCatching { JSONObject(it) }.getOrNull() }
    private var pluginJob: String? = null
    private var pluginNote = ""
    private var pluginTarget: String? = null
    private var pluginFailedTarget: String? = null
    private var restoredScrollY = 0
    private data class DiscoveryControl(val button: TextView, val progress: ProgressBar, var visual: String = "", var percent: Int = -2)
    private val discoveryControls = mutableMapOf<String, DiscoveryControl>()

    private fun pluginKey(id: String, repo: String) = id + "|" + repo

    private fun discoveryEntries(): Map<String, JSONObject> {
        val entries = pluginData?.optJSONArray("available") ?: JSONArray()
        return (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .associateBy { pluginKey(it.optString("id"), it.optString("repo")) }
    }

    private fun updateDiscoveryControls() {
        val entries = discoveryEntries()
        val status = pluginJob?.let { ManagerPluginMailbox.status(it) }
        for ((key, control) in discoveryControls) {
            val item = entries[key] ?: continue
            val active = pluginJob != null && pluginTarget == key
            val pending = item.optString("pending")
            val current = item.optString("installedVersion")
            val version = item.optString("version")
            val ready = pending.isNotEmpty() || (item.optBoolean("installed") && current == version)
            val buttonText = when {
                active -> if ((status?.second ?: -1) >= 0) "Installiere … " + status!!.second + "%" else status?.first ?: "Installiere …"
                key == pluginFailedTarget -> "Erneut versuchen"
                pending.isNotEmpty() -> "Update bereit"
                ready -> "Installiert"
                item.optBoolean("installed") -> "Aktualisieren"
                else -> "Installieren"
            }
            val enabled = pluginJob == null && (!ready || key == pluginFailedTarget)
            val success = ready && !active && key != pluginFailedTarget
            val visual = "$buttonText|$enabled|$success|$active"
            if (control.visual != visual) {
                control.visual = visual
                control.button.text = buttonText
                control.button.isEnabled = enabled
                control.button.alpha = 1f
                control.button.setTextColor(if (success) mintColor else if(enabled||active) onAccentColor else mutedColor)
                control.button.background = if (success || (!enabled&&!active))
                    interactive(raisedColor, borderColor, 13) else interactive(accentColor, accentColor, 13)
                control.progress.visibility = if (active) View.VISIBLE else View.INVISIBLE
            }
            val percent = status?.second ?: -1
            if (active && control.percent != percent) {
                control.percent = percent
                control.progress.isIndeterminate = percent < 0
                if (percent >= 0) control.progress.progress = percent
            }
            if (!active) control.percent = -2
        }
    }
    private val pluginPoll = object : Runnable {
        override fun run() {
            val id = pluginJob ?: return
            val raw = ManagerPluginMailbox.result(id)
            if (raw == null) {
                updateBusyStrip()
                updateDiscoveryControls()
                mainHandler.postDelayed(this, if(ManagerConnection.status().online())150L else 350L)
                return
            }
            val target = pluginTarget
            pluginJob = null
            pluginTarget = null
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("error", "Ungültige Antwort") }
            if (result.has("error")) {
                pluginNote = result.optString("error")
                if(lastActionKey?.startsWith("theme_")==true) {
                    discordThemeState=discordThemeState?.let{JSONObject(it.toString()).put("ready",false).put("message",pluginNote)}
                }
                if(lastActionKey?.startsWith("theme_snapshot|")==true||lastActionKey?.startsWith("snapshot|")==true){
                    // Permit another automatic sync after a genuine reconnect,
                    // not a tight retry loop during a server-side failure.
                    if(!ManagerConnection.status().online())connectionSyncs.clear()
                }
                pluginFailedTarget = target
                Toast.makeText(this@ValancedXActivity, pluginNote, Toast.LENGTH_LONG).show()
            } else if (result.has("legacyPreview")) {
                val item=result.getJSONObject("legacyPreview")
                pluginNote="Legacy-Manifest geprüft."
                AlertDialog.Builder(this@ValancedXActivity).setTitle(item.optString("name"))
                    .setMessage(item.optString("description")+"\n"+item.optString("author")+"\n\n"+
                        item.optString("url")+"\nSHA-256: "+item.optString("sha256")+
                        "\n\nExperimenteller Port. Fremder JS-Code läuft nach deiner Aktivierung in Discord. Kompatibilität nicht garantiert.")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Importieren"){_,_->pluginAction("legacy_install",JSONObject().put("token",item.optString("token")))}.show()
            } else if (result.has("themeState")) {
                discordThemeState=result.getJSONObject("themeState")
                if(result.has("available"))pluginData=result
                pluginNote=discordThemeState?.optString("message").orEmpty().ifBlank{result.optString("message")}
            } else if (result.has("sourcePreview")) {
                val source=result.getJSONObject("sourcePreview")
                pluginNote="Index erfolgreich geprüft."
                AlertDialog.Builder(this@ValancedXActivity)
                    .setTitle(source.optString("name"))
                    .setMessage(source.optString("url")+"\n\n"+source.optInt("count")+" Plugin-Einträge\n"+source.optString("description")+
                        "\n\nDer Index ist gültig. Plugins aus Drittquellen können Code in Discord ausführen.")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Hinzufügen") { _,_ -> pluginAction("add_repo",JSONObject().put("url",source.optString("url"))) }.show()
            } else if (result.has("token")) {
                val actions = result.optJSONArray("actions") ?: JSONArray()
                if (actions.length() == 0) {
                    pluginNote = "Bereits aktuell."
                } else {
                    val lines = (0 until actions.length()).mapNotNull { i ->
                        actions.optJSONObject(i)?.let { it.optString("id") + "  " + it.optString("version") + "\n" + it.optString("repo") }
                    }
                    val warnings = result.optJSONArray("warnings") ?: JSONArray()
                    val notes = (0 until warnings.length()).map { warnings.optString(it) }
                    AlertDialog.Builder(this@ValancedXActivity)
                        .setTitle("Plugins installieren?")
                        .setMessage(lines.joinToString("\n\n") +
                            if (notes.isEmpty()) "\n\nPlugins führen Code in Discord aus. Nur vertrauenswürdige Quellen nutzen."
                            else "\n\n" + notes.joinToString("\n") + "\n\nNur vertrauenswürdige Quellen nutzen.")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Installieren") { _, _ ->
                            pluginAction("install", JSONObject().put("token", result.optString("token")))
                        }.show()
                    pluginNote = "Installationsplan erstellt."
                }
            } else {
                if(result.has("available")){
                    val oldTheme=discordThemeState
                    pluginData=result
                    if(oldTheme!=null)pluginData?.put("themeState",oldTheme)
                }
                pluginFailedTarget = null
                pluginNote = result.optString("message")
                if (target != null) Toast.makeText(this@ValancedXActivity, pluginNote, Toast.LENGTH_SHORT).show()
            }
            pluginData?.let { snapshot ->
                if(snapshot.has("available"))runCatching {
                    val file=java.io.File(filesDir,"manager-catalog.json")
                    val temp=java.io.File(filesDir,"manager-catalog.tmp")
                    temp.writeText(snapshot.toString());temp.renameTo(file)
                }
            }
            updateBusyStrip()
            lastActionKey=null
            if (currentTab == 3) render(preserveScroll=true)
            if (currentTab == 2) {
                if (target != null && pluginPage == 1 && discoveryControls.isNotEmpty()) {
                    // Update the existing views, not the whole ScrollView.
                    updateDiscoveryControls()
                } else {
                    render(preserveScroll = true)
                }
            }
        }
    }

    private fun pluginAction(operation: String, data: JSONObject = JSONObject()) {
        if (pluginJob != null || restartRunning || analysisJob?.isActive==true) {
            Toast.makeText(this,"Eine Aktion läuft bereits.",Toast.LENGTH_SHORT).show();return
        }
        connectionGranted = grantConnection()
        runCatching { ManagerPluginMailbox.submit(operation, data) }
            .onSuccess {
                pluginJob = it
                lastActionKey=actionKey(operation,data)
                pluginTarget = if (operation == "install_direct" && currentTab==2 && pluginPage==1) pluginKey(data.optString("id"), data.optString("repo")) else null
                pluginFailedTarget = null
                pluginNote = "Discord verarbeitet die Aktion …"
                mainHandler.removeCallbacks(pluginPoll)
                liveConnection.signal()
                mainHandler.post(pluginPoll)
            }.onFailure {
                pluginNote = it.message.orEmpty()
                Toast.makeText(this, pluginNote, Toast.LENGTH_LONG).show()
            }
        updateBusyStrip()
        updateDiscoveryControls()
    }

    private fun addPluginSource(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Plugin-Quelle hinzufügen")
            .setMessage(url + "\n\nPlugins dieser Quelle können nach deiner Bestätigung installiert werden. Fremder Code läuft innerhalb von Discord.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Hinzufügen") { _, _ -> pluginAction("add_repo", JSONObject().put("url", url)) }
            .show()
    }

    private fun customPluginSource() {
        val input = EditText(this).apply {
            setTextColor(whiteColor)
            setHintTextColor(mutedColor)
            backgroundTintList=ColorStateList.valueOf(accentColor)
            hint = "https://example.org/index.json"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Neue Repository-URL").setView(input)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Prüfen") { _, _ -> pluginAction("preview_repo",JSONObject().put("url",input.text.toString().trim())) }.show()
    }


    private fun twoActions(parent: LinearLayout, first: View, second: View, top: Int = 10) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(first, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(second, LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = dp(8) })
        add(parent,row,top=top)
    }

    private fun capsule(value: String, color: Int): TextView = label(value,11f,color,true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9),dp(5),dp(9),dp(5))
        background = rounded(VxThemes.blend(surfaceColor,color,.10f),VxThemes.blend(borderColor,color,.24f),20)
    }

    private fun themeSwitch(parent: LinearLayout, title: String, subtitle: String, key: String, fallback: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val details = column()
        add(details,label(title,14f,whiteColor,true))
        add(details,label(subtitle,12f,mutedColor),top=3)
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Switch(this).apply {
            isChecked = prefs.getBoolean(key,fallback)
            contentDescription = title
            applySwitchPalette(this)
            setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean(key,enabled).apply()
                render(preserveScroll=true)
            }
        })
        add(parent,row,top=5,bottom=5)
    }

    private fun themeSlider(parent: LinearLayout, title: String, key: String, min: Int, max: Int, fallback: Int, suffix: String) {
        val current = prefs.getInt(key,fallback).coerceIn(min,max)
        val value = label(title + " · " + current + suffix,13f,whiteColor,true)
        add(parent,value,top=6)
        add(parent,android.widget.SeekBar(this).apply {
            this.max = max-min
            progress = current-min
            progressTintList=ColorStateList.valueOf(accentColor)
            thumbTintList=ColorStateList.valueOf(accentColor)
            contentDescription=title
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text=title+" · "+(progress+min)+suffix
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) {
                    prefs.edit().putInt(key,(bar?.progress ?: current-min)+min).apply()
                    render(preserveScroll=true)
                }
            })
        },top=4)
    }

    private fun themeScreen(parent: LinearLayout) {
        val selected = VxThemes.get(prefs.getString("ui_theme_id","dark"))
        card(parent,top=12) {
            val row=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            row.addView(label("DEIN LOOK",11f,mutedColor,true),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(capsule("AKTIV",mintColor))
            add(this,row)
            add(this,label(selected.name,25f,whiteColor,true),top=9)
            add(this,label("Manager-Design · Änderungen sofort sichtbar",12f,mutedColor),top=4)
            addView(VxThemePreview(this@ValancedXActivity, currentTheme,cardRadius,glowEnabled),
                LinearLayout.LayoutParams(-1,dp(180)).apply { topMargin=dp(14) })
        }
        eyebrow(parent,"THEME-KOLLEKTION")
        for (theme in VxThemes.all) {
            card(parent,top=9) {
                val row=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
                val title=column()
                add(title,label(theme.name,17f,whiteColor,true))
                add(title,label(theme.description,11f,mutedColor),top=4)
                row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
                if (selected.id==theme.id) row.addView(capsule("Aktiv",mintColor))
                add(this,row)
                addView(VxThemePreview(this@ValancedXActivity,theme,cardRadius,theme.id=="glass"||theme.id=="neon"),
                    LinearLayout.LayoutParams(-1,dp(145)).apply { topMargin=dp(12) })
                if(selected.id!=theme.id) add(this,button("Anwenden",true) {
                    prefs.edit().putString("ui_theme_id",theme.id).remove("ui_accent_color").apply()
                    render(preserveScroll=true)
                },top=10)
            }
        }
        eyebrow(parent,"PERSONALISIERUNG")
        card(parent,top=8) {
            add(this,label("Akzentfarbe",15f,whiteColor,true))
            add(this,label("Eine Farbe für Buttons, Navigation und Details.",12f,mutedColor),top=4)
            val swatches=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            for ((name,color) in VxThemes.accents) {
                val swatch=View(this@ValancedXActivity).apply {
                    contentDescription=name
                    background=rounded(color,if(color==accentColor) whiteColor else color,24)
                    isFocusable=true; isClickable=true
                    setOnClickListener { prefs.edit().putInt("ui_accent_color",color).apply(); render(preserveScroll=true) }
                }
                swatches.addView(swatch,LinearLayout.LayoutParams(0,dp(36),1f).apply { setMargins(dp(3),0,dp(3),0) })
            }
            add(this,swatches,top=12)
            add(this,button("Preset-Akzent wiederherstellen") { prefs.edit().remove("ui_accent_color").apply(); render(preserveScroll=true) },top=10)
            line(this)
            themeSwitch(this,"AMOLED-Schwarz","Echter schwarzer App-Hintergrund.","ui_amoled_enabled",false)
            themeSwitch(this,"Glow-Kanten","Subtiler Akzent an Cards und aktiven Elementen.","ui_glow",selected.id=="glass"||selected.id=="neon")
            themeSwitch(this,"Kompakter Modus","Weniger Innenabstand, mehr sichtbare Einträge.","ui_compact",false)
            line(this)
            themeSlider(this,"Eckenradius","ui_card_radius",12,30,20," dp")
            themeSlider(this,"Card-Intensität","ui_card_intensity",0,100,50,"%")
            add(this,button("Design zurücksetzen",destructive=true) {
                val edit=prefs.edit()
                for (key in listOf("ui_theme_id","ui_accent_color","ui_amoled_enabled","ui_glow","ui_compact","ui_card_radius","ui_card_intensity")) edit.remove(key)
                edit.apply()
                render(preserveScroll=true)
            },top=12)
        }
        add(parent,label("Themes ändern die Oberfläche von ValancedX, nicht das Discord-Design.",12f,mutedColor),top=12)
    }


    private var busyLabel:TextView?=null
    private var busyProgress:ProgressBar?=null
    private var busyCancel:TextView?=null
    private var restartRunning=false
    private var lastActionKey:String?=null
    private val actionControls=mutableMapOf<String,TextView>()
    private fun actionKey(op:String,data:JSONObject):String=op+"|"+data.optString("id")+"|"+data.optString("url")+"|"+data.optString("repo")+"|"+data.optString("motion")+"|"+data.optString("enabled")
    private fun operationButton(title:String,op:String,data:JSONObject=JSONObject(),primary:Boolean=false):TextView {
        val key=actionKey(op,data)
        return button(title,primary) { pluginAction(op,data) }.apply {
            tag="vx-action-"+key
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
            actionControls[key]=this
            if(pluginJob!=null&&lastActionKey==key)text=ManagerPluginMailbox.status(pluginJob!!)?.first ?: "Wird verarbeitet …"
        }
    }
    private fun updateBusyStrip(){
        val job=pluginJob
        val status=job?.let{ManagerPluginMailbox.status(it)}
        busyLabel?.text=if(job!=null)status?.first ?: "Warte auf Discord …" else pluginNote.ifBlank{"Bereit"}
        busyLabel?.setTextColor(if(job!=null)accentColor else mutedColor)
        busyProgress?.visibility=if(job!=null)View.VISIBLE else View.INVISIBLE
        busyProgress?.let{bar->
            val percent=status?.second?:-1
            if(bar.isIndeterminate!=(percent<0))bar.isIndeterminate=percent<0
            if(percent>=0)bar.progress=percent
        }
        busyCancel?.visibility=if(job!=null&&status?.first=="Warte auf Discord")View.VISIBLE else View.INVISIBLE
        val current=lastActionKey?.let{actionControls[it]}
        if(job!=null&&current!=null){
            val value=if((status?.second?:-1)>=0)"Installiere … "+status!!.second+"%" else status?.first?:"Wird verarbeitet …"
            if(current.text.toString()!=value)current.text=value
            current.isEnabled=false
        }
    }
    private fun busyStrip(parent:LinearLayout){
        val strip=column()
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        busyLabel=label("",11f,mutedColor).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        row.addView(busyLabel,LinearLayout.LayoutParams(0,dp(22),1f))
        busyCancel=label("Abbrechen",11f,accentColor,true).apply{
            gravity=Gravity.CENTER;setPadding(dp(8),0,dp(8),0)
            setOnClickListener{if(pluginJob?.let{ManagerPluginMailbox.cancel(it)}!=true)Toast.makeText(this@ValancedXActivity,"Aktion läuft bereits.",Toast.LENGTH_SHORT).show()}
        }
        row.addView(busyCancel,LinearLayout.LayoutParams(-2,dp(22)))
        add(strip,row)
        busyProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100;progressTintList=ColorStateList.valueOf(accentColor);indeterminateTintList=ColorStateList.valueOf(accentColor)
        }
        strip.addView(busyProgress,LinearLayout.LayoutParams(-1,dp(3)))
        add(parent,strip,top=9)
        updateBusyStrip()
    }

    private fun restartDiscord(){
        if(restartRunning)return
        if(!ManagerPluginMailbox.resetUnclaimedForRestart()){
            Toast.makeText(this,"Eine Aktion läuft. Erst abschließen, dann neu starten.",Toast.LENGTH_LONG).show();return
        }
        if(discordVersion()==null){Toast.makeText(this,"Discord nicht gefunden.",Toast.LENGTH_SHORT).show();return}
        restartRunning=true
        connectionGranted=grantConnection()
        Toast.makeText(this,"Discord wird neu gestartet …",Toast.LENGTH_SHORT).show()
        Thread {
            val success=runCatching{
                val user=android.os.Process.myUid()/100000
                val proc=ProcessBuilder("su","-c","/system/bin/am force-stop --user "+user+" com.discord").redirectErrorStream(true).start()
                // Do not leave a pending root prompt or process alive forever.
                if(!proc.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){proc.destroyForcibly();false}
                else proc.exitValue()==0
            }.getOrDefault(false)
            runOnUiThread{
                restartRunning=false
                if(isFinishing||isDestroyed)return@runOnUiThread
                if(success){
                    discordThemeState=null
                    openDiscord()
                    Toast.makeText(this,"Discord vollständig neu gestartet.",Toast.LENGTH_SHORT).show()
                }else{
                    Toast.makeText(this,"Root-Neustart nicht möglich. Bitte Root-Zugriff erlauben.",Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun segmented(parent:LinearLayout,names:List<String>,selected:Int,choose:(Int)->Unit,top:Int=12){
        val nav=LinearLayout(this).apply{background=rounded(surfaceColor,borderColor,18);setPadding(dp(4),dp(4),dp(4),dp(4))}
        for((i,name) in names.withIndex()){
            val cell=label(name,12f,if(i==selected)accentColor else mutedColor,i==selected).apply{
                gravity=Gravity.CENTER;maxLines=1
                setPadding(dp(2),dp(11),dp(2),dp(11))
                background=interactive(if(i==selected)selectedSurface else surfaceColor,
                    if(i==selected)selectedStroke else surfaceColor,14)
                isSelected=i==selected;setOnClickListener{choose(i)}
            }
            nav.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        add(parent,nav,top=top)
    }
    private fun disconnectNotice(parent:LinearLayout){
        if(pluginData!=null)return
        card(parent,top=8){
            add(this,label("Katalog noch nicht geladen",15f,whiteColor,true))
            add(this,label("Discord einmal starten und aktualisieren. Bereits geladene Einträge bleiben künftig erhalten.",12f,mutedColor),top=5)
        }
    }
    private fun sourceMenu(item:JSONObject){
        val url=item.optString("url");val enabled=item.optBoolean("enabled")
        val options=arrayOf("Aktualisieren",if(enabled)"Deaktivieren" else "Aktivieren","URL kopieren","Details","Entfernen")
        AlertDialog.Builder(this).setTitle(item.optString("name",url)).setItems(options){_,which->
            val data=JSONObject().put("url",url)
            when(which){
                0->pluginAction("refresh_repo",data)
                1->pluginAction("toggle_repo",data)
                2->(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Repository",url))
                3->AlertDialog.Builder(this).setTitle("Repository").setMessage(url+"\n\n"+item.optInt("count")+" Einträge\n"+item.optString("description")).setPositiveButton("OK",null).show()
                4->AlertDialog.Builder(this).setTitle("Quelle entfernen?").setMessage("Installierte Plugins bleiben erhalten. Ihre Update-Quelle wird entfernt.")
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen"){_,_->pluginAction("remove_repo",data)}.show()
            }
        }.show()
    }
    private fun sourceScreen(parent:LinearLayout){
        val arr=pluginData?.optJSONArray("repos")?:JSONArray()
        val items=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}
        val urls=items.map{it.optString("url")}.toSet()
        val active=items.filter{it.optBoolean("enabled")}
        card(parent,top=10){
            add(this,label("Repository Hub",23f,whiteColor,true))
            add(this,label("${active.size} aktive Quellen · ${active.sumOf{it.optInt("count")}} Plugin-Einträge",13f,mutedColor),top=5)
            twoActions(this,button("+ Quelle",true){customPluginSource()},operationButton("Synchronisieren","refresh"))
        }
        eyebrow(parent,"INSTALLIERTE QUELLEN")
        if(items.isEmpty())disconnectNotice(parent)
        if(items.isNotEmpty())card(parent,top=7){
            for((i,item) in items.withIndex()){
                if(i>0)line(this)
                val url=item.optString("url")
                val row=LinearLayout(this@ValancedXActivity).apply{gravity=Gravity.CENTER_VERTICAL;tag="vx-repo-"+url}
                val icon=LinearLayout(this@ValancedXActivity).apply{
                    gravity=Gravity.CENTER;background=rounded(raisedColor,borderColor,12)
                    addView(VxIcon(this@ValancedXActivity,"source",accentColor),LinearLayout.LayoutParams(dp(22),dp(22)))
                }
                row.addView(icon,LinearLayout.LayoutParams(dp(42),dp(42)).apply{rightMargin=dp(10)})
                val title=column()
                add(title,label(item.optString("name",url),15f,whiteColor,true).apply{maxLines=2})
                add(title,label(Uri.parse(url).host?:url,11f,mutedColor).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},top=3)
                val enabled=item.optBoolean("enabled")
                val error=item.optString("error")
                val status=if(!enabled)"Deaktiviert" else if(error.isNotBlank())"Indexfehler" else if(item.optBoolean("cached"))"Aktiv" else "Nicht geprüft"
                add(title,label(item.optInt("count").toString()+" Plugins · "+status,11f,if(error.isNotBlank())errorColor else if(enabled)mintColor else mutedColor),top=4)
                row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(Switch(this@ValancedXActivity).apply{
                    isChecked=enabled;contentDescription="Quelle aktivieren"
                    applySwitchPalette(this)
                    isEnabled=pluginJob==null
                    setOnCheckedChangeListener{_,_->pluginAction("toggle_repo",JSONObject().put("url",url))}
                })
                row.addView(label("⋮",25f,mutedColor,true).apply{
                    gravity=Gravity.CENTER;contentDescription="Quellenoptionen";setOnClickListener{sourceMenu(item)}
                },LinearLayout.LayoutParams(dp(32),dp(44)))
                add(this,row)
                if(error.isNotBlank())add(this,label(error.take(180),11f,errorColor),top=5)
            }
        }
        eyebrow(parent,"WEITERE KATALOGE")
        val suggested=listOf(
            Triple("Valenced Community","https://community.valenced.app/index.json","Offizieller Katalog"),
            Triple("dxckie-cell","https://dxckie-cell.github.io/valenced-plugins/index.json","Community"),
            Triple("ndb1995","https://raw.githubusercontent.com/ndb1995/valenced-plugins/main/index.json","Community"))
        card(parent,top=7){
            for((i,source) in suggested.withIndex()){
                if(i>0)line(this)
                val(name,url,description)=source
                val row=LinearLayout(this@ValancedXActivity).apply{gravity=Gravity.CENTER_VERTICAL;tag="vx-suggest-"+url}
                val title=column();add(title,label(name,15f,whiteColor,true));add(title,label(description,11f,mutedColor),top=3)
                row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
                if(url in urls)row.addView(label("Hinzugefügt",11f,mintColor,true))
                else row.addView(operationButton("+ Hinzufügen","preview_repo",JSONObject().put("url",url),true),LinearLayout.LayoutParams(-2,-2))
                add(this,row)
            }
        }
        eyebrow(parent,"LEGACY")
        card(parent,top=7){
            add(this,label("Revenge / Vendetta",16f,whiteColor,true))
            add(this,label("Experimenteller JS-Port, getrennt von nativen Quellen.",12f,mutedColor),top=5)
            add(this,button("Legacy-Import"){pluginPage=3;render()},top=8)
        }
    }

    private fun catalogPreview(url:String):JSONArray? = DiscordCatalogPreviews.forUrl(url)

    private fun themePreview(parent:LinearLayout,values:JSONArray?,animated:Boolean=false){
        parent.addView(DiscordThemePreview(this,DiscordThemePreview.palette(values,currentTheme),animated),
            LinearLayout.LayoutParams(-1,dp(162)).apply{topMargin=dp(11)})
    }
    private fun builtinPalette(id:String):JSONArray=VxDiscordPalettes.preview(id)
    private fun backgroundScreen(parent:LinearLayout,ready:Boolean){
        eyebrow(parent,"VEYRA MOTION STUDIO")
        val background=discordThemeState?.optJSONObject("background")?:JSONObject()
        val path=background.optString("path")
        val currentMotion=VxMotionPresets.fromPath(path)
        val activePalette=discordThemeState?.optJSONObject("activeTheme")?.optString("id").orEmpty()
        var opacity=background.optDouble("opacity",1.0)
        var blur=background.optInt("blur",0)
        var dim=background.optDouble("dimming",.7)
        fun payload(source:String)=JSONObject().put("source",source).put("opacity",opacity).put("blur",blur).put("dimming",dim)
        fun apply(data:JSONObject){
            if(ready)pluginAction("theme_background",data)
            else Toast.makeText(this,"Theme Engine zuerst verbinden.",Toast.LENGTH_SHORT).show()
        }
        card(parent,top=8){
            add(this,label("Hintergrund-Einstellungen",17f,whiteColor,true))
            add(this,label(if(currentMotion!=null)"Aktiv: Veyra "+currentMotion.name else if(path.isNotBlank())"Aktiv: "+(path.substringAfterLast('/').take(70)) else "Quelle: "+background.optString("source","theme"),12f,mutedColor),top=4)
            fun slider(title:String,value:Int,max:Int,set:(Int)->Unit){
                val text=label(title+" · "+value,12f,whiteColor,true)
                add(this,text,top=10)
                add(this,android.widget.SeekBar(this@ValancedXActivity).apply{
                    this.max=max;progress=value.coerceIn(0,max);progressTintList=ColorStateList.valueOf(accentColor);thumbTintList=ColorStateList.valueOf(accentColor)
                    setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener{
                        override fun onProgressChanged(b:android.widget.SeekBar?,v:Int,fromUser:Boolean){text.text=title+" · "+v;set(v)}
                        override fun onStartTrackingTouch(b:android.widget.SeekBar?){}
                        override fun onStopTrackingTouch(b:android.widget.SeekBar?){}
                    })
                })
            }
            slider("Deckkraft %",(opacity*100).toInt(),100){opacity=it/100.0}
            slider("Unschärfe",blur,40){blur=it}
            slider("Abdunklung %",(dim*100).toInt(),100){dim=it/100.0}
            add(this,button("Aktuelle Werte anwenden",true){
                val motion=VxMotionPresets.fromPath(path)?.id
                val source=background.optString("source","theme")
                val data=payload(source)
                if(source=="custom"){if(motion!=null)data.put("motion",motion) else data.put("path",path)}
                apply(data)
            },top=9)
            twoActions(this,button("Theme-Hintergrund"){apply(payload("theme"))},button("Kein Hintergrund"){apply(payload("none"))})
            add(this,button("+ Bild-URL"){urlPrompt("Hintergrundbild","https://…/image.png"){url->apply(payload("custom").put("path",url))}},top=8)
        }
        eyebrow(parent,"ANIMIERTE THEMES · "+VxMotionPresets.all.size)
        add(parent,label("Ein Theme verbindet eine eigene Farbpalette mit einem animierten Hintergrund. Alternativ nur den Hintergrund übernehmen.",12f,mutedColor),top=6)
        val designs=VxMotionPresets.all.sortedByDescending{it.id==currentMotion?.id}
        for(preset in designs){
            card(parent,top=9){
                val id=preset.id
                tag="vx-motion-"+id
                val bgActive=currentMotion?.id==id
                val fullActive=bgActive&&activePalette=="motion-"+id
                val title=LinearLayout(this@ValancedXActivity).apply{gravity=Gravity.CENTER_VERTICAL}
                val labels=column()
                add(labels,label("Veyra "+preset.name,17f,whiteColor,true))
                add(labels,label(preset.description,11f,mutedColor),top=4)
                title.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                if(bgActive)title.addView(label(if(fullActive)"Aktiv" else "Hintergrund aktiv",10f,mintColor,true))
                add(this,title)
                add(this,label(when(preset.motion){"orbit"->"ORBIT";"pulse"->"PULSE";"sweep"->"SWEEP";else->"DRIFT"}+"  ·  "+(preset.duration*2/1000)+" s Loop",10f,android.graphics.Color.parseColor(preset.accent),true),top=6)
                addView(DiscordThemePreview(this@ValancedXActivity,DiscordThemePreview.palette(preset.preview(),currentTheme),true,preset),
                    LinearLayout.LayoutParams(-1,dp(168)).apply{topMargin=dp(10)})
                val swatches=LinearLayout(this@ValancedXActivity).apply{gravity=Gravity.CENTER_VERTICAL}
                for(color in listOf(preset.base,preset.glowA,preset.glowB,preset.accent)){
                    val v=View(this@ValancedXActivity).apply{this.background=rounded(android.graphics.Color.parseColor(color),borderColor,6)}
                    swatches.addView(v,LinearLayout.LayoutParams(dp(24),dp(8)).apply{rightMargin=dp(5)})
                }
                add(this,swatches,top=8)
                fun motionPayload(full:Boolean)=JSONObject().put("source","custom").put("motion",id)
                    .put("motionTheme",full).put("opacity",1.0).put("blur",0).put("dimming",preset.dim)
                add(this,button(if(fullActive)"Theme erneut anwenden" else "Theme anwenden",true){apply(motionPayload(true))},top=9)
                add(this,button("Nur Hintergrund"){apply(motionPayload(false))},top=6)
            }
        }
        add(parent,label("Animationen werden bei inaktivem Discord angehalten. Die Android-Einstellung für reduzierte Bewegung wird berücksichtigt.",11f,mutedColor),top=12)
    }

    private var themePage=0
    private var discordThemeState:JSONObject?=null
    private var themeKind="themes"
    private val themeCatalog = listOf(
        Triple("Catppuccin Mocha","Mauve · Dark","https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/mocha/mauve.json"),
        Triple("Catppuccin Frappé","Lavender · Dark","https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/frappe/lavender.json"),
        Triple("Catppuccin Macchiato","Mauve · Dark","https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/macchiato/mauve.json"),
        Triple("Catppuccin Latte","Mauve · Light","https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/latte/mauve.json"),
        Triple("system24","Monochrome · Teal","https://raw.githubusercontent.com/LukasDerBaum42/discord-theme/main/themes/system24.json"),
        Triple("system24 Purple","Monochrome · Violet","https://raw.githubusercontent.com/LukasDerBaum42/discord-theme/main/themes/system24-purple.json")
    )

    private fun themesPage(parent:LinearLayout) {
        eyebrow(parent,"APPEARANCE")
        add(parent,label("Themes",29f,whiteColor,true),top=4)
        add(parent,label("Gestalte Discord und ValancedX getrennt.",13f,mutedColor),top=5)
        segmented(parent,listOf("Discord","Manager"),themePage,{i->
            themePage=i;render()
            if(i==0&&discordThemeState==null&&pluginJob==null)pluginAction("theme_snapshot")
        },15)
        if(themePage==1){themeScreen(parent);return}
        busyStrip(parent)
        val state=discordThemeState
        val ready=state?.optBoolean("ready")==true && ManagerConnection.status().online() && ManagerConnection.status().themeReady
        card(parent,top=12){
            add(this,label("DISCORD THEME ENGINE",11f,accentColor,true))
            add(this,label(if(ready)"Verbunden" else if(pluginJob!=null)"Verbindet …" else "Nicht verbunden",22f,whiteColor,true),top=8)
            add(this,label(if(ready)"Farben, Hintergründe, Fonts und Packs werden im echten Valenced-Theme-System verwaltet."
                else state?.optString("message")?.takeIf{it.isNotBlank()} ?: pluginNote.takeIf{it.isNotBlank()} ?: "Discord öffnen und synchronisieren. Der Engine-Status wird live geprüft.",12f,mutedColor),top=5)
            twoActions(this,operationButton("Synchronisieren","theme_snapshot"),button("Discord neu starten"){restartDiscord()})
            if(!ready)add(this,button("Discord öffnen"){openDiscord()},top=7)
            if(!ready)add(this,button("Theme Engine installieren / aktualisieren",true){
                pluginAction("install_direct",JSONObject().put("id","valenced.settings.themes").put("repo","https://community.valenced.app/index.json"))
            },top=8)
            if(!ready)add(this,button("Themes aktivieren"){
                pluginAction("enable",JSONObject().put("id","valenced.settings.themes"))
            },top=8)
        }
        val kinds=listOf("themes" to "Farben","fontPacks" to "Fonts","iconPacks" to "Icons","emojiPacks" to "Emoji","backgrounds" to "Hintergrund")
        val kindIndex=kinds.indexOfFirst{it.first==themeKind}.coerceAtLeast(0)
        // The fourth main category is the actual Theme Studio. Emoji packs
        // retain their own secondary control, never a false selected state.
        val mainKinds=listOf("themes","fontPacks","iconPacks","backgrounds")
        segmented(parent,listOf("Farben","Fonts","Icons","Themes"),mainKinds.indexOf(themeKind),{i->
            themeKind=mainKinds[i];render(preserveScroll=true)
        })
        val extras=LinearLayout(this).apply{gravity=Gravity.END}
        extras.addView(label(if(themeKind=="emojiPacks")"Emoji-Pakete · Aktiv" else "Emoji-Pakete",12f,
            if(themeKind=="emojiPacks")accentColor else mutedColor,true).apply{
            setPadding(dp(12),dp(8),dp(12),dp(8))
            background=interactive(if(themeKind=="emojiPacks")selectedSurface else surfaceColor,
                if(themeKind=="emojiPacks")selectedStroke else borderColor,12)
            setOnClickListener{themeKind="emojiPacks";render(preserveScroll=true)}
        })
        add(parent,extras,top=6)
        if(themeKind=="backgrounds"){backgroundScreen(parent,ready);return}
        val entries=state?.optJSONArray("entries")?:JSONArray()
        val selected=(0 until entries.length()).mapNotNull{entries.optJSONObject(it)}.filter{it.optString("kind")==themeKind}
        eyebrow(parent,"DISCORD STANDARD")
        card(parent,top=7){
            add(this,label("Discord Original",17f,whiteColor,true))
            add(this,label("Standard-Einstellungen für "+kinds[kindIndex].second,12f,mutedColor),top=4)
            if(ready&&state?.optJSONObject("defaults")?.optBoolean(themeKind)==true)add(this,capsule("Aktiv",mintColor),top=8)
            else add(this,operationButton("Wiederherstellen","theme_reset",JSONObject().put("kind",themeKind)),top=8)
        }
        if(themeKind=="themes"&&!ready){
            eyebrow(parent,"VEYRA STUDIO")
            for(id in listOf("midnight","lavender","frost","neon","glass","amoled","bloom","eclipse"))card(parent,top=8){
                tag="vx-theme-own-"+id
                add(this,label("Veyra "+id.replaceFirstChar{it.uppercase()},16f,whiteColor,true))
                themePreview(this,builtinPalette(id))
                add(this,label("Theme Engine verbinden, um dieses Design anzuwenden.",11f,mutedColor),top=8)
            }
        }
        if(selected.isNotEmpty())eyebrow(parent,when(themeKind){"themes"->"DEINE DESIGNS";"fontPacks"->"SCHRIFTPAKETE";"iconPacks"->"ICON-PAKETE";else->"EMOJI-PAKETE"})
        for(item in selected)card(parent,top=8){
            val id=item.optString("id");val provider=item.optString("plugin");val group=item.optString("group")
            tag="vx-theme-"+provider+"|"+group+"|"+id
            add(this,label(item.optString("name"),17f,whiteColor,true))
            if(item.optString("description").isNotBlank())add(this,label(item.optString("description"),12f,mutedColor),top=5)
            when(themeKind){
                "themes"->{
                    val palette=item.optJSONArray("preview")
                    if(palette!=null&&palette.length()>0)themePreview(this,palette)
                    else add(this,label("Farben noch nicht aufgelöst.",11f,mutedColor),top=6)
                    if(item.optString("background").isNotBlank())add(this,label("Enthält Hintergrund",11f,accentColor),top=5)
                }
                "fontPacks"->{
                    add(this,label("Aa  Veyra Studio",24f,whiteColor,true),top=7)
                    add(this,label(item.optString("detail"),11f,mutedColor),top=4)
                    add(this,label("Schrift-Familien · Vorschau im Manager verwendet Systemschrift",10f,mutedColor),top=3)
                }
                "iconPacks"->add(this,label(item.optString("detail"),12f,accentColor),top=7)
                "emojiPacks"->add(this,label(if(item.optBoolean("own"))"😀 💜 🔥 🥰" else "Emoji-Paket · Vorschau in Discord",if(item.optBoolean("own"))23f else 12f,whiteColor),top=7)
            }
            if(item.optBoolean("active"))add(this,capsule("Aktiv",mintColor),top=8)
            else add(this,operationButton("Anwenden","theme_apply",JSONObject().put("plugin",provider).put("group",group)
                .put("id",id).put("kind",item.optString("kind")),true),top=8)
            if(item.optBoolean("imported"))add(this,button("Import entfernen",destructive=true){
                AlertDialog.Builder(this@ValancedXActivity).setTitle("Import entfernen?")
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen"){_,_->pluginAction("theme_remove",JSONObject().put("url",id).put("kind",themeKind))}.show()
            },top=6)
        }
        if(ready&&selected.isEmpty()&&themeKind!="themes")add(parent,label("Noch keine zusätzlichen Pakete dieser Kategorie registriert.",12f,mutedColor),top=10)
        val available=pluginData?.optJSONArray("available")?:JSONArray()
        val packs=(0 until available.length()).mapNotNull{available.optJSONObject(it)}.filter{it.optBoolean("themePack")}
        if(packs.isNotEmpty()){
            eyebrow(parent,"THEME-PLUGINS")
            for(item in packs)card(parent,top=8){
                val id=item.optString("id");val repo=item.optString("repo")
                tag="vx-pack-"+id+"|"+repo
                add(this,label(item.optString("name"),15f,whiteColor,true))
                add(this,label(item.optString("description"),12f,mutedColor),top=4)
                if(item.optBoolean("installed")&&item.optString("installedVersion")==item.optString("version")){
                    add(this,operationButton("Aktivieren","enable",JSONObject().put("id",id)),top=7)
                }else add(this,operationButton(if(item.optBoolean("installed"))"Aktualisieren" else "Installieren","install_direct",JSONObject().put("id",id).put("repo",repo),true),top=7)
            }
        }
        if(themeKind=="fontPacks"){
            eyebrow(parent,"FONT-IMPORT")
            card(parent,top=8){
                add(this,label("Bunny / Revenge Schriftpaket",16f,whiteColor,true))
                add(this,label("Spec-1 JSON mit TTF-/OTF-Dateien.",12f,mutedColor),top=5)
                add(this,button("+ Font-Pack URL",true){urlPrompt("Font-Pack","https://…/font.json"){url->pluginAction("theme_font_import",JSONObject().put("url",url))}},top=8)
            }
        }
        if(themeKind=="themes"){
            eyebrow(parent,"THEME-KATALOG")
            card(parent,top=8){
                add(this,label("Eigenes JSON-Theme",16f,whiteColor,true))
                add(this,label("Vendetta, Bunny, Revenge Spec 2.",12f,mutedColor),top=5)
                add(this,button("+ Theme-URL",true){urlPrompt("Discord Theme","https://…/theme.json"){url->pluginAction("theme_import",JSONObject().put("url",url))}},top=8)
            }
            for((name,desc,url) in themeCatalog)card(parent,top=8){
                tag="vx-catalog-"+url
                add(this,label(name,17f,whiteColor,true))
                add(this,label(desc,12f,mutedColor),top=4)
                catalogPreview(url)?.let{themePreview(this,it)}
                val imported=selected.any{it.optString("id")==url}
                if(imported)add(this,capsule("Importiert",mintColor),top=8)
                else add(this,operationButton("Importieren","theme_import",JSONObject().put("url",url),true),top=8)
            }
        }
    }
    private fun urlPrompt(title:String,hint:String,run:(String)->Unit){
        val input=EditText(this).apply{setTextColor(whiteColor);setHintTextColor(mutedColor);backgroundTintList=ColorStateList.valueOf(accentColor);this.hint=hint;setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI}
        AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Abbrechen",null)
            .setPositiveButton("Weiter"){_,_->run(input.text.toString().trim())}.show()
    }

    private fun legacyScreen(parent:LinearLayout) {
        val installed=pluginData?.optJSONArray("installed")?:JSONArray()
        val entries=(0 until installed.length()).mapNotNull{installed.optJSONObject(it)}
        val mode=pluginData?.optBoolean("legacyEnabled")?:entries.any{it.optString("id")=="veyra.legacy.compat"&&it.optBoolean("enabled")}
        eyebrow(parent,"REVENGE COMPATIBILITY")
        card(parent,top=9){
            val row=LinearLayout(this@ValancedXActivity).apply{gravity=Gravity.CENTER_VERTICAL}
            val title=column()
            add(title,label("Revenge Legacy",22f,whiteColor,true))
            add(title,label(if(mode)"Kompatibilitätsmodus aktiv" else "Optionaler Kompatibilitätsmodus",12f,if(mode)mintColor else mutedColor),top=4)
            row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Switch(this@ValancedXActivity).apply{
                isChecked=mode;isEnabled=pluginJob==null;contentDescription="Revenge Compatibility";applySwitchPalette(this)
                setOnCheckedChangeListener{_,enabled->pluginAction("legacy_mode",JSONObject().put("enabled",enabled))}
            })
            add(this,row)
            add(this,label("Die alten JS-Plugins und sechs separat integrierte Ports werden nur bei aktiviertem Modus angeboten. Kein Plugin wird automatisch eingeschaltet.",12f,mutedColor),top=8)
            if(mode)twoActions(this,button("+ Plugin-URL",true){urlPrompt("Revenge Plugin","https://…/manifest.json"){url->pluginAction("legacy_preview",JSONObject().put("url",url))}},
                button("Discord neu starten"){restartDiscord()})
            else add(this,operationButton("Kompatibilität aktivieren","legacy_mode",JSONObject().put("enabled",true),true),top=10)
        }
        if(!mode)return
        eyebrow(parent,"VEYRA · NATIVE PORTS")
        val ports=entries.filter{it.optString("id").startsWith("veyra.port.")}
        add(parent,label("Sechs unabhängige Neuimplementierungen klassischer Plugins. Einzelne Ports können einen Discord-Neustart benötigen.",12f,mutedColor),top=5)
        if(ports.isEmpty())add(parent,label("Plugin-Liste wird synchronisiert.",12f,mutedColor),top=6)
        for(item in ports.sortedBy{it.optString("name")})card(parent,top=8){
            val id=item.optString("id")
            val enabled=item.optBoolean("enabled")
            tag="vx-legacy-port-"+id
            add(this,label(item.optString("name"),17f,whiteColor,true))
            add(this,label(item.optString("description"),12f,mutedColor),top=5)
            statusRow(this,"Integrierter Port",if(item.optBoolean("failed"))"Fehler" else if(enabled)"Aktiviert" else "Aus",
                if(item.optBoolean("failed"))errorColor else if(enabled)mintColor else mutedColor)
            add(this,operationButton(if(enabled)"Deaktivieren" else "Aktivieren",if(enabled)"disable" else "enable",
                JSONObject().put("id",id),!enabled),top=8)
            analysisEntry(this,item,"installed")
        }
        eyebrow(parent,"IMPORTIERTE LEGACY-PLUGINS")
        val imported=entries.filter{it.optString("id").startsWith("veyra.legacy.")&&it.optString("id")!="veyra.legacy.compat"}
        if(imported.isEmpty())add(parent,label("Noch keine Original-Plugins importiert.",12f,mutedColor),top=7)
        for(item in imported)card(parent,top=8){
            val id=item.optString("id");val enabled=item.optBoolean("enabled")
            tag="vx-legacy-import-"+id
            add(this,label(item.optString("name"),16f,whiteColor,true))
            add(this,label(item.optString("description"),12f,mutedColor),top=5)
            statusRow(this,"Original-JS",if(item.optBoolean("failed"))"Fehler" else if(enabled)"Aktiviert" else "Aus",
                if(item.optBoolean("failed"))errorColor else if(enabled)mintColor else mutedColor)
            add(this,operationButton(if(enabled)"Deaktivieren" else "Aktivieren",if(enabled)"disable" else "enable",JSONObject().put("id",id)),top=8)
            analysisEntry(this,item,"installed")
            add(this,button("Deinstallieren",destructive=true){
                AlertDialog.Builder(this@ValancedXActivity).setTitle("Legacy-Plugin entfernen?")
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen"){_,_->pluginAction("uninstall",JSONObject().put("id",id))}.show()
            },top=7)
        }
        eyebrow(parent,"ORIGINALQUELLEN · EXPERIMENTELL")
        add(parent,label("Veröffentlichte alte JS-Bundles. Import mit Manifest und Hash-Prüfung; keine universelle Laufzeitkompatibilität.",12f,mutedColor),top=6)
        val originals=listOf(
            "Char Counter" to "char-counter","Clean URLs" to "clean-urls","Message Preview" to "message-preview",
            "Use System Emoji" to "use-system-emoji","Twemoji Everywhere" to "twemoji-everywhere","Themes+" to "themes-plus",
            "Tenor Gif Fix" to "tenor-gif-fix","Local Pins" to "local-pins","NexxUtils" to "nexxutils")
            .map{it.first to ("https://revenge.nexpid.xyz/"+it.second+"/manifest.json")}+
            listOf("No Typing" to "no-typing","Picture Links" to "picture-links","No Embed Copy" to "no-embed-copy")
            .map{it.first to ("https://redstonekasi.github.io/vendetta-plugins/"+it.second+"/manifest.json")}
        for((name,url) in originals)card(parent,top=8){
            tag="vx-legacy-url-"+url
            add(this,label(name,15f,whiteColor,true))
            add(this,label(Uri.parse(url).host.orEmpty()+" · Original-JS",11f,warmColor),top=4)
            add(this,operationButton("Import prüfen","legacy_preview",JSONObject().put("url",url)),top=7)
        }
        add(parent,label("Revenge Next ZIPs mit nativen Komponenten und JARs sind weiterhin nicht unterstützt.",12f,mutedColor),top=12)
        twoActions(parent,button("Revenge Archiv"){openWeb("https://github.com/revenge-mod/revenge-community-plugins")},button("Vendetta Archiv"){openWeb("https://vd-plugins.github.io/web/")})
    }


    private val analysisScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
    private val analyzer by lazy { app.valenced.xposed.manager.analysis.PluginAnalyzer(applicationContext){liveConnection.signal()} }
    private var analysisJob:kotlinx.coroutines.Job?=null
    private var analysisNote="Lokaler Analyzer bereit"
    private var analysisLabel:TextView?=null
    private var analysisQuery=""
    private var analysisFilter="installed"
    private var analysisLimit=60
    private var vencordQuery=""
    private var vencordLimit=60
    private val vencordCatalog by lazy {
        runCatching{app.valenced.xposed.manager.analysis.VencordTranslator.catalog(this)}.getOrElse{JSONObject()}
    }
    private fun analysisItems(kind:String):List<JSONObject>{
        val arr=if(kind=="vencord")vencordCatalog.optJSONArray("plugins")
            else pluginData?.optJSONArray(if(kind=="catalog")"available" else "installed")
        return (0 until (arr?.length()?:0)).mapNotNull{arr?.optJSONObject(it)}
    }
    private fun analysisProgress(note:String){
        analysisNote=note
        analysisLabel?.text=note
    }
    private fun showAnalysis(item:JSONObject,r:JSONObject){
        val text=StringBuilder()
        if(r.has("error"))text.append("Analyse nicht abgeschlossen: ").append(r.optString("error"))
        else{
            text.append("Deklarierte Funktion\n").append(r.optString("declared").ifBlank{item.optString("description","Keine Beschreibung")})
            text.append("\n\nGeprüft: ").append(r.optInt("sourceBytes")).append(" Quelltext-Bytes · ").append(r.optInt("files")).append(" Datei(en)")
            text.append("\nIsolierter Prozess: ").append(if(r.optBoolean("isolated"))"Ja" else "Nicht bestätigt")
            val test=r.optJSONObject("test")
            text.append("\n\nSandbox-Test\n").append(test?.optString("status").orEmpty())
            test?.optString("detail")?.takeIf{it.isNotBlank()}?.let{text.append("\n").append(it)}
            val trace=test?.optJSONArray("trace")
            for(i in 0 until(trace?.length()?:0))text.append("\n• ").append(trace?.optString(i))
            text.append("\n\nErkannte Code-Merkmale")
            val findings=r.optJSONArray("findings")
            if((findings?.length()?:0)==0)text.append("\nKeine Merkmale erkannt. Das ist kein Sicherheitsnachweis.")
            for(i in 0 until(findings?.length()?:0)){
                val f=findings?.optJSONObject(i)?:continue
                text.append("\n\n").append(f.optString("category")).append(": ").append(f.optString("description"))
                val evidence=f.optJSONArray("evidence")
                if(evidence!=null)text.append("\nHinweise: ").append((0 until evidence.length()).joinToString(", "){evidence.optString(it)})
            }
            r.optJSONObject("translation")?.let{plan->
                text.append("\n\nVencord → Mobile\n")
                if(plan.optBoolean("supported"))text.append(plan.optString("adaptation")).append("\n").append(plan.optString("limitation"))
                else text.append(plan.optString("reason"))
            }
            if(r.optBoolean("native"))text.append("\n\nNative Komponenten wurden nicht ausgeführt.")
            r.optString("sourceCoverage").takeIf{it.isNotBlank()}?.let{text.append("\n").append(it)}
            text.append("\n\n").append(r.optString("disclaimer"))
        }
        AlertDialog.Builder(this).setTitle(item.optString("name","Plugin-Analyse")).setMessage(text.toString())
            .setNeutralButton("Kopieren"){_,_->(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ValancedX Plugin-Analyse",text.toString()))}
            .setPositiveButton("Schließen",null).show()
    }
    private fun startAnalysis(items:List<JSONObject>,kind:String,show:Boolean=false,translate:Boolean=false){
        if(items.isEmpty())return
        if(pluginJob!=null||restartRunning||analysisJob?.isActive==true){
            Toast.makeText(this,"Eine Aktion läuft bereits.",Toast.LENGTH_SHORT).show();return
        }
        val queue=items.distinctBy{analyzer.key(it,kind)}
        var last:JSONObject?=null
        analysisJob=analysisScope.launch(start=kotlinx.coroutines.CoroutineStart.LAZY){
            var done=0;var failures=0
            try{
                for(item in queue){
                    analysisProgress("Analysiere "+(done+1)+"/"+queue.size+" · "+item.optString("name"))
                    val r=analyzer.scan(item,kind)
                    last=r;done++;if(r.has("error"))failures++
                }
                analysisProgress(done.toString()+" analysiert · "+failures+" nicht abgeschlossen")
            }catch(e:kotlinx.coroutines.CancellationException){analysisProgress("Analyse gestoppt · "+done+" abgeschlossen");throw e}
            finally{analysisJob=null}
            if(isFinishing||isDestroyed)return@launch
            if(currentTab==2&&(pluginPage==4||pluginPage==5))render(preserveScroll=true)
            val result=last
            if(translate&&result!=null){
                val plan=result.optJSONObject("translation")
                if(!result.has("error")&&plan?.optBoolean("supported")==true){
                    pluginAction("vencord_activate",JSONObject().put("id",queue.first().optString("id")).put("sha256",queue.first().optString("sha256")))
                }else showAnalysis(queue.first(),result)
            }else if(show&&result!=null)showAnalysis(queue.first(),result)
        }
        analysisProgress("Sandbox wird vorbereitet …")
        analysisJob?.start()
        if(currentTab==2&&(pluginPage==4||pluginPage==5))render(preserveScroll=true)
    }
    private fun analysisEntry(parent:LinearLayout,item:JSONObject,kind:String){
        val report=analyzer.report(item,kind)
        add(parent,button(if(report==null)"Analysieren" else "Analyse ansehen"){
            if(report==null)startAnalysis(listOf(item),kind,show=true) else showAnalysis(item,report)
        },top=7)
    }
    private fun analysisScreen(parent:LinearLayout){
        eyebrow(parent,"PLUGIN LAB · LOKAL")
        card(parent,top=8){
            add(this,label("Plugin Analyzer",23f,whiteColor,true))
            add(this,label("Getrennter Android-Prozess je Plugin. Statische Codeanalyse und begrenzter JS-Lifecycle-Test mit simulierten APIs. Keine Discord-Daten oder Root-Zugriffe.",12f,mutedColor),top=6)
            analysisLabel=label(analysisNote,12f,accentColor)
            add(this,analysisLabel!!,top=8)
            if(analysisJob?.isActive==true)add(this,button("Analyse stoppen"){analysisJob?.cancel();render(preserveScroll=true)},top=8)
        }
        segmented(parent,listOf("Installiert","Katalog"),if(analysisFilter=="installed")0 else 1,{i->
            analysisFilter=if(i==0)"installed" else "catalog";analysisLimit=60;analysisQuery="";render(preserveScroll=true)
        })
        val kind=analysisFilter
        val items=analysisItems(kind)
        val pending=items.filter{analyzer.report(it,kind)?.let{r->!r.has("error")}!=true}
        twoActions(parent,button("Alle analysieren",true){
            AlertDialog.Builder(this).setTitle(items.size.toString()+" Plugins lokal analysieren?")
                .setMessage("Katalogdateien werden von ihren Quellen geladen. Jeder Test hat eine eigene Sandbox. Keine Installation oder Aktivierung.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Starten"){_,_->startAnalysis(items,kind)}.show()
        },button("Offene analysieren"){startAnalysis(pending,kind)})
        val search=EditText(this).apply{hint="Plugin suchen …";setText(analysisQuery);setTextColor(whiteColor);setHintTextColor(mutedColor);setSingleLine(true);background=rounded(surfaceColor,borderColor,14);setPadding(dp(12),dp(10),dp(12),dp(10))}
        add(parent,search,top=9)
        val list=column();add(parent,list,top=5)
        fun draw(){
            list.removeAllViews()
            val matching=items.filter{it.optString("name").contains(analysisQuery,true)||it.optString("id").contains(analysisQuery,true)}
            eyebrow(list,matching.size.toString()+" PLUGINS · "+(items.size-pending.size)+" ANALYSIERT")
            for(item in matching.take(analysisLimit))card(list,top=7){
                tag="vx-analysis-"+analyzer.key(item,kind)
                add(this,label(item.optString("name"),15f,whiteColor,true))
                val report=analyzer.report(item,kind)
                add(this,label(if(report==null)"Noch nicht analysiert" else if(report.has("error"))"Analyse unvollständig" else report.optJSONObject("test")?.optString("status").orEmpty(),11f,if(report?.has("error")==true)warmColor else mutedColor),top=4)
                twoActions(this,button(if(report==null)"Analysieren" else "Bericht",true){if(report==null)startAnalysis(listOf(item),kind,true)else showAnalysis(item,report)},
                    button("Neu testen"){startAnalysis(listOf(item),kind,true)})
            }
            if(matching.size>analysisLimit)add(list,button("Weitere anzeigen"){analysisLimit+=60;draw()},top=8)
        }
        draw()
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){analysisQuery=s?.toString().orEmpty();analysisLimit=60;draw()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
    }
    private fun vencordScreen(parent:LinearLayout){
        eyebrow(parent,"VENCORD → MOBILE")
        val entries=analysisItems("vencord")
        val supported=entries.count{app.valenced.xposed.manager.analysis.VencordTranslator.plan(it.optString("id"),it.optString("sha256")).optBoolean("supported")}
        card(parent,top=8){
            add(this,label("Plugin Translator",23f,whiteColor,true))
            add(this,label(entries.size.toString()+" Desktop-Plugins · "+supported+" vorbereitete Mobile-Regeln",13f,mintColor),top=5)
            add(this,label("Beim Aktivieren wird der gepinnte Vencord-Quellcode lokal geprüft und eine passende integrierte Mobile-Implementierung ausgewählt. Unbekannte Webpack-, CSS- und Node-Hooks werden nicht blind ausgeführt.",12f,mutedColor),top=6)
            add(this,label("Revision: "+vencordCatalog.optString("revision").take(12),10f,mutedColor),top=6)
            analysisLabel=label(analysisNote,11f,accentColor);add(this,analysisLabel!!,top=5)
            if(analysisJob?.isActive==true)add(this,button("Analyse stoppen"){analysisJob?.cancel();render(preserveScroll=true)},top=7)
        }
        val search=EditText(this).apply{hint="Vencord-Plugin suchen …";setText(vencordQuery);setTextColor(whiteColor);setHintTextColor(mutedColor);setSingleLine(true);background=rounded(surfaceColor,borderColor,14);setPadding(dp(12),dp(10),dp(12),dp(10))}
        add(parent,search,top=10)
        val list=column();add(parent,list,top=5)
        val installed=analysisItems("installed")
        fun draw(){
            list.removeAllViews()
            val matching=entries.filter{it.optString("name").contains(vencordQuery,true)||it.optString("id").contains(vencordQuery,true)}
                .sortedWith(compareByDescending<JSONObject>{app.valenced.xposed.manager.analysis.VencordTranslator.rules.containsKey(it.optString("id"))}.thenBy{it.optString("name").lowercase(Locale.ROOT)})
            for(item in matching.take(vencordLimit))card(list,top=8){
                val id=item.optString("id")
                val plan=app.valenced.xposed.manager.analysis.VencordTranslator.plan(id,item.optString("sha256"))
                val ready=plan.optBoolean("supported")
                val target=plan.optString("target")
                val active=ready&&installed.any{it.optString("id")==target&&it.optBoolean("enabled")}
                tag="vx-vencord-"+id
                add(this,label(item.optString("name"),16f,whiteColor,true))
                add(this,label(item.optString("description"),12f,mutedColor),top=4)
                add(this,label(if(active)"Mobile-Port aktiviert" else if(ready)"Übersetzungsregel verfügbar" else "Manueller Port erforderlich",11f,if(ready)mintColor else warmColor),top=5)
                if(ready){
                    add(this,label(plan.optString("adaptation"),12f,whiteColor),top=6)
                    add(this,label(plan.optString("limitation"),11f,mutedColor),top=4)
                    add(this,button(if(active)"Mobile-Port deaktivieren" else "Übersetzen & aktivieren",!active){
                        if(active)pluginAction("vencord_disable",JSONObject().put("id",id))
                        else startAnalysis(listOf(item),"vencord",translate=true)
                    },top=7)
                }else{
                    val flags=listOfNotNull(if(item.optBoolean("native"))"Node.js" else null,if(item.optBoolean("css"))"CSS" else null,if(item.optBoolean("patches"))"Webpack-Patches" else null)
                    if(flags.isNotEmpty())add(this,label(flags.joinToString(" · "),11f,mutedColor),top=4)
                }
                analysisEntry(this,item,"vencord")
            }
            if(matching.size>vencordLimit)add(list,button("Weitere anzeigen"){vencordLimit+=60;draw()},top=8)
        }
        draw()
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){vencordQuery=s?.toString().orEmpty();vencordLimit=60;draw()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
    }

    private fun pluginScreen(parent: LinearLayout) {
        eyebrow(parent, "PLUGIN MANAGER")
        add(parent, label("Plugins", 27f, whiteColor, true), top = 4)
        add(parent, label("Installieren, verwalten und Quellen hinzufügen.", 13f, mutedColor), top = 5)
        segmented(parent,listOf("Installiert","Entdecken","Quellen","Legacy"),if(pluginPage<=3)pluginPage else -1,{i->
            pluginPage=i;pluginQuery="";render()
            if(pluginData==null&&pluginJob==null)pluginAction("snapshot")
        },15)
        twoActions(parent,button("Analyse",pluginPage==4){pluginPage=4;render()},button("Vencord",pluginPage==5){pluginPage=5;render()},top=7)
        discoveryControls.clear()
        busyStrip(parent)
        if(pluginPage==4){analysisScreen(parent);return}
        if(pluginPage==5){vencordScreen(parent);return}
        if(pluginPage==2){sourceScreen(parent);return}
        if(pluginPage==3){legacyScreen(parent);return}
        twoActions(parent,operationButton("Aktualisieren",if(pluginPage==0)"snapshot" else "refresh"),
            button("Discord neu starten"){restartDiscord()})
        if(pluginPage==1)add(parent,label("Ein Tipp installiert das Plugin. Neue Plugins bleiben bis zur Aktivierung aus.",11f,mutedColor),top=8)
        disconnectNotice(parent)
        val search = EditText(this).apply {
            hint = "Plugins suchen …"
            setText(pluginQuery)
            setTextColor(whiteColor)
            setHintTextColor(mutedColor)
            setSingleLine(true)
            setPadding(dp(14),dp(10),dp(14),dp(10))
            background = rounded(surfaceColor,borderColor,14)
        }
        add(parent, search, top = 12)
        val list = column()
        add(parent, list, top = 4)
        fun showEntries() {
            list.removeAllViews()
            discoveryControls.clear()
            val entries = pluginData?.optJSONArray(if (pluginPage == 0) "installed" else "available") ?: JSONArray()
            val filtered = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
                .filter { item ->
                    // The dedicated Legacy page owns these controls. External
                    // imports remain visible there even after the mode is reenabled.
                    val id=item.optString("id")
                    pluginPage!=0||(!id.startsWith("veyra.port.")&&!id.startsWith("veyra.legacy."))
                }
                .filter { item -> listOf("name","description","id","author").any { item.optString(it).contains(pluginQuery, ignoreCase = true) } }
                .sortedBy { it.optString("name").lowercase(Locale.ROOT) }
            eyebrow(list, "${filtered.size} Plugins")
            if (filtered.isEmpty()) add(list,label("Keine Einträge. Quellen hinzufügen oder aktualisieren.",13f,mutedColor),top=8)
            for (item in filtered) {
                val id = item.optString("id")
                card(list, top = 8) {
                    tag="vx-plugin-"+id+"|"+item.optString("repo")
                    add(this,label(item.optString("name",id),16f,whiteColor,true))
                    add(this,label("v" + item.optString("version") + " · " + item.optString("author").substringBefore(" <").substringBefore(" (").trim(),11f,accentColor),top=4)
                    if (item.optString("description").isNotBlank()) add(this,label(item.optString("description"),12f,mutedColor),top=6)
                    if (pluginPage == 0) {
                        val essential = item.optBoolean("essential")
                        val enabled = item.optBoolean("enabled")
                        statusRow(this, if (item.optBoolean("internal")) "Integriert" else "Extern",
                            if (item.optBoolean("failed")) "Fehler" else if (enabled) "Aktiviert" else "Deaktiviert",
                            if (item.optBoolean("failed")) errorColor else if (enabled) mintColor else mutedColor)
                        if (item.optString("pending").isNotBlank()) add(this,label("Update bereit – Neustart erforderlich",11f,warmColor))
                        if (!essential) add(this,operationButton(if(enabled)"Deaktivieren" else "Aktivieren",if(enabled)"disable" else "enable",JSONObject().put("id",id)),top=6)
                        if (!essential && !item.optBoolean("internal")) add(this,button("Deinstallieren", destructive=true) {
                            AlertDialog.Builder(this@ValancedXActivity).setTitle("Plugin deinstallieren?")
                                .setMessage("Auch lokale Daten dieses Plugins werden gelöscht.")
                                .setNegativeButton("Abbrechen",null).setPositiveButton("Deinstallieren") { _, _ -> pluginAction("uninstall",JSONObject().put("id",id)) }.show()
                        },top=6)
                    } else {
                        val repo = item.optString("repo")
                        add(this,label(Uri.parse(repo).host ?: repo,11f,mutedColor),top=5)
                        val install = button("Installieren", true) {
                            pluginAction("install_direct", JSONObject().put("id",id).put("repo",repo))
                        }
                        install.maxLines = 1
                        install.ellipsize = android.text.TextUtils.TruncateAt.END
                        add(this, install, top = 8)
                        val progress = ProgressBar(this@ValancedXActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = 100
                            visibility = View.INVISIBLE
                            progressTintList = ColorStateList.valueOf(accentColor)
                            indeterminateTintList = ColorStateList.valueOf(accentColor)
                        }
                        addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(3) })
                        discoveryControls[pluginKey(id,repo)] = DiscoveryControl(install,progress)
                    }
                    analysisEntry(this,item,if(pluginPage==0)"installed" else "catalog")
                }
            }
            updateDiscoveryControls()
        }
        showEntries()
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pluginQuery = s?.toString().orEmpty()
                showEntries()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }


    private fun render(preserveScroll: Boolean = false) {
        val previousY = if (preserveScroll) scrollView?.scrollY ?: restoredScrollY else 0
        val anchor=if(preserveScroll)scrollView?.let{old->
            fun find(v:View):Pair<String,Int>? {
                val tag=v.tag as? String
                if(tag!=null&&tag.startsWith("vx-")&&!tag.startsWith("vx-action-")){
                    val rect=android.graphics.Rect(0,0,v.width,v.height)
                    old.offsetDescendantRectToMyCoords(v,rect)
                    if(rect.bottom>old.scrollY&&rect.top<old.scrollY+old.height)return tag to (rect.top-old.scrollY)
                }
                if(v is android.view.ViewGroup)for(i in 0 until v.childCount){find(v.getChildAt(i))?.let{return it}}
                return null
            }
            runCatching{find(old)}.getOrNull()
        }else null
        busyLabel=null;busyProgress=null;busyCancel=null;actionControls.clear()
        connectionText=null;connectionDot=null;analysisLabel=null
        restoredScrollY = 0
        discoveryControls.clear()
        readTheme()
        val root = column().apply { setBackgroundColor(backgroundColor) }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(12))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_valancedx)
            contentDescription = "ValancedX"
        }, LinearLayout.LayoutParams(dp(43), dp(43)))
        val title = column().apply { setPadding(dp(11), 0, 0, 0) }
        add(title, label("ValancedX", 23f, whiteColor, true))
        add(title, label("XPOSED  /  " + BuildConfig.VERSION_NAME, 10f, mutedColor, true), top = 3)
        val link=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        connectionDot=View(this)
        link.addView(connectionDot,LinearLayout.LayoutParams(dp(6),dp(6)).apply{rightMargin=dp(5)})
        connectionText=label("Discord verbinden …",10f,mutedColor,true)
        link.addView(connectionText,LinearLayout.LayoutParams(-2,-2))
        link.contentDescription="Discord-Verbindungsstatus"
        link.setOnClickListener{if(!ManagerConnection.status().online())openDiscord() else liveConnection.signal()}
        add(title,link,top=4)
        updateConnectionStatus()
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        fun headerIcon(name:String,description:String,action:()->Unit): View =
            LinearLayout(this).apply {
                gravity=Gravity.CENTER
                background=interactive(surfaceColor,borderColor,12)
                contentDescription=description
                isClickable=true;isFocusable=true
                addView(VxIcon(this@ValancedXActivity,name,accentColor),LinearLayout.LayoutParams(dp(22),dp(22)))
                setOnClickListener { action() }
            }
        header.addView(headerIcon("theme","Themes") {
            currentTab=3;themePage=0;render();if(pluginJob==null)pluginAction("theme_snapshot")
        },LinearLayout.LayoutParams(dp(40),dp(42)).apply { rightMargin=dp(6) })
        header.addView(headerIcon("refresh","Aktualisieren") {
            connectionGranted=grantConnection()
            if(currentTab==3&&themePage==0&&pluginJob==null) pluginAction("theme_snapshot")
            else if(currentTab==2&&pluginJob==null) pluginAction(if(pluginPage==0) "snapshot" else "refresh")
            else render(preserveScroll=true)
        },LinearLayout.LayoutParams(dp(40),dp(42)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        scrollView = scroll
        val page = column().apply { setPadding(dp(18), 0, dp(18), dp(22)) }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (currentTab) {
            0 -> dashboard(page)
            1 -> modules(page)
            2 -> pluginScreen(page)
            3 -> themesPage(page)
            4 -> diagnostics(page)
            else -> about(page)
        }
        val navigation = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surfaceColor, borderColor, 22)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val symbols = arrayOf("home", "module", "plugins", "theme", "diagnose", "info")
        for (index in tabs.indices) {
            val selected = index == currentTab
            val cell = column().apply {
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = interactive(if (selected) selectedSurface else surfaceColor,
                    if (selected) selectedStroke else surfaceColor, 16)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = tabs[index]
                addView(VxIcon(this@ValancedXActivity,symbols[index],if(selected) accentColor else mutedColor),
                    LinearLayout.LayoutParams(dp(24),dp(24)))
                add(this, label(tabs[index], 10f, if (selected) accentColor else mutedColor, selected).apply {
                    maxLines=1
                    ellipsize=android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, top = 2)
                setOnClickListener {
                    if (currentTab != index) { currentTab = index; render(); if (index == 2 && pluginData == null && pluginJob == null) pluginAction("snapshot"); if(index==3&&themePage==0&&discordThemeState==null&&pluginJob==null)pluginAction("theme_snapshot") }
                }
            }
            navigation.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val navParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(5), dp(14), dp(9))
        }
        root.addView(navigation, navParams)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        if(previousY>0||anchor!=null)scroll.viewTreeObserver.addOnGlobalLayoutListener(object:android.view.ViewTreeObserver.OnGlobalLayoutListener{
            override fun onGlobalLayout(){
                scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                var y=previousY
                if(anchor!=null){
                    val v=scroll.findViewWithTag<View>(anchor.first)
                    if(v!=null){val rect=android.graphics.Rect(0,0,v.width,v.height);scroll.offsetDescendantRectToMyCoords(v,rect);y=rect.top-anchor.second}
                }
                scroll.scrollTo(0,y.coerceAtLeast(0))
            }
        })
    }
}
