package dev.rosalyn.northstar.lib.component

import dev.rosalyn.northstar.logger
import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent
import net.dv8tion.jda.api.components.section.SectionContentComponent
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.SKU
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectInteraction
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction
import net.dv8tion.jda.api.interactions.modals.ModalInteraction
import net.dv8tion.jda.api.modals.Modal
import java.net.URL
import java.util.UUID

typealias ComponentHandler = suspend Interaction.() -> Unit

interface ComponentBuilder<T> {
    fun build(): T?
}

class ContainerBuilder : ComponentBuilder<Container> {
    val children = mutableListOf<ContainerChildComponent>()

    fun textDisplay(text: String) {
        children += TextDisplay.of(text)
    }

    fun separator(isDivider: Boolean, spacing: Separator.Spacing) {
        children += Separator.create(isDivider, spacing)
    }

    fun section(block: SectionBuilder.() -> Unit) {
        val builder = SectionBuilder()
        block(builder)

        children += builder.build()
            ?: return
    }

    fun actionRow(block: ActionRowBuilder.() -> Unit) {
        val builder = ActionRowBuilder()
        block(builder)

        children += builder.build()
    }

    override fun build(): Container {
        return Container.of(children)
    }
}

class ModalBuilder(
    val title: String,
    val onSubmit: (suspend ModalInteraction.() -> Unit)?
) : ComponentBuilder<Modal> {
    val children = mutableListOf<ModalTopLevelComponent>()

    fun textDisplay(text: String) {
        children += TextDisplay.of(text)
    }

    fun textInput(
        id: String,
        style: TextInputStyle,
        title: String,
        description: String? = null,
        value: String? = null,
        placeholder: String? = null,
        lengthRange: ClosedRange<Int>? = null,
        required: Boolean = true
    ) {
        val builder = TextInput.create(id, style)
            .setValue(value)
            .setPlaceholder(placeholder)
            .setRequired(required)

        if (lengthRange != null)
            builder.setRequiredRange(lengthRange.start, lengthRange.endInclusive)

        children += Label.of(title, description, builder.build())
    }

    fun stringSelect(
        id: String,
        options: Collection<SelectOption>,
        title: String,
        description: String? = null,
        defaultValues: Collection<String> = emptyList(),
        placeholder: String? = null,
        requiredRange: ClosedRange<Int>? = null,
        required: Boolean? = null,
        disabled: Boolean = false
    ) {
        val menu = StringSelectMenu.create(id)
            .addOptions(options)
            .setDefaultValues(defaultValues)
            .setPlaceholder(placeholder)
            .setRequired(required)
            .setDisabled(disabled)

        if (requiredRange != null)
            menu.setRequiredRange(requiredRange.start, requiredRange.endInclusive)

        children += Label.of(title, description, menu.build())
    }

    fun entitySelect(
        id: String,
        vararg types: EntitySelectMenu.SelectTarget,
        title: String,
        description: String? = null,
        defaultValues: Collection<EntitySelectMenu.DefaultValue> = emptyList(),
        channelTypes: Collection<ChannelType> = emptyList(),
        placeholder: String? = null,
        requiredRange: ClosedRange<Int>? = null,
        required: Boolean? = null,
        disabled: Boolean = false
    ) {
        val menu = EntitySelectMenu.create(id, types.toList())
            .setChannelTypes(channelTypes)
            .setDefaultValues(defaultValues)
            .setPlaceholder(placeholder)
            .setRequired(required)
            .setDisabled(disabled)

        if (requiredRange != null)
            menu.setRequiredRange(requiredRange.start, requiredRange.endInclusive)

        children += Label.of(title, description, menu.build())
    }

    fun fileUpload(
        id: String,
        title: String,
        description: String? = null,
        valueRange: ClosedRange<Int>? = null,
        required: Boolean = true
    ) {
        val builder = AttachmentUpload.create(id)
            .setRequired(required)

        if (valueRange != null)
            builder.setRequiredRange(valueRange.start, valueRange.endInclusive)

        children += Label.of(title, description, builder.build())
    }

    override fun build(): Modal {
        val customID = UUID.randomUUID()

        if (onSubmit != null)
            registerHandler(customID) { onSubmit(this as ModalInteraction) }

        return Modal.create(customID.toString(), title)
            .addComponents(children)
            .build()
    }
}

class ActionRowBuilder : ComponentBuilder<ActionRow> {
    val components = mutableListOf<ActionRowChildComponent>()

    fun button(
        style: ButtonStyle,
        label: String,
        url: URL? = null,
        sku: SKU? = null,
        emoji: Emoji? = null,
        disabled: Boolean = false,
        onClick: (suspend ButtonInteraction.() -> Unit)? = null
    ) {
        if (components.isNotEmpty() && components[0] !is Button)
            return logger.warn("Tried to create action row with multiple types of components", IllegalStateException())

        if (style == ButtonStyle.LINK) {
            if (url == null)
                return logger.warn("Tried to add link button accessory with no URL", IllegalStateException())

            components += Button.of(style, url.toString(), label, emoji).withDisabled(disabled)
            return
        }

        if (style == ButtonStyle.PREMIUM) {
            if (sku == null)
                return logger.warn("Tried to add premium button accessory with no SKU", IllegalStateException())

            components += Button.of(style, sku.id, null, null).withDisabled(disabled)
            return
        }

        val customID = UUID.randomUUID()
        components += Button.of(
            style,
            customID.toString(),
            label,
            emoji
        ).withDisabled(disabled)

        if (onClick != null)
            registerHandler(customID) { onClick(this as ButtonInteraction) }
    }

