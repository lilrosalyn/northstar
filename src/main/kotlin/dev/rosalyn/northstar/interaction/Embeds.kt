package dev.rosalyn.northstar.interaction

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

@Serializable
data class EmbedConfig(
    var header: ProfileField? = null,
    var footer: ProfileField? = null,
    var title: String? = null,
    var color: String? = null,
    var image: String? = null,
    var thumbnail: String? = null,
    var description: String? = null
) {
    @Serializable
    data class ProfileField(
        var imageURL: String? = null,
        var content: String? = null
    )

    fun toEmbed(
        vararg placeholders: Pair<String, String>
    ) = toEmbed(mapOf(*placeholders))

    fun toEmbed(
        placeholderMap: Map<String, String>
    ): MessageEmbed? {
        val builder = EmbedBuilder()
        color?.let { builder.setColor(Color.decode(it)) }
        title?.let { builder.setTitle(processMessage(it, placeholderMap)) }
        description?.let { builder.setDescription(processMessage(it, placeholderMap)) }
        builder.setThumbnail(thumbnail)
        builder.setImage(image)

        val header = header
        val footer = footer

        if (header != null) {
            try {
                val content = header.content?.let { processMessage(it, placeholderMap) }
                val imageURL = header.imageURL?.let { processMessage(it, placeholderMap) }
                builder.setAuthor(content)
                    .setAuthor(content, null, imageURL)
            } catch (_: IllegalArgumentException) {}
        }

        if (footer != null) {
            try {
                val content = footer.content?.let { processMessage(it, placeholderMap) }
                val imageURL = footer.imageURL?.let { processMessage(it, placeholderMap) }
                builder.setFooter(content)
                    .setFooter(content, imageURL)
            } catch (_: IllegalArgumentException) {}
        }

        return builder.takeUnless(EmbedBuilder::isEmpty)?.build()
    }
}