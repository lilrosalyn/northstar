package dev.rosalyn.northstar.event

import dev.rosalyn.northstar.Commando
import dev.rosalyn.northstar.feature.FeatureEvent
import dev.rosalyn.northstar.jda
import dev.rosalyn.commando.common.node.Node
import dev.rosalyn.commando.common.parser.InteractionType
import dev.rosalyn.commando.common.parser.handle.FunctionHandle
import net.dv8tion.jda.api.events.Event
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Listener

data class EventContext(
    val function: KFunction<*>,
    val eventType: KClass<out Event>,
)

class EventInteraction(
    private val commando: Commando
) : InteractionType<Nothing?, Nothing?>() {
    override fun testParent(parent: KClass<*>): Result<Nothing?> {
        if (parent.java.annotations.none { it.annotationClass == Listener::class })
            return Result.failure(IllegalStateException("Class doesn't have a listener annotation"))

        return Result.success(null)
    }

    override fun testFunction(parent: KClass<*>, handle: FunctionHandle): Result<Nothing?> {
        val reflector = handle.reflector

        if (reflector.parameters.size != 1)
            return Result.failure(IllegalStateException("Function '${reflector.name}' needs exactly one parameter"))

        if (!reflector.javaMethod!!.parameterTypes.first().kotlin.isSubclassOf(Event::class))
            return Result.failure(IllegalStateException("Function '${reflector.name}' doesn't have an Event as the first parameter."))

        return Result.success(null)
    }

    override fun createRootNode(parent: KClass<*>): Node<Nothing?> {
        return Node(commando, null, parent.jvmName, null)
    }

    override fun parse(root: Node<Nothing?>, parent: KClass<*>, handle: FunctionHandle) {
        @Suppress("UNCHECKED_CAST")
        val eventType = handle.parameters[0].first.type.kotlin as KClass<out Event>
        val node = FeatureEvent(commando, handle.name, eventType, handle.reflector, root)
        root.children += node
    }

    override fun execute(root: Node<Nothing?>, context: Nothing?): Result<Nothing?> {
        return Result.failure(IllegalStateException("Execute should not be called on EventInteraction"))
    }

    override fun postParse(root: Node<Nothing?>) {
        for (node in root.children) {
            when (node) {
                is FeatureEvent -> {
                    jda.addEventListener(node)
                }
            }
        }
    }
}