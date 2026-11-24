package app.valenced.xposed.tweaks.bridge

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.valenced.plugins.PluginStatesStore
import app.valenced.xposed.manager.ModuleSettings
import app.valenced.xposed.manager.HookPolicy
import app.valenced.xposed.manager.HookMode
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.atomic.AtomicInteger
import java.lang.reflect.Method

/**
 * Process-local hooks on Android's public AudioManager entry points.
 * Only loaded in Discord's main process. Never hooks system_server,
 * AudioFlinger, telephony, other applications or Bluetooth services.
 *
 * LE mode uses a real communication-device selection. A2DP mode avoids
 * entering the SCO/HFP route and allows Android's media policy to choose
 * the output. OpenSL voice routing remains dependent on the audio policy.
 */
internal object StereoCallController {
    private const val ID="vexcord.stereo-call"
    private const val TAG="VexcordStereo"
    private val lock=Any()
    private val reentry=ThreadLocal<Boolean>()
    private val hits=AtomicInteger(0)
    private var app:Context?=null
    private var installed=0
    private var modeHook=false
    private var configured=false
    private var preference=StereoRoutePolicy.Preference.AUTO
    private var requestedCall=false
    private var suppressedMode=false
    private var selectedLe=false
    private var leRequestedAt=0L
    private var fallbackForCall=false
    private var manager:AudioManager?=null
    private var leDeviceId=0
    private var routeNote="idle"
    private var discordCallHook=false
    private var callGeneration=0
    private var splitStartedAt=0L
    private val main=Handler(Looper.getMainLooper())
    private var deferredSco: (() -> Unit)?=null

    private fun active():Boolean = runCatching {
        val cfg=ModuleSettings.current()
        configured&&modeHook&&HookPolicy.resolve(cfg.enabled,cfg.javascriptEnabled,cfg.safeMode)==HookMode.FULL&&
            PluginStatesStore.active.isPluginEnabled(ID)&&!PluginStatesStore.active.isPluginEnabled("mxtiy.audiofix")
    }.getOrDefault(false)

    private fun <T> bypass(block:()->T):T {
        val old=reentry.get()
        reentry.set(true)
        return try{block()}finally{reentry.set(old)}
    }

