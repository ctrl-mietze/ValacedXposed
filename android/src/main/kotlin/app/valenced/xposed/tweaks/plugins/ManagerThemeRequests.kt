package app.valenced.xposed.tweaks.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import app.valenced.xposed.ETagFetchResult
import app.valenced.xposed.PluginHttps
import app.valenced.plugins.repos.sha256Hex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import app.valenced.xposed.tweaks.bridge.ValencedBridgeRegistry
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

/** Independent, correlated theme requests, not the shared RN callable-return FIFO. */
internal object ManagerThemeRequests {
    private data class Request(val id:String,val operation:String,val data:String,
        val response:CompletableDeferred<String>,var claimed:Boolean=false)
    private var pending:Request?=null
    @Volatile private var ready=false
    fun markReady() {
        ready=true
        Log.i("ValancedXTheme","JS dispatcher ready")
    }
    @Synchronized fun claim():String? {
        val r=pending ?: return null
        if(r.claimed)return null
        r.claimed=true
        Log.d("ValancedXTheme","Claim "+r.operation)
        return JSONObject().put("id",r.id).put("operation",r.operation).put("data",r.data).toString()
    }
    @Synchronized fun finish(id:String,result:String) {
        val r=pending ?: return
        if(r.id==id && r.claimed) {
            val safe=if(result.length<=220_000)result else JSONObject().put("error","Theme-Katalog zu groß.").toString()
            Log.i("ValancedXTheme","Finish "+r.operation+" ("+safe.length+" chars)")
            r.response.complete(safe)
        }
    }
    suspend fun request(operation:String,data:JSONObject):JSONObject {
        val request=Request(UUID.randomUUID().toString(),operation,data.toString(),CompletableDeferred())
        synchronized(this) { check(pending==null){"Theme-Aktion läuft bereits."};pending=request }
        try {
            val available=withTimeoutOrNull(2500L) {
                while(!ready||!ValencedBridgeRegistry.isReady)delay(100L)
                true
            }==true
            check(available) {"Discord-Theme-Bridge nicht gestartet. Discord öffnen und synchronisieren."}
            Log.d("ValancedXTheme","Dispatch "+operation)
            ValencedBridgeRegistry.wakeManagerThemes()
            val raw=withTimeoutOrNull(if(operation=="theme_snapshot")6000L else 15000L){request.response.await()}
                ?: throw IllegalStateException(
                    if(request.claimed) "Discord hat die Theme-Anfrage angenommen, aber nicht abgeschlossen."
                    else "Discords JS-Runtime antwortet nicht. Discord öffnen und erneut synchronisieren.")
            return JSONObject(raw)
        } finally { synchronized(this){if(pending===request)pending=null} }

    }
}

internal object ManagerThemeAssets {
    private fun validUrl(value:String):String {
        val u=URI(value)
        require(u.scheme=="https"&&!u.host.isNullOrBlank()&&u.userInfo==null&&u.fragment==null&&value.length<=1400){"HTTPS-URL erforderlich."}
        return u.toString()
    }
    private suspend fun download(url:String,max:Int):ByteArray {
        val data=PluginHttps.fetch(validUrl(url),null,15_000L,max.toLong()) as? ETagFetchResult.Fetched
            ?:error("Datei nicht verfügbar.")
        require(data.bytes.size in 1..max){"Dateigröße nicht unterstützt."}
        return data.bytes
    }
    private fun directory(context:Context):File =
        File(context.filesDir,"valenced/manager-theme-assets").apply{mkdirs()}

    suspend fun importFonts(context:Context,url:String):JSONObject {
        val doc=JSONObject(download(url,96_000).decodeToString())
        require(doc.optInt("spec")==1&&doc.has("main")){"Nur Bunny-Font-Packs (Spec 1)."}
        val main=doc.getJSONObject("main")
        require(main.length() in 1..16){"1–16 Schriftdateien je Pack."}
        val target=File(directory(context),sha256Hex(url).take(24)).apply{mkdirs()}
        val families=JSONObject()
        var total=0
        for(name in main.keys()) {
            require(name.matches(Regex("[A-Za-z0-9_-]{1,100}"))){"Ungültiger Schriftname."}
            val bytes=download(main.getString(name),4_000_000)
            total+=bytes.size
            require(total<=20_000_000){"Font-Pack zu groß."}
            require(bytes.size>=12){"Ungültige Schriftdatei."}
            val magic=bytes.copyOfRange(0,4)
            val otf=magic.contentEquals("OTTO".encodeToByteArray())
            require(otf||magic.contentEquals(byteArrayOf(0,1,0,0))||magic.contentEquals("true".encodeToByteArray())){"Nur TTF/OTF-Dateien."}
            val file=File(target,sha256Hex(bytes).take(24)+if(otf)".otf" else ".ttf")
            file.writeBytes(bytes)
            // Android's font parser must recognize it before registration.
            android.graphics.Typeface.createFromFile(file)
            families.put(name,file.absolutePath)
        }
        return JSONObject().put("name",doc.optString("name","Importierte Fonts").take(100))
            .put("description",doc.optString("description").take(200)).put("files",families)
    }

    /** A locally generated backing gradient. JS animates it only in the
     * Discord background layer, not via a video overlay over chat controls. */
    fun motionPath(context:Context,style:String):String {
        val colors=when(style){
            "aurora"->intArrayOf(0xff080b17.toInt(),0xff412b70.toInt(),0xff17605b.toInt())
            "bloom"->intArrayOf(0xff07060c.toInt(),0xff602b65.toInt(),0xff251c46.toInt())
            "eclipse"->intArrayOf(0xff000000.toInt(),0xff213b63.toInt(),0xff382753.toInt())
            else->error("Unbekannter Motion-Hintergrund.")
        }
        val file=File(directory(context),"veyra-motion-"+style+".png")
        if(!file.isFile){
            val bitmap=Bitmap.createBitmap(144,240,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            val paint=android.graphics.Paint().apply{
                shader=android.graphics.LinearGradient(0f,0f,144f,240f,colors,null,android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f,0f,144f,240f,paint)
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        return file.absolutePath
    }
}
