@file:Feature("Verification", "Fend against bots with a password verification system.", toggleable = true)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableRole
import dev.rosalyn.northstar.interaction.ContainerBuilder
import dev.rosalyn.northstar.interaction.EmbedConfig
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.interaction.makeActionRow
import dev.rosalyn.northstar.interaction.makeModal
import dev.rosalyn.northstar.interaction.makeTextDisplay
import dev.rosalyn.northstar.language.translate
import dev.rosalyn.northstar.schema.getGuild
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction

@Serializable
data class Verification(
    override var enabled: Boolean = false,
    var code: String? = null,
    var message: MessageConfig? = null,
    var verifiedMessage: MessageConfig? = MessageConfig("You have successfully verified."),
    var wrongCodeMessage: MessageConfig? = MessageConfig("That code wasn't correct."),
    var beforeRoles: MutableList<SerializableRole> = mutableListOf(),
    var afterRoles: MutableList<SerializableRole> = mutableListOf()
) : ModuleSettings {
    fun isValidForUse() = code != null && message != null && verifiedMessage != null && wrongCodeMessage != null
            && (beforeRoles.isNotEmpty() || afterRoles.isNotEmpty())
}

private val moduleConfig = Verification::class

private fun ContainerBuilder.hookSettings(interaction: Interaction, settings: Verification, stage: SettingsStage) {
    if (stage == SettingsStage.AfterSettings)
        section {
            val (name, description) = interaction.translate {
                val module = modules["verification"]!!
                module["channel"]!!
            }

            textDisplay("**$name**\n-# $description")
            buttonAccessory(
                ButtonStyle.PRIMARY, "Choose"
            ) {
                if (!settings.enabled || !settings.isValidForUse())
                    return@buttonAccessory reply("You have to enable & configure verification before you can set a channel.")
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
                                    components += Button.of(ButtonStyle.PRIMARY, "verify", "Verify")
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
    if (event.customId != "verify")
        return

    val settings = getGuild(event.guild!!).modules.verification
    val interaction = event.interaction

    if (!settings.enabled || !settings.isValidForUse())
        return interaction.reply("Verification either isn't enabled or isn't configured.")
            .setEphemeral(true).queue()

    interaction.replyModal(makeModal("Verify", onSubmit = {
        val inputtedCode = getValue("code")!!.asString

        if (inputtedCode.equals(settings.code, true)) {
            val guild = guild!!
            val member = member!!

            guild.modifyMemberRoles(member, settings.afterRoles, settings.beforeRoles).queue()

            reply(settings.verifiedMessage!!.toMessage())
                .setEphemeral(true).queue()
        } else reply(settings.wrongCodeMessage!!.toMessage())
            .setEphemeral(true).queue()
    }) {
        textInput("code", TextInputStyle.SHORT, "Code", "Enter the password to verify.")
    }).queue()
}

private suspend fun onJoin(event: GuildMemberJoinEvent) {
    val settings = getGuild(event.guild).modules.verification
    event.guild.modifyMemberRoles(event.member, event.member.roles + settings.beforeRoles).queue()
}