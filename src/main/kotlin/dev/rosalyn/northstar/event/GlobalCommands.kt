@file:Listener

package dev.rosalyn.northstar.event

import dev.rosalyn.northstar.commando
import dev.rosalyn.northstar.feature.FeatureBase
import dev.rosalyn.northstar.feature.FeatureInitializer
import dev.rosalyn.northstar.jda
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import kotlin.reflect.jvm.isAccessible

private fun onReady(event: ReadyEvent) {
    val commands = mutableListOf<CommandData>()

    for (parseResult in commando.parsedNodes) {
        val root = parseResult.node as? FeatureBase
        val initializingNode = root?.children?.find { it is FeatureInitializer && it.isGlobal } as FeatureInitializer?
            ?: continue

        initializingNode.function.isAccessible = true
        commands += initializingNode.function.call(event)
    }

    jda.updateCommands()
        .addCommands(commands)
        .queue()
}