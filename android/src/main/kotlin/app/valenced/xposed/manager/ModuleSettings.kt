package app.valenced.xposed.manager

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import app.valenced.xposed.BuildConfig

/** The only IPC channel shared with Discord. No privileged Android permissions. */
internal object ModuleSettings {
    data class Config(
        val enabled: Boolean = false,
        val javascriptEnabled: Boolean = false,
        val safeMode: Boolean = true,
    )

    private val uri: Uri
        get() = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".settings")

    fun read(context: Context): Config =
        runCatching {
            val result = context.contentResolver.call(uri, "configuration", null, null)
                ?: error("Missing manager configuration")
            Config(
                enabled = result.getBoolean("hook_enabled", true),
                javascriptEnabled = result.getBoolean("javascript_enabled", true),
                safeMode = result.getBoolean("safe_mode", false),
            )
        }.getOrElse {
            // Fail closed. A broken or missing manager must not unexpectedly
            // inject code into another application.
            Log.e("ValancedX", "Cannot reach manager; skipping optional hooks", it)
            Config()
        }

    fun report(context: Context, state: String, details: String? = null) {
        runCatching {
            val extras = Bundle().apply {
                putString("state", state)
                putString("details", details?.take(240))
            }
            context.contentResolver.call(uri, "report", null, extras)
        }.onFailure {
            Log.w("ValancedX", "Unable to send local module event", it)
        }
    }
}
