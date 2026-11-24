package app.valenced.xposed.manager.analysis

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object SandboxClient{
    suspend fun run(context:Context,bytes:ByteArray,meta:JSONObject):JSONObject=withContext(Dispatchers.IO){
        require(Build.VERSION.SDK_INT>=29){"Getrennte Sandbox-Instanzen benötigen Android 10 oder neuer."}
        require(bytes.size<=AnalysisEngine.MAX_INPUT){"Plugin zu groß für die lokale Sandbox."}
        val file=File.createTempFile("vx-analysis-", ".bin",context.cacheDir)
        val answer=CompletableDeferred<JSONObject>()
        val receiver=Messenger(Handler(Looper.getMainLooper()){msg->
            if(msg.what==2){
                val result=runCatching{JSONObject(msg.data.getString("result").orEmpty())}.getOrElse{JSONObject().put("error","Ungültige Sandbox-Antwort.")}
                answer.complete(result)
            }
            true
        })
        val conn=object:ServiceConnection{
            override fun onServiceConnected(name:ComponentName,binder:IBinder){
                runCatching{
                    ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                        Messenger(binder).send(Message.obtain(null,1).apply{
                            replyTo=receiver
                            data=Bundle().apply{putParcelable("input",fd);putString("meta",meta.toString())}
                        })
                    }
                }.onFailure{answer.completeExceptionally(it)}
            }
            override fun onServiceDisconnected(name:ComponentName){answer.completeExceptionally(IllegalStateException("Sandbox beendet."))}
            override fun onBindingDied(name:ComponentName){answer.completeExceptionally(IllegalStateException("Sandbox-Prozess beendet."))}
            override fun onNullBinding(name:ComponentName){answer.completeExceptionally(IllegalStateException("Sandbox nicht verfügbar."))}
        }
        var bound=false
        try{
            file.writeBytes(bytes)
            val intent=Intent(context,PluginSandboxService::class.java)
            bound=context.bindIsolatedService(intent,Context.BIND_AUTO_CREATE,"scan_"+UUID.randomUUID().toString().replace("-",""),context.mainExecutor,conn)
            check(bound){"Sandbox konnte nicht gestartet werden."}
            withTimeout(9000L){answer.await()}
        }finally{
            if(bound)runCatching{context.unbindService(conn)}
            file.delete()
        }
    }
}
