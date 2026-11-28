package dev.rosalyn.northstar.language

import cc.ekblad.toml.TomlMapper
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import dev.rosalyn.northstar.Commando
import dev.rosalyn.northstar.logger
import net.dv8tion.jda.api.interactions.Interaction

private val availableLocales = mutableMapOf<String, CoreLanguage>()

fun loadLocales() {
    val mapper = tomlMapper {}
    loadLocale(mapper, "en")
}

fun <T : Any> translate(block: CoreLanguage.() -> T): T
    = GlobalContext.translate(block)

fun translateProcessed(vararg placeholders: Pair<String, String>, block: CoreLanguage.() -> String): String
    = GlobalContext.translateProcessed(*placeholders, block = block)

fun <T : Any> Interaction.translate(block: CoreLanguage.() -> T): T
    = InteractionContext(this).translate(block)

fun Interaction.translateProcessed(vararg placeholders: Pair<String, String>, block: CoreLanguage.() -> String): String
    = InteractionContext(this).translateProcessed(*placeholders, block = block)

fun <T : Any> TranslationContext.translate(block: CoreLanguage.() -> T): T {
    val key = language
    val locale = availableLocales[key]
        ?: availableLocales[key.split("-")[0]]
        ?: availableLocales["en"]!!

    return block(locale)
}

fun TranslationContext.translateProcessed(vararg placeholders: Pair<String, String>, block: CoreLanguage.() -> String): String {
    val value = translate(block)
    return processMessage(value, mapOf(*placeholders))
}

private fun loadLocale(mapper: TomlMapper, key: String) {
    val stream = Commando::class.java.getResourceAsStream("/lang/$key.toml")
        ?: return logger.warn("Lang file '/lang/$key.toml' doesn't exist")

    availableLocales[key] = mapper.decode<CoreLanguage>(stream)
    stream.close()
}