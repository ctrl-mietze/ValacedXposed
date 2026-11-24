package app.valenced.xposed.manager.analysis

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** No extraction to disk, no loading of native artifacts, and no real Discord APIs. */
internal object AnalysisEngine {
    const val MAX_INPUT=12*1024*1024
    private const val MAX_CODE=2*1024*1024
    private const val MAX_EXPANDED=24*1024*1024
    data class Input(val source:String,val native:Boolean=false,val manifest:JSONObject?=null,val files:Int=1,val executable:Boolean=false)

    fun readLimited(input:java.io.InputStream,max:Int):ByteArray {
        val out=ByteArrayOutputStream()
        val b=ByteArray(16384)
        var total=0
        while(true){
            val n=input.read(b);if(n<0)break
            total+=n;require(total<=max){"Analyse-Größenlimit überschritten."};out.write(b,0,n)
        }
        return out.toByteArray()
    }
    fun unpack(bytes:ByteArray):Input{
        var total=0;var files=0;var native=false;var manifest:JSONObject?=null
        val scripts=linkedMapOf<String,String>()
        var sourceTotal=0
        ZipInputStream(ByteArrayInputStream(bytes)).use{zip->
            while(true){
                val e=zip.nextEntry?:break
                require(++files<=256){"Zu viele ZIP-Einträge."}
                require(!e.name.startsWith("/")&&!e.name.contains('\\')&&".." !in e.name.split('/')){"Ungültiger ZIP-Pfad."}
                if(e.isDirectory)continue
                val name=e.name.lowercase()
                if(name.endsWith(".dex")||name.endsWith(".jar")||name.endsWith(".so")||name.endsWith(".apk"))native=true
                val inspect=e.name=="manifest.json"||name.endsWith(".js")||name.endsWith(".ts")||name.endsWith(".tsx")
                val out=if(inspect)ByteArrayOutputStream() else null
                val buffer=ByteArray(16384)
                var size=0
                while(true){
                    val n=zip.read(buffer)
                    if(n<0)break
                    size+=n;total+=n;require(total<=MAX_EXPANDED){"ZIP entpackt zu groß."}
                    if(out!=null){sourceTotal+=n;require(size<=MAX_CODE&&sourceTotal<=4*MAX_CODE){"Quelltext zu groß."};out.write(buffer,0,n)}
                }
                if(out!=null){
                    val text=out.toByteArray().decodeToString()
                    if(e.name=="manifest.json")manifest=JSONObject(text)
                    else scripts[e.name]=text
                }
            }
        }
        val m=manifest?:error("Kein Plugin-Manifest im Archiv.")
        val entry=m.optJSONObject("dist")?.optString("script").orEmpty()
        val src=scripts[entry].orEmpty()
        return Input(src,native||m.optJSONObject("dist")?.has("android")==true,m,files,src.isNotBlank()&&entry.endsWith(".js"))
    }
    private data class Rule(val name:String,val description:String,val tokens:List<String>)
    private val rules=listOf(
        Rule("Netzwerk","Kann Netzwerkzugriffe oder HTTP-Anfragen verwenden.",listOf("fetch(","XMLHttpRequest","RestAPI","WebSocket","axios","httpClient")),
        Rule("Dateien / native API","Enthält Verweise auf Datei- oder native Schnittstellen.",listOf("NativeModules","callNativeMethod","readFile","writeFile","node:fs","child_process","Runtime.exec","VencordNative")),
        Rule("Zwischenablage","Kann die Zwischenablage lesen oder verändern.",listOf("Clipboard","clipboard","setString","navigator.clipboard")),
        Rule("Nachrichten","Verarbeitet oder verändert Nachrichten bzw. deren Darstellung.",listOf("sendMessage","editMessage","deleteMessage","MESSAGE_CREATE","RowManager","MessageStore","ChatInput")),
        Rule("Kontodaten / sensible APIs","Verweist auf Konto-, Authentifizierungs- oder Token-Schnittstellen.",listOf("getToken","AuthenticationStore","Authorization","getCurrentUser","localStorage")),
        Rule("Oberfläche","Ändert Komponenten, Menüs, Farben oder Layout.",listOf("createElement","ReactNative","StyleSheet","contextMenus","semanticColors","registerTheme","className","document.")),
        Rule("Hooks / Patches","Sucht Module oder greift in deren Funktionen ein.",listOf("patcher","withProps","findByProps","@webpack","patches:","instead(","after(","before(")),
        Rule("Persistenz","Speichert Einstellungen oder Plugin-Daten.",listOf("jsonStorage","definePluginSettings","DataStore","storage","AsyncStorage")),
        Rule("Dynamischer Code","Kann zusätzlichen Code dynamisch auswerten.",listOf("eval(","new Function","Function(","import(")),
        Rule("Ereignisse / Hintergrund","Registriert Ereignislistener oder Timer.",listOf("FluxDispatcher","onFluxEventDispatched","setInterval","setTimeout","addEventListener")),
    )
    fun findings(source:String):JSONArray=JSONArray().apply{
        for(rule in rules){
            val matches=rule.tokens.filter{source.contains(it,ignoreCase=true)}
            if(matches.isNotEmpty())put(JSONObject().put("category",rule.name).put("description",rule.description).put("evidence",JSONArray(matches.take(8))))
        }
    }

