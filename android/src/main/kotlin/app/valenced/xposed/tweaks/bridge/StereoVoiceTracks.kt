package app.valenced.xposed.tweaks.bridge

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.WeakHashMap

/**
 * Optional per-stream route for Discord's Java voice backend.
 * Never reads samples, changes volumes, captures other apps, or modifies
 * global Android audio policy. OpenSL ES has no Java AudioTrack handle;
 * absence of observed Java voice tracks is reported, not treated as success.
 */
internal object StereoVoiceTracks {
    private val lock=Any()
    private data class Output(val original:AudioDeviceInfo?,val targetId:Int)
    private data class Input(val original:AudioDeviceInfo?,val targetId:Int)
    private val outputs=WeakHashMap<AudioTrack,Output>()
    private val inputs=WeakHashMap<AudioRecord,Input>()
    private var installed=0
    fun install(target:()->Pair<AudioDeviceInfo?,AudioDeviceInfo?>):Int{
        synchronized(lock){
            if(installed>0)return installed
        }
        fun add(type:Class<*>,name:String,body:(XC_MethodHook.MethodHookParam)->Unit){
            runCatching{
                XposedBridge.hookMethod(type.getDeclaredMethod(name),object:XC_MethodHook(){
                    override fun beforeHookedMethod(param:MethodHookParam){
                        runCatching{body(param)}
                    }
                })
                synchronized(lock){installed++}
            }
        }
        if(Build.VERSION.SDK_INT>=29){
            add(AudioTrack::class.java,"play"){p->
                val track=p.thisObject as? AudioTrack?:return@add
                if(track.state!=AudioTrack.STATE_INITIALIZED||
                    track.audioAttributes.usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION)return@add
                val device=target().first?:return@add
                synchronized(lock){
                    if(outputs.containsKey(track))return@synchronized
                    val original=track.preferredDevice
                    if(track.setPreferredDevice(device))outputs[track]=Output(original,device.id)
                }
            }
        }
        add(AudioRecord::class.java,"startRecording"){p->
            val record=p.thisObject as? AudioRecord?:return@add
            if(record.state!=AudioRecord.STATE_INITIALIZED||record.audioSource!=MediaRecorder.AudioSource.VOICE_COMMUNICATION)return@add
            val device=target().second?:return@add
            synchronized(lock){
                if(inputs.containsKey(record))return@synchronized
                val original=record.preferredDevice
                if(record.setPreferredDevice(device))inputs[record]=Input(original,device.id)
            }
        }
        add(AudioTrack::class.java,"release"){p->synchronized(lock){outputs.remove(p.thisObject as? AudioTrack)}}
        add(AudioRecord::class.java,"release"){p->synchronized(lock){inputs.remove(p.thisObject as? AudioRecord)}}
        return synchronized(lock){installed}
    }
    fun restore(){
        synchronized(lock){
            for((track,state) in outputs.entries.toList())runCatching{
                if(track.preferredDevice?.id==state.targetId)track.setPreferredDevice(state.original)
            }
            for((record,state) in inputs.entries.toList())runCatching{
                if(record.preferredDevice?.id==state.targetId)record.setPreferredDevice(state.original)
            }
            outputs.clear();inputs.clear()
        }
    }
    fun status():Map<String,Any> = synchronized(lock){
        val outputMatches=outputs.count{(track,state)->runCatching{track.playState==AudioTrack.PLAYSTATE_PLAYING&&track.routedDevice?.id==state.targetId}.getOrDefault(false)}
        val inputMatches=inputs.count{(record,state)->runCatching{record.recordingState==AudioRecord.RECORDSTATE_RECORDING&&record.routedDevice?.id==state.targetId}.getOrDefault(false)}
        mapOf("trackHooks" to installed,"javaOutputRequests" to outputs.size,"javaInputRequests" to inputs.size,
            "javaOutputVerified" to outputMatches,"javaInputVerified" to inputMatches)
    }
}
