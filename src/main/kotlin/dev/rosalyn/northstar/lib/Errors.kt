package dev.rosalyn.northstar.lib

import dev.rosalyn.northstar.logger
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback

private const val interactionError = "An error occurred handling this interaction. Try again later, or file a bug report."

fun handleError(context: Any?, exception: Exception) {
    if (context is GenericInteractionCreateEvent) {
        val interaction = context.interaction
        logger.error("An error occurred handling an interaction event. (context: $context)", exception)

        val extraInformation = "\n" + when (val cause = exception.cause) {
            is InsufficientPermissionException -> "The bot is missing the permission `${cause.permission.name}`. Please have an admin give the bot this permission."
            else -> ""
        }.trimEnd()

        if (interaction is IReplyCallback)
            interaction.reply(interactionError + extraInformation)
                .setEphemeral(true).queue()

        return
    }

    if (context is GenericEvent)
        return logger.error("An error occurred handling an event. (context: $context)", exception)

    logger.error("An error occurred. (context: $context)", exception)
}