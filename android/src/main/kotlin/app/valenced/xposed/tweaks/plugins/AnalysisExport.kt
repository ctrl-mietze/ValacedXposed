package app.valenced.xposed.tweaks.plugins

import app.valenced.plugins.external.requireValidPluginId
import app.valenced.plugins.repos.sha256Hex
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Read-only export of declared, installed plugin artifacts. Never reads plugin
 * storage, Discord credentials, chat data, arbitrary paths or account state. */
internal object AnalysisExport{
    private data class Export(val token:String,val bytes:ByteArray,val at:Long)
    private var pending:Export?=null
    fun prepare(dataDir:String,id:String):JSONObject{
        requireValidPluginId(id)
        val manifest=pluginRegistry.knownManifests()[id]?:error("Unbekanntes Plugin.")
        val root=externalPluginsRoot(dataDir).canonicalFile
        val directory=File(root,id).canonicalFile
        require(directory.parentFile==root){"Ungültiger Plugin-Pfad."}
        val file=File(directory,"manifest.json").canonicalFile
        if(!file.isFile){
            return JSONObject().put("kind","internal").put("id",id)
                .put("description",manifest.description.take(2000))
        }
        require(file.parentFile==directory && file.length()<=128_000L){"Manifest außerhalb des Plugin-Verzeichnisses oder zu groß."}
        val json=JSONObject(file.readText())
        require(json.optString("id")==id){"Plugin-ID stimmt nicht überein."}
        val dist=json.optJSONObject("dist")?:JSONObject()
        val path=dist.optString("script")
        val script=if(path.isBlank())"" else {
            val f=File(directory,path).canonicalFile
            require(f.path.startsWith(directory.path+File.separator)&&f.isFile&&f.length()<=2_000_000L){"Quelldatei nicht verfügbar oder zu groß."}
            f.readText()
        }
        val bytes=JSONObject().put("manifest",json).put("source",script)
            .put("native",dist.has("android")).put("executable",path.endsWith(".js"))
            .toString().encodeToByteArray()
        require(bytes.size<=4_000_000){"Export zu groß."}
        val export=Export(UUID.randomUUID().toString(),bytes,SystemClock.elapsedRealtime())
        pending=export
        return JSONObject().put("kind","installed").put("token",export.token).put("size",bytes.size).put("sha256",sha256Hex(bytes))
    }
    fun chunk(token:String,offset:Int):JSONObject{
        val e=pending?:error("Analyse-Export abgelaufen.")
        require(e.token==token&&SystemClock.elapsedRealtime()-e.at<120_000L)
        require(offset in 0 until e.bytes.size)
        val end=minOf(offset+96_000,e.bytes.size)
        val result=JSONObject().put("offset",offset).put("next",end)
            .put("base64",Base64.encodeToString(e.bytes.copyOfRange(offset,end),Base64.NO_WRAP))
        if(end==e.bytes.size)pending=null
        return result
    }
}
