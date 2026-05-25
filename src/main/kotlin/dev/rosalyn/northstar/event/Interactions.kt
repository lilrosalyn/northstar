@file:Listener

package dev.rosalyn.northstar.event

import dev.rosalyn.northstar.interaction.handlerRecord
import dev.rosalyn.northstar.lib.handleError
import dev.rosalyn.northstar.logger
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.ICustomIdInteraction
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import java.util.*

private suspend fun onComponentInteraction(event: GenericInteractionCreateEvent) {
    try {
        val customID = (event as? ICustomIdInteraction)?.customId?.let(::tryParseUUID) ?: return
        val (_, handler) = handlerRecord[customID]
            ?: return (event.interaction as? IReplyCallback)?.reply("This command has expired. Please run it again.")
                ?.setEphemeral(true)?.queue() ?: Unit

        handler(event.interaction)
    } catch (exception: Exception) {
        handleError(event, exception)
    }
}

private fun tryParseUUID(id: String): UUID? {
    return try {
        UUID.fromString(id)
    } catch (_: IllegalArgumentException) {
        null
    }
}