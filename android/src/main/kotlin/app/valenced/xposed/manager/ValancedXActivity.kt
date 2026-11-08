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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** An offline-first native manager. No Discord APK patcher or telemetry backend. */
class ValancedXActivity : Activity() {
    private val backgroundColor = Color.rgb(10, 13, 23)
    private val surfaceColor = Color.rgb(22, 27, 43)
    private val raisedColor = Color.rgb(32, 38, 58)
    private val borderColor = Color.rgb(53, 60, 81)
    private val accentColor = Color.rgb(181, 158, 255)
    private val mintColor = Color.rgb(113, 230, 201)
    private val warmColor = Color.rgb(255, 203, 115)
    private val errorColor = Color.rgb(255, 143, 157)
    private val whiteColor = Color.rgb(245, 245, 252)
    private val mutedColor = Color.rgb(158, 169, 192)
    private val tabs = arrayOf("Start", "Module", "Diagnose", "Info")
    private var currentTab = 0

    private val prefs by lazy {
        getSharedPreferences(SettingsProvider.PREFS, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        currentTab = savedInstanceState?.getInt("tab") ?: 0
        if (!prefs.getBoolean("welcome_seen", false)) {
            prefs.edit().putBoolean("welcome_seen", true).apply()
            showWelcome()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

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

    private fun add(parent: LinearLayout, view: View, top: Int = 0, bottom: Int = 0) {
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(top), 0, dp(bottom))
        parent.addView(view, params)
    }

    private fun card(parent: LinearLayout, top: Int = 12, tint: Int = surfaceColor, build: LinearLayout.() -> Unit) {
        val child = column().apply {
            setPadding(dp(17), dp(16), dp(17), dp(17))
            background = rounded(tint)
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
            background = rounded(
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
                render()
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
        "bundle_injected" -> "Hermes-Bundle zuletzt geladen"
        "hooks_ready" -> "Hooks zuletzt registriert"
        "framework_attached" -> "Xposed-Einstieg erkannt"
        "module_path_missing" -> "Modul-Dateipfad nicht gefunden"
        "script_loader_missing" -> "React-Native-Loader nicht gefunden"
        "native_only" -> "Minimalmodus aktiv"
        "safe_mode" -> "Recovery-Modus aktiv"
        "disabled" -> "Beim letzten Start deaktiviert"
        "initializing" -> "Letzter Start: Initialisierung"
        "bundle_error" -> "Fehler beim Hermes-Bundle"
        "bootstrap_error" -> "Fehler bei der Hook-Initialisierung"
        else -> "Noch keine Hook-Rückmeldung"
    }

    private fun stateColor(): Int = when (state()) {
        "bundle_injected" -> mintColor
        "hooks_ready", "framework_attached" -> accentColor
        "script_loader_missing", "bundle_error", "bootstrap_error", "module_path_missing" -> errorColor
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
        card(parent, top = 18, tint = Color.rgb(27, 28, 49)) {
            add(this, label("MODULE HEALTH", 11f, accentColor, true))
            add(this, label(statusLabel(), 21f, stateColor(), true), top = 12)
            add(this, label("Letzte Rückmeldung: " + time(prefs.getLong("last_time", 0L)), 12f, mutedColor), top = 8)
            add(
                this,
                label(
                    if (discordVersion() != null) "Discord " + discordVersion() + " erkannt"
                    else "Discord wurde nicht gefunden",
                    13f, if (discordVersion() != null) mintColor else warmColor,
                ),
                top = 16,
            )
            add(
                this,
                label(
                    if (prefs.getLong("last_time", 0L) == 0L)
                        "Keine Xposed-Injektion bestätigt. Prüfe, ob ein kompatibles LSPosed-Framework aktiv ist und com.discord im Scope liegt. LSPatch allein lädt keine Hooks in die unveränderte Discord-App."
                    else
                        "Status basiert auf beobachteten Ereignissen, nicht auf einem permanenten Live-Test.",
                    12f, mutedColor,
                ),
                top = 9,
            )
        }
        stats(parent)
        heading(parent, "Schnellzugriff")
        card(parent) {
            add(this, button("Discord öffnen", primary = true) { openDiscord() })
            add(this, button("Xposed-Scope einrichten") { showSetup() }, top = 10)
            add(this, button("Diagnose kopieren") { copyDiagnostics() }, top = 10)
        }
        heading(parent, "Kompatibilität")
        card(parent) {
            details(this, "Android-Manager", "ValancedX " + BuildConfig.VERSION_NAME, mintColor)
            line(this)
            details(this, "Discord-Paket", if (discordVersion() != null) "com.discord installiert" else "Nicht installiert")
            line(this)
            details(this, "Xposed-Framework", "Scope im Framework konfigurieren", accentColor)
            line(this)
            details(this, "Hook-Kompatibilität", "Noch kein bestätigter Gerätetest", mutedColor)
        }
    }

    private fun modules(parent: LinearLayout) {
        heading(parent, "Hook-Konfiguration")
        card(parent) {
            setting(this, "Xposed-Hook", "Startet ausschließlich im Discord-Hauptprozess.", "hook_enabled", true)
            line(this)
            setting(this, "Hermes-Integration", "Lädt die native Bridge und das integrierte Bundle.", "javascript_enabled", true)
            line(this)
            setting(this, "Recovery-Modus", "Deaktiviert sämtliche optionalen Hooks beim nächsten Start.", "safe_mode", false)
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
        heading(parent, "Datenschutz")
        card(parent) {
            setting(this, "Ereignishistorie", "Maximal 32 lokale Status-Ereignisse.", "history_enabled", true)
            line(this)
            setting(this, "Kurze Fehlerdetails", "Speichert Fehlermeldungen nur auf diesem Gerät.", "diagnostics_enabled", true)
            add(this, label("Kein eigener Telemetrie-Upload. Änderungen gelten beim nächsten Discord-Neustart.", 12f, mutedColor), top = 10)
        }
        heading(parent, "Wiederherstellung")
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
        heading(parent, "Diagnosezentrale")
        card(parent) {
            add(this, label(statusLabel(), 19f, stateColor(), true))
            add(this, label("Zuletzt: " + time(prefs.getLong("last_time", 0L)), 12f, mutedColor), top = 8)
            add(this, label("Fehlerfolge: " + prefs.getInt("error_streak", 0) + " / 3", 12f, mutedColor), top = 7)
            val last = prefs.getString("last_details", "").orEmpty()
            if (last.isNotBlank()) add(this, label(last, 12f, warmColor), top = 10)
        }
        stats(parent)
        card(parent) {
            add(this, button("Bericht kopieren", true) { copyDiagnostics() })
            add(this, button("Bericht teilen") { shareDiagnostics() }, top = 9)
        }
        heading(parent, "Ereignisverlauf")
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
        heading(parent, "Über ValancedX")
        card(parent, tint = Color.rgb(27, 28, 49)) {
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
        heading(parent, "Open Source")
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
        val launch = packageManager.getLaunchIntentForPackage("com.discord")
        if (launch == null) {
            Toast.makeText(this, "Discord nicht gefunden", Toast.LENGTH_SHORT).show()
        } else startActivity(launch)
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
                "1. Ein auf deiner Android-Version funktionierendes systemweites LSPosed/Xposed-Framework aktivieren. LSPatch alleine reicht für eine unveränderte Discord-App nicht aus.\n\n" +
                "2. ValancedX im Framework aktivieren.\n\n" +
                "3. Ausschließlich com.discord als Scope wählen.\n\n" +
                "4. Discord vollständig stoppen und neu starten.\n\n" +
                "5. Unter Diagnose den Hook-Status prüfen.\n\n" +
                "Bei Problemen Recovery aktivieren. Es werden keine Discord-APKs gepatcht.",
            )
            .setPositiveButton("Fertig", null)
            .show()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }
        val page = column().apply {
            setPadding(dp(19), dp(24), dp(19), dp(35))
        }
        scroll.addView(page)
        setContentView(scroll)

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_valancedx)
            contentDescription = "ValancedX"
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val title = column().apply { setPadding(dp(12), 0, 0, 0) }
        add(title, label("ValancedX", 27f, whiteColor, true))
        add(title, label("XPOSED MODULE  /  " + BuildConfig.VERSION_NAME, 11f, accentColor, true), top = 5)
        header.addView(title)
        add(page, header)
        add(page, label("Discord, ohne gepatchten Client.", 14f, mutedColor), top = 18)

        val navigation = LinearLayout(this).apply {
            background = rounded(surfaceColor, borderColor, 16)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        for (index in tabs.indices) {
            val selected = index == currentTab
            val cell = label(
                tabs[index], 12f,
                if (selected) backgroundColor else mutedColor,
                selected,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                if (selected) background = rounded(accentColor, accentColor, 12)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentTab = index
                    render()
                }
            }
            navigation.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(page, navigation, top = 22)

        when (currentTab) {
            0 -> dashboard(page)
            1 -> modules(page)
            2 -> diagnostics(page)
            else -> about(page)
        }
        add(
            page,
            label("VALACEDXPOSED · GPL-3.0 · DISCORD XPOSED", 10f, mutedColor).apply {
                gravity = Gravity.CENTER
            },
            top = 30,
        )
    }
}
