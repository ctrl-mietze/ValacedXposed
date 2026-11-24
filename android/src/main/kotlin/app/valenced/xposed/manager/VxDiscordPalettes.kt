package app.valenced.xposed.manager

import org.json.JSONArray

internal object VxDiscordPalettes {
    private val values = mapOf(
        "midnight" to listOf("#0c0f14", "#131823", "#20283a", "#a990ff", "#f4f6fc", "#a990ff", "#171c28", "#1b2130", "#9ca5b8", "#2a3244", "#2b2440"),
        "lavender" to listOf("#111116", "#18181f", "#2b2b38", "#c4a8fa", "#f4f6fc", "#c4a8fa", "#1e1e27", "#242430", "#9ca5b8", "#383846", "#322b44"),
        "frost" to listOf("#0c121b", "#131e2a", "#253548", "#a4ceff", "#f4f6fc", "#a4ceff", "#182534", "#1e2c3c", "#9ca5b8", "#304154", "#253f58"),
        "neon" to listOf("#0a1015", "#131b22", "#273640", "#55e1d9", "#f4f6fc", "#55e1d9", "#19232b", "#202c35", "#9ca5b8", "#34434d", "#233b3c"),
        "glass" to listOf("#101115", "#17191e", "#2c303a", "#c2b0ff", "#f4f6fc", "#c2b0ff", "#1d2027", "#252831", "#9ca5b8", "#3a3e49", "#302d40"),
        "amoled" to listOf("#000000", "#0b0c10", "#20232b", "#c9b5ff", "#f4f6fc", "#c9b5ff", "#111319", "#171a20", "#9ca5b8", "#30343e", "#29243a"),
        "bloom" to listOf("#111014", "#19171c", "#302b37", "#f1afd3", "#f4f6fc", "#f1afd3", "#201d24", "#28242d", "#9ca5b8", "#413946", "#382b3a"),
        "eclipse" to listOf("#080b13", "#111725", "#263047", "#aebdff", "#f4f6fc", "#aebdff", "#171e2e", "#1d2638", "#9ca5b8", "#303c54", "#2b3457")
    )
    fun preview(id:String):JSONArray=JSONArray(values[id] ?: values.getValue("midnight"))
}
