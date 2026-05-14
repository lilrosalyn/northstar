@file:Feature("Settings", "Configure Northstar's settings.")
@file:Suppress("UNCHECKED_CAST")

package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.SerializableChannel
import dev.rosalyn.northstar.SerializableRole
import dev.rosalyn.northstar.commando
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.interaction.EmbedConfig
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.interaction.makeActionRow
import dev.rosalyn.northstar.interaction.makeContainer
import dev.rosalyn.northstar.interaction.makeModal
import dev.rosalyn.northstar.interaction.makeTextDisplay
import dev.rosalyn.northstar.language.translate
import dev.rosalyn.northstar.schema.Guild
import dev.rosalyn.northstar.schema.editGuild
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

enum class SettingsStage {
    Root,
    AfterButtons,
    BeforeSettings,
    AfterSettings
}

private fun initializeModule(event: GuildInitializeEvent): List<CommandData> {
    return listOf(
        Commands.slash("settings", "Configure Northstar's settings.")
            .addOption(OptionType.STRING, "module", "A specific module to configure.")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
    )
}

private suspend fun onCommand(event: SlashCommandInteractionEvent) {
    if (event.name != "settings")
        return

    val interaction = event.interaction
    val module = interaction.getOption("module")?.asString

    if (module != null) {
        val module = commando.parsedNodes.find { module in ((it.node as? FeatureBase)?.name ?: "") }?.node as? FeatureBase
            ?: return interaction.reply("An error occurred. I couldn't find the module.")
                .setEphemeral(true)
                .queue()

        return interaction.replyComponents(configureModule(interaction, module))
            .setEphemeral(true)
            .useComponentsV2()
            .queue()
    }

    interaction.replyComponents(configureModules())
        .setEphemeral(true)
        .useComponentsV2()
        .queue()
}

private fun configureModules(): Container {
    return makeContainer {
        textDisplay("""
            ### Modules
            Northstar's features are divided into modules, which can be toggled on/off and configured independently. Select a module in the drop-down below to configure it.
        """.trimIndent())

        actionRow {
            val modules = commando.parsedNodes.map { it.node }
                .filterIsInstance<FeatureBase>()
                .filter { it.context.toggleable || it.moduleConfig != null }
                .map { SelectOption.of(it.name, it.identifier) }

            stringSelect(
                modules,
                placeholder = "Configure a module",
                requiredRange = 1..1
            ) {
                val module = commando.parsedNodes.find { (it.node as? FeatureBase)?.identifier == values[0] }?.node as? FeatureBase
                    ?: return@stringSelect reply("An error occurred. I couldn't find the module.")
                        .setEphemeral(true)
                        .queue()

                editComponents(configureModule(this@stringSelect, module))
                    .useComponentsV2().queue()
            }
        }
    }
}

private suspend fun configureModule(interaction: Interaction, module: FeatureBase): Container {
    val guildData = getGuild(interaction.guild!!)
    val context = module.context
    val settingsRef = module.moduleConfig
    val settings = settingsRef?.let { module ->
        val properties = Guild.Modules::class.memberProperties.find { it.returnType.classifier == module }
        properties?.get(guildData.modules) as ModuleSettings
    }

    val hook = (module.children.find { it is FeatureSettingsHook } as FeatureSettingsHook?)?.function
    hook?.isAccessible = true

    return makeContainer {
        hook?.call(this, interaction, settings, SettingsStage.Root)
        val properties = settingsRef?.memberProperties

        textDisplay("### ${context.name}\n${context.description}")

        actionRow {
            button(ButtonStyle.SECONDARY, "Back") {
                editComponents(configureModules()).useComponentsV2().queue()
            }

            if (context.toggleable && settings != null) {
                val enabled = settings.enabled

                button(
                    if (enabled) ButtonStyle.DANGER else ButtonStyle.SUCCESS,
                    if (enabled) "Disable Module" else "Enable Module"
                ) {
                    settings.enabled = !settings.enabled
                    editButton(button.withStyle(if (settings.enabled) ButtonStyle.DANGER else ButtonStyle.SUCCESS)
                        .withLabel(if (settings.enabled) "Disable Module" else "Enable Module")).queue()

                    scope.launch {
                        editGuild(guildData)
                    }
                }
            }
        }

        hook?.call(this, interaction, settings, SettingsStage.AfterButtons)

        if (properties != null && properties.size > 1) {
            separator(true, Separator.Spacing.SMALL)
            hook?.call(this, interaction, settings, SettingsStage.BeforeSettings)
            children += generateProperties(interaction, module, guildData, settingsRef, settings!!)
            hook?.call(this, interaction, settings, SettingsStage.AfterSettings)
        }
    }
}

