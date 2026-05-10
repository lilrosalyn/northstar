@file:Feature("Roleplay", "Roleplay as a character in chat.", toggleable = true)

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableChannel
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.schema.CacheWithPeriod
import dev.rosalyn.northstar.schema.RoleplayProfile
import dev.rosalyn.northstar.schema.createRoleplayMessage
import dev.rosalyn.northstar.schema.createRoleplayProfile
import dev.rosalyn.northstar.schema.editRoleplayProfile
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.schema.listRoleplayProfiles
import dev.rosalyn.northstar.schema.probeRoleplayMessageAuthor
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.modals.Modal
import net.dv8tion.jda.api.utils.FileUpload
import java.net.URI

@Serializable
data class Roleplay(
    override var enabled: Boolean = false,
    var channels: MutableList<SerializableChannel> = mutableListOf()
) : ModuleSettings

private val moduleConfig = Roleplay::class
private val webhookCache by CacheWithPeriod<TextChannel, Webhook>()

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("roleplay", "Manage roleplay profiles."),
        Commands.context(Command.Type.MESSAGE, "Get roleplay author")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
    )
}

private suspend fun onChat(event: MessageReceivedEvent) {
    val webhookName = "Northstar Roleplay"
    val message = event.message
    val user = message.author
    val content = message.contentRaw
    val channel = event.channel as? GuildChannel ?: return
    val config = getGuild(event.guild).modules.roleplay

    if (!config.enabled || (config.channels.isNotEmpty() && channel !in config.channels))
        return

    val parent = if (channel is ThreadChannel)
        channel.parentChannel.asTextChannel()
    else channel as TextChannel

    scope.launch {
        val profiles = listRoleplayProfiles(user)

        for (profile in profiles) {
            if (content.startsWith(profile.prefix) && content.endsWith(profile.suffix ?: "")) {
                val webhook = webhookCache.getOrPut(parent) { parent.retrieveWebhooks().complete().find { it.name == webhookName }
                    ?: parent.createWebhook(webhookName).complete() }

                message.delete().queue()
                val newContent = StringBuilder()

                if (message.referencedMessage != null) {
                    val ref = message.referencedMessage!!
                    val author = probeRoleplayMessageAuthor(ref) ?: ref.author
                    var limitedContent = ref.contentStripped

                    if (author != ref.author) {
                        val lines = limitedContent.lines().toMutableList()
                        lines.removeFirst()
                        lines.removeFirst()
                        limitedContent = lines.joinToString(" ")
                    }

                    if (limitedContent.length > 50)
                        limitedContent = limitedContent.substring(0, 50) + "..."

                    newContent.append("> Replying to ${ref.author.name} (${author.asMention})\n> $limitedContent\n")
                }

                newContent.append(content.removeSurrounding(profile.prefix, profile.suffix ?: ""))
                val newMessage = webhook.sendMessage(newContent.toString())
                    .setUsername(profile.name)
                    .setAvatarUrl(profile.avatar)
                    .setThread(channel as? ThreadChannel)
                    .addFiles(message.attachments.map {
                        val data = URI(it.proxyUrl).toURL().openStream()
                        FileUpload.fromData(data, it.fileName)
                    })
                    .setAllowedMentions(listOf(Message.MentionType.USER))
                    .complete()

                createRoleplayMessage(user, newMessage)
                break
            }
        }
    }
}

private suspend fun onProbe(event: MessageContextInteractionEvent) {
    if (event.name != "Get roleplay author")
        return

    if (event.guild?.let { getGuild(it).modules.roleplay.enabled } != true)
        return event.reply("The roleplay module isn't enabled.")
            .setEphemeral(true)
            .queue()

    val target = event.target

    scope.launch {
        val user = probeRoleplayMessageAuthor(target)

        if (user == null) {
            event.reply("This isn't a roleplay message, or we lost track of who said it.")
                .setEphemeral(true)
                .queue()

            return@launch
        }

        event.reply("This message was sent by ${user.asMention}.")
            .setEphemeral(true)
            .queue()
    }
}

