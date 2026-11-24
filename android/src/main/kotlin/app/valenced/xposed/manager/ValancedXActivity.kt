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
    private val warmColor = Color.rgb(255, 203, 115)
    private val errorColor = Color.rgb(255, 143, 157)
    private val whiteColor get() = currentTheme.text
    private val mutedColor get() = currentTheme.muted

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

    private val tabs = arrayOf("Start", "Module", "Plugins", "Diagnose", "Info")
    private var currentTab = 0
    private var scrollView: ScrollView? = null
    private var connectionGranted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { if (currentTab != 2) render(preserveScroll = true) }
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
        currentTab = (savedInstanceState?.getInt("tab") ?: 0).coerceIn(0, tabs.lastIndex)
        pluginPage = (savedInstanceState?.getInt("pluginPage") ?: 0).coerceIn(0,3)
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
        if (pluginJob != null) mainHandler.post(pluginPoll)
    }

    override fun onPause() {
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
            if (primary) backgroundColor else if (destructive) errorColor else whiteColor,
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
            thumbTintList = ColorStateList.valueOf(if (enabled) accentColor else mutedColor)
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
            add(this, button("Discord öffnen", primary = true) { openDiscord() }, top = 12)
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
            card(parent, tint = Color.rgb(45, 32, 40)) {
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
                control.button.alpha = if (enabled || active || ready) 1f else .55f
                control.button.setTextColor(if (success) mintColor else backgroundColor)
                control.button.background = if (success)
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
                updateDiscoveryControls()
                mainHandler.postDelayed(this, 350)
                return
            }
            val target = pluginTarget
            pluginJob = null
            pluginTarget = null
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("error", "Ungültige Antwort") }
            if (result.has("error")) {
                pluginNote = result.optString("error")
                pluginFailedTarget = target
                if (target != null) Toast.makeText(this@ValancedXActivity, pluginNote, Toast.LENGTH_LONG).show()
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
                pluginData = result
                pluginFailedTarget = null
                pluginNote = result.optString("message")
                if (target != null) Toast.makeText(this@ValancedXActivity, pluginNote, Toast.LENGTH_SHORT).show()
            }
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
        if (pluginJob != null) return
        connectionGranted = grantConnection()
        runCatching { ManagerPluginMailbox.submit(operation, data) }
            .onSuccess {
                pluginJob = it
                pluginTarget = if (operation == "install_direct") pluginKey(data.optString("id"), data.optString("repo")) else null
                pluginFailedTarget = null
                pluginNote = "Discord verarbeitet die Aktion …"
                mainHandler.removeCallbacks(pluginPoll)
                mainHandler.post(pluginPoll)
            }.onFailure {
                pluginNote = it.message.orEmpty()
                Toast.makeText(this, pluginNote, Toast.LENGTH_LONG).show()
            }
        if (currentTab == 2) {
            if (operation == "install_direct" && pluginPage == 1) updateDiscoveryControls()
            else render(preserveScroll = true)
        }
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
            thumbTintList = ColorStateList.valueOf(if(isChecked) accentColor else mutedColor)
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

    private fun sourceScreen(parent: LinearLayout) {
        val repos=pluginData?.optJSONArray("repos") ?: JSONArray()
        val items=(0 until repos.length()).mapNotNull { repos.optJSONObject(it) }
        val urls=items.map { it.optString("url") }.toSet()
        val active=items.count { it.optBoolean("enabled") }
        card(parent,top=12) {
            val header=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            header.addView(VxIcon(this@ValancedXActivity,"source",accentColor),LinearLayout.LayoutParams(dp(28),dp(28)).apply { rightMargin=dp(10) })
            header.addView(label("Repository Hub",22f,whiteColor,true),LinearLayout.LayoutParams(0,-2,1f))
            add(this,header)
            add(this,label("Verwalte die Herkunft deiner Plugins.",13f,mutedColor),top=6)
            val counts=LinearLayout(this@ValancedXActivity)
            for ((number,caption) in listOf(items.size to "Quellen",active to "Aktiv",items.filter { it.optBoolean("enabled") }.sumOf { it.optInt("count") } to "Einträge")) {
                val tile=column().apply {
                    setPadding(dp(12),dp(10),dp(8),dp(10))
                    background=rounded(raisedColor,borderColor,14)
                    add(this,label(number.toString(),23f,accentColor,true))
                    add(this,label(caption,11f,mutedColor),top=3)
                }
                counts.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),0) })
            }
            add(this,counts,top=15)
            twoActions(this,button("+ Quelle",true) { customPluginSource() },button("Alle aktualisieren") { pluginAction("refresh") })
        }
        eyebrow(parent,"DEINE QUELLEN")
        if(items.isEmpty()) add(parent,label("Noch keine Quellen geladen. Aktualisiere die Liste.",13f,mutedColor),top=6)
        for (item in items) {
            val url=item.optString("url")
            val enabled=item.optBoolean("enabled")
            val error=item.optString("error")
            val checked=item.optLong("checkedAt")
            card(parent,top=9) {
                val header=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
                val iconBox=LinearLayout(this@ValancedXActivity).apply {
                    gravity=Gravity.CENTER
                    background=rounded(VxThemes.blend(raisedColor,accentColor,.10f),borderColor,14)
                    addView(VxIcon(this@ValancedXActivity,"source",accentColor),LinearLayout.LayoutParams(dp(22),dp(22)))
                }
                header.addView(iconBox,LinearLayout.LayoutParams(dp(44),dp(44)).apply { rightMargin=dp(12) })
                val title=column()
                add(title,label(item.optString("name",url),16f,whiteColor,true))
                add(title,label(Uri.parse(url).host ?: url,11f,mutedColor),top=3)
                header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
                header.addView(capsule(if(!enabled) "Aus" else if(error.isNotBlank()) "Fehler" else if(item.optBoolean("cached")) "Aktiv" else "Offen",
                    if(!enabled) mutedColor else if(error.isNotBlank()) errorColor else if(item.optBoolean("cached")) mintColor else warmColor))
                add(this,header)
                if(item.optString("description").isNotBlank()) add(this,label(item.optString("description"),12f,mutedColor),top=10)
                val info=LinearLayout(this@ValancedXActivity).apply { gravity=Gravity.CENTER_VERTICAL }
                info.addView(capsule(item.optInt("count").toString()+" Plugins",accentColor))
                val kind=if(url=="https://community.valenced.app/index.json") "Offizieller Katalog" else "Externe Quelle"
                info.addView(capsule(kind,mutedColor),LinearLayout.LayoutParams(-2,-2).apply { leftMargin=dp(6) })
                add(this,info,top=12)
                add(this,label(if(checked>0L) "Letzte Prüfung: "+time(checked) else "Letzte Prüfung: nicht erfasst",11f,mutedColor),top=10)
                if(error.isNotBlank()) add(this,label(error,12f,errorColor),top=5)
                twoActions(this,button("Aktualisieren") { pluginAction("refresh_repo",JSONObject().put("url",url)) },
                    button(if(enabled) "Deaktivieren" else "Aktivieren",primary=!enabled) { pluginAction("toggle_repo",JSONObject().put("url",url)) })
                twoActions(this,button("Details") {
                    AlertDialog.Builder(this@ValancedXActivity).setTitle(item.optString("name",url))
                        .setMessage(url+"\n\n"+item.optInt("count")+" Plugin-Einträge\n"+kind+
                            "\n\nFremder Plugin-Code läuft in Discord. Ein gültiger Index ist kein Sicherheitsnachweis.")
                        .setNeutralButton("URL kopieren") { _,_ -> (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Repository",url)) }
                        .setPositiveButton("Schließen",null).show()
                },button("Entfernen",destructive=true) {
                    AlertDialog.Builder(this@ValancedXActivity).setTitle("Quelle entfernen?")
                        .setMessage("Installierte Plugins bleiben erhalten, verlieren aber ihre Update-Quelle.")
                        .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen") { _,_ -> pluginAction("remove_repo",JSONObject().put("url",url)) }.show()
                },top=7)
            }
        }
        eyebrow(parent,"EMPFOHLENE KATALOGE")
        val suggested=listOf(
            Triple("Valenced Community","https://community.valenced.app/index.json","Offizieller Plugin-Katalog"),
            Triple("dxckie-cell","https://dxckie-cell.github.io/valenced-plugins/index.json","Optionale Community-Erweiterungen")
        )
        for ((name,url,description) in suggested) {
            card(parent,top=8) {
                add(this,label(name,17f,whiteColor,true))
                add(this,label(description,12f,mutedColor),top=4)
                add(this,label(Uri.parse(url).host ?: url,11f,mutedColor),top=5)
                if(url in urls) add(this,capsule("Bereits hinzugefügt",mintColor),top=9)
                else add(this,button("Quelle prüfen",true) { pluginAction("preview_repo",JSONObject().put("url",url)) },top=10)
            }
        }
        eyebrow(parent,"LEGACY")
        card(parent,top=8) {
            add(this,label("Revenge Next",16f,whiteColor,true))
            add(this,label("Archivierte Plattform. Legacy-Plugins benötigen einen kompatiblen Port; kein automatischer Import.",12f,mutedColor),top=6)
            add(this,button("Archiv ansehen") { openWeb("https://github.com/revenge-mod/revenge-community-plugins") },top=10)
        }
    }


    private fun pluginScreen(parent: LinearLayout) {
        eyebrow(parent, "PLUGIN MANAGER")
        add(parent, label("Plugins", 27f, whiteColor, true), top = 4)
        add(parent, label("Installieren, verwalten und Quellen hinzufügen.", 13f, mutedColor), top = 5)
        val nav = LinearLayout(this).apply { background = rounded(surfaceColor, borderColor, 15); setPadding(dp(3),dp(3),dp(3),dp(3)) }
        for ((i, text) in arrayOf("Installiert", "Entdecken", "Quellen", "Themes").withIndex()) {
            nav.addView(label(text, 12f, if (pluginPage == i) whiteColor else mutedColor, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(12), dp(2), dp(12))
                background = interactive(if (pluginPage == i) raisedColor else surfaceColor, borderColor, 12)
                setOnClickListener {
                    pluginPage=i;pluginQuery="";render()
                    if(i!=3&&pluginData==null&&pluginJob==null) pluginAction("snapshot")
                }
            }, LinearLayout.LayoutParams(0,-2,1f))
        }
        add(parent, nav, top = 15)
        discoveryControls.clear()
        if(pluginPage==3) { themeScreen(parent); return }
        // The list stays mounted while an individual plugin is installing.
        if (pluginNote.isNotBlank() && pluginPage != 1)
            add(parent, label(pluginNote, 12f, if (pluginJob != null) accentColor else mutedColor), top = 10)
        if (pluginPage==2) {
            if (pluginJob!=null) {
                add(parent,ProgressBar(this).apply { isIndeterminate=true },top=4)
                add(parent,label("Quellen werden geprüft …",12f,accentColor),top=4)
            }
            sourceScreen(parent);return
        }
        if (pluginJob != null && pluginTarget == null) {
            add(parent, ProgressBar(this).apply { isIndeterminate = true }, top = 6)
            add(parent, button("Discord öffnen") { openDiscord() }, top = 8)
            add(parent, button("Wartende Aktion abbrechen") {
                if (pluginJob?.let { ManagerPluginMailbox.cancel(it) } != true)
                    Toast.makeText(this, "Aktion wird bereits ausgeführt.", Toast.LENGTH_SHORT).show()
            }, top = 8)
            return
        }
        val actions = LinearLayout(this)
        actions.addView(button("Aktualisieren") { pluginAction(if (pluginPage == 0) "snapshot" else "refresh") }, LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("Discord öffnen") { openDiscord() }, LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = dp(6) })
        add(parent, actions, top = 10)
        if (pluginPage == 1) add(parent, label("Ein Tipp installiert das Plugin samt benötigter Abhängigkeiten. Nur vertrauenswürdige Quellen verwenden.", 11f, mutedColor), top = 8)
        if (pluginData == null) {
            add(parent, label("Öffne Discord einmal nach dem Update und aktualisiere dann die Liste. Der Manager nutzt die echte Plugin-Bridge.", 13f, mutedColor), top = 10)
        }
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
                .filter { item -> listOf("name","description","id","author").any { item.optString(it).contains(pluginQuery, ignoreCase = true) } }
                .sortedBy { it.optString("name").lowercase(Locale.ROOT) }
            eyebrow(list, "${filtered.size} Plugins")
            if (filtered.isEmpty()) add(list,label("Keine Einträge. Quellen hinzufügen oder aktualisieren.",13f,mutedColor),top=8)
            for (item in filtered) {
                val id = item.optString("id")
                card(list, top = 8) {
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
                        if (!essential) add(this,button(if (enabled) "Deaktivieren" else "Aktivieren") {
                            pluginAction(if (enabled) "disable" else "enable",JSONObject().put("id",id))
                        },top=6)
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
            currentTab=2;pluginPage=3;render()
        },LinearLayout.LayoutParams(dp(40),dp(42)).apply { rightMargin=dp(6) })
        header.addView(headerIcon("refresh","Aktualisieren") {
            connectionGranted=grantConnection()
            if(currentTab==2&&pluginPage!=3&&pluginJob==null) pluginAction(if(pluginPage==0) "snapshot" else "refresh")
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
            3 -> diagnostics(page)
            else -> about(page)
        }
        val navigation = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surfaceColor, borderColor, 22)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val symbols = arrayOf("home", "module", "plugins", "diagnose", "info")
        for (index in tabs.indices) {
            val selected = index == currentTab
            val cell = column().apply {
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = interactive(if (selected) raisedColor else surfaceColor,
                    if (selected) VxThemes.blend(borderColor,accentColor,.28f) else surfaceColor, 16)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = tabs[index]
                addView(VxIcon(this@ValancedXActivity,symbols[index],if(selected) accentColor else mutedColor),
                    LinearLayout.LayoutParams(dp(24),dp(24)))
                add(this, label(tabs[index], 11f, if (selected) whiteColor else mutedColor, selected).apply {
                    gravity = Gravity.CENTER
                }, top = 2)
                setOnClickListener {
                    if (currentTab != index) { currentTab = index; render(); if (index == 2 && pluginPage != 3 && pluginData == null && pluginJob == null) pluginAction("snapshot") }
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
        if (previousY > 0) scroll.post { scroll.scrollTo(0, previousY) }
    }
}
