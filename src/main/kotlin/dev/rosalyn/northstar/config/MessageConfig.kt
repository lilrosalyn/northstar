package dev.rosalyn.northstar.config

import dev.rosalyn.northstar.language.processMessage
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData

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

    fun toEdit(
        vararg placeholders: Pair<String, String>,
        block: (MessageEditBuilder) -> Unit = {}
    ) = toEdit(mapOf(*placeholders), block)

    fun toEdit(placeholderMap: Map<String, String>, block: (MessageEditBuilder) -> Unit = {}): MessageEditData {
        val builder = MessageEditBuilder()
            .setContent(content?.let { processMessage(it, placeholderMap) })

        val embed = embed?.toEmbed(placeholderMap)
        embed?.let { builder.setEmbeds(it) }
        block(builder)
        return builder.build()
    }
}