private suspend fun onExecute(event: SlashCommandInteractionEvent) {
    if (event.name != "roleplay")
        return

    if (event.guild?.let { getGuild(it).modules.roleplay.enabled } != true)
        return event.reply("The roleplay module isn't enabled.")
            .setEphemeral(true)
            .queue()

    scope.launch {
        val profiles = listRoleplayProfiles(event.user)

        event.interaction.replyComponents(Container.of(
            TextDisplay.of("""
                ### Roleplay Profiles
                
                You're able to create 'roleplay profiles,' which allow you to send messages as any character of your choosing. You can change their name and profile picture, and you may have up to 10 profiles at a time.
                
                Below, you may either edit an existing profile or create a new one.
            """.trimIndent()),
            ActionRow.of(StringSelectMenu.create("edit_profile")
                .setMaxValues(1)
                .setPlaceholder(if (profiles.isEmpty()) "You have no profiles to edit." else "Edit Profile")
                .setDisabled(profiles.isEmpty())
                .addOptions(profiles.map {
                    SelectOption.of(it.name, it.id.toString())
                }.ifEmpty { listOf(SelectOption.of("No profiles exist", "no_profiles_exist")) })
                .build()),
            ActionRow.of(Button.of(ButtonStyle.PRIMARY, "create_profile", "Create Profile")
                .withDisabled(profiles.size >= 10))
        )).useComponentsV2().setEphemeral(true).queue()
    }
}

private fun onPressButton(event: ButtonInteractionEvent) {
    when (event.button.customId) {
        "create_profile" -> {
            val modal = createProfileModal()
            event.interaction.replyModal(modal).queue()
        }
    }
}

private fun onSelect(event: StringSelectInteractionEvent) {
    if (event.customId != "edit_profile")
        return

    scope.launch {
        val owner = event.user
        val profile = listRoleplayProfiles(owner).find { it.id == event.selectedOptions[0]!!.value.toInt() }
        val modal = createProfileModal(profile)
        event.interaction.replyModal(modal).queue()
    }
}

private fun onSubmitModal(event: ModalInteractionEvent) {
    if (event.modalId == "create_profile") {
        val interaction = event.interaction
        val name = interaction.getValue("name")!!.asString
        val prefix = interaction.getValue("prefix")!!.asString
        val suffix = interaction.getValue("suffix")?.asString?.ifBlank { null }
        val profilePicture = interaction.getValue("avatar")!!.asAttachmentList[0]

        scope.launch {
            createRoleplayProfile(
                interaction.user,
                name,
                profilePicture,
                prefix,
                suffix
            )

            interaction.reply("A new profile, **$name**, has been created.")
                .setEphemeral(true)
                .queue()
        }
    }

    if (event.modalId.startsWith("edit_profile_")) {
        scope.launch {
            val interaction = event.interaction
            val id = event.modalId.removePrefix("edit_profile_").toInt()
            val profile = listRoleplayProfiles(event.user).find { it.id == id }!!

            profile.name = interaction.getValue("name")!!.asString
            profile.prefix = interaction.getValue("prefix")!!.asString
            profile.suffix = interaction.getValue("suffix")?.asString?.ifBlank { null }
            interaction.getValue("avatar")?.asAttachmentList?.getOrNull(0)?.proxyUrl?.let { profile.avatar = it }

            editRoleplayProfile(profile)
            interaction.reply("The profile **${profile.name}** has been edited.")
                .setEphemeral(true)
                .queue()
        }
    }
}

private fun createProfileModal(profile: RoleplayProfile? = null): Modal {
    val customId = if (profile != null) "edit_profile_${profile.id}" else "create_profile"
    val title = if (profile != null) "Edit Profile" else "Create Profile"

    val modal = Modal.create(customId, title)
        .addComponents(
            Label.of(
                "Name",
                null,
                TextInput.create("name", TextInputStyle.SHORT)
                    .setValue(profile?.name)
                    .setRequiredRange(1, 50)
                    .setRequired(true)
                    .build()
            ),
            Label.of(
                "Prefix",
                "Your message must start with the supplied prefix in order to chat as this character.",
                TextInput.create("prefix", TextInputStyle.SHORT)
                    .setValue(profile?.prefix)
                    .setRequiredRange(1, 10)
                    .setRequired(true)
                    .build()
            ),
            Label.of(
                "Suffix",
                "Your message must end with the supplied suffix in order to chat as this character.",
                TextInput.create("suffix", TextInputStyle.SHORT)
                    .setValue(profile?.suffix?.ifBlank { null })
                    .setRequiredRange(1, 10)
                    .setRequired(false)
                    .build()
            ),
            Label.of(
                "Profile Picture",
                if (profile != null) "Leave blank to keep the same profile picture, otherwise attach an image to change it." else null,
                AttachmentUpload.create("avatar")
                    .setMaxValues(1)
                    .setRequired(profile == null)
                    .build()
            )
        ).build()

    return modal
}