package app.valenced.xposed.tweaks.media

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import app.valenced.xposed.BuildConfig
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Independent app-side hook. Only media AudioTracks in selected LSPosed apps.
 * Does not modify focus, media volume, samples, microphone or system audio policy. */
internal object MusicMediaHooks {
    private val installed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val lock = Any()
    private data class Route(val original:AudioDeviceInfo?, val targetId:Int)
    private val observed = WeakHashMap<AudioTrack, Unit>()
    private val routed = WeakHashMap<AudioTrack, Route>()
    @Volatile private var target:AudioDeviceInfo? = null
    private fun apply(track:AudioTrack) {
        val device = target ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED ||
            track.audioAttributes.usage != AudioAttributes.USAGE_MEDIA) return
        if (routed[track]?.targetId == device.id) return
        val old = if(routed.containsKey(track))routed[track]?.original else track.preferredDevice
        if (track.setPreferredDevice(device)) routed[track] = Route(old, device.id)
    }
    private fun restore() {
        for ((track, route) in routed.entries.toList()) runCatching {
            if (track.preferredDevice?.id == route.targetId) track.setPreferredDevice(route.original)
        }
        routed.clear()
    }
    fun install(pkg:String) {
        if(pkg !in MediaRouteLease.packages || !installed.compareAndSet(false,true)) return
        fun hook(name:String, action:(AudioTrack)->Unit) {
            runCatching {
                XposedBridge.hookMethod(AudioTrack::class.java.getDeclaredMethod(name), object:XC_MethodHook(){
                    override fun beforeHookedMethod(param:MethodHookParam) {
                        val track=param.thisObject as? AudioTrack ?: return
                        runCatching { synchronized(lock) { action(track) } }
                    }
                })
            }.onFailure { Log.w("VexcordMedia", "AudioTrack hook unavailable: "+name) }
        }
        hook("play") { track ->
            if(track.state==AudioTrack.STATE_INITIALIZED && track.audioAttributes.usage==AudioAttributes.USAGE_MEDIA){
                observed[track]=Unit; apply(track)
            }
        }
        hook("release") { track -> observed.remove(track); routed.remove(track) }
        XposedHelpers.findAndHookMethod(Application::class.java,"attach",Context::class.java,object:XC_MethodHook(){
            override fun afterHookedMethod(param:MethodHookParam) {
                val app=param.thisObject as? Application ?: return
                if(app.packageName!=pkg || !started.compareAndSet(false,true))return
                start(app)
            }
        })
    }
    private fun start(ctx:Context) {
        val thread=HandlerThread("VexcordMediaRoute").apply{start()}
        val handler=Handler(thread.looper)
        val manager=ctx.getSystemService(AudioManager::class.java) ?: return
        val uri=Uri.parse("content://"+BuildConfig.APPLICATION_ID+".settings")
        val tick=object:Runnable {
            override fun run() {
                try {
                    val state=runCatching { ctx.contentResolver.call(uri,"media_route_read",null,null) }
                        .getOrNull()?.getBoolean("enabled") == true
                    val ordinary=manager.mode==AudioManager.MODE_NORMAL || manager.mode==AudioManager.MODE_IN_COMMUNICATION
                    val devices=manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val wired=devices.any { it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_DEVICE) }
                    val next=if(state && ordinary && !wired)
                        devices.firstOrNull { it.type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } else null
                    var requests=0;var verified=0
                    synchronized(lock) {
                        if(target?.id!=next?.id)restore()
                        target=next
                        if(next!=null)for(track in observed.keys.toList())runCatching {
                            if(track.playState==AudioTrack.PLAYSTATE_PLAYING)apply(track)
                        }
                        requests=routed.size
                        verified=routed.count{(track,route)->runCatching {
                            track.playState==AudioTrack.PLAYSTATE_PLAYING && track.routedDevice?.id==route.targetId
                        }.getOrDefault(false)}
                    }
                    if(state)runCatching { ctx.contentResolver.call(uri,"media_route_report",null,Bundle().apply {
                        putInt("requested",requests); putInt("verified",verified);putInt("type",next?.type?:0)
                    }) }
                }catch(_:Throwable){ synchronized(lock){target=null;restore()} }
                finally { handler.postDelayed(this,3000L) }
            }
        }
        handler.post(tick)
        Log.i("VexcordMedia","Media routing hook attached: "+ctx.packageName)
    }
}
