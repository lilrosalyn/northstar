package dev.rosalyn.northstar

import dev.rosalyn.northstar.config.configToml
import dev.rosalyn.northstar.config.loadConfigToml
import dev.rosalyn.northstar.event.EventInteraction
import dev.rosalyn.northstar.event.GuildCleanupEvent
import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.northstar.feature.FeatureBase
import dev.rosalyn.northstar.feature.FeatureInitializer
import dev.rosalyn.northstar.feature.FeatureInteractionType
import dev.rosalyn.northstar.interaction.MessageConfig
import dev.rosalyn.northstar.interaction.cleanupOldHandlers
import dev.rosalyn.northstar.language.loadLocales
import dev.rosalyn.northstar.schema.editGuild
import dev.rosalyn.northstar.schema.editMember
import dev.rosalyn.northstar.schema.getGuild
import dev.rosalyn.northstar.schema.getMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.reflect.jvm.isAccessible

val logger: Logger = LoggerFactory.getLogger("Northstar")
val scope = CoroutineScope(Dispatchers.IO)
lateinit var jda: JDA; private set
lateinit var commando: Commando; private set

fun main() {
    loadLocales()
    loadConfigToml()
    connectDatabase()
    scope.launch { cleanupOldHandlers() }
    jda = JDABuilder.createLight(configToml.token, listOf(
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.MESSAGE_CONTENT,
        GatewayIntent.GUILD_INVITES
    )).build()

    commando = Commando()
    commando.interactionRegistry.register(FeatureInteractionType(commando, jda))
    commando.interactionRegistry.register(EventInteraction(commando))
    commando.register("dev.rosalyn.northstar", "event", "feature")

    Runtime.getRuntime().addShutdownHook(Thread {
        for (guild in jda.guilds) {
            val event = GuildCleanupEvent(guild)
            jda.eventManager.handle(event)
        }
    })

    jda.awaitReady()

    for (guild in jda.guilds) {
        val commands = mutableListOf<CommandData>()

        scope.launch {
            val event = GuildInitializeEvent(guild, false)

            for (parseResult in commando.parsedNodes) {
                val root = parseResult.node as? FeatureBase
                val initializingNode = root?.children?.find { it is FeatureInitializer } as FeatureInitializer?
                    ?: continue

                initializingNode.function.isAccessible = true
                commands += initializingNode.function.call(event)
            }

            guild.updateCommands()
                .addCommands(commands)
                .queue()
        }
    }

    logger.info("Northstar is ready.")
}