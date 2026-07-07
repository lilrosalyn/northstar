@file:Feature("Partnerships", "Manage partnerships.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableChannel
import dev.rosalyn.northstar.config.MessageConfig
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.jda
import dev.rosalyn.northstar.lib.calculateAge
import dev.rosalyn.northstar.schema.editMember
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.schema.getMember
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.utils.messages.MessageCreateData

@Serializable
data class PartnershipsProfile(
    val name: String,
    var channel: SerializableChannel? = null,
    var trigger: String? = null,
    var message: MessageConfig? = null
)

@Serializable
data class Partnerships(
    override var enabled: Boolean = false,
    var profiles: MutableList<PartnershipsProfile> = mutableListOf()
) : ModuleSettings

private val moduleConfig = Partnerships::class

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("partnerships", "Manage partnerships.")
            .addSubcommands(SubcommandData("increase", "Increase somebody's partnership count.")
                .addOption(OptionType.USER, "member", "The member.", true)
                .addOption(OptionType.INTEGER, "amount", "The amount to increase.", false))
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
    )
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "partnerships")
        return

    val guild = event.guild!!
    val config = getGuild(guild).modules.partnerships

    if (!config.enabled)
        return event.interaction.reply("The partnerships module is not enabled/configured.").queue()

    val member = event.getOption("member")!!.asMember!!
    val amount = event.getOption("amount")?.asInt ?: 1

    val memberData = getMember(member)
    memberData.partnerships += amount
    editMember(memberData)
    event.interaction.reply("${member.asMention}'s new partnership count is **${memberData.partnerships}** (previously was **${memberData.partnerships - amount}**.)")
        .setEphemeral(true)
        .queue()
}

private suspend fun onMessage(event: MessageReceivedEvent) {
    if (!event.isFromGuild || event.message.author.isBot)
        return

    val guild = event.guild
    val config = getGuild(guild).modules.partnerships
    val channel = event.guildChannel

    if (!config.enabled)
        return

    for (profile in config.profiles.sortedBy { if (it.trigger == null || it.trigger!! == "") 1 else 0 }) {
        val message = event.message

        if (channel.idLong != profile.channel?.id || (profile.trigger != null && profile.trigger!! !in message.contentRaw)
            || profile.message?.isValidForUse() != true)
            continue

        val invite = message.invites.getOrNull(0)?.let { Invite.resolve(jda, it, true).complete() }
            ?: return

        if (invite.guild == null)
            return

        val member = event.member!!
        val memberData = getMember(member)
        memberData.partnerships++

        channel.sendMessage(generatePartnershipEmbed(member, invite, profile)).queue()
        editMember(memberData)
        break
    }
}

private suspend fun generatePartnershipEmbed(
    member: Member,
    invite: Invite,
    profile: PartnershipsProfile,
): MessageCreateData {
    val inviteGuild = invite.guild!!
    val count = getMember(member).partnerships
    val placeholders = arrayOf(
        "username" to member.effectiveName,
        "mention" to member.asMention,
        "count" to count.toString(),
        "serverName" to inviteGuild.name,
        "serverAge" to calculateAge(inviteGuild.timeCreated.toEpochSecond()),
        "serverMemberCount" to inviteGuild.memberCount.toString()
    )

    return profile.message!!.toMessage(*placeholders)
}