@file:Feature("Punishments", "Issue punishments to users.", customModule = true)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.config.MessageConfig
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.lib.component.makeModal
import dev.rosalyn.northstar.lib.tryRetrieveMember
import dev.rosalyn.northstar.logger
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ContextException
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.util.concurrent.TimeUnit

@Serializable
data class Punishments(
    override var enabled: Boolean = false,
    var message: MessageConfig? = null
) : ModuleSettings {
    fun isValidForUse() = message?.isValidForUse() == true
}

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("ban", "Ban a user.")
            .addOption(OptionType.USER, "user", "The user to ban.", true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
    )
}

private val moduleConfig = Punishments::class

private suspend fun onExecute(event: SlashCommandInteractionEvent) = withContext(Dispatchers.IO) {
    if (event.name != "ban")
        return@withContext

    val guild = event.guild!!
    val guildData = getGuild(guild)
    val config = guildData.modules.punishments
    val interaction = event.interaction

    if (!config.enabled || !config.isValidForUse())
        return@withContext interaction.reply("The punishments module is not enabled/configured.")
            .setEphemeral(true)
            .queue()

    val moderator = interaction.user
    val user = interaction.getOption("user")!!.asUser

    interaction.replyModal(makeModal("Punish User", onSubmit = {
        val reason = getValue("reason")!!.asString

        val member = guild.tryRetrieveMember(user)
        var sentMessage = false

        if (member != null) {
            try {
                val channel = user.openPrivateChannel().complete()
                val messageData = config.message!!.toMessage("reason" to reason)
                channel.sendMessage(messageData).complete()
                sentMessage = true
            } catch (_: Exception) {}
        }

        guild.ban(user, 7, TimeUnit.DAYS)
            .reason(reason + "\nBanned by ${moderator.name} (${moderator.id})")
            .queue({
                interaction.reply("${user.name} has been banned." + if (sentMessage) "" else "\nThe DM failed to send.")
                    .setEphemeral(true)
                    .queue()
            }, {
                interaction.reply("We failed to ban ${user.name}. Is the bot's role above the member's roles?")
                    .setEphemeral(true)
                    .queue()
            })
    }) {
        textInput("reason", TextInputStyle.PARAGRAPH, "Reason")
    }).queue()
}