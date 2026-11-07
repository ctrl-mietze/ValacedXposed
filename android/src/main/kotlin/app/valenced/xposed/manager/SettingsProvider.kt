package app.valenced.xposed.manager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * IPC is scoped to our UID and Discord's actual UID.
 * Only configuration reads and hook status writes are exposed.
 */
class SettingsProvider : ContentProvider() {
    companion object {
        const val PREFS = "valancedx"
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val packages = ctx.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        val isSelf = Binder.getCallingUid() == Process.myUid()
        val isDiscord = packages.contains("com.discord")
        if (!isSelf && !isDiscord) throw SecurityException("ValancedX: caller not authorized")

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (method) {
            "configuration" -> Bundle().apply {
                putBoolean("hook_enabled", prefs.getBoolean("hook_enabled", true))
                putBoolean("javascript_enabled", prefs.getBoolean("javascript_enabled", true))
            }
            "report" -> {
                if (!isDiscord) throw SecurityException("Only Discord can report module events")
                val state = extras?.getString("state").orEmpty().take(48)
                val allowed = setOf(
                    "disabled", "initializing", "hooks_ready", "native_only",
                    "bootstrap_error", "bundle_injected", "bundle_error",
                )
                if (state !in allowed) throw IllegalArgumentException("Unknown state")
                val write = prefs.edit()
                    .putString("last_state", state)
                    .putLong("last_time", System.currentTimeMillis())
                if (prefs.getBoolean("diagnostics_enabled", true)) {
                    write.putString("last_details", extras?.getString("details").orEmpty().take(240))
                } else {
                    write.remove("last_details")
                }
                write.apply()
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
        throw UnsupportedOperationException("read-only API")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only API")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only API")
}
