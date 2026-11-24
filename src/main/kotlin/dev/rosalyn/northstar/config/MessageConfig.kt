package dev.rosalyn.northstar.config

import dev.rosalyn.northstar.language.processMessage
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
        vararg placeholders: Pair<String, String>,
        block: (MessageCreateBuilder) -> Unit = {}
    ) = toMessage(mapOf(*placeholders), block)

    fun toMessage(placeholderMap: Map<String, String>, block: (MessageCreateBuilder) -> Unit = {}): MessageCreateData {
        val builder = MessageCreateBuilder()
            .setContent(content?.let { processMessage(it, placeholderMap) })

        val embed = embed?.toEmbed(placeholderMap)
        embed?.let { builder.addEmbeds(it) }
        block(builder)
        return builder.build()
    }
}