package app.valenced.xposed.manager.analysis

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Translation rules use stable, audited feature mappings rather than eval
 * of desktop code or blindly rewriting webpack patterns to Metro. */
internal object VencordTranslator {
    data class Rule(val port:String,val adaptation:String,val limitation:String,val sourceHash:String)
    val rules=mapOf(
        "silentTyping" to Rule("veyra.port.no-typing","Flux: TYPING_START_LOCAL wird lokal unterdrückt.","Keine Desktop-Schaltfläche; der Port gilt bei Aktivierung dauerhaft.","406bbdc2f92d3303ede55b504e40f002420e193b1dd81352ccc8328c4d7cb27a"),
        "characterCounter" to Rule("veyra.port.char-counter","Webpack-UI-Patch → Metro ChatInputCharCounter und React-Native-Text.","Mobile Darstellung; ein Discord-Neustart kann nötig sein.","b79783bcb4384a2d5fca086a870b9b3e7d563d1167ad4eff034cc27077fc9255"),
        "clearURLs" to Rule("veyra.port.clean-urls","Desktop-Message-API → mobile Sende-/Editier-Hooks und Clipboard.","Unterstützt eine begrenzte Liste bekannter Tracking-Parameter. Signierte URLs bleiben unverändert.","8a34be79d0d1f7c20788d52f8f42009e738cb8322e20b707d85557a227792c9f"),
        "imageLink" to Rule("veyra.port.image-links","Webpack-Renderpatch → Metro RowManager.generate.","Bild-/GIF-Embeds ohne Text; keine Änderung von Medienberechtigungen.","c0bfa770adadd938e2acb2aa22b0653bca46f9554aa0f9d26534103915fcad53"),
        "alwaysAnimate" to Rule("veyra.port.animate","Desktop canAnimate-Patches → mobile Avatar- und Icon-URL-Auflösung.","Nur bereits animierte Discord-Avatare und Icons, keine vollständige Desktop-Funktion.","a307cd3ed85d67aeed3b353cabed6f2be8e1f8e4eb84c35d5987f6d9420e2a19"),
    )
    fun plan(id:String,hash:String):JSONObject{
        val rule=rules[id]
        if(rule==null||rule.sourceHash!=hash)return JSONObject().put("supported",false)
            .put("reason",if(rule==null)"Kein verifizierter Mobile-Adapter vorhanden." else "Desktop-Quellcode hat sich geändert; Regel muss geprüft werden.")
        return JSONObject().put("supported",true).put("target",rule.port)
            .put("adaptation",rule.adaptation).put("limitation",rule.limitation)
    }
    fun catalog(context:Context):JSONObject=context.assets.open("vencord-catalog.json").bufferedReader().use{JSONObject(it.readText())}
}
