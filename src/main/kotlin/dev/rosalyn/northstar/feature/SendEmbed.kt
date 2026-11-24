@file:Feature("Send Message", "Make the bot send a message/embed.", toggleable = false)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.commando
import dev.rosalyn.northstar.config.EmbedConfig
import dev.rosalyn.northstar.config.MessageConfig
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.jda
import dev.rosalyn.northstar.lib.component.makeActionRow
import dev.rosalyn.northstar.lib.component.makeContainer
import dev.rosalyn.northstar.schema.getGuild
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.entities.Guild as JDAGuild

data class SendMessage(var message: MessageConfig = MessageConfig())

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("message", "Makes the bot send a message/embed.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
        Commands.message("Edit Message")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
    )
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "message")
        return

    sendProbe(event.guild!!, event.interaction, "Send Message") {
        channel.sendMessage(it.toMessage()).queue()
        reply("The message has been sent.").setEphemeral(true).queue()
    }
}

private suspend fun onExecute(event: MessageContextInteractionEvent) {
    if (event.name != "Edit Message")
        return

    val target = event.target

    if (target.author != jda.selfUser)
        return event.interaction.reply("The bot cannot edit this message.")
            .setEphemeral(true).queue()

    sendProbe(
        event.guild!!,
        event.interaction,
        "Edit Message",
        MessageConfig(target.contentRaw, target.embeds.firstOrNull()?.let(::EmbedConfig))
    ) {
        target.editMessage(it.toEdit()).queue()
        reply("The message has been edited.").setEphemeral(true).queue()
    }
}

private suspend fun sendProbe(
    guild: JDAGuild,
    interaction: IReplyCallback,
    label: String,
    message: MessageConfig = MessageConfig(),
    block: ButtonInteraction.(MessageConfig) -> Unit
) {
    val module = commando.parsedNodes.find {
        val node = it.node as? FeatureBase ?: return@find false
        "Send Message" == node.name
    }!!.node as FeatureBase
    val guild = getGuild(guild)

    interaction.replyComponents(makeContainer {
        textDisplay("### Send Message\nConfigure the message.")
        separator(true, Separator.Spacing.SMALL)
        children += generateProperties(
            interaction,
            module,
            guild,
            MessageConfig::class,
            message
        )
    }, makeActionRow {
        button(ButtonStyle.PRIMARY, label) {
            block(message)
        }
    }).useComponentsV2().setEphemeral(true).queue()
}