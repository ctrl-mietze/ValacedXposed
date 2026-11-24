package app.valenced.xposed.manager

import app.valenced.xposed.manager.analysis.VencordTranslator
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PluginCatalogViewTest {
    private fun source(id:String):JSONObject {
        val rule=VencordTranslator.rules.getValue(id)
        return JSONObject().put("id",id).put("name",id).put("sha256",rule.sourceHash).put("treeHash",rule.treeHash)
    }
    @Test fun groupingPreservesEverySource(){
        val rows=listOf(JSONObject().put("id","a").put("repo","https://one.example/index.json"),
            JSONObject().put("id","a").put("repo","https://two.example/index.json"),
            JSONObject().put("id","b").put("repo","https://three.example/index.json"))
        val result=PluginCatalogView.group(rows)
        assertEquals(2,result.size)
        assertEquals(rows,result.flatMap{it.entries})
        assertEquals(2,result.first().entries.size)
    }
    @Test fun aliasesShareOneActualTarget(){
        val index=PluginCatalogView.bindings(listOf(source("typingIndicator"),source("typingTweaks")))
        assertEquals(1,index.size)
        assertEquals(2,index.values.first().size)
    }
    @Test fun repositoryMappingsRequireExactVersionAndHash(){
        val id="typingIndicator"
        val rule=VencordTranslator.rules.getValue(id)
        val index=PluginCatalogView.bindings(listOf(source(id)))
        val item=JSONObject().put("id",rule.port).put("repo",rule.repo).put("version",rule.version).put("artifactSha256",rule.artifactHash)
        val aliases=PluginCatalogView.forEntry(item,index,false)
        assertEquals(1,aliases.size)
        assertTrue(PluginCatalogView.matches(item,aliases,"typingIndicator"))
        assertTrue(PluginCatalogView.forEntry(JSONObject(item.toString()).put("artifactSha256","wrong"),index,false).isEmpty())
        assertTrue(PluginCatalogView.forEntry(JSONObject(item.toString()).put("repo","https://wrong.example"),index,false).isEmpty())
    }
    @Test fun internalPortsStayDistinctFromRepositoryArtifacts(){
        val id="gifPaste";val rule=VencordTranslator.rules.getValue(id)
        val index=PluginCatalogView.bindings(listOf(source(id)))
        val item=JSONObject().put("id",rule.port).put("internal",true)
        assertEquals(1,PluginCatalogView.forEntry(item,index,true).size)
        assertTrue(PluginCatalogView.forEntry(item,index,false).isEmpty())
        assertFalse(PluginCatalogView.systemEntry(item))
    }
}
