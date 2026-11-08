package app.valenced.xposed

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import app.valenced.bridge.ValencedBridge
import app.valenced.xposed.api.HostScope
import app.valenced.xposed.manager.HookMode
import app.valenced.xposed.manager.HookPolicy
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweaks.*
import app.valenced.xposed.tweaks.base.lifecycleSupport
import app.valenced.xposed.tweaks.base.scriptLoader
import app.valenced.xposed.tweaks.base.scriptLoaderInstalled
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
 * Discord-only Xposed entrypoint.
 *
 * The loader is kept ABI-compatible with the upstream Hermes bundle.
 * All controls are read from the separately installed ValancedX manager.
 */
@Suppress("UNUSED")
class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        private val attached = AtomicBoolean(false)
        private val initialized = AtomicBoolean(false)
    }

    private val runtime: List<TweakSpec> = listOf(
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
            XposedBridge.log("ValancedX: Xposed module path missing")
            return
        }

        val scope = HostScopeImpl(
            modulePath = modulePath,
            appInfo = param.appInfo,
            classLoader = param.classLoader,
        )

        // Minimal lifecycle hook always runs so the manager can receive
        // a status, even when safe mode disables all optional integrations.
        lifecycleSupport.applyTo(scope)
        scope.withAppContext { hostContext ->
            if (!initialized.compareAndSet(false, true)) return@withAppContext

            val config = ModuleSettings.read(hostContext)
            when (HookPolicy.resolve(config.enabled, config.javascriptEnabled, config.safeMode)) {
                HookMode.OFF -> {
                    ModuleSettings.report(hostContext, "disabled")
                    return@withAppContext
                }
                HookMode.RECOVERY -> {
                    ModuleSettings.report(hostContext, "safe_mode")
                    return@withAppContext
                }
                HookMode.MINIMAL -> {
                    ModuleSettings.report(hostContext, "native_only")
                    return@withAppContext
                }
                HookMode.FULL -> Unit
            }

            ModuleSettings.report(hostContext, "initializing")
            try {
                DexFinder.init(param.appInfo, param.classLoader, modulePath)
                for (spec in runtime) spec.applyTo(scope)
                if (!scriptLoaderInstalled) {
                    ModuleSettings.report(
                        hostContext,
                        "script_loader_missing",
                        "Discord React Native bundle loader was not found",
                    )
                } else {
                    ModuleSettings.report(hostContext, "hooks_ready")
                }
            } catch (error: Throwable) {
                XposedBridge.log("ValancedX: bootstrap error: $error")
                ModuleSettings.report(hostContext, "bootstrap_error", error.message)
            } finally {
                runCatching { DexFinder.release() }.onFailure {
                    XposedBridge.log("ValancedX: DexKit cleanup error: $it")
                }
            }
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
