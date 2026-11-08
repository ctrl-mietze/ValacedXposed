package app.valenced.xposed.manager

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import app.valenced.xposed.BuildConfig

/**
 * Lightweight cross-process settings channel.
 *
 * The Xposed framework scope is an explicit user opt-in. If manager IPC is
 * temporarily unreachable, keep that scope's baseline behavior (enabled)
 * instead of silently disabling the injected client. Log the failure loudly.
 * An accessible manager remains authoritative, including OFF/Recovery.
 *
 * Legacy Valenced hooks are registered before Application.attach. Only the
 * code execution inside the Hermes loader is gated when Context is ready.
 */
internal object ModuleSettings {
    data class Config(
        val enabled: Boolean = true,
        val javascriptEnabled: Boolean = true,
        val safeMode: Boolean = false,
    )

    @Volatile
    private var active = Config()

    fun current(): Config = active

    private val uri: Uri
        get() = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".settings")

    fun read(context: Context): Config {
        val next = runCatching {
            val result = context.contentResolver.call(uri, "configuration", null, null)
                ?: error("No configuration Bundle from ValancedX manager")
            Config(
                enabled = result.getBoolean("hook_enabled", true),
                javascriptEnabled = result.getBoolean("javascript_enabled", true),
                safeMode = result.getBoolean("safe_mode", false),
            )
        }.getOrElse {
            Log.w(
                "ValancedX",
                "Manager IPC unavailable; retaining explicit Xposed scope defaults",
                it,
            )
            // Existing LSPosed scope grants permission to load this module.
            Config()
        }
        active = next
        return next
    }

    fun report(context: Context, state: String, details: String? = null) {
        runCatching {
            val extras = Bundle().apply {
                putString("state", state)
                putString("details", details?.take(240))
            }
            context.contentResolver.call(uri, "report", null, extras)
        }.onFailure {
            Log.w("ValancedX", "Could not send manager hook status", it)
        }
    }
}
