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
import app.valenced.xposed.tweaks.base.withAppContext
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

@Volatile
private var modulePath: String? = null

/**
 * ValancedX preserves Valenced's critical early Xposed bootstrap.
 *
 * Native tweaks are installed in handleLoadPackage before Android attaches
 * the Discord Application or React Native begins to load Hermes. The manager
 * and diagnostics observe the lifecycle but never delay hook registration.
 *
 * Legacy Xposed and LSPosed API deliberately retained for upstream ABI
 * compatibility; this is not a second incompatible hook framework.
 */
@Suppress("UNUSED")
class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val processClaimed = AtomicBoolean(false)
    private val tweaksApplied = AtomicBoolean(false)

    private val tweaks: List<TweakSpec> = listOf(
        // Original Valenced ordering is important.
        lifecycleSupport,
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
        // ProcessPhoenix's :phoenix process must never receive the same tweaks.
        if (param.packageName != ValencedConstants.TARGET_PACKAGE) return
        if (param.processName != param.packageName) return
        if (!processClaimed.compareAndSet(false, true)) return

        log("Xposed handleLoadPackage reached Discord; installing native hooks early")

        val zygotePath = modulePath?.takeIf { it.isNotBlank() && File(it).isFile }
        val bootstrapScope = HostScopeImpl(zygotePath.orEmpty(), param.appInfo, param.classLoader)

        // Lifecycle fallback remains observational and is registered before
        // the original tweaks. It does not move the native hook installation.
        registerContextFallback(bootstrapScope)

        if (zygotePath != null) {
            installTweaks(param, zygotePath, lifecycleAlreadyInstalled = false)
        } else {
            // Unusual legacy-Xposed compatibility fallback. A missing module
            // path cannot be used to read embedded bundles, so register only
            // the context tracker and attempt recovery once Android attaches.
            log("initZygote did not supply module APK path; enabling late recovery")
            lifecycleSupport.applyTo(bootstrapScope)
            withAppContext { context ->
                val path = resolveModuleApk(context)
                if (path == null) {
                    log("Cannot resolve module APK for hook recovery")
                    ModuleSettings.report(context, "module_path_missing")
                } else {
                    log("Recovered module path; installing original native tweaks")
                    installTweaks(param, path, lifecycleAlreadyInstalled = true)
                }
            }
        }
    }

    private fun installTweaks(
        param: XC_LoadPackage.LoadPackageParam,
        path: String,
        lifecycleAlreadyInstalled: Boolean,
    ) {
        if (!tweaksApplied.compareAndSet(false, true)) return

        val scope = HostScopeImpl(path, param.appInfo, param.classLoader)
        log("Early native hook bootstrap started")
        try {
            // This is deliberately the original Valenced order:
            // DexFinder -> ALL ordered tweaks -> release.
            DexFinder.init(param.appInfo, param.classLoader, path)
            for (spec in tweaks) {
                if (lifecycleAlreadyInstalled && spec === lifecycleSupport) continue
                spec.applyTo(scope)
            }
            log("Early native hooks registered; scriptLoaderInstalled=" + scriptLoaderInstalled)
            // All native hooks have already been registered at load-package
            // time. Only manager reporting is deferred until Context exists.
            withAppContext { context ->
                ModuleSettings.report(context, "framework_attached")
                val config = ModuleSettings.read(context)
                when (HookPolicy.resolve(config.enabled, config.javascriptEnabled, config.safeMode)) {
                    HookMode.OFF -> ModuleSettings.report(context, "disabled")
                    HookMode.RECOVERY -> ModuleSettings.report(context, "safe_mode")
                    HookMode.MINIMAL -> ModuleSettings.report(context, "native_only")
                    HookMode.FULL -> {
                        if (scriptLoaderInstalled) {
                            ModuleSettings.report(context, "hooks_ready")
                        } else {
                            ModuleSettings.report(
                                context,
                                "script_loader_missing",
                                "React Native script delegate was not hooked",
                            )
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            log("Native tweak bootstrap failed", error)
            withAppContext { context ->
                ModuleSettings.report(context, "bootstrap_error", error.message)
            }
        } finally {
            runCatching { DexFinder.release() }
                .onFailure { log("DexFinder release failed", it) }
        }
    }

    private fun registerContextFallback(scope: HostScope) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                methodHook {
                    after {
                        val app = thisObject as? Application ?: return@after
                        if (app.packageName == ValencedConstants.TARGET_PACKAGE) {
                            notifyContextFallback(app, scope)
                        }
                    }
                }.build(),
            )
        }.onFailure { log("Context fallback Application.attach failed", it) }

        runCatching {
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                Application::class.java,
                methodHook {
                    before {
                        val app = args.firstOrNull() as? Application ?: return@before
                        if (app.packageName == ValencedConstants.TARGET_PACKAGE) {
                            notifyContextFallback(app, scope)
                        }
                    }
                }.build(),
            )
        }.onFailure { log("Context fallback Instrumentation failed", it) }
    }

    private fun resolveModuleApk(context: Context): String? = runCatching {
        context.packageManager.getApplicationInfo(
            BuildConfig.APPLICATION_ID, 0,
        ).sourceDir.takeIf { File(it).isFile }
    }.getOrNull()

    private fun log(message: String, error: Throwable? = null) {
        Log.i("ValancedX", message, error)
        XposedBridge.log(
            "ValancedX: " + message +
                (if (error == null) "" else " (" + error.javaClass.simpleName + ": " + error.message + ")"),
        )
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
