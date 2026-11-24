package app.valenced.xposed.manager.analysis

import android.content.Context
import android.util.Base64
import app.valenced.plugins.repos.sha256Hex
import app.valenced.xposed.BuildConfig
import app.valenced.xposed.ETagFetchResult
import app.valenced.xposed.PluginHttps
import app.valenced.xposed.manager.ManagerPluginMailbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

internal class PluginAnalyzer(private val context:Context,private val signal:()->Unit){
    private val reportsDir=File(context.filesDir,"plugin-analysis").apply{mkdirs()}
    fun key(item:JSONObject,kind:String):String=sha256Hex(BuildConfig.VERSION_CODE.toString()+"|"+kind+"|"+item.optString("id")+"|"+item.optString("repo")+"|"+item.optString("version")+"|"+item.optString("artifactSha256")+"|"+item.optString("sha256"))
    fun report(item:JSONObject,kind:String):JSONObject?=runCatching{JSONObject(File(reportsDir,key(item,kind)+".json").readText())}.getOrNull()
    private fun save(item:JSONObject,kind:String,result:JSONObject){
        result.put("timestamp",System.currentTimeMillis())
        val f=File(reportsDir,key(item,kind)+".json")
        val temp=File(f.path+".tmp")
        temp.writeText(result.toString());if(!temp.renameTo(f)){temp.delete()}
    }
    private suspend fun ipc(op:String,payload:JSONObject):JSONObject{
        val id=ManagerPluginMailbox.submit(op,payload)
        signal()
        try{
            return withTimeout(18000L){
                while(true){
                    val raw=ManagerPluginMailbox.result(id)
                    if(raw!=null){
                        val r=JSONObject(raw);check(!r.has("error")){r.optString("error")};return@withTimeout r
                    }
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") JSONObject()
            }
        }finally{ManagerPluginMailbox.cancel(id)}
    }
    private suspend fun fetch(url:String,size:Long,hash:String):ByteArray{
        require(size in 1..AnalysisEngine.MAX_INPUT.toLong() && hash.matches(Regex("[a-f0-9]{64}"))){"Ungültige Größe oder SHA-256."}
        val result=PluginHttps.fetch(url,timeoutMillis=22000L,maxBytes=size) as? ETagFetchResult.Fetched?:error("Keine Plugin-Datei empfangen.")
        require(result.bytes.size.toLong()==size&&sha256Hex(result.bytes)==hash){"Datei stimmt nicht mit SHA-256 / Größe überein."}
        return result.bytes
    }
    private fun internalSource(id:String):ByteArray{
        val base="analysis-internal"
        val assets=context.assets
        for(stage in assets.list(base).orEmpty()){
            val stagePath="$base/$stage"
            for(dir in assets.list(stagePath).orEmpty()){
                val path="$stagePath/$dir"
                val manifest=runCatching{assets.open("$path/manifest.json").bufferedReader().use{JSONObject(it.readText())}}.getOrNull()?:continue
                if(manifest.optString("id")!=id)continue
                val text=StringBuilder()
                fun walk(folder:String,depth:Int){
                    if(depth>6||text.length>=1_500_000)return
                    for(n in assets.list(folder).orEmpty()){
                        if(n.endsWith(".ts")||n.endsWith(".tsx")){
                            val raw=assets.open("$folder/$n").use{AnalysisEngine.readLimited(it,1_000_000)}.decodeToString()
                            if(text.length+raw.length<=1_500_000)text.append("\n// ").append(n).append('\n').append(raw)
                        }else if(n!="manifest.json")walk("$folder/$n",depth+1)
                    }
                }
                walk(path,0)
                return text.toString().encodeToByteArray()
            }
        }
        return ByteArray(0)
    }
    suspend fun scan(item:JSONObject,kind:String):JSONObject=withContext(Dispatchers.IO){
        val result=try{
            val meta=JSONObject().put("name",item.optString("name")).put("id",item.optString("id"))
                .put("description",item.optString("description"))
            val bytes=when(kind){
                "catalog"->{
                    meta.put("kind","zip")
                    fetch(item.getString("artifactUrl"),item.getLong("artifactSize"),item.getString("artifactSha256"))
                }
                "vencord"->{
                    val revision=VencordTranslator.catalog(context).getString("revision")
                    val path=item.getString("path")
                    require(revision.matches(Regex("[a-f0-9]{40}"))&&path.matches(Regex("src/plugins/[a-zA-Z0-9._-]+/index\\.tsx?"))){"Ungültiger Vencord-Quellpfad."}
                    meta.put("kind","typescript").put("native",item.optBoolean("native")).put("files",item.optInt("files",1))
                    fetch("https://raw.githubusercontent.com/Vendicated/Vencord/$revision/$path",item.getLong("size"),item.getString("sha256"))
                }
                else->{
                    val prepared=ipc("analysis_prepare",JSONObject().put("id",item.getString("id"))).getJSONObject("analysisInput")
                    if(prepared.optString("kind")=="internal"){
                        meta.put("kind","internal");internalSource(item.getString("id"))
                    }else{
                        meta.put("kind","installed")
                        val size=prepared.getInt("size");require(size in 1..4_000_000)
                        val token=prepared.getString("token")
                        val out=ByteArrayOutputStream(size);var offset=0
                        while(offset<size){
                            val chunk=ipc("analysis_chunk",JSONObject().put("token",token).put("offset",offset)).getJSONObject("analysisChunk")
                            require(chunk.getInt("offset")==offset)
                            val data=Base64.decode(chunk.getString("base64"),Base64.DEFAULT)
                            out.write(data);offset=chunk.getInt("next")
                            require(offset==out.size()&&offset<=size&&data.isNotEmpty())
                        }
                        out.toByteArray().also{require(sha256Hex(it)==prepared.getString("sha256")){"Export-Prüfsumme ungültig."}}
                    }
                }
            }
            val r=SandboxClient.run(context,bytes,meta)
            if(kind=="vencord"&&!r.has("error")){
                r.put("translation",VencordTranslator.plan(item.optString("id"),sha256Hex(bytes)))
                r.put("sourceCoverage","Einstiegspunkt index.ts(x); weitere Desktop-Dateien werden nicht dynamisch ausgeführt.")
            }
            r
        }catch(e:kotlinx.coroutines.TimeoutCancellationException){JSONObject().put("error","Sandbox oder Discord-Export hat das Zeitlimit erreicht.")}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Throwable){JSONObject().put("error",(e.message?:e.javaClass.simpleName).take(400))}
        save(item,kind,result)
        result
    }
}
