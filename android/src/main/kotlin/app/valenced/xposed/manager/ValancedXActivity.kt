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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Native, offline-first companion for the Xposed module. No APK patching,
 * downloads, accounts, or network access from the manager.
 */
class ValancedXActivity : Activity() {
    private val background = Color.rgb(9, 12, 20)
    private val surface = Color.rgb(20, 25, 40)
    private val border = Color.rgb(45, 52, 76)
    private val primary = Color.rgb(176, 156, 255)
    private val mint = Color.rgb(110, 225, 198)
    private val white = Color.rgb(246, 247, 255)
    private val muted = Color.rgb(157, 167, 192)

    private val prefs by lazy { getSharedPreferences(SettingsProvider.PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + .5f).toInt()

    private fun panelBackground(fill: Int = surface, outline: Int = border): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(19).toFloat()
            setColor(fill)
            setStroke(dp(1), outline)
        }

    private fun label(value: String, size: Float, color: Int = white, heavy: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(
                if (heavy) "sans-serif-medium" else "sans-serif",
                if (heavy) Typeface.BOLD else Typeface.NORMAL,
            )
            includeFontPadding = true
        }

    private fun add(
        parent: LinearLayout, child: View,
        top: Int = 0, bottom: Int = 0,
    ) {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        params.setMargins(0, dp(top), 0, dp(bottom))
        parent.addView(child, params)
    }

    private fun card(parent: LinearLayout, content: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        box.content()
        add(parent, box, top = 12)
    }

    private fun action(value: String, filled: Boolean = false, onTap: () -> Unit): TextView =
        label(value, 14f, if (filled) background else white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = panelBackground(
                if (filled) primary else Color.rgb(30, 37, 57),
                if (filled) primary else border,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
        }

    private fun switchRow(
        parent: LinearLayout, name: String, subtitle: String,
        key: String, default: Boolean,
    ) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(11), 0, dp(11))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(texts, label(name, 15f, white, true))
        add(texts, label(subtitle, 12f, muted), top = 5)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = prefs.getBoolean(key, default)
            thumbTintList = ColorStateList.valueOf(if (isChecked) primary else muted)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                render()
            }
        }
        row.addView(toggle)
        add(parent, row)
    }

    private fun installedDiscordVersion(): String? = try {
        packageManager.getPackageInfo("com.discord", 0).versionName ?: "Unbekannt"
    } catch (_: Exception) {
        null
    }

    private fun statusSummary(): String = when (prefs.getString("last_state", null)) {
        "bundle_injected" -> "Bundle zuletzt erfolgreich geladen"
        "hooks_ready" -> "Native Hooks registriert"
        "native_only" -> "Native-Modus ohne JavaScript"
        "initializing" -> "Letzter Start: Initialisierung begonnen"
        "disabled" -> "Beim letzten Start deaktiviert"
        "bundle_error", "bootstrap_error" -> "Hook-Fehler gemeldet"
        else -> "Noch keine Hook-Rückmeldung"
    }

    private fun recordedStatus(): String {
        val timestamp = prefs.getLong("last_time", 0L)
        if (timestamp <= 0L) return "Starte Discord nach der Aktivierung des Moduls."
        val date = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.GERMANY,
        ).format(Date(timestamp))
        return "Letzte Rückmeldung: " + date
    }

    private fun copyDiagnostics() {
        val details = prefs.getString("last_details", "").orEmpty()
        val version = installedDiscordVersion() ?: "nicht installiert"
        val summary = listOf(
            "ValancedX " + BuildConfig.VERSION_NAME,
            "Discord: " + version,
            "Status: " + statusSummary(),
            recordedStatus(),
            if (details.isNotBlank()) "Details: " + details else "Details: keine",
        ).joinToString("\n")
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ValancedX Diagnose", summary))
        Toast.makeText(this, "Diagnose kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun openDiscord() {
        val intent = packageManager.getLaunchIntentForPackage("com.discord")
        if (intent == null) {
            Toast.makeText(this, "Discord ist nicht installiert", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intent)
        }
    }

    private fun showSetup() {
        AlertDialog.Builder(this)
            .setTitle("Xposed-Einrichtung")
            .setMessage(
                "1. Aktiviere ValancedX im LSPosed- oder kompatiblen Xposed-Manager.\n\n" +
                "2. Wähle ausschließlich com.discord als Scope.\n\n" +
                "3. Stoppe Discord vollständig und starte es neu.\n\n" +
                "4. Öffne ValancedX erneut, um die letzte Hook-Rückmeldung zu sehen.\n\n" +
                "Der Manager patcht oder installiert keine Discord-APK."
            )
            .setPositiveButton("Verstanden", null)
            .show()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@ValancedXActivity.background)
            clipToPadding = false
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)

        val headline = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_valancedx)
            contentDescription = "ValancedX"
        }
        headline.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        add(heading, label("ValancedX", 27f, white, true))
        add(heading, label("Xposed Companion · Discord", 12f, primary))
        headline.addView(heading)
        add(content, headline)
        add(content, label("Ein Modul. Deine Discord-App. Kein APK-Patching.", 14f, muted), top = 22)

        card(content) {
            add(this, label("HOOK-STATUS", 11f, mint, true))
            add(this, label(statusSummary(), 19f, white, true), top = 9)
            add(this, label(recordedStatus(), 12f, muted), top = 6)
            val details = prefs.getString("last_details", "").orEmpty()
            if (details.isNotBlank()) add(this, label(details, 12f, muted), top = 10)
            val version = installedDiscordVersion()
            add(
                this,
                label(
                    if (version == null) "Discord: nicht installiert"
                    else "Discord: " + version,
                    12f,
                    if (version == null) muted else mint,
                ),
                top = 13,
            )
        }

        add(content, label("Steuerung", 18f, white, true), top = 28)
        card(content) {
            switchRow(
                this,
                "Xposed-Hook aktivieren",
                "Registriert den Hook beim nächsten Discord-Start.",
                "hook_enabled", true,
            )
            separator(this)
            switchRow(
                this,
                "Hermes-Erweiterungen",
                "Lädt das integrierte JavaScript-Bundle in Discord.",
                "javascript_enabled", true,
            )
            separator(this)
            switchRow(
                this,
                "Lokale Diagnose",
                "Speichert zusätzliche Fehlermeldungen nur auf diesem Gerät.",
                "diagnostics_enabled", true,
            )
            add(this, label("Änderungen werden nach einem vollständigen Discord-Neustart wirksam.", 12f, muted), top = 9)
        }

        add(content, label("Werkzeuge", 18f, white, true), top = 27)
        card(content) {
            add(this, action("Discord öffnen", true) { openDiscord() })
            add(this, action("Xposed-Scope einrichten") { showSetup() }, top = 10)
            add(this, action("Diagnose kopieren") { copyDiagnostics() }, top = 10)
            add(this, action("Diagnose zurücksetzen") {
                prefs.edit().remove("last_state").remove("last_time")
                    .remove("last_details").apply()
                render()
            }, top = 10)
        }

        add(
            content,
            label(
                "ValacedXposed · 0.1.0\n" +
                "Lokaler Xposed-Manager ohne Konto, APK-Patcher oder eigenen Trackingdienst.\n" +
                "Open-Source-Fork auf Basis von Valenced (GPL-3.0).",
                12f,
                muted,
            ),
            top = 27,
        )
    }

    private fun separator(parent: LinearLayout) {
        val line = View(this).apply { setBackgroundColor(border) }
        parent.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
    }
}
