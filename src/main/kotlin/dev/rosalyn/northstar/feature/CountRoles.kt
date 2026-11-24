@file:Feature("Count Roles", "Count VA roles.", toggleable = true, customModule = true)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.event.GuildInitializeEvent
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands

private val headerRegex = Regex("^# .+\\s+")

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.message("Count Roles")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
    )
}

private fun onExecute(event: MessageContextInteractionEvent) {
    if (event.name != "Count Roles")
        return

    val target = event.interaction.target
    val content = target.contentRaw.replace("*", "")
        .replace(headerRegex, "")
        .trimEnd()

    val output = StringBuilder()
    val characters = content.split("\n\n")
    var totalAllTVACount = 0
    var totalAllSVACount = 0
    var totalAllBVACount = 0
    var totalFilledTVACount = 0
    var totalFilledSVACount = 0
    var totalFilledBVACount = 0

    for (character in characters) {
        val lines = character.lines().toMutableList()
        val name = lines.removeFirst()

        val allTVACount = lines.count { 'T' in it }
        val allSVACount = lines.count { 'S' in it }
        val allBVACount = lines.count { 'B' in it }

        val filledTVACount = lines.count { 'T' in it && '@' in it }
        val filledSVACount = lines.count { 'S' in it && '@' in it }
        val filledBVACount = lines.count { 'B' in it && '@' in it }

        output.append("$name: $filledTVACount/$allTVACount TVAs, $filledSVACount/$allSVACount SVAs, $filledBVACount/$allBVACount BVAs\n")
        totalAllTVACount += allTVACount
        totalAllSVACount += allSVACount
        totalAllBVACount += allBVACount
        totalFilledTVACount += filledTVACount
        totalFilledSVACount += filledSVACount
        totalFilledBVACount += filledBVACount
    }

    output.append("All totaled up: $totalFilledTVACount/$totalAllTVACount TVAs, $totalFilledSVACount/$totalAllSVACount SVAs, $totalFilledBVACount/$totalAllBVACount BVAs")
    event.reply(output.toString())
        .setEphemeral(true)
        .queue()
}