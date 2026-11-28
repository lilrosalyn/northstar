@file:Feature("About Me", "Shows information about the bot.", toggleable = false)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.config.configToml
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.language.GlobalContext
import dev.rosalyn.northstar.language.InteractionContext
import dev.rosalyn.northstar.language.TranslationContext
import dev.rosalyn.northstar.language.translate
import dev.rosalyn.northstar.language.translateProcessed
import dev.rosalyn.northstar.lib.component.makeContainer
import dev.rosalyn.northstar.schema.editGuild
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.net.URI

private suspend fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    val guild = event.guild
    val config = getGuild(guild)

    if (!config.modules.extra.sentInformation) {
        val channel = getUpdatesChannel(guild)

        if (channel != null) {
            channel.sendMessageComponents(makeAbout(GlobalContext))
                .useComponentsV2()
                .queue()

            scope.launch {
                config.modules.extra.sentInformation = true
                editGuild(config)
            }
        }
    }

    return listOf(
        Commands.slash("about", "Shows information about the bot.")
    )
}

private fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "about")
        return

    val interaction = event.interaction
    interaction.replyComponents(makeAbout(InteractionContext(interaction)))
        .setEphemeral(true).useComponentsV2().queue()
}

private fun makeAbout(context: TranslationContext): Container {
    return makeContainer {
        textDisplay(context.translateProcessed("github" to configToml.github) { about.info })

        separator(true, Separator.Spacing.SMALL)

        actionRow {
            button(ButtonStyle.PRIMARY, context.translate { about.configuration }) {
                if (member?.hasPermission(Permission.ADMINISTRATOR) != true)
                    return@button reply(translate { about.adminsOnly }).setEphemeral(true).queue()

                replyComponents(configureModules(guild!!))
                    .setEphemeral(true)
                    .useComponentsV2()
                    .queue()
            }

            button(ButtonStyle.LINK, label = context.translate { about.discord }, url = URI(configToml.discord).toURL())
            button(ButtonStyle.LINK, label = context.translate { about.github }, url = URI(configToml.github).toURL())
        }
    }
}

private fun getUpdatesChannel(guild: Guild): TextChannel? {
    return guild.communityUpdatesChannel?.takeIf { it.canTalk() }
        ?: guild.channels.firstOrNull { it is TextChannel && it.canTalk() } as? TextChannel
}