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
    private val backgroundColor = Color.rgb(13, 14, 18)
    private val surfaceColor = Color.rgb(23, 25, 31)
    private val raisedColor = Color.rgb(33, 35, 44)
    private val borderColor = Color.rgb(49, 51, 61)
    private val accentColor = Color.rgb(190, 174, 255)
    private val mintColor = Color.rgb(113, 230, 201)
    private val warmColor = Color.rgb(255, 203, 115)
    private val errorColor = Color.rgb(255, 143, 157)
    private val whiteColor = Color.rgb(245, 245, 252)
    private val mutedColor = Color.rgb(160, 163, 177)
    private val tabs = arrayOf("Start", "Module", "Plugins", "Diagnose", "Info")
    private var currentTab = 0
    private var scrollView: ScrollView? = null
    private var connectionGranted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { render(preserveScroll = true) }
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
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        currentTab = (savedInstanceState?.getInt("tab") ?: 0).coerceIn(0, tabs.lastIndex)
        if (!prefs.getBoolean("welcome_seen", false)) {
            prefs.edit().putBoolean("welcome_seen", true).apply()
            showWelcome()
        }
    }

    override fun onResume() {
        super.onResume()
        connectionGranted = grantConnection()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        render()
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(tint, borderColor, 18)
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
            setPadding(dp(12), dp(13), dp(12), dp(13))
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
        card(parent, tint = Color.rgb(26, 26, 35)) {
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
    private var pluginData: JSONObject? = null
    private var pluginJob: String? = null
    private var pluginNote = ""
    private val pluginPoll = object : Runnable {
        override fun run() {
            val id = pluginJob ?: return
            val raw = ManagerPluginMailbox.result(id)
            if (raw == null) {
                mainHandler.postDelayed(this, 600)
                return
            }
            pluginJob = null
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("error", "Ungültige Antwort") }
            if (result.has("error")) {
                pluginNote = result.optString("error")
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
                pluginNote = result.optString("message")
            }
            if (currentTab == 2) render(preserveScroll = true)
        }
    }

    private fun pluginAction(operation: String, data: JSONObject = JSONObject()) {
        if (pluginJob != null) return
        connectionGranted = grantConnection()
        runCatching { ManagerPluginMailbox.submit(operation, data) }
            .onSuccess {
                pluginJob = it
                pluginNote = "Discord verarbeitet die Aktion …"
                mainHandler.removeCallbacks(pluginPoll)
                mainHandler.post(pluginPoll)
            }.onFailure { pluginNote = it.message.orEmpty() }
        if (currentTab == 2) render(preserveScroll = true)
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
            .setPositiveButton("Weiter") { _, _ -> addPluginSource(input.text.toString().trim()) }.show()
    }

    private fun pluginScreen(parent: LinearLayout) {
        eyebrow(parent, "PLUGIN MANAGER")
        add(parent, label("Plugins", 27f, whiteColor, true), top = 4)
        add(parent, label("Installieren, verwalten und Quellen hinzufügen.", 13f, mutedColor), top = 5)
        val nav = LinearLayout(this).apply { background = rounded(surfaceColor, borderColor, 15); setPadding(dp(3),dp(3),dp(3),dp(3)) }
        for ((i, text) in arrayOf("Installiert", "Entdecken", "Quellen").withIndex()) {
            nav.addView(label(text, 12f, if (pluginPage == i) whiteColor else mutedColor, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(12), dp(2), dp(12))
                background = interactive(if (pluginPage == i) raisedColor else surfaceColor, borderColor, 12)
                setOnClickListener { pluginPage = i; pluginQuery = ""; render() }
            }, LinearLayout.LayoutParams(0,-2,1f))
        }
        add(parent, nav, top = 15)
        if (pluginNote.isNotBlank()) add(parent, label(pluginNote, 12f, if (pluginJob != null) accentColor else mutedColor), top = 10)
        if (pluginJob != null) {
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
        if (pluginData == null) {
            add(parent, label("Öffne Discord einmal nach dem Update und aktualisiere dann die Liste. Der Manager nutzt die echte Plugin-Bridge.", 13f, mutedColor), top = 10)
        }
        if (pluginPage == 2) {
            val repos = pluginData?.optJSONArray("repos") ?: JSONArray()
            eyebrow(parent, "Aktive & gespeicherte Quellen")
            for (i in 0 until repos.length()) {
                val item = repos.optJSONObject(i) ?: continue
                val url = item.optString("url")
                card(parent, top = 8) {
                    add(this, label(item.optString("name"), 16f, whiteColor, true))
                    add(this, label(url, 11f, mutedColor), top = 5)
                    statusRow(this, "${item.optInt("count")} Plugins", if (item.optBoolean("enabled")) "Aktiv" else "Inaktiv", if (item.optBoolean("enabled")) mintColor else mutedColor)
                    add(this, button(if (item.optBoolean("enabled")) "Deaktivieren" else "Aktivieren") { pluginAction("toggle_repo", JSONObject().put("url", url)) }, top = 5)
                    add(this, button("Entfernen", destructive = true) {
                        AlertDialog.Builder(this@ValancedXActivity).setTitle("Quelle entfernen?")
                            .setMessage("Installierte Plugins bleiben erhalten, verlieren aber ihre Update-Quelle.")
                            .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen") { _, _ -> pluginAction("remove_repo", JSONObject().put("url",url)) }.show()
                    }, top = 6)
                }
            }
            eyebrow(parent, "Empfohlene Quellen")
            card(parent, top = 8) {
                add(this, label("Valenced Community", 16f, whiteColor, true))
                add(this, label("Offizieller, gepflegter Plugin-Katalog. Nachfolger von Revenge Next.", 12f, mutedColor), top = 5)
                add(this, button("Community hinzufügen", true) { addPluginSource("https://community.valenced.app/index.json") }, top = 8)
                line(this)
                add(this, label("dxckie-cell", 16f, whiteColor, true))
                add(this, label("Externer Valenced-Katalog. Freiwillige Zusatzquelle.", 12f, mutedColor), top = 5)
                add(this, button("Zusatzquelle hinzufügen") { addPluginSource("https://dxckie-cell.github.io/valenced-plugins/index.json") }, top = 8)
                line(this)
                add(this, label("Revenge Next", 16f, whiteColor, true))
                add(this, label("Archiviert. Viele Plugins wurden zu Valenced portiert. Alte Revenge-/Vendetta-Formate sind nicht automatisch kompatibel.", 12f, mutedColor), top = 5)
                add(this, button("Revenge-Archiv ansehen") { openWeb("https://github.com/revenge-mod/revenge-community-plugins") }, top = 8)
            }
            add(parent, button("+ Eigene Repository-URL", true) { customPluginSource() }, top = 12)
            return
        }

        val entries = pluginData?.optJSONArray(if (pluginPage == 0) "installed" else "available") ?: JSONArray()
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
            val filtered = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
                .filter { item -> listOf("name","description","id","author").any { item.optString(it).contains(pluginQuery, ignoreCase = true) } }
                .sortedBy { it.optString("name").lowercase(Locale.ROOT) }
            eyebrow(list, "${filtered.size} Plugins")
            if (filtered.isEmpty()) add(list,label("Keine Einträge. Quellen hinzufügen oder aktualisieren.",13f,mutedColor),top=8)
            for (item in filtered) {
                val id = item.optString("id")
                card(list, top = 8) {
                    add(this,label(item.optString("name",id),16f,whiteColor,true))
                    add(this,label("v" + item.optString("version") + " · " + item.optString("author"),11f,accentColor),top=4)
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
                        add(this,button(if (item.optBoolean("installed")) "Version prüfen" else "Installieren",true) {
                            pluginAction("plan",JSONObject().put("id",id).put("repo",repo))
                        },top=8)
                    }
                }
            }
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
        val previousY = if (preserveScroll) scrollView?.scrollY ?: 0 else 0
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
        header.addView(label("↻", 23f, accentColor, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Aktualisieren"
            background = interactive(surfaceColor, borderColor, 12)
            isFocusable = true
            setOnClickListener { connectionGranted = grantConnection(); render(preserveScroll = true) }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
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
        val symbols = arrayOf("⌂", "≡", "⊞", "≋", "ⓘ")
        for (index in tabs.indices) {
            val selected = index == currentTab
            val cell = column().apply {
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = interactive(if (selected) raisedColor else surfaceColor,
                    if (selected) borderColor else surfaceColor, 16)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = tabs[index]
                add(this, label(symbols[index], 21f, if (selected) accentColor else mutedColor, true).apply {
                    gravity = Gravity.CENTER
                })
                add(this, label(tabs[index], 11f, if (selected) whiteColor else mutedColor, selected).apply {
                    gravity = Gravity.CENTER
                }, top = 2)
                setOnClickListener {
                    if (currentTab != index) { currentTab = index; render(); if (index == 2 && pluginData == null && pluginJob == null) pluginAction("snapshot") }
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
