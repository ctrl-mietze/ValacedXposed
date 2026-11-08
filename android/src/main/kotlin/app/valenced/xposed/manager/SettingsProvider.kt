package app.valenced.xposed.manager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * Controlled IPC boundary. The caller must be this APK or the actual com.discord UID.
 * No arbitrary reads/writes to the file system and no exported database operations.
 */
class SettingsProvider : ContentProvider() {
    companion object {
        const val PREFS = "valancedx"
        const val MAX_EVENTS = 32
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val uid = Binder.getCallingUid()
        val isSelf = uid == Process.myUid()
        val isDiscord = ctx.packageManager.getPackagesForUid(uid).orEmpty().contains("com.discord")
        if (!isSelf && !isDiscord) throw SecurityException("ValancedX: unsupported caller")

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (method) {
            "configuration" -> Bundle().apply {
                putBoolean("hook_enabled", prefs.getBoolean("hook_enabled", true))
                putBoolean("javascript_enabled", prefs.getBoolean("javascript_enabled", true))
                putBoolean("safe_mode", prefs.getBoolean("safe_mode", false))
            }
            "report" -> {
                if (!isDiscord) throw SecurityException("Only Discord can publish module status")
                val state = extras?.getString("state").orEmpty().take(48)
                val allowed = setOf(
                    "disabled", "safe_mode", "initializing", "hooks_ready",
                    "framework_attached", "module_path_missing",
                    "native_only", "bootstrap_error", "bundle_injected", "bundle_error",
                    "script_loader_missing",
                )
                if (state !in allowed) throw IllegalArgumentException("Invalid module status")
                val details = extras?.getString("details").orEmpty().take(240)
                val at = System.currentTimeMillis()

                // Serialized preference changes are executed in the single provider process.
                val edit = prefs.edit()
                    .putString("last_state", state)
                    .putLong("last_time", at)

                val keepDetails = prefs.getBoolean("diagnostics_enabled", true)
                if (keepDetails && details.isNotBlank()) {
                    edit.putString("last_details", details)
                } else {
                    edit.remove("last_details")
                }

                var errors = prefs.getInt("total_errors", 0)
                var successes = prefs.getInt("total_successes", 0)
                var boots = prefs.getInt("total_starts", 0)
                var streak = prefs.getInt("error_streak", 0)

                when (state) {
                    "initializing" -> boots++
                    "bundle_injected" -> {
                        successes++
                        streak = 0
                    }
                    "bootstrap_error", "bundle_error", "script_loader_missing", "module_path_missing" -> {
                        errors++
                        streak++
                        // A real reported boot failure, repeated three times, triggers
                        // a recovery-only next launch. No speculative crash detection.
                        if (streak >= 3) {
                            edit.putBoolean("safe_mode", true)
                                .putBoolean("auto_recovery", true)
                        }
                    }
                }

                edit.putInt("total_errors", errors)
                    .putInt("total_successes", successes)
                    .putInt("total_starts", boots)
                    .putInt("error_streak", streak)

                if (prefs.getBoolean("history_enabled", true)) {
                    val entries = runCatching {
                        JSONArray(prefs.getString("event_history", "[]"))
                    }.getOrElse { JSONArray() }
                    entries.put(JSONObject().apply {
                        put("at", at)
                        put("state", state)
                        if (keepDetails && details.isNotBlank()) put("details", details)
                    })
                    val capped = JSONArray()
                    val start = (entries.length() - MAX_EVENTS).coerceAtLeast(0)
                    for (i in start until entries.length()) capped.put(entries.getJSONObject(i))
                    edit.putString("event_history", capped.toString())
                }

                edit.apply()
                Bundle.EMPTY
            }
            else -> throw IllegalArgumentException("Unknown method")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Unsupported operation")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Unsupported operation")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Unsupported operation")
}
