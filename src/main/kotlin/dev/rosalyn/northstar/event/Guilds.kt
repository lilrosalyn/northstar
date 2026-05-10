@file:Listener

package dev.rosalyn.northstar.event

import dev.rosalyn.northstar.jda
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent

class GuildInitializeEvent(val guild: Guild, val isNewGuild: Boolean) : Event(jda) {
    val commandTree = guild.retrieveCommands().complete()
}

class GuildCleanupEvent(val guild: Guild) : Event(jda)

private fun onGuildAdd(event: GuildJoinEvent) {
    val event = GuildInitializeEvent(event.guild, true)
    jda.eventManager.handle(event)
}

private fun onGuildRemove(event: GuildLeaveEvent) {
    val event = GuildCleanupEvent(event.guild)
    jda.eventManager.handle(event)
}