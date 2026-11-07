package app.valenced.xposed.manager

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import app.valenced.xposed.BuildConfig

/** Narrow IPC contract between Discord and the installed module manager. */
internal object ModuleSettings {
    data class Config(
        val enabled: Boolean = true,
        val javascriptEnabled: Boolean = true,
    )

    private val uri: Uri
        get() = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".settings")

    fun read(context: Context): Config =
        runCatching {
            val result = context.contentResolver.call(uri, "configuration", null, null)
            Config(
                enabled = result?.getBoolean("hook_enabled", true) ?: true,
                javascriptEnabled = result?.getBoolean("javascript_enabled", true) ?: true,
            )
        }.getOrElse {
            Log.w("ValancedX", "Manager configuration unavailable; using defaults", it)
            Config()
        }

    fun report(context: Context, state: String, details: String? = null) {
        runCatching {
            val extras = Bundle().apply {
                putString("state", state)
                putString("details", details?.take(240))
            }
            context.contentResolver.call(uri, "report", null, extras)
        }.onFailure { Log.w("ValancedX", "Could not send local hook status", it) }
    }
}
