package app.valenced.xposed.manager.analysis

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Source-derived IR adapters and separately labelled community equivalents.
 * A full source-tree digest is required for activation, not only a filename. */
internal object VencordTranslator {
    data class Rule(val port:String,val adaptation:String,val limitation:String,val sourceHash:String,
        val treeHash:String,val kind:String,val repo:String="",val version:String="",val artifactHash:String="")
    val rules:Map<String,Rule> get()=VencordCompiledRules.all
    fun plan(id:String,hash:String,treeHash:String?=null):JSONObject{
        val rule=rules[id]
        if(rule==null||rule.sourceHash!=hash||(treeHash!=null&&rule.treeHash!=treeHash))
            return JSONObject().put("supported",false).put("reason",if(rule==null)
                "Kein geprüfter Mobile-Adapter. Details im AST-Quellbericht."
                else "Desktop-Quellrevision stimmt nicht mit dem Compiler-Inventar überein.")
        return JSONObject().put("supported",true).put("target",rule.port).put("mode",rule.kind)
            .put("adaptation",rule.adaptation).put("limitation",rule.limitation)
            .put("repo",rule.repo).put("version",rule.version).put("artifactHash",rule.artifactHash)
            .put("treeHash",rule.treeHash)
    }
    fun inventoryReport(item:JSONObject):JSONObject{
        val ast=item.optJSONObject("analysis")?:JSONObject()
        val r=JSONObject().put("id",item.optString("id")).put("name",item.optString("name"))
            .put("declared",item.optString("description")).put("sourceBytes",ast.optInt("totalBytes"))
            .put("files",item.optInt("files")).put("kind","vencord-inventory").put("isolated",false)
            .put("native",item.optBoolean("native")).put("ast",ast).put("findings",JSONArray())
            .put("test",JSONObject().put("status","AST-Quellinventar abgeschlossen")
                .put("detail","Vollständige Quelldateien dieser gepinnten Revision wurden beim Erstellen des Katalogs untersucht. Kein Gerätelaufzeittest."))
            .put("sourceCoverage","AST-Inventar: "+ast.optInt("codeFiles")+" Code-Dateien; "+item.optInt("files")+" Dateien insgesamt.")
            .put("disclaimer","Statische Codeanalyse und Mobile-Plan sind kein Nachweis vollständiger Laufzeitkompatibilität.")
        val plan=plan(item.optString("id"),item.optString("sha256"),item.optString("treeHash"))
        if(!plan.optBoolean("supported"))plan.put("reason",item.optJSONObject("translation")?.optString("reason")?:plan.optString("reason"))
        return r.put("translation",plan)
    }
    fun catalog(context:Context):JSONObject=context.assets.open("vencord-catalog.json").bufferedReader().use{JSONObject(it.readText())}
}
