@file:Feature("Steal Emojis", "Steal emojis from a server.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.event.GuildInitializeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.internal.entities.emoji.CustomEmojiImpl

private val customEmojiRegex = Regex("<(a?):([^:]+):(\\d+)>")

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.message("Steal Emojis")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.CREATE_GUILD_EXPRESSIONS))
    )
}

private suspend fun onExecute(event: MessageContextInteractionEvent) = withContext(Dispatchers.IO) {
    if (event.name != "Steal Emojis")
        return@withContext

    val target = event.target
    val customEmojis = customEmojiRegex.findAll(target.contentRaw)
        .map { it.groupValues[2] to CustomEmojiImpl(
            it.groupValues[2],
            it.groupValues[3].toLong(),
            it.groupValues[1] == "a"
        ) }

    val response = event.interaction.reply("Stealing emojis...")
        .setEphemeral(true)
        .complete()

    for ((name, asset) in customEmojis) {
        val image = asset.image.downloadAsIcon().get()
        event.guild!!.createEmoji(name, image).complete()
    }

    response.editOriginal("Stole ${customEmojis.count()} emoji(s).")
        .queue()
}