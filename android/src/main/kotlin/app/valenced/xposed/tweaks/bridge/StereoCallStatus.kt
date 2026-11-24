package app.valenced.xposed.tweaks.bridge

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import app.valenced.plugins.PluginStatesStore

/** Reports route capabilities; does not change global mode or Bluetooth
 * profile, request audio focus, or touch another app's recording. */
internal object StereoCallStatus {
    fun get(context:Context):Map<String,Any>{
        return runCatching{
            val a=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val types=a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map{it.type}.toSet()
            val a2dp=AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types
            val le=Build.VERSION.SDK_INT>=31&&(AudioDeviceInfo.TYPE_BLE_HEADSET in types||AudioDeviceInfo.TYPE_BLE_SPEAKER in types)
            val wired=types.any{it in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_DEVICE)}
            val ordinary=a.mode==AudioManager.MODE_NORMAL||a.mode==AudioManager.MODE_IN_COMMUNICATION
            val conflict=PluginStatesStore.active.isPluginEnabled("mxtiy.audiofix")
            val active=PluginStatesStore.active.isPluginEnabled("vexcord.stereo-call")
            val route=if(Build.VERSION.SDK_INT>=31)runCatching{a.communicationDevice?.type?:0}.getOrDefault(0)else 0
            mapOf("a2dp" to a2dp,"leAudio" to le,"wired" to wired,"mode" to a.mode,"communicationType" to route,
                "conflict" to conflict,"eligible" to (active&&a2dp&&!wired&&ordinary&&!conflict))
        }.getOrElse{mapOf("eligible" to false,"unavailable" to true)}
    }
}
