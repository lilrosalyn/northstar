@file:Feature("Reaction Roles", "Allow users to select what roles they want.")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.RolePartial
import dev.rosalyn.northstar.SerializableRole
import dev.rosalyn.northstar.lib.component.ContainerBuilder
import dev.rosalyn.northstar.config.MessageConfig
import dev.rosalyn.northstar.lib.component.makeActionRow
import dev.rosalyn.northstar.lib.component.makeTextDisplay
import dev.rosalyn.northstar.language.translate
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction

@Serializable
data class ReactionRoles(
    override var enabled: Boolean = false,
    var message: MessageConfig? = null,
    var roles: MutableList<SerializableRole> = mutableListOf()
) : ModuleSettings {
    fun isValidForUse() = message != null && roles.isNotEmpty()
}

private val moduleConfig = ReactionRoles::class

private fun ContainerBuilder.hookSettings(interaction: Interaction, settings: ReactionRoles, stage: SettingsStage) {
    if (stage == SettingsStage.AfterSettings)
        section {
            val (name, description) = interaction.translate {
                val module = modules["reactionroles"]!!
                module["channel"]!!
            }

            textDisplay("**$name**\n-# $description")
            buttonAccessory(
                ButtonStyle.PRIMARY, "Choose"
            ) {
                if (!settings.enabled || !settings.isValidForUse())
                    return@buttonAccessory reply("You have to enable & configure the module before you can set a channel.")
                        .setEphemeral(true).queue()

                replyComponents(
                    makeTextDisplay("Pick a channel to send the message in."),
                    makeActionRow {
                        entitySelect(
                            EntitySelectMenu.SelectTarget.CHANNEL,
                            channelTypes = listOf(
                                ChannelType.TEXT,
                                ChannelType.VOICE,
                                ChannelType.GUILD_PUBLIC_THREAD,
                                ChannelType.GUILD_PRIVATE_THREAD,
                                ChannelType.GUILD_NEWS_THREAD,
                                ChannelType.NEWS
                            )
                        ) {
                            val channel = values[0] as TextChannel
                            channel.sendMessage(settings.message!!.toMessage())
                                .addComponents(makeActionRow {
                                    components += Button.of(ButtonStyle.PRIMARY, "pick_roles", "Pick Roles")
                                }).queue()

                            reply("The message has been sent.")
                                .setEphemeral(true).queue()
                        }
                    }
                ).setEphemeral(true).useComponentsV2().queue()
            }
        }
}

private suspend fun onButton(event: ButtonInteractionEvent) {
    if (event.customId != "pick_roles")
        return

    val guild = event.guild!!
    val interaction = event.interaction
    val settings = getGuild(guild).modules.reactionRoles

    if (!settings.enabled || !settings.isValidForUse())
        return interaction.reply("The reaction roles module isn't enabled / configured.")
            .setEphemeral(true).queue()

    event.replyComponents(
        makeTextDisplay("Please pick your roles."),
        makeActionRow {
            val mappedRoles = settings.roles.mapNotNull { it.get() }
            val priorSelectedRoles = mappedRoles.filter { it in event.member!!.roles }

            stringSelect(
                mappedRoles.map { SelectOption.of(it.name, it.id) },
                defaultValues = priorSelectedRoles.map { it.id },
                requiredRange = 1..25
            ) {
                val selectedRoles = values.mapNotNull { guild.getRoleById(it) }
                val rolesToAdd = mutableListOf<Role>()
                val rolesToRemove = mutableListOf<Role>()

                for (option in selectMenu.options) {
                    val role = guild.getRoleById(option.value) ?: continue
                    val wasEnabled = role in priorSelectedRoles

                    if (wasEnabled && role !in selectedRoles)
                        rolesToRemove += role
                    else if (!wasEnabled && role in selectedRoles)
                        rolesToAdd += role
                }

                guild.modifyMemberRoles(event.member!!, rolesToAdd, rolesToRemove).queue()
                val actionRow = message.components[1].asActionRow()
                val selectMenu = actionRow.components[0].asStringSelectMenu()
                    .createCopy()
                    .setDefaultValues((priorSelectedRoles + rolesToAdd - rolesToRemove.toSet()).map(Role::getId))
                    .build()

                editComponents(
                    makeTextDisplay("Your roles have been updated."),
                    ActionRow.of(selectMenu)
                ).useComponentsV2().queue()
            }
        }
    ).useComponentsV2().setEphemeral(true).queue()
}

private suspend fun onSelect(event: StringSelectInteractionEvent) {
    if (event.customId != "select_roles")
        return

    val guild = event.guild!!
    val interaction = event.interaction
    val settings = getGuild(guild).modules.reactionRoles

    if (!settings.enabled || !settings.isValidForUse())
        return interaction.reply("The reaction roles module isn't enabled / configured.")
            .setEphemeral(true).queue()


}