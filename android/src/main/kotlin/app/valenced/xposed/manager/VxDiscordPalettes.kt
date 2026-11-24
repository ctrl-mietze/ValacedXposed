package app.valenced.xposed.manager

import org.json.JSONArray

/** Offline fallback mirrors the Discord preset roles in palettes.ts. */
internal object VxDiscordPalettes {
    private val values = mapOf(
        "midnight" to listOf("#0e1016", "#141824", "#1e2435", "#a990ff", "#f3f5fd", "#a990ff", "#181c28", "#1b2030", "#8991a8", "#262d40", "#30264c" ),
        "lavender" to listOf("#111017", "#191623", "#2a253c", "#c4a8fa", "#f5f0fc", "#c4a8fa", "#1e192a", "#242034", "#a39ab5", "#373046", "#3b2c56" ),
        "frost" to listOf("#0c121b", "#131e2a", "#253548", "#a4ceff", "#eff6ff", "#a4ceff", "#182534", "#1e2c3c", "#91a6bd", "#304154", "#253f58" ),
        "neon" to listOf("#090d14", "#111923", "#20303f", "#55e1d9", "#edf7fa", "#55e1d9", "#15202c", "#1a2634", "#8ca7b4", "#2e404d", "#1a413f" ),
        "glass" to listOf("#11121a", "#191b26", "#2b2e40", "#c2b0ff", "#f5f4fc", "#c2b0ff", "#1e202e", "#242636", "#9da0b5", "#37394d", "#35304d" ),
        "amoled" to listOf("#000000", "#0b0d12", "#1d202a", "#c9b5ff", "#f6f5fc", "#c9b5ff", "#111319", "#161922", "#9295aa", "#292d39", "#2f2446" ),
        "bloom" to listOf("#120e14", "#1d1720", "#34283b", "#f1afd3", "#fcf2fa", "#f1afd3", "#241b28", "#2b2131", "#af99ad", "#473548", "#4b2e46" ),
        "eclipse" to listOf("#080b13", "#111725", "#263047", "#aebdff", "#f0f3ff", "#aebdff", "#171e2e", "#1d2638", "#939fbb", "#303c54", "#2b3457" )
    )
    fun preview(id:String):JSONArray=JSONArray(values[id] ?: values.getValue("midnight"))
}
