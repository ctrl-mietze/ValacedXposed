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
    @Test fun ruleRequiresPinnedSource(){
        val r=VencordTranslator.rules.getValue("silentTyping")
        assertTrue(VencordTranslator.plan("silentTyping",r.sourceHash).getBoolean("supported"))
        assertFalse(VencordTranslator.plan("silentTyping","wrong").getBoolean("supported"))
        assertFalse(VencordTranslator.plan("unknown","wrong").getBoolean("supported"))
    }
}
