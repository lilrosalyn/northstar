package dev.rosalyn.northstar.language

import net.dv8tion.jda.api.interactions.Interaction

interface TranslationContext {
    val language: String
}

object GlobalContext : TranslationContext {
    override val language = "en"
}

value class InteractionContext(val interaction: Interaction) : TranslationContext {
    override val language get() = interaction.userLocale.locale
}