    /** Fresh interpreted JS context. No Java bridge or host objects are exposed.
     * Only bounded synthetic callbacks are called. Unsupported APIs throw. */
    private class BudgetFactory:ContextFactory(){
        private val deadline=System.nanoTime()+700_000_000L
        private var instructions=0L
        override fun makeContext():Context=super.makeContext().apply{
            optimizationLevel=-1
            languageVersion=Context.VERSION_ES6
            instructionObserverThreshold=10000
            setMaximumInterpreterStackDepth(256)
            setClassShutter { false }
        }
        override fun observeInstructionCount(cx:Context,instructionCount:Int){
            instructions+=instructionCount
            if(instructions>2_000_000L||System.nanoTime()>deadline)throw BudgetExceeded()
        }
    }
    private class BudgetExceeded:Error("Sandbox-Limit erreicht")
    private val mocks="""
        var __events=[];
        function __event(x){if(__events.length<32)__events.push(x);}
        function __noop(){return undefined;}
        function __patch(){__event('Hook angefordert');return __noop;}
        var __patcher={before:__patch,after:__patch,instead:__patch};
        var __storage={};
        var __log={log:__noop,info:__noop,warn:__noop,error:__noop};
        var __react={createElement:function(){__event('UI angefordert');return {};}};
        var __finders={findByProps:function(){__event('Modulsuche');return undefined;},findByName:function(){__event('Modulsuche');return undefined;},findByStoreName:function(){return undefined;}};
        var vendetta={patcher:__patcher,plugin:{storage:__storage},logger:__log,metro:__finders,storage:{storage:__storage}};
        var revenge=vendetta,bunny=vendetta;
        var valenced={patcher:__patcher,react:{React:__react},modules:{finders:__finders},discord:{},assets:{}};
        function plugin(options){return options;}
        var console=__log;
        var __api={cleanup:__noop,plugin:{manifest:{id:'sandbox'},reportError:__noop,requireReload:__noop},unscoped:valenced,jsonStorage:{cache:{}}};
        function __run(candidate){
            var p=candidate && (candidate.default||candidate);
            if(!p||typeof p!=='object')return 'Kein unterstützter Plugin-Export';
            __event('Export gelesen');
            if(typeof p.preInit==='function'){p.preInit(__api);__event('preInit aufgerufen');}
            if(typeof p.init==='function'){p.init(__api);__event('init aufgerufen');}
            var start=p.onLoad||p.start;
            if(typeof start==='function'){var r=start.call(p,__api);if(r&&typeof r.then==='function')return 'Async-Lifecycle nicht vollständig simulierbar';__event('Start aufgerufen');}
            var stop=p.onUnload||p.stop;
            if(typeof stop==='function'){stop.call(p,__api);__event('Stop aufgerufen');}
            return 'Mock-Lifecycle abgeschlossen';
        }
    """.trimIndent()
    fun mock(source:String):JSONObject{
        if(source.length>300_000)return JSONObject().put("status","Nicht ausgeführt").put("detail","JS-Bundle überschreitet das dynamische Testlimit.")
        return try{
            BudgetFactory().call { cx->
                val scope=cx.initSafeStandardObjects()
                for(name in listOf("Packages","java","javax","org","com","JavaAdapter","JavaImporter","getClass","Java","Polyglot"))ScriptableObject.deleteProperty(scope,name)
                cx.evaluateString(scope,mocks,"mock-api",1,null)
                val cleaned=source.trim().trimEnd(';')
                // Actual executable JavaScript expressions only. TS/JSX goes through static analysis.
                cx.evaluateString(scope,"var __candidate = ("+cleaned+"\n);","plugin-under-test",1,null)
                val status=Context.toString(cx.evaluateString(scope,"__run(__candidate)","mock-test",1,null))
                val trace=Context.toString(cx.evaluateString(scope,"JSON.stringify(__events)","trace",1,null))
                JSONObject().put("status",status.take(200)).put("trace",runCatching{JSONArray(trace)}.getOrDefault(JSONArray()))
            }
        }catch(e:BudgetExceeded){
            JSONObject().put("status","Abgebrochen").put("detail","Zeit-/Instruktionslimit erreicht.")
        }catch(e:Throwable){
            JSONObject().put("status","Nicht vollständig simulierbar").put("detail",(e.message?:e.javaClass.simpleName).take(300))
        }
    }
    fun analyze(bytes:ByteArray,meta:JSONObject):JSONObject{
        val kind=meta.optString("kind")
        val input=when(kind){
            "zip"->unpack(bytes)
            "installed"->{val j=JSONObject(bytes.decodeToString());Input(j.optString("source"),j.optBoolean("native"),j.optJSONObject("manifest"),j.optInt("files",1),j.optBoolean("executable"))}
            else->Input(bytes.decodeToString(),meta.optBoolean("native"),null,meta.optInt("files",1),kind=="javascript")
        }
        if(input.manifest!=null&&meta.optString("id").isNotBlank())
            require(input.manifest.optString("id")==meta.optString("id")){"Plugin-ID stimmt nicht mit dem Katalog überein."}
        val source=input.source
        val native=input.native
        val result=JSONObject().put("name",meta.optString("name")).put("id",meta.optString("id"))
            .put("declared",input.manifest?.optString("description")?.takeIf{it.isNotBlank()}?:meta.optString("description"))
            .put("kind",kind).put("sourceBytes",source.encodeToByteArray().size).put("files",input.files).put("native",native)
            .put("findings",findings(source)).put("isolated",true)
        val test=when{
            native->JSONObject().put("status","Native Komponenten nicht ausgeführt").put("detail","DEX/JAR/ELF werden nicht geladen.")
            source.isBlank()->JSONObject().put("status","Kein JS-Quelltext verfügbar")
            !input.executable->JSONObject().put("status","Statisch analysiert").put("detail","TypeScript/JSX oder eingebautes Modul; kein ausführbarer Desktop-Code.")
            else->mock(source)
        }
        return result.put("test",test).put("disclaimer","Nur lokale Stichprobe mit simulierten APIs. Kein Nachweis, dass ein Plugin sicher oder vollständig kompatibel ist.")
    }
}
