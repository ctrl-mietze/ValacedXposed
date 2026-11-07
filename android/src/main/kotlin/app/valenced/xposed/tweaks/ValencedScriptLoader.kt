package app.valenced.xposed.tweaks

import app.valenced.xposed.ValencedConstants
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.base.InjectorScope
import app.valenced.xposed.tweaks.base.registerScriptInjector
import app.valenced.xposed.tweaks.base.withAppContext
import java.util.concurrent.atomic.AtomicBoolean

/** Ensures that an individual Discord process loads its Hermes extension once. */
private val bundleAttempted = AtomicBoolean(false)

val valencedScriptLoader by tweak {
    registerScriptInjector { scope: InjectorScope ->
        runValancedXScripts(scope)
    }
}

private fun runValancedXScripts(scope: InjectorScope) {
    if (!bundleAttempted.compareAndSet(false, true)) return
    val log = scope.tweakLog

    try {
        val devBundle = DevLoader.awaitBundle()
        for (preload in DevLoader.preloads()) {
            log.d("Loading development preload: " + preload.name)
            scope.runFile(preload.absolutePath)
        }

        if (devBundle != null) {
            log.i("Loading local development bundle")
            scope.runFile(devBundle.absolutePath)
        } else {
            log.i("Loading bundled ValancedX Hermes script")
            scope.runAsset(ValencedConstants.BUNDLE_ASSET)
        }
        withAppContext { context ->
            ModuleSettings.report(context, "bundle_injected")
        }
    } catch (error: Throwable) {
        log.e("ValancedX Hermes injection failed", error)
        withAppContext { context ->
            ModuleSettings.report(context, "bundle_error", error.message)
        }
    }
}
