@file:Feature("Auto Roles", "Automatically grant roles to new members.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableRole
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent

@Serializable
data class AutoRoles(
    override var enabled: Boolean = false,
    var roles: MutableList<SerializableRole> = mutableListOf()
) : ModuleSettings

private val moduleConfig = AutoRoles::class

private suspend fun onJoin(event: GuildMemberJoinEvent) {
    val guild = event.guild
    val settings = getGuild(guild).modules.autoRoles

    if (!settings.enabled)
        return

    guild.modifyMemberRoles(
        event.member,
        settings.roles.mapNotNull { it.get() },
        emptyList()
    ).queue()
}

