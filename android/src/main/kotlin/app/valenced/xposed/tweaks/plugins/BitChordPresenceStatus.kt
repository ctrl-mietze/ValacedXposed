package app.valenced.xposed.tweaks.plugins

import android.os.SystemClock
import org.json.JSONObject

/** Fixed diagnostics only. No song metadata, identifiers, or credentials. */
internal object BitChordPresenceStatus {
    private val allowed=mapOf(
        "discord" to setOf("waiting","ready"),
        "media" to setOf("idle","playing","paused","connecting","connection-denied","connection-timeout","suspended","session-ended","disabled","unavailable","bridge-error"),
        "publication" to setOf("idle","waiting","submitted","observed","error","disabled","stopped"),
        "cover" to setOf("idle","pending","resolved","unavailable")
    )
    @Volatile private var value:String?=null
    @Volatile private var updated:Long=0L
    @Synchronized fun report(payload:String){
        if(payload.length>512)return
        runCatching{
            val input=JSONObject(payload)
            val result=JSONObject()
            for((key,values) in allowed){
                val entry=input.optString(key)
                require(entry in values)
                result.put(key,entry)
            }
            value=result.toString()
            updated=SystemClock.elapsedRealtime()
        }
    }
    @Synchronized fun snapshot():JSONObject? {
        val text=value?:return null
        return JSONObject(text).put("ageSeconds",((SystemClock.elapsedRealtime()-updated).coerceAtLeast(0L))/1000L)
    }
}
