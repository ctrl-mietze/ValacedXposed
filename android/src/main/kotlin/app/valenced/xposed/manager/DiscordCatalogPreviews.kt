package app.valenced.xposed.manager

import org.json.JSONArray

/** Palette samples from the linked, published Spec-2 theme documents. */
internal object DiscordCatalogPreviews {
    private val values=mapOf(
        "https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/mocha/mauve.json" to listOf("#1e1e2e","#181825","#313244","#cba6f7","#cdd6f4","#cba6f7"),
        "https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/frappe/lavender.json" to listOf("#303446","#292c3c","#414559","#babbf1","#c6d0f5","#babbf1"),
        "https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/macchiato/mauve.json" to listOf("#24273a","#1e2030","#363a4f","#c6a0f6","#cad3f5","#c6a0f6"),
        "https://raw.githubusercontent.com/catppuccin/vendetta/main/themes/latte/mauve.json" to listOf("#e6e9ef","#eff1f5","#ccd0da","#8839ef","#4c4f69","#8839ef"),
        "https://raw.githubusercontent.com/LukasDerBaum42/discord-theme/main/themes/system24.json" to listOf("#141414","#141414","#6f6f6f33","#262626","#aeaeae","#4fc6a2"),
        "https://raw.githubusercontent.com/LukasDerBaum42/discord-theme/main/themes/system24-purple.json" to listOf("#141414","#141414","#6f6f6f33","#262626","#aeaeae","#c598e7")
    )
    fun forUrl(url:String):JSONArray?=values[url]?.let{JSONArray(it)}
}