    private fun audio():AudioManager?=manager?:app?.getSystemService(AudioManager::class.java)
    private fun ordinary(a:AudioManager)=a.mode==AudioManager.MODE_NORMAL||a.mode==AudioManager.MODE_IN_COMMUNICATION
    private fun leDevice(a:AudioManager):AudioDeviceInfo? =
        if(Build.VERSION.SDK_INT>=31)runCatching{a.availableCommunicationDevices.firstOrNull{it.type==AudioDeviceInfo.TYPE_BLE_HEADSET}}.getOrNull()
        else null
    private fun path(a:AudioManager):StereoRoutePolicy.Path {
        val devices=a.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val types=devices.map{it.type}.toSet()
        val wired=types.any{it in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_DEVICE)}
        return StereoRoutePolicy.choose(preference,StereoRoutePolicy.Inputs(
            active()&&!fallbackForCall,runCatching{PluginStatesStore.active.isPluginEnabled("mxtiy.audiofix")}.getOrDefault(true),
            ordinary(a),AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types,leDevice(a)!=null,wired))
    }
    private fun currentType(a:AudioManager):Int=if(Build.VERSION.SDK_INT>=31)
        runCatching{a.communicationDevice?.type?:0}.getOrDefault(0)else 0
    private fun requestLe(a:AudioManager):Boolean{
        if(Build.VERSION.SDK_INT<31)return false
        val target=leDevice(a)?:return false
        if(a.communicationDevice?.id==target.id){
            selectedLe=true;leDeviceId=target.id
            leRequestedAt=SystemClock.elapsedRealtime()
            routeNote="le-selected";return true
        }
        if(selectedLe&&leDeviceId==target.id&&SystemClock.elapsedRealtime()-leRequestedAt<8000L)return true
        val accepted=runCatching{bypass{a.setCommunicationDevice(target)}}.getOrDefault(false)
        if(accepted){
            selectedLe=true;leDeviceId=target.id;leRequestedAt=SystemClock.elapsedRealtime()
            routeNote="le-requested"
        }
        return accepted
    }
    private fun restore(reason:String){
        StereoVoiceTracks.restore()
        splitStartedAt=0L
        val a=manager
        val mode=suppressedMode
        val le=selectedLe
        val sco=deferredSco
        suppressedMode=false;selectedLe=false;leDeviceId=0;deferredSco=null
        fallbackForCall=requestedCall
        routeNote=reason
        if(a==null||!requestedCall||!ordinary(a))return
        runCatching{bypass{
            if(le&&Build.VERSION.SDK_INT>=31&&a.communicationDevice?.type==AudioDeviceInfo.TYPE_BLE_HEADSET)a.clearCommunicationDevice()
            if(mode)a.mode=AudioManager.MODE_IN_COMMUNICATION
            sco?.invoke()
        }}.onFailure{routeNote="restore-failed"}
    }
    private fun endCall(a:AudioManager){
        callGeneration++
        StereoVoiceTracks.restore()
        splitStartedAt=0L
        if(selectedLe&&Build.VERSION.SDK_INT>=31)runCatching{bypass{
            if(a.communicationDevice?.id==leDeviceId)a.clearCommunicationDevice()
        }}
        requestedCall=false;suppressedMode=false;selectedLe=false;leDeviceId=0;deferredSco=null
        fallbackForCall=false;manager=null;routeNote="idle"
    }
    private fun reconcile(a:AudioManager){
        if(!requestedCall)return
        if(!ordinary(a)){
            StereoVoiceTracks.restore()
            splitStartedAt=0L
            suppressedMode=false;selectedLe=false;deferredSco=null;fallbackForCall=true;routeNote="system-call-priority";return
        }
        if((suppressedMode||selectedLe)&&(!active()||(!fallbackForCall&&path(a)==StereoRoutePolicy.Path.SYSTEM)||
            (selectedLe&&leDevice(a)==null)))restore("system-fallback")
        if(suppressedMode&&splitStartedAt>0L&&SystemClock.elapsedRealtime()-splitStartedAt>=6000L){
            val track=StereoVoiceTracks.status()
            val output=(track["javaOutputVerified"] as? Int)?:0
            val input=(track["javaInputVerified"] as? Int)?:0
            if(output>0&&input>0)routeNote="a2dp-track-verified"
            else{
                restore("split-not-verified")
                // Prefer a real LE communication device as the fallback,
                // rather than asserting that the split mode is working.
                if(active()&&preference==StereoRoutePolicy.Preference.A2DP&&leDevice(a)!=null){
                    if(requestLe(a))routeNote="split-le-fallback"
                }
            }
        }
        if(selectedLe&&currentType(a)==AudioDeviceInfo.TYPE_BLE_HEADSET){
            routeNote="le-selected"
        }else if(selectedLe&&SystemClock.elapsedRealtime()-leRequestedAt>=8000L){
            restore("le-not-selected")
        }
    }
    fun configure(enabled:Boolean,mode:String):Map<String,Any> = synchronized(lock){
        val p=when(mode){"a2dp"->StereoRoutePolicy.Preference.A2DP;"le"->StereoRoutePolicy.Preference.LE;else->StereoRoutePolicy.Preference.AUTO}
        if(!enabled||p!=preference)restore("standard-routing")
        configured=enabled;preference=p
        app?.getSharedPreferences("vexcord_stereo_native",Context.MODE_PRIVATE)?.edit()
            ?.putBoolean("enabled",enabled)?.putString("mode",p.name)?.apply()
        status()
    }
    fun status():Map<String,Any> = synchronized(lock){
        val a=audio()?:return@synchronized mapOf("backend" to installed,"note" to "unavailable")
        return@synchronized runCatching{
            reconcile(a)
            val types=a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map{it.type}.toSet()
            mapOf("backend" to installed,"modeHook" to modeHook,"enabled" to active(),"callRequested" to requestedCall,
                "a2dp" to (AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types),"leAudio" to (leDevice(a)!=null),
                "communicationType" to currentType(a),"mode" to a.mode,"preference" to preference.name,
                "policy" to path(a).name,"suppressedMode" to suppressedMode,"hookHits" to hits.get(),
                "note" to routeNote,"discordCallHook" to discordCallHook)+StereoVoiceTracks.status()
        }.getOrElse{mapOf("backend" to installed,"note" to "unavailable")}
    }
    fun install(context:Context,classLoader:ClassLoader){
        synchronized(lock){
            if(app!=null)return
            app=context.applicationContext?:context
            val prefs=app!!.getSharedPreferences("vexcord_stereo_native",Context.MODE_PRIVATE)
            configured=prefs.getBoolean("enabled",false)
            preference=runCatching{StereoRoutePolicy.Preference.valueOf(prefs.getString("mode","AUTO")!!)}.getOrDefault(StereoRoutePolicy.Preference.AUTO)
            fun hook(name:String,vararg params:Class<*>,body:(XC_MethodHook.MethodHookParam,AudioManager)->Unit):Boolean{
                return runCatching{
                    val method=AudioManager::class.java.getDeclaredMethod(name,*params)
                    XposedBridge.hookMethod(method,object:XC_MethodHook(){
                        override fun beforeHookedMethod(param:MethodHookParam){
                            if(reentry.get()==true)return
                            val a=param.thisObject as? AudioManager?:return
                            synchronized(lock){
                                runCatching{body(param,a)}.onFailure{routeNote="hook-error"}
                            }
                        }
                    })
                    installed++
                    true
                }.getOrElse{false}
            }
            modeHook=hook("setMode",Int::class.javaPrimitiveType!!){param,a->
                val mode=param.args.firstOrNull() as? Int?:return@hook
                if(mode==AudioManager.MODE_NORMAL){endCall(a);return@hook}
                if(mode!=AudioManager.MODE_IN_COMMUNICATION||!active()||!ordinary(a))return@hook
                if(!requestedCall)fallbackForCall=false
                requestedCall=true;manager=a
                if(path(a)==StereoRoutePolicy.Path.A2DP_PHONE_MIC){
                    // Replace Discord's request, not global audio service state.
                    param.args[0]=AudioManager.MODE_NORMAL
                    suppressedMode=true
                    if(splitStartedAt==0L)splitStartedAt=SystemClock.elapsedRealtime()
                    hits.incrementAndGet();routeNote="a2dp-mode-request"
                }
            }
            fun bluetoothRequest(param:XC_MethodHook.MethodHookParam,a:AudioManager,booleanResult:Boolean){
                if(!active()||!ordinary(a))return
                if(!requestedCall){
                    // A late/standalone request is not a confirmed Discord call.
                    return
                }
                val target=path(a)
                if(target==StereoRoutePolicy.Path.SYSTEM)return
                val method=param.method as? Method?:return
                val args=param.args.copyOf()
                val original={XposedBridge.invokeOriginalMethod(method,a,args);Unit}
                if(target==StereoRoutePolicy.Path.LE_COMMUNICATION){
                    if(!requestLe(a)){
                        restore("le-request-rejected")
                        fallbackForCall=true;return
                    }
                    deferredSco=original
                    param.result=if(booleanResult)true else null
                    hits.incrementAndGet()
                }else if(suppressedMode){
                    // No fake SCO-connected events and no false successful
                    // Bluetooth route claims. Leave media routing to Android.
                    deferredSco=original
                    param.result=if(booleanResult)false else null
                    hits.incrementAndGet();routeNote="a2dp-sco-suppressed"
                }
            }
            hook("startBluetoothSco"){param,a->bluetoothRequest(param,a,false)}
            hook("stopBluetoothSco"){_,_->
                deferredSco=null
            }
            hook("setBluetoothScoOn",Boolean::class.javaPrimitiveType!!){param,a->
                if(param.args.firstOrNull()==true)bluetoothRequest(param,a,false)
                else deferredSco=null
            }
            if(Build.VERSION.SDK_INT>=31){
                hook("clearCommunicationDevice"){_,_->
                    selectedLe=false;leDeviceId=0;deferredSco=null
                }
                hook("setCommunicationDevice",AudioDeviceInfo::class.java){param,a->
                    val device=param.args.firstOrNull() as? AudioDeviceInfo?:return@hook
                    if(device.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||device.type==AudioDeviceInfo.TYPE_BLE_HEADSET)
                        bluetoothRequest(param,a,true)
                    else if(selectedLe){
                        // The app selected another communication device. Do not
                        // later replay an old Bluetooth request over this choice.
                        selectedLe=false;leDeviceId=0;deferredSco=null
                        fallbackForCall=true;routeNote="system-route-selected"
                    }
                }
            }
            // Verified in the current Discord APK: setCommunicationModeOn(boolean).
            // Observe call lifecycle at the actual host entrypoint; let the
            // original method run, and keep route selection in AudioManager.
            discordCallHook=runCatching{
                val cls=classLoader.loadClass("com.discord.audio.AudioManagerModule")
                val method=cls.getDeclaredMethod("setCommunicationModeOn",Boolean::class.javaPrimitiveType!!)
                XposedBridge.hookMethod(method,object:XC_MethodHook(){
                    override fun beforeHookedMethod(param:MethodHookParam){
                        synchronized(lock){
                            val a=audio()?:return
                            val on=param.args.firstOrNull() as? Boolean?:return
                            if(on&&active()&&ordinary(a)){
                                if(!requestedCall){fallbackForCall=false;callGeneration++}
                                requestedCall=true
                                manager=a
                            }
                        }
                    }
                    override fun afterHookedMethod(param:MethodHookParam){
                        if(param.hasThrowable())return
                        synchronized(lock){
                            val a=audio()?:return
                            val on=param.args.firstOrNull() as? Boolean?:return
                            if(!on){endCall(a);return}
                            if(!requestedCall||!active())return
                            val generation=callGeneration
                            // Audio mode and device changes can be asynchronous.
                            // Only retry in the same call, and only if Bluetooth
                            // is already selected or the LE request was pending.
                            for(delay in listOf(350L,1500L)){
                                main.postDelayed({
                                    synchronized(lock){
                                        runCatching{
                                            if(generation!=callGeneration||!requestedCall||!active()||!ordinary(a))return@runCatching
                                            val type=currentType(a)
                                            if(path(a)==StereoRoutePolicy.Path.LE_COMMUNICATION&&
                                                (selectedLe||type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||type==AudioDeviceInfo.TYPE_BLE_HEADSET))
                                                requestLe(a)
                                        }
                                    }
                                },delay)
                            }
                        }
                    }
                })
                installed++
                true
            }.getOrDefault(false)
            // Java voice streams have explicit preferred devices. This is an
            // additional backend, not a claim to patch OpenSL ES in C++.
            installed+=StereoVoiceTracks.install{
                synchronized(lock){
                    val a=audio()
                    if(a==null||!requestedCall||!suppressedMode||path(a)!=StereoRoutePolicy.Path.A2DP_PHONE_MIC)
                        Pair(null,null)
                    else{
                        val out=a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull{it.type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}
                        val input=a.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull{it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC}
                        Pair(out,input)
                    }
                }
            }
            runCatching{
                audio()?.registerAudioDeviceCallback(object:AudioDeviceCallback(){
                    override fun onAudioDevicesRemoved(removedDevices:Array<out AudioDeviceInfo>){
                        synchronized(lock){runCatching{audio()?.let{reconcile(it)}}}
                    }
                },Handler(Looper.getMainLooper()))
            }
            Log.i(TAG,"Native audio hooks installed: "+installed+"; mode="+modeHook+"; discord="+discordCallHook)
        }
    }
}
