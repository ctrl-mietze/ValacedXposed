package app.valenced.xposed

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import app.valenced.bridge.ValencedBridge
import app.valenced.xposed.api.HostScope
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweaks.*
import app.valenced.xposed.tweaks.base.lifecycleSupport
import app.valenced.xposed.tweaks.base.scriptLoader
import app.valenced.xposed.tweaks.bridge.ValencedBridgeRegistry
import app.valenced.xposed.tweaks.bridge.additionalBridgeMethods
import app.valenced.xposed.tweaks.bridge.valencedBridgeSupport
import app.valenced.xposed.tweaks.plugins.pluginInstallMethods
import app.valenced.xposed.tweaks.plugins.pluginLoader
import app.valenced.xposed.tweaks.plugins.pluginMethods
import app.valenced.xposed.tweaks.plugins.pluginStates
import app.valenced.xposed.tweaks.plugins.repos.pluginRepos
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

private lateinit var modulePath: String

/**
 * ValacedXposed deliberately hooks only Discord's main process.
 * The upstream native/JS bridge IDs remain stable for bundle compatibility.
 */
@Suppress("UNUSED")
class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        private val attached = AtomicBoolean(false)
        private val initialized = AtomicBoolean(false)
    }

    private val tweaks: List<TweakSpec> = listOf(
        valencedBridgeSupport,
        scriptLoader,
        discordVersionRetriever,
        fixResources,
        caches,
        pluginStates,
        pluginRepos,
        devLoader,
        valencedBundleManifest,
        recoveryCompanion,
        discordDevSupport,
        additionalBridgeMethods,
        pluginLoader,
        pluginMethods,
        pluginInstallMethods,
        valencedScriptLoader,
    )

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != ValencedConstants.TARGET_PACKAGE) return
        if (param.processName != ValencedConstants.TARGET_PACKAGE) return
        if (!attached.compareAndSet(false, true)) return

        if (!::modulePath.isInitialized) {
            XposedBridge.log("ValancedX: module path unavailable; skipping hook")
            return
        }

        val scope = HostScopeImpl(
            modulePath = modulePath,
            appInfo = param.appInfo,
            classLoader = param.classLoader,
        )

        try {
            lifecycleSupport.applyTo(scope)
            scope.withAppContext { appContext ->
                if (!initialized.compareAndSet(false, true)) return@withAppContext
                val config = ModuleSettings.read(appContext)
                if (!config.enabled) {
                    ModuleSettings.report(appContext, "disabled", "Hook disabled in ValancedX")
                    return@withAppContext
                }

                ModuleSettings.report(appContext, "initializing")
                try {
                    DexFinder.init(param.appInfo, param.classLoader, modulePath)
                    for (spec in tweaks) {
                        if (!config.javascriptEnabled && spec === valencedScriptLoader) continue
                        spec.applyTo(scope)
                    }
                    ModuleSettings.report(
                        appContext,
                        if (config.javascriptEnabled) "hooks_ready" else "native_only",
                    )
                } catch (error: Throwable) {
                    XposedBridge.log("ValancedX: hook bootstrap failed: $error")
                    ModuleSettings.report(appContext, "bootstrap_error", error.message)
                } finally {
                    runCatching { DexFinder.release() }.onFailure {
                        XposedBridge.log("ValancedX: DexKit cleanup failed: $it")
                    }
                }
            }
        } catch (error: Throwable) {
            XposedBridge.log("ValancedX: unable to install lifecycle hook: $error")
        }
    }
}

private class HostScopeImpl(
    override val modulePath: String,
    override val appInfo: ApplicationInfo,
    override val classLoader: ClassLoader,
) : HostScope {
    override val bridge: ValencedBridge get() = ValencedBridgeRegistry
    override val dexFinder: DexFinder get() = DexFinder
    override fun withAppContext(block: (Context) -> Unit) =
        app.valenced.xposed.tweaks.base.withAppContext(block)
    override fun withAppActivity(block: (Activity) -> Unit) =
        app.valenced.xposed.tweaks.base.withAppActivity(block)
}
