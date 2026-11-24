package app.valenced.xposed.manager.analysis

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Runs as a distinct isolated UID. Does not have app-private storage or app permissions. */
class PluginSandboxService:Service(){
    private val used=AtomicBoolean(false)
    private val incoming=Messenger(Handler(Looper.getMainLooper()){msg->
        if(msg.what!=1||!used.compareAndSet(false,true))return@Handler true
        val reply=msg.replyTo
        @Suppress("DEPRECATION")
        val fd=msg.data.getParcelable<ParcelFileDescriptor>("input")
        val meta=msg.data.getString("meta").orEmpty()
        Thread {
            val result=try{
                require(fd!=null){"Eingabe fehlt."}
                val bytes=ParcelFileDescriptor.AutoCloseInputStream(fd).use{AnalysisEngine.readLimited(it,AnalysisEngine.MAX_INPUT)}
                AnalysisEngine.analyze(bytes,JSONObject(meta))
            }catch(e:Throwable){JSONObject().put("error",(e.message?:e.javaClass.simpleName).take(400)).put("isolated",true)}
            runCatching{reply.send(Message.obtain(null,2).apply{data=Bundle().apply{putString("result",result.toString().take(60000))}})}
        }.start()
        true
    })
    override fun onBind(intent:Intent?):IBinder=incoming.binder
}
