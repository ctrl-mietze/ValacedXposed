package app.valenced.xposed.manager

import android.graphics.Color
import org.json.JSONArray

/** Same original preset colors as manager.themes/motion-presets.ts. */
internal object VxMotionPresets {
    data class Preset(val id:String,val name:String,val description:String,
        val base:String,val glowA:String,val glowB:String,val accent:String,
        val duration:Long,val motion:String,val dim:Double,val colors:List<String>) {
        fun preview():JSONArray=JSONArray(colors)
        fun baseColor()=Color.parseColor(base)
        fun firstColor()=Color.parseColor(glowA)
        fun secondColor()=Color.parseColor(glowB)
    }
    val all=listOf(
        Preset("aurora","Aurora","Violett und Mint · schwebendes Nordlicht","#080b17","#7453c7","#187f73","#99daca",7200L,"drift",0.38,listOf("#080b17","#161925","#313643","#99daca","#f4f6fc","#99daca","#1c202b","#232732","#a8afbf","#3b4b51","#3d3660")),
        Preset("bloom","Bloom Motion","Rosé und Flieder · weiche Blütenlichter","#110a15","#9c416f","#54308b","#f6b9d6",8400L,"pulse",0.38,listOf("#110a15","#1b1924","#323643","#f6b9d6","#f4f6fc","#f6b9d6","#1f202b","#232732","#a8afbf","#4d4453","#493046")),
        Preset("eclipse","Eclipse Motion","Mitternachtsblau und Violett · langsame Umlaufbahn","#080b14","#3152a0","#6042ab","#b2c1ff",10000L,"orbit",0.38,listOf("#080b14","#161923","#313643","#b2c1ff","#f4f6fc","#b2c1ff","#1c202a","#232731","#a8afbf","#40465b","#293554")),
        Preset("crimson","Crimson Red","Tiefes Rot · wandernde Glut","#100608","#b82742","#641018","#ff9eae",6600L,"sweep",0.38,listOf("#100608","#1a171d","#313542","#ff9eae","#f4f6fc","#ff9eae","#1f1f27","#232630","#a8afbf","#4e3f4b","#512838")),
        Preset("rose","Rose Silk","Rosa und Roségold · fließender Seidenschimmer","#130b13","#b74783","#7e385d","#ffc0de",8200L,"drift",0.38,listOf("#130b13","#1c1923","#323642","#ffc0de","#f4f6fc","#ffc0de","#20202a","#242731","#a8afbf","#4e4654","#51324c")),
        Preset("purple","Purple Haze","Violett und Amethyst · pulsierende Nebel","#0c0718","#753fc6","#492294","#d4b9ff",7600L,"pulse",0.38,listOf("#0c0718","#181725","#313643","#d4b9ff","#f4f6fc","#d4b9ff","#1e1f2c","#232632","#a8afbf","#46445b","#3d3060")),
        Preset("ruby","Rubin Red","Schwarz und Rubinrot · tiefe, edle Lichtreflexe","#080306","#a80c35","#620b3a","#ff93b3",9400L,"orbit",0.38,listOf("#080306","#16151c","#313542","#ff93b3","#f4f6fc","#ff93b3","#1c1e26","#232630","#a8afbf","#4e3d4c","#4c2034")),
        Preset("sapphire","Sapphire Tide","Kobaltblau und Eisblau · ozeanische Wellen","#060b16","#245dcb","#126477","#a4d4ff",9000L,"sweep",0.38,listOf("#060b16","#151924","#313643","#a4d4ff","#f4f6fc","#a4d4ff","#1c202b","#232732","#a8afbf","#3d495b","#253961")),
        Preset("emerald","Emerald Forest","Smaragd und Türkis · wanderndes Waldlicht","#060e0c","#137d5b","#17685f","#8eebc7",8700L,"drift",0.38,listOf("#060e0c","#151b1f","#313642","#8eebc7","#f4f6fc","#8eebc7","#1c2128","#232731","#a8afbf","#394e50","#204240")),
        Preset("solar","Solar Gold","Gold und dunkles Bernstein · warme Lichtpulse","#110d07","#a56a18","#804324","#ffda91",9600L,"pulse",0.38,listOf("#110d07","#1b1a1d","#323642","#ffda91","#f4f6fc","#ffda91","#1f2127","#232730","#a8afbf","#4e4b46","#4b3d2c")),
        Preset("obsidian","Obsidian Silver","AMOLED-Schwarz und Silber · dezentes Lichtband","#000000","#4d5261","#343842","#e0e3ed",11000L,"sweep",0.25,listOf("#000000","#121419","#303541","#e0e3ed","#f4f6fc","#e0e3ed","#1a1d24","#222630","#a8afbf","#494c57","#313542")),
        Preset("sunset","Sunset Flame","Orange, Koralle und Magenta · Abendhimmel","#100911","#bb4a40","#7c2965","#ffc1ac",7900L,"orbit",0.38,listOf("#100911","#1a1822","#313642","#ffc1ac","#f4f6fc","#ffc1ac","#1f1f2a","#232631","#a8afbf","#4e464b","#523338")),
        Preset("ruby-violet","Ruby Violet","Rubinrot und tiefes Lila · zwei schwebende Nebelschichten","#0a0610","#b3123f","#6c2bd9","#d98cff",9000L,"orbit",0.34,listOf("#0a0610","#17131f","#211a2c","#d98cff","#f5f2ff","#d98cff","#1e1828","#14101b","#b4abc9","#3a2e4a","#40224f"))
    )
    fun ambientDuration(preset:Preset):Long=if(preset.id=="ruby-violet")12400L else maxOf(10000L,kotlin.math.round(preset.duration*1.37).toLong())
    fun get(id:String)=all.firstOrNull{it.id==id}
    fun fromPath(path:String):Preset? {
        val id=Regex("veyra-motion-([a-z]+(?:-[a-z]+)*?)(?:-v[23])?\\.png$").find(path)?.groupValues?.getOrNull(1)
        return id?.let(::get)
    }
}