    fun stringSelect(
        options: Collection<SelectOption>,
        defaultValues: Collection<String> = emptyList(),
        placeholder: String? = null,
        requiredRange: ClosedRange<Int>? = null,
        required: Boolean? = null,
        disabled: Boolean = false,
        onClick: (suspend StringSelectInteraction.() -> Unit)? = null
    ) {
        if (components.isNotEmpty() && components[0] !is StringSelectMenu)
            return logger.warn("Tried to create action row with multiple types of components", IllegalStateException())

        val customID = UUID.randomUUID()
        val menu = StringSelectMenu.create(customID.toString())
            .addOptions(options)
            .setDefaultValues(defaultValues)
            .setPlaceholder(placeholder)
            .setRequired(required)
            .setDisabled(disabled)

        if (requiredRange != null)
            menu.setRequiredRange(requiredRange.start, requiredRange.endInclusive)

        components += menu.build()

        if (onClick != null)
            registerHandler(customID) { onClick(this as StringSelectInteraction) }
    }

    fun entitySelect(
        vararg types: EntitySelectMenu.SelectTarget,
        defaultValues: Collection<EntitySelectMenu.DefaultValue> = emptyList(),
        channelTypes: Collection<ChannelType> = emptyList(),
        placeholder: String? = null,
        requiredRange: ClosedRange<Int>? = null,
        required: Boolean? = null,
        disabled: Boolean = false,
        onClick: (suspend EntitySelectInteraction.() -> Unit)? = null
    ) {
        if (components.isNotEmpty() && components[0] !is StringSelectMenu)
            return logger.warn("Tried to create action row with multiple types of components", IllegalStateException())

        val customID = UUID.randomUUID()
        val menu = EntitySelectMenu.create(customID.toString(), types.toList())
            .setChannelTypes(channelTypes)
            .setDefaultValues(defaultValues)
            .setPlaceholder(placeholder)
            .setRequired(required)
            .setDisabled(disabled)

        if (requiredRange != null)
            menu.setRequiredRange(requiredRange.start, requiredRange.endInclusive)

        components += menu.build()

        if (onClick != null)
            registerHandler(customID) { onClick(this as EntitySelectInteraction) }
    }

    override fun build(): ActionRow {
        return ActionRow.of(components)
    }
}

class SectionBuilder : ComponentBuilder<Section> {
    lateinit var accessory: SectionAccessoryComponent; private set
    val contentChildren = mutableListOf<SectionContentComponent>()

    fun buttonAccessory(
        style: ButtonStyle,
        label: String,
        url: URL? = null,
        sku: SKU? = null,
        emoji: Emoji? = null,
        disabled: Boolean = false,
        onClick: (suspend ButtonInteraction.() -> Unit)? = null
    ) {
        if (style == ButtonStyle.LINK) {
            if (url == null)
                return logger.warn("Tried to add link button accessory with no URL")

            accessory = Button.of(style, url.toString(), label, emoji).withDisabled(disabled)
            return
        }

        if (style == ButtonStyle.PREMIUM) {
            if (sku == null)
                return logger.warn("Tried to add premium button accessory with no SKU")

            accessory = Button.of(style, sku.id, null, null).withDisabled(disabled)
            return
        }

        val customID = UUID.randomUUID()
        accessory = Button.of(
            style,
            customID.toString(),
            label,
            emoji
        ).withDisabled(disabled)

        if (onClick != null)
            registerHandler(customID) { onClick(this as ButtonInteraction) }
    }

    fun textDisplay(text: String) {
        contentChildren += TextDisplay.of(text)
    }

    override fun build(): Section? {
        if (!::accessory.isInitialized) {
            logger.warn("Tried to build section with no defined accessory", IllegalStateException())
            return null
        }

        if (contentChildren.size !in 1..3) {
            logger.warn("Tried to build section with illegal amount of children (${contentChildren.size})", IllegalStateException())
            return null
        }

        return Section.of(accessory, contentChildren)
    }
}

fun makeModal(
    title: String,
    onSubmit: (suspend ModalInteraction.() -> Unit)? = null,
    block: ModalBuilder.() -> Unit
): Modal {
    val builder = ModalBuilder(title, onSubmit)
    block(builder)
    return builder.build()
}

fun makeTextDisplay(text: String): TextDisplay {
    return TextDisplay.of(text)
}

fun makeContainer(block: ContainerBuilder.() -> Unit): Container {
    val builder = ContainerBuilder()
    block(builder)
    return builder.build()
}

fun makeActionRow(block: ActionRowBuilder.() -> Unit): ActionRow {
    val builder = ActionRowBuilder()
    block(builder)
    return builder.build()
}