package app.valenced.xposed.manager

import app.valenced.xposed.manager.analysis.VencordTranslator
import org.json.JSONObject

/** Presentation only. No repository index, enabled state or plugin artifact
 * is changed when grouping rows or toggling the compiler's visibility. */
internal object PluginCatalogView {
    data class Binding(val source:JSONObject,val rule:VencordTranslator.Rule){
        val sourceId:String get()=source.optString("id")
        val title:String get()=source.optString("name",sourceId)
    }
    data class Group(val id:String,val entries:List<JSONObject>)
    fun bindings(sources:List<JSONObject>):Map<String,List<Binding>> =
        sources.mapNotNull{source->
            val rule=VencordTranslator.rules[source.optString("id")]?:return@mapNotNull null
            if(rule.sourceHash!=source.optString("sha256")||rule.treeHash!=source.optString("treeHash"))return@mapNotNull null
            Binding(source,rule)
        }.groupBy{it.rule.port}
    fun forEntry(item:JSONObject,bindings:Map<String,List<Binding>>,installed:Boolean):List<Binding>{
        val all=bindings[item.optString("id")].orEmpty()
        return all.filter{binding->
            val rule=binding.rule
            if(rule.kind!="repository") installed&&item.optBoolean("internal")
            else item.optString("repo")==rule.repo&&item.optString("version")==rule.version&&
                item.optString("artifactSha256")==rule.artifactHash
        }
    }
    fun group(entries:List<JSONObject>):List<Group> =
        entries.groupBy{it.optString("id")}.map{(id,items)->Group(id,items)}
    fun matches(item:JSONObject,aliases:List<Binding>,query:String):Boolean=
        listOf("name","description","id","author","repo").any{item.optString(it).contains(query,true)}||
            aliases.any{it.title.contains(query,true)||it.sourceId.contains(query,true)}
    fun systemEntry(item:JSONObject):Boolean {
        val id=item.optString("id")
        return item.optBoolean("essential")||id.startsWith("valenced.api.")||
            id.startsWith("veyra.manager.")||id=="veyra.legacy.compat"
    }
}
