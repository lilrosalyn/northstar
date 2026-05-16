@file:Feature("Lock", "Lock channels.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableRole
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.interaction.EmbedConfig
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands

@Serializable
data class Lock(
    override var enabled: Boolean,
    var role: SerializableRole? = null,
    var locked: MessageConfig? = null,
    var unlocked: MessageConfig? = null
) : ModuleSettings {
    fun isValidForUse() = locked != null && unlocked != null && role != null
}

private val moduleConfig = Lock::class

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("lock", "Lock channels.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
    )
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "lock")
        return

    val guild = event.guild!!
    val config = getGuild(guild).modules.lock

    if (!config.isValidForUse() || !config.enabled)
        return event.interaction.reply("The locking module is not enabled/configured.").queue()

    val channel = event.channel.asTextChannel()
    val role = config.role!!

    try {
        val roleOverwrite = channel.upsertPermissionOverride(role)
        val isLocked = Permission.MESSAGE_SEND in roleOverwrite.deniedPermissions

        if (isLocked) roleOverwrite.setAllowed(roleOverwrite.allowedPermissions + Permission.MESSAGE_SEND)
        else roleOverwrite.setDenied(roleOverwrite.deniedPermissions + Permission.MESSAGE_SEND)

        roleOverwrite.queue()

        val message = (if (isLocked) config.unlocked else config.locked)!!.toMessage(
            "channelName" to channel.name,
            "channelMention" to channel.asMention,
            "username" to event.member!!.effectiveName
        )

        channel.sendMessage(message).queue()
        event.interaction.reply("${if (isLocked) "Unlocked" else "Locked"} the channel.")
            .setEphemeral(true)
            .queue()
    } catch (exception: InsufficientPermissionException) {
        if (exception.permission == Permission.MANAGE_PERMISSIONS)
            event.interaction.reply("""
                The bot doesn't have permission to modify this channel's permissions.
                Either grant `Manage Permissions` for this channel specifically, or grant `Manage Roles` to the bot globally.
            """.trimIndent()).setEphemeral(true).queue()
        else event.interaction.reply("This bot doesn't have the `${exception.permission.name}` permission.")
            .setEphemeral(true).queue()
    }
}