package app.valenced.xposed.tweaks

import app.valenced.xposed.ValencedConstants
import app.valenced.xposed.manager.HookMode
import app.valenced.xposed.manager.HookPolicy
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.base.InjectorScope
import app.valenced.xposed.tweaks.base.registerScriptInjector
import app.valenced.xposed.tweaks.base.withAppContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keep upstream's ScriptInjector and its early registration. The manager
 * config gates execution here, immediately before React Native loads its
 * original Hermes bundle, not registration of the native Xposed hooks.
 */
private val bundleAttempted = AtomicBoolean(false)

val valencedScriptLoader by tweak {
    registerScriptInjector { scope: InjectorScope ->
        runValancedXScripts(scope)
    }
}

private fun runValancedXScripts(scope: InjectorScope) {
    if (!bundleAttempted.compareAndSet(false, true)) return
    val log = scope.tweakLog

    val config = ModuleSettings.current()
    val mode = HookPolicy.resolve(
        config.enabled,
        config.javascriptEnabled,
        config.safeMode,
    )
    if (!HookPolicy.shouldInject(mode)) {
        log.i("Hermes injection suppressed by manager mode: $mode")
        return
    }

    try {
        // Preserve the official upstream bundle and bridge ABI.
        val devBundle = DevLoader.awaitBundle()
        for (preload in DevLoader.preloads()) {
            log.d("Loading development preload: " + preload.name)
            scope.runFile(preload.absolutePath)
        }

        if (devBundle != null) {
            log.i("Loading local development bundle")
            scope.runFile(devBundle.absolutePath)
        } else {
            log.i("Loading ValancedX Hermes bundle before Discord's bundle")
            scope.runAsset(ValencedConstants.BUNDLE_ASSET)
        }

        withAppContext { context ->
            ModuleSettings.report(context, "bundle_injected")
        }
    } catch (error: Throwable) {
        log.e("ValancedX Hermes script injection failed", error)
        withAppContext { context ->
            ModuleSettings.report(context, "bundle_error", error.message)
        }
    }
}
