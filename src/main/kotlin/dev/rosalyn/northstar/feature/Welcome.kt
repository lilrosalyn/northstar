@file:Feature("Welcome", "Embrace new users with a welcome message.", toggleable = true)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableChannel
import dev.rosalyn.northstar.event.GuildCleanupEvent
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.interaction.EmbedConfig
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.interaction.processMessage
import dev.rosalyn.northstar.lib.calculateAge
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData

@Serializable
data class Welcome(
    override var enabled: Boolean = false,
    var channel: SerializableChannel? = null,
    var message: MessageConfig? = null
) : ModuleSettings {
    fun isValidForUse() = channel != null && message != null
}

private val moduleConfig = Welcome::class
private val memberCounts = mutableMapOf<Guild, Int>()

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    val guild = event.guild
    memberCounts[guild] = guild.memberCount

    return listOf(
        Commands.slash("welcome", "Test the welcome embed.")
            .addOption(OptionType.USER, "user", "The user.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
    )
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "welcome")
        return

    val config = getGuild(event.guild!!).modules.welcome

    if (!config.enabled || !config.isValidForUse())
        return event.interaction.reply("The welcome module is not configured.").queue()

    val embed = event.guild!!.generateWelcomeEmbed(event.member!!)
    event.reply(embed).queue()
}

private fun onGuildCleanup(event: GuildCleanupEvent) {
    memberCounts -= event.guild
}

private suspend fun onMemberJoin(event: GuildMemberJoinEvent) {
    val guild = event.guild
    val settings = getGuild(guild).modules.welcome

    if (!settings.isValidForUse())
        return

    val count = memberCounts[guild]?.plus(1) ?: guild.memberCount
    memberCounts[guild] = count

    val embed = guild.generateWelcomeEmbed(event.member)
    (settings.channel!! as TextChannel).sendMessage(embed).queue()
}

private fun onMemberLeave(event: GuildMemberRemoveEvent) {
    val guild = event.guild
    val count = memberCounts[guild]?.minus(1) ?: guild.memberCount
    memberCounts[guild] = count
}

suspend fun Guild.generateWelcomeEmbed(member: Member): MessageCreateData {
    val settings = getGuild(this).modules.welcome
    val placeholders = arrayOf(
        "username" to member.effectiveName,
        "mention" to member.asMention,
        "age" to calculateAge(member.timeCreated.toEpochSecond()),
        "profilePicture" to member.effectiveAvatarUrl,
        "memberCount" to (memberCounts[this] ?: 2).toString(),
        "serverName" to name
    )

    return settings.message!!.toMessage(*placeholders)
}