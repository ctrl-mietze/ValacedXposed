package app.valenced.xposed.tweaks.bridge

import android.app.AndroidAppHelper
import android.content.Context
import android.media.AudioManager
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Discord can obtain AudioManager while Application.attachBaseContext is
 * still running. On Android versions that dereference mApplicationContext
 * directly in setMode(), this cached manager can crash even with Stereo Call
 * disabled. Repair only the missing context, never the requested audio mode.
 * Installed only in Discord's main process, independent of plugin state.
 */
internal object AudioContextGuard {
    private const val HOST = "com.discord"
    private const val TAG = "VexcordAudioGuard"
    private val registered = AtomicBoolean(false)
    private val repairs = AtomicInteger(0)
    @Volatile private var attached: Context? = null

    fun attach(context: Context) {
        if (runCatching { context.packageName == HOST }.getOrDefault(false)) attached = context
    }

    fun repairCount() = repairs.get()
    fun isInstalled() = registered.get()

    private fun repair(manager: AudioManager): Boolean = runCatching {
        val field = XposedHelpers.findField(AudioManager::class.java, "mApplicationContext")
        if (field.get(manager) != null) return@runCatching false
        // Do not acquire another AudioManager during application attachment.
        val candidate = AndroidAppHelper.currentApplication() ?: attached ?: return@runCatching false
        val app = candidate.applicationContext ?: candidate
        if (app.packageName != HOST) return@runCatching false
        // Confirm the attached context can actually resolve its package.
        app.getOpPackageName()
        synchronized(manager) {
            if (field.get(manager) != null) return@synchronized false
            field.set(manager, app)
            repairs.incrementAndGet()
            Log.i(TAG, "Repaired missing Discord AudioManager context")
            true
        }
    }.getOrDefault(false)

    fun install() {
        if (!registered.compareAndSet(false, true)) return
        runCatching {
            val method = AudioManager::class.java.getDeclaredMethod("setMode", Int::class.javaPrimitiveType!!)
            XposedBridge.hookMethod(method, object : XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? AudioManager)?.let { repair(it) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val error = param.throwable as? NullPointerException ?: return
                    // Only retry the exact null-context crash observed on the device.
                    if (!AudioContextRecoveryPolicy.isKnownCrash(error)) return
                    val manager = param.thisObject as? AudioManager ?: return
                    if (!repair(manager)) return
                    runCatching {
                        XposedBridge.invokeOriginalMethod(method, manager, param.args)
                    }.onSuccess { param.result = it }
                        .onFailure { Log.w(TAG, "Context repair retry failed; preserving original error", it) }
                }
            })
        }.onFailure {
            registered.set(false)
            Log.w(TAG, "Context compatibility hook unavailable", it)
        }
    }
}
