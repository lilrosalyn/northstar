package dev.rosalyn.northstar.interaction

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData

@Serializable
data class MessageConfig(
    var content: String? = null,
    var embed: EmbedConfig? = null,
) {
    fun isValidForUse() = content != null || embed != null

    fun toMessage(vararg placeholders: Pair<String, String>): MessageCreateData {
        val builder = MessageCreateBuilder()
            .setContent(content)

        val embed = embed?.toEmbed(*placeholders)
        embed?.let { builder.addEmbeds(it) }
        return builder.build()
    }
}