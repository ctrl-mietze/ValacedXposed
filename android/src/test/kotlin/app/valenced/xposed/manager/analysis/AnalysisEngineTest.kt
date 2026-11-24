package app.valenced.xposed.manager.analysis

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFails
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnalysisEngineTest {
    @Test fun mockLifecycleIsolated(){
        val r=AnalysisEngine.mock("({default:{start:function(){valenced.patcher.after()},stop:function(){}}})")
        assertEquals("Mock-Lifecycle abgeschlossen",r.getString("status"))
        assertTrue(r.getJSONArray("trace").toString().contains("Start aufgerufen"))
    }
    @Test fun javaAccessIsUnavailable(){
        val r=AnalysisEngine.mock("({start:function(){java.lang.System.getProperty('user.home')}})")
        assertEquals("Nicht vollständig simulierbar",r.getString("status"))
    }
    @Test fun loopingPluginIsTerminated(){
        val r=AnalysisEngine.mock("({start:function(){while(true){}}})")
        assertEquals("Abgebrochen",r.getString("status"))
    }
    @Test fun suspiciousCapabilitiesAreEvidenceNotVerdict(){
        val r=AnalysisEngine.findings("fetch(url); Clipboard.setString(text); new Function(code)")
        assertTrue(r.toString().contains("Netzwerk"))
        assertTrue(r.toString().contains("Zwischenablage"))
        assertTrue(r.toString().contains("Dynamischer Code"))
    }
    @Test fun blocksZipTraversal(){
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use{z->z.putNextEntry(ZipEntry("../outside.js"));z.write("x".toByteArray());z.closeEntry()}
        assertFails{AnalysisEngine.unpack(bytes.toByteArray())}
    }
    @Test fun compilerRuntimeStatusSeparatesRegistrationAndRender(){
        val id=VencordTranslator.rules.getValue("copyUserURLs").port
        val state=app.valenced.xposed.tweaks.plugins.CompilerRuntimeState
        state.report(id,JSONObject().put("expected",org.json.JSONArray(listOf("ActionSheet.openLazy","UserProfileMenu")))
            .put("bound",org.json.JSONArray(listOf("ActionSheet.openLazy"))).put("state","running").toString())
        assertEquals("partial",state.snapshot(id)?.optString("status"))
        state.report(id,JSONObject().put("expected",org.json.JSONArray(listOf("ActionSheet.openLazy","UserProfileMenu")))
            .put("bound",org.json.JSONArray(listOf("ActionSheet.openLazy","UserProfileMenu"))).put("state","running").toString())
        assertEquals("bound",state.snapshot(id)?.optString("status"))
        state.report(id,JSONObject().put("expected",org.json.JSONArray(listOf("ActionSheet.openLazy")))
            .put("bound",org.json.JSONArray(listOf("untrusted"))).put("state","running").toString())
        assertEquals("bound",state.snapshot(id)?.optString("status"))
        state.report(id,JSONObject().put("expected",org.json.JSONArray()).put("bound",org.json.JSONArray()).put("state","stopped").toString())
        assertEquals("stopped",state.snapshot(id)?.optString("status"))
    }
    @Test fun ruleRequiresPinnedSource(){
        val r=VencordTranslator.rules.getValue("silentTyping")
        assertTrue(VencordTranslator.plan("silentTyping",r.sourceHash).getBoolean("supported"))
        assertFalse(VencordTranslator.plan("silentTyping","wrong").getBoolean("supported"))
        assertFalse(VencordTranslator.plan("unknown","wrong").getBoolean("supported"))
        assertFalse(VencordTranslator.plan("silentTyping",r.sourceHash,"wrong").getBoolean("supported"))
        assertTrue(VencordTranslator.plan("silentTyping",r.sourceHash,r.treeHash).getBoolean("supported"))
        assertEquals(8,VencordTranslator.rules.values.count{it.kind=="compiled"})
        assertEquals(5,VencordTranslator.rules.values.count{it.kind=="bundled"})
        assertEquals(24,VencordTranslator.rules.values.count{it.kind=="repository"})
        assertTrue(VencordTranslator.rules.values.filter{it.kind=="repository"}.all{it.artifactHash.matches(Regex("[a-f0-9]{64}"))})
        assertEquals(2,VencordCompiledRules.dependencyPins.size)
    }
}
