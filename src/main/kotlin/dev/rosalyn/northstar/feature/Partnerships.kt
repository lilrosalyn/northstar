@file:Feature("Partnerships", "Manage partnerships.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableChannel
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.interaction.EmbedConfig
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.interaction.processMessage
import dev.rosalyn.northstar.jda
import dev.rosalyn.northstar.lib.calculateAge
import dev.rosalyn.northstar.schema.editMember
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.schema.getMember
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Invite
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
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

        if (channel != profile.channel || (profile.trigger != null && profile.trigger!! !in message.contentRaw)
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