private fun generateProperties(
    interaction: Interaction,
    module: FeatureBase,
    guildData: Guild,
    settingsRef: KClass<*>,
    settings: Any
): List<ContainerChildComponent> {
    return makeContainer {
        val properties = settingsRef.declaredMemberProperties
        val sortedProperties = properties.sortedBy { if (it.returnType.classifier == MutableList::class) 1 else 0 }

        for (property in sortedProperties) {
            if (property !is KMutableProperty1<*, *>)
                continue

            val name = property.name

            if (name == "enabled")
                continue

            val type = property.returnType.classifier as KClass<*>
            val value = property::get.call(settings)

            val (displayName, description) = interaction.translate {
                val module = modules[settingsRef.simpleName!!.lowercase()]!!
                module[name]!!
            }

            if (type == MutableList::class) {
                val innerType = property.returnType.arguments[0].type!!.classifier as KClass<*>

                when (innerType) {
                    GuildChannel::class -> {
                        value as MutableList<SerializableChannel>
                        textDisplay("**$displayName**\n-# $description")

                        actionRow {
                            entitySelect(
                                SelectTarget.CHANNEL,
                                defaultValues = value.map { EntitySelectMenu.DefaultValue.channel(it.idLong) },
                                requiredRange = 0..25
                            ) {
                                value.clear()
                                value.addAll(values.mapNotNull { it as? GuildChannel })

                                reply("The list of channels has been updated.")
                                    .setEphemeral(true).queue()

                                editGuild(guildData)
                            }
                        }
                    }
                    Role::class -> {
                        value as MutableList<SerializableRole>
                        textDisplay("**$displayName**\n-# $description")

                        actionRow {
                            entitySelect(
                                SelectTarget.ROLE,
                                defaultValues = value.map { EntitySelectMenu.DefaultValue.role(it.idLong) },
                                requiredRange = 0..25
                            ) {
                                value.clear()
                                value.addAll(values.map { it as Role })

                                reply("The list of roles has been updated.")
                                    .setEphemeral(true).queue()

                                editGuild(guildData)
                            }
                        }
                    }
                    PartnershipsProfile::class -> {
                        value as MutableList<PartnershipsProfile>
                        textDisplay("**$displayName**\n-# $description")

                        val uuidMap = mapOf(*value.map { UUID.randomUUID() to it }.toTypedArray())

                        actionRow {
                            stringSelect(
                                listOf(
                                    SelectOption.of("Create new profile", "create_new_profile"),
                                    *uuidMap.map { (uuid, it) ->
                                        SelectOption.of(it.name, "$uuid")
                                    }.toTypedArray()
                                ),
                                placeholder = "Select a profile to edit",
                                requiredRange = 1..1,
                            ) {
                                val option = selectedOptions[0].value

                                if (option == "create_new_profile") {
                                    replyModal(makeModal("Create New Profile", onSubmit = {
                                        val name = getValue("name")!!.asString
                                        val profile = PartnershipsProfile(name)
                                        value.add(profile)

                                        editGuild(guildData)
                                        editComponents(
                                            configureModule(this, module),
                                            makeTextDisplay("A new profile named **$name** has been created.")
                                        ).useComponentsV2().queue()
                                    }) {
                                        textInput("name", TextInputStyle.SHORT, "Name",
                                            "The name of the profile. Can be changed later.")
                                    }).queue()

                                    return@stringSelect
                                }

                                val profile = uuidMap[UUID.fromString(option)]
                                    ?: return@stringSelect reply("Couldn't find that profile. This is likely a bug.")
                                        .setEphemeral(true).queue()

                                replyComponents(
                                    makeContainer {
                                        textDisplay("""
                                            ### ${profile.name}
                                            You may configure the settings of the profile here.
                                        """.trimIndent())
                                        separator(true, Separator.Spacing.SMALL)
                                        children += generateProperties(interaction, module, guildData, PartnershipsProfile::class, profile)
                                    },
                                    makeActionRow {
                                        button(ButtonStyle.DANGER, "Delete Profile") {
                                            replyComponents(
                                                makeTextDisplay("""
                                                    *This is a dangerous action!* Are you sure you want to delete the profile?
                                                    Close this message if not. Otherwise, click "Confirm."
                                                """.trimIndent()),
                                                makeActionRow {
                                                    button(ButtonStyle.DANGER, "Confirm") {
                                                        value -= profile
                                                        reply("The profile has been deleted.")
                                                            .setEphemeral(true).queue()

                                                        editGuild(guildData)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ).setEphemeral(true).useComponentsV2().queue()
                            }
                        }
                    }
                }

                continue
            }

            section {
                textDisplay("**$displayName**\n-# $description")
                buttonAccessory(ButtonStyle.PRIMARY, when (type) {
                    java.lang.Boolean::class, Boolean::class -> "Toggle"
                    else -> "Edit"
                }) {
                    when (type) {
                        java.lang.Boolean::class, Boolean::class -> {
                            property::set.call(settings, !(value as Boolean))
                            reply("**$displayName** is now set to **${if (value) "off" else "on"}**.")
                                .setEphemeral(true).queue()
                        }
                        String::class -> {
                            value as String?
                            replyModal(makeModal("Edit $name", onSubmit = {
                                val value = getValue("value")?.asString?.ifEmpty { null }
                                property::set.call(settings, value)
                                reply("**$displayName** has been edited.")
                                    .setEphemeral(true).queue()
                                editGuild(guildData)
                            }) {
                                textDisplay("-# $description")
                                textInput(
                                    "value", TextInputStyle.PARAGRAPH,
                                    "New Value", value = value, required = false
                                )
                            }).queue()
                        }
                        GuildChannel::class -> {
                            value as GuildChannel?

                            replyComponents(
                                makeTextDisplay("Select a new channel."),
                                makeActionRow {
                                    entitySelect(
                                        SelectTarget.CHANNEL,
                                        defaultValues = value?.let { listOf(EntitySelectMenu.DefaultValue.channel(it.idLong)) }
                                            ?: emptyList(),
                                        requiredRange = 1..1
                                    ) {
                                        val newValue = values[0] as GuildChannel
                                        property::set.call(settings, newValue)
                                        editComponents(
                                            makeTextDisplay("Done! The channel is now ${newValue.asMention}."),
                                            message.components[1]
                                        ).useComponentsV2().queue()
                                        editGuild(guildData)
                                    }
                                }
                            ).useComponentsV2().setEphemeral(true).queue()
                        }
                        Role::class -> {
                            value as Role?

                            replyComponents(
                                makeTextDisplay("Select a new role."),
                                makeActionRow {
                                    entitySelect(
                                        SelectTarget.ROLE,
                                        defaultValues = value?.let { listOf(EntitySelectMenu.DefaultValue.role(it.idLong)) }
                                            ?: emptyList(),
                                        requiredRange = 1..1
                                    ) {
                                        val newValue = values[0] as Role
                                        property::set.call(settings, newValue)
                                        editComponents(
                                            makeTextDisplay("Done! The role is now ${newValue.asMention}."),
                                            message.components[1]
                                        ).useComponentsV2().queue()
                                        editGuild(guildData)
                                    }
                                }
                            ).useComponentsV2().setEphemeral(true).queue()
                        }
                        MessageConfig::class -> {
                            var value = value as MessageConfig?

                            if (value == null) {
                                value = MessageConfig()
                                property::set.call(settings, value)
                            }

                            replyComponents(makeContainer {
                                textDisplay("""
                                    ### $displayName
                                    $description
                                """.trimIndent())
                                separator(true, Separator.Spacing.SMALL)
                                children += generateProperties(
                                    interaction,
                                    module,
                                    guildData,
                                    MessageConfig::class,
                                    value
                                )
                            }).setEphemeral(true).useComponentsV2().queue()
                        }
                        EmbedConfig::class -> {
                            var value = value as EmbedConfig?

                            if (value == null) {
                                value = EmbedConfig()
                                property::set.call(settings, value)
                            }

                            fun IReplyCallback.sendEmbed(edited: Boolean = false) {
                                val imageMessage = "[Click here](https://cdn.discordapp.com/attachments/1394727305573564578/1500207856694333521/image.png?ex=69f798f1&is=69f64771&hm=e2cab49e142831500fce8f461eb8133533de0bf5cb89be7dc925227e313ba56b&) to see what all fields do."

                                val embed = value.toEmbed()
                                val actionRow = makeActionRow {
                                    button(ButtonStyle.PRIMARY, "Edit Main Fields") {
                                        replyModal(makeModal("Edit Main Fields", onSubmit = {
                                            value.title = getValue("title")?.asString?.ifEmpty { null }
                                            value.description = getValue("description")?.asString?.ifEmpty { null }
                                            value.color = getValue("color")?.asString?.ifEmpty { null }
                                            sendEmbed(true)

                                            editGuild(guildData)
                                        }) {
                                            textDisplay(imageMessage)

                                            textInput(
                                                "title", TextInputStyle.SHORT,
                                                "Title", value = value.title, required = false
                                            )
                                            textInput(
                                                "description", TextInputStyle.PARAGRAPH,
                                                "Description", value = value.description, required = false
                                            )
                                            textInput(
                                                "color", TextInputStyle.SHORT,
                                                "Color", "The color of the embed (ex. #FF5555)",
                                                value = value.color, required = false
                                            )
                                        }).queue()
                                    }

                                    button(ButtonStyle.PRIMARY, "Edit Header/Footer") {
                                        replyModal(makeModal("Edit Header/Footer", onSubmit = {
                                            if (value.header == null)
                                                value.header = EmbedConfig.ProfileField()

                                            value.header!!.imageURL = getValue("headerimage")?.asString?.ifEmpty { null }
                                            value.header!!.content = getValue("headercontent")?.asString?.ifEmpty { null }

                                            if (value.footer == null)
                                                value.footer = EmbedConfig.ProfileField()

                                            value.footer!!.imageURL = getValue("footerimage")?.asString?.ifEmpty { null }
                                            value.footer!!.content = getValue("footercontent")?.asString?.ifEmpty { null }
                                            sendEmbed(true)
                                            editGuild(guildData)
                                        }) {
                                            textDisplay(imageMessage)

                                            textInput(
                                                "headerimage", TextInputStyle.SHORT,
                                                "Header Image URL", value = value.header?.imageURL, required = false
                                            )
                                            textInput(
                                                "headercontent", TextInputStyle.SHORT,
                                                "Header Content", value = value.header?.content, required = false
                                            )

                                            textInput(
                                                "footerimage", TextInputStyle.SHORT,
                                                "Footer Image URL", value = value.footer?.imageURL, required = false
                                            )
                                            textInput(
                                                "footercontent", TextInputStyle.SHORT,
                                                "Footer Content", value = value.footer?.content, required = false
                                            )
                                        }).queue()
                                    }

                                    button(ButtonStyle.PRIMARY, "Edit Images") {
                                        replyModal(makeModal("Edit Images", onSubmit = {
                                            value.thumbnail = getValue("thumbnail")?.asString?.ifEmpty { null }
                                            value.image = getValue("image")?.asString?.ifEmpty { null }
                                            sendEmbed(true)
                                            editGuild(guildData)
                                        }) {
                                            textDisplay(imageMessage)

                                            textInput(
                                                "thumbnail", TextInputStyle.SHORT,
                                                "Thumbnail URL", value = value.thumbnail, required = false
                                            )
                                            textInput(
                                                "image", TextInputStyle.SHORT,
                                                "Image URL", value = value.image, required = false
                                            )
                                        }).queue()
                                    }
                                }

                                if (edited && this is IMessageEditCallback)
                                    editMessage(if (embed != null) "The embed has been edited." else "There is no embed currently.")
                                        .setEmbeds(embed?.let { listOf(it) } ?: emptyList())
                                        .setComponents(actionRow)
                                        .queue()
                                else reply("")
                                    .setEmbeds(embed?.let { listOf(it) } ?: emptyList())
                                    .addComponents(actionRow)
                                    .setEphemeral(true)
                                    .queue()
                            }

                            sendEmbed(false)
                        }
                    }
                }
            }
        }
    }.components
}