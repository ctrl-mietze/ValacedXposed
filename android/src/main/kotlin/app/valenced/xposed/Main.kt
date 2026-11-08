package app.valenced.xposed

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import app.valenced.bridge.ValencedBridge
import app.valenced.xposed.api.HostScope
import app.valenced.xposed.manager.HookMode
import app.valenced.xposed.manager.HookPolicy
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.tweaks.*
import app.valenced.xposed.tweaks.base.lifecycleSupport
import app.valenced.xposed.tweaks.base.notifyContextFallback
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
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Set by a compatible Xposed / LSPosed runtime. */
@Volatile
private var modulePath: String? = null

/**
 * Discord-only Xposed startup, with two independent Application lifecycle
 * fallbacks. The official Discord APK is never modified by this module.
 */
@Suppress("UNUSED")
class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        private val packageSeen = AtomicBoolean(false)
        private val initialized = AtomicBoolean(false)
        private const val MODULE_PACKAGE = "ctrl.mietze.valacedxposed"
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
        if (!packageSeen.compareAndSet(false, true)) return

        reportToLog("Xposed reached Discord main process; registering lifecycle callbacks")
        val earlyScope = HostScopeImpl(
            modulePath.orEmpty(),
            param.appInfo,
            param.classLoader,
        )

        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                methodHook {
                    after {
                        val app = thisObject as? Application ?: return@after
                        if (app.packageName != ValencedConstants.TARGET_PACKAGE) return@after
                        notifyContextFallback(app, earlyScope)
                    }
                }.build(),
            )
            reportToLog("Application.attach fallback registered")
        }.onFailure { reportToLog("Application.attach fallback failed", it) }

        runCatching {
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                Application::class.java,
                methodHook {
                    before {
                        val app = args.firstOrNull() as? Application ?: return@before
                        if (app.packageName != ValencedConstants.TARGET_PACKAGE) return@before
                        notifyContextFallback(app, earlyScope)
                    }
                }.build(),
            )
            reportToLog("Application.onCreate fallback registered")
        }.onFailure { reportToLog("Application.onCreate fallback failed", it) }

        lifecycleSupport.applyTo(earlyScope)
        earlyScope.withAppContext { hostContext ->
            if (!initialized.compareAndSet(false, true)) return@withAppContext
            bootstrap(hostContext, param)
        }
    }

    private fun bootstrap(
        hostContext: Context,
        param: XC_LoadPackage.LoadPackageParam,
    ) {
        reportToLog("Discord Application Context acquired")
        ModuleSettings.report(hostContext, "framework_attached")

        val config = ModuleSettings.read(hostContext)
        when (HookPolicy.resolve(config.enabled, config.javascriptEnabled, config.safeMode)) {
            HookMode.OFF -> {
                ModuleSettings.report(hostContext, "disabled")
                return
            }
            HookMode.RECOVERY -> {
                ModuleSettings.report(hostContext, "safe_mode")
                return
            }
            HookMode.MINIMAL -> {
                ModuleSettings.report(hostContext, "native_only")
                return
            }
            HookMode.FULL -> Unit
        }

        val path = findModuleApk(hostContext)
        if (path == null) {
            reportToLog("Module APK path unavailable")
            ModuleSettings.report(hostContext, "module_path_missing")
            return
        }

        val scope = HostScopeImpl(path, param.appInfo, param.classLoader)
        ModuleSettings.report(hostContext, "initializing")
        reportToLog("Registering Discord native hooks")
        try {
            DexFinder.init(param.appInfo, param.classLoader, path)
            for (spec in runtime) spec.applyTo(scope)
            if (!scriptLoaderInstalled) {
                ModuleSettings.report(
                    hostContext,
                    "script_loader_missing",
                    "Discord React Native bundle loader not found",
                )
            } else {
                ModuleSettings.report(hostContext, "hooks_ready")
            }
        } catch (error: Throwable) {
            reportToLog("Hook bootstrap failed", error)
            ModuleSettings.report(hostContext, "bootstrap_error", error.message)
        } finally {
            runCatching { DexFinder.release() }
                .onFailure { reportToLog("DexKit cleanup error", it) }
        }
    }

    private fun findModuleApk(context: Context): String? {
        modulePath?.takeIf { it.isNotBlank() && File(it).isFile }?.let { return it }
        return runCatching {
            context.packageManager.getApplicationInfo(MODULE_PACKAGE, 0).sourceDir
                ?.takeIf { File(it).isFile }
        }.getOrNull()
    }

    private fun reportToLog(message: String, error: Throwable? = null) {
        Log.i("ValancedX", message, error)
        XposedBridge.log("ValancedX: " + message +
            (if (error != null) " (" + error.javaClass.simpleName + ": " + error.message + ")" else ""))
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
