package app.valenced.xposed.tweaks.plugins

import app.valenced.xposed.manager.analysis.VencordTranslator
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Only compiler diagnostics; never messages, account data, or hook arguments. */
internal object CompilerRuntimeState {
    private val labels=setOf("Moment","ChatRows","sendMessage","editMessage","ChatInputRightActions","ActionSheet.openLazy","UserProfileMenu","EmojiMenu","ReplyCreate","ReplyEvent")
    private val known by lazy {VencordTranslator.rules.values.filter{it.kind=="compiled"}.map{it.port}.toSet()}
    private val states=ConcurrentHashMap<String,String>()
    fun report(id:String,payload:String){
        if(id !in known||payload.length>1024)return
        runCatching{
            val input=JSONObject(payload)
            fun list(key:String):List<String>{
                val a=input.optJSONArray(key)?:JSONArray()
                require(a.length()<=labels.size)
                return (0 until a.length()).map{a.getString(it)}.distinct().also{require(it.all(labels::contains))}
            }
            val expected=list("expected")
            val bound=list("bound")
            require(bound.all(expected::contains))
            val state=input.optString("state")
            require(state in setOf("running","stopped"))
            val status=when{
                state=="stopped"->"stopped"
                expected.isNotEmpty()&&expected.all(bound::contains)->"bound"
                bound.isNotEmpty()->"partial"
                else->"waiting"
            }
            states[id]=JSONObject().put("expected",JSONArray(expected)).put("bound",JSONArray(bound)).put("status",status).toString()
        }
    }
    fun snapshot(id:String):JSONObject?=states[id]?.let{JSONObject(it)}
}
