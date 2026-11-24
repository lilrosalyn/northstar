@file:Feature("Tickets", "Manage staff-user communication with tickets.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.config.MessageConfig
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.lib.component.makeActionRow
import dev.rosalyn.northstar.lib.component.makeContainer
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import kotlin.random.Random

private const val customIdPrefix = "new_ticket_"

@Serializable
data class Tickets(
    override var enabled: Boolean = false,
    var ticketMessage: MessageConfig? = null,
    var ticketCreationSuccess: MessageConfig? = MessageConfig("Your ticket is available in {thread}, thank you!"),
    var profiles: MutableList<TicketProfile> = mutableListOf()
) : ModuleSettings {
    fun isValidForUse() = ticketMessage != null && ticketCreationSuccess != null && profiles.all { it.isValidForUse() }
}

private val moduleConfig = Tickets::class

@Serializable
data class TicketProfile(
    override var name: String,
    var messages: MutableList<MessageConfig> = mutableListOf(),
    val id: String = Random.nextBytes(4).toHexString()
) : Profile {
    fun isValidForUse() = true
}

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("sendticket", "Send the ticket message.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
    )
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "sendticket")
        return

    val guild = event.guild!!
    val config = getGuild(guild).modules.tickets

    if (!config.isValidForUse() || !config.enabled)
        return event.interaction.reply("The tickets module is not enabled/configured.").queue()

    val message = config.ticketMessage!!.toMessage {
        it.addComponents(makeActionRow {
            for ((name, _, id) in config.profiles) {
                components += Button.of(
                    ButtonStyle.PRIMARY,
                    "$customIdPrefix$id",
                    name
                )
            }
        })
    }

    event.channel.sendMessage(message).queue()
    event.interaction.reply("Sent the message.")
        .setEphemeral(true)
        .queue()
}

private suspend fun onButtonInteraction(event: ButtonInteractionEvent) = withContext(Dispatchers.IO) {
    val customId = event.button.customId
    val interaction = event.interaction

    if (customId == "close_ticket") {
        val channel = interaction.channel

        if (channel.type != ChannelType.GUILD_PRIVATE_THREAD)
            interaction.reply("An error occurred trying to delete this ticket.")
                .setEphemeral(true)
                .queue()
        else channel.delete()
            .complete()

        return@withContext
    }

    if (customId?.startsWith(customIdPrefix) != true)
        return@withContext

    val guild = event.guild!!
    val config = getGuild(guild).modules.tickets

    if (!config.isValidForUse() || !config.enabled)
        return@withContext interaction.reply("The tickets module is not enabled/configured.").queue()

    val id = customId.removePrefix(customIdPrefix)
    val profile = config.profiles.find { it.id == id }
        ?: return@withContext interaction.reply("That ticket type doesn't exist anymore. Please contact an admin.")
            .queue()

    val name = event.member!!.effectiveName
    val channel = interaction.channel as TextChannel
    val thread = channel.createThreadChannel("$name's ticket", true)
        .setInvitable(true)
        .reason("$name created a new ticket.")
        .complete()

    interaction.reply(config.ticketCreationSuccess!!.toMessage("thread" to thread.asMention))
        .setEphemeral(true)
        .queue()

    for ((index, message) in profile.messages.withIndex()) {
        thread.sendMessage(message.toMessage(
            "username" to name,
            "mention" to event.user.asMention,
            "profilePicture" to event.member!!.effectiveAvatarUrl
        ) {
            if (index == 0)
                it.addComponents(ActionRow.of(Button.of(
                    ButtonStyle.DANGER,
                    "close_ticket",
                    "Close Ticket"
                )))
        }).queue()
    }
}