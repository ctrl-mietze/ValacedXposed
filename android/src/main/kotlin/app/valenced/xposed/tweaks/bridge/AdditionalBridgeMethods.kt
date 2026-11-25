package app.valenced.xposed.tweaks.bridge

import app.valenced.bridge.asDelegate
import app.valenced.reloadApp
import app.valenced.xposed.api.registerNativeMethod
import app.valenced.xposed.openFileGuarded
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.plugins.ManagerPluginWorker
import app.valenced.xposed.tweaks.plugins.ManagerThemeRequests
import app.valenced.xposed.tweaks.plugins.CompilerRuntimeState
import app.valenced.xposed.tweaks.plugins.BitChordPresenceStatus
import java.io.File
import android.net.Uri
import app.valenced.plugins.PluginStatesStore
import app.valenced.xposed.BuildConfig

/**
 * `valenced.fs.*` + `valenced.app.*` bridge methods.
 */
val additionalBridgeMethods by tweak {
    registerNativeMethod("vexcord.bitchord.status") { args ->
        BitChordPresenceStatus.report(args.getOrNull(0) as? String ?: "")
        null
    }
    registerNativeMethod("vexcord.runtime.info") {
        mapOf("name" to "Vexcord","apiVersion" to 1,"version" to app.valenced.xposed.BuildConfig.VERSION_NAME,
            "platform" to "android","compatibility" to "valenced-v1")
    }
    registerNativeMethod("valancedx.compiler.state") { args ->
        CompilerRuntimeState.report(args.getOrNull(0) as? String ?: "",args.getOrNull(1) as? String ?: "")
        null
    }
    registerNativeMethod("valancedx.manager.themeClaim") { ManagerThemeRequests.claim() }
    registerNativeMethod("valancedx.manager.themeFinish") { args ->
        ManagerThemeRequests.finish(args.getOrNull(0) as? String ?: "",args.getOrNull(1) as? String ?: "")
        null
    }
    registerNativeMethod("valancedx.manager.themesReady") {
        ManagerThemeRequests.markReady()
        null
    }
    registerNativeMethod("vexcord.compiler.state") { args ->
        CompilerRuntimeState.report(args.getOrNull(0) as? String ?: "",args.getOrNull(1) as? String ?: "")
        null
    }
    registerNativeMethod("vexcord.manager.themeClaim") { ManagerThemeRequests.claim() }
    registerNativeMethod("vexcord.manager.themeFinish") { args ->
        ManagerThemeRequests.finish(args.getOrNull(0) as? String ?: "",args.getOrNull(1) as? String ?: "")
        null
    }
    registerNativeMethod("vexcord.manager.themesReady") {
        ManagerThemeRequests.markReady()
        null
    }
    registerNativeMethod("valenced.app.reload") {
        reloadApp()
    }

    withAppContext { ctx ->
        val managerUri=Uri.parse("content://"+BuildConfig.APPLICATION_ID+".settings")
        AudioContextGuard.attach(ctx)
        StereoCallController.install(ctx,classLoader)
        registerNativeMethod("vexcord.audio.route") {
            StereoCallStatus.get(ctx) + StereoCallController.status()
        }
        registerNativeMethod("vexcord.audio.configure") { args ->
            val enabled=args.getOrNull(0) as? Boolean ?: false
            val preference=args.getOrNull(1) as? String ?: "auto"
            StereoCallController.configure(enabled,preference,args.getOrNull(2) as? Boolean ?: false)
        }
        registerNativeMethod("vexcord.bitchord.session") {
            if(!PluginStatesStore.active.isPluginEnabled("vexcord.bitchord-presence"))
                "{\"status\":\"disabled\"}"
            else runCatching{
                ctx.contentResolver.call(managerUri,"bitchord_session",null,null)?.getString("session")
            }.getOrNull()?:"{\"status\":\"unavailable\"}"
        }
        registerNativeMethod("vexcord.bitchord.stop") {
            runCatching{ctx.contentResolver.call(managerUri,"bitchord_stop",null,null)}
            null
        }
        registerNativeMethod("valenced.fs.getConstants") {
            mapOf(
                "data" to ctx.dataDir.absolutePath,
                "files" to ctx.filesDir.absolutePath,
                "cache" to ctx.cacheDir.absolutePath,
            )
        }

        registerNativeMethod("valenced.fs.delete") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val f = File(path)
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        }

        registerNativeMethod("valenced.fs.exists") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            File(path).exists()
        }

        registerNativeMethod("valenced.fs.read") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val file = File(path).also { it.openFileGuarded() }
            file.bufferedReader().use { it.readText() }
        }

        registerNativeMethod("valenced.fs.write") { args ->
            val argv = args.asDelegate()
            val path by argv.string()
            val contents by argv.string()
            File(path).apply {
                if (isDirectory) throw Error("Path is a directory: $path")
                parentFile?.mkdirs()
                writeText(contents)
            }

            null
        }
    }
}
