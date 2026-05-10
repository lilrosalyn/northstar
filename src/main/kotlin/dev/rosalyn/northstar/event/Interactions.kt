@file:Listener

package dev.rosalyn.northstar.event

import dev.rosalyn.northstar.interaction.handlerRecord
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import java.util.*

private suspend fun onComponentInteraction(event: GenericComponentInteractionCreateEvent) {
    val customID = tryParseUUID(event.customId) ?: return
    val (_, handler) = handlerRecord[customID]
        ?: return event.interaction.reply("This command has expired. Please run it again.")
            .setEphemeral(true).queue()

    handler(event.interaction)
}

private suspend fun onModalSubmit(event: ModalInteractionEvent) {
    val customID = tryParseUUID(event.customId) ?: return
    val (_, handler) = handlerRecord[customID]
        ?: return event.interaction.reply("This command has expired. Please run it again.")
            .setEphemeral(true).queue()

    handler(event.interaction)
}

private fun tryParseUUID(id: String): UUID? {
    return try {
        UUID.fromString(id)
    } catch (_: IllegalArgumentException) {
        null
    }
}