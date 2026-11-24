package app.valenced.xposed.manager

import android.content.Context
import android.content.ContentProviderClient
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import app.valenced.xposed.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** One exact, deliberately unmapped URI, not a real Discord file. The host
 * grants it itself. We only acquire a provider process reference; never call
 * query/openFile/getType or request a directory/prefix/write permission. */
internal object DiscordConnectionEndpoint {
    const val AUTHORITY = "com.discord.file-provider"
    const val HOST = "com.discord"
    val uri: Uri get() = Uri.parse("content://"+AUTHORITY+"/__valancedx_connection_no_file_v1__/lease")
    val managerUri: Uri get() = Uri.parse("content://"+BuildConfig.APPLICATION_ID+".settings/connection")
}

/** State lives only in the manager process; timestamps use elapsed time. */
internal object ManagerConnection {
    data class Peer(val pid:Int=0,val version:Int=0,val seen:Long=0L,
        val themeReady:Boolean=false,val enabled:Boolean=false,val offered:Boolean=false) {
        fun fresh(now:Long=SystemClock.elapsedRealtime())=pid>0&&seen>0&&now-seen in 0..3500L
        fun online(now:Long=SystemClock.elapsedRealtime())=fresh(now)&&version==BuildConfig.VERSION_CODE&&enabled
    }
    private var until=0L
    private var peer=Peer()
    @Volatile var retained=false
        private set
    @Synchronized fun renew() {until=SystemClock.elapsedRealtime()+5000L}
    @Synchronized fun end() {until=0L}
    @Synchronized fun visible():Boolean=until>SystemClock.elapsedRealtime()
    @Synchronized fun status()=peer
    @Synchronized fun heartbeat(pid:Int,extras:Bundle?):Bundle {
        if(pid>0)peer=Peer(pid,extras?.getInt("manager_version",0)?:0,SystemClock.elapsedRealtime(),
            extras?.getBoolean("theme_ready",false)==true,extras?.getBoolean("enabled",false)==true,
            extras?.getBoolean("provider_offered",false)==true)
        return Bundle().apply{putBoolean("visible",visible())}
    }
    internal fun setRetained(value:Boolean){retained=value}
}

/** Holds an UNSTABLE provider reference only while the Activity is visible.
 * Unlike a stable client, a Discord crash doesn't kill its dependent manager.
 * The platform can still reclaim processes under exceptional conditions. */
internal class DiscordConnectionController(private val context:Context) {
    private val active=AtomicBoolean(false)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var task:Job?=null
    fun start(){
        if(!active.compareAndSet(false,true))return
        ManagerConnection.renew()
        signal()
        val previous=task
        task=scope.launch{
            previous?.join()
            if(!active.get())return@launch
            var client:ContentProviderClient?=null
            var acquiredAt=0L
            var peerPid=0
            var nextAttempt=0L
            try{
                while(isActive&&active.get()){
                    val now=SystemClock.elapsedRealtime()
                    val peer=ManagerConnection.status()
                    // A dead/unresponsive worker invalidates the retained reference.
                    if(client!=null&&((peerPid>0&&peer.pid!=peerPid)||(!peer.fresh(now)&&now-acquiredAt>6000L)||(peer.fresh(now)&&!peer.enabled))){
                        runCatching{client?.close()};client=null;ManagerConnection.setRetained(false)
                        nextAttempt=now+500L
                    }
                    if(client==null&&now>=nextAttempt&&(!peer.fresh(now)||peer.enabled)){
                        nextAttempt=now+1800L
                        client=runCatching{
                            val info=context.packageManager.resolveContentProvider(DiscordConnectionEndpoint.AUTHORITY,0)
                            if(info?.packageName!=DiscordConnectionEndpoint.HOST ||
                                info.processName!=DiscordConnectionEndpoint.HOST ||
                                info.name!="androidx.core.content.FileProvider" || !info.grantUriPermissions) null
                            else if(context.checkUriPermission(DiscordConnectionEndpoint.uri,Process.myPid(),Process.myUid(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED) null
                            else context.contentResolver.acquireUnstableContentProviderClient(DiscordConnectionEndpoint.uri)
                        }.getOrNull()
                        if(client!=null){
                            acquiredAt=now;peerPid=peer.pid
                            ManagerConnection.setRetained(true)
                            android.util.Log.i("ValancedXLink","Discord provider reference acquired")
                            signal()
                        }
                    }
                    if(client!=null&&peer.fresh(now))peerPid=peer.pid
                    delay(700L)
                }
            }finally{
                runCatching{client?.close()}
                ManagerConnection.setRetained(false)
                android.util.Log.i("ValancedXLink","Discord provider reference released")
            }
        }
    }
    fun signal(){
        runCatching{context.contentResolver.notifyChange(DiscordConnectionEndpoint.managerUri,null,0)}
    }
    fun stop(){
        if(!active.getAndSet(false))return
        ManagerConnection.end()
        signal()
        task?.cancel()
    }
    fun destroy(){stop();scope.cancel()}
}
