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

    fun toMessage(
        vararg placeholders: Pair<String, String>
    ) = toMessage(mapOf(*placeholders))

    fun toMessage(placeholderMap: Map<String, String>): MessageCreateData {
        val builder = MessageCreateBuilder()
            .setContent(content?.let { processMessage(it, placeholderMap) })

        val embed = embed?.toEmbed(placeholderMap)
        embed?.let { builder.addEmbeds(it) }
        return builder.build()
    }
}