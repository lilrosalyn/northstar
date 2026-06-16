package dev.rosalyn.northstar.feature

import dev.rosalyn.northstar.event.GuildInitializeEvent
import dev.rosalyn.commando.common.Commando
import dev.rosalyn.commando.common.node.Node
import dev.rosalyn.commando.common.parser.InteractionType
import dev.rosalyn.commando.common.parser.handle.FunctionHandle
import dev.rosalyn.northstar.ModuleSettings
import dev.rosalyn.northstar.lib.component.ContainerBuilder
import dev.rosalyn.northstar.lib.handleError
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.isAccessible

@Target(AnnotationTarget.FILE)
annotation class Feature(
    val name: String,
    val description: String,
    val toggleable: Boolean = true,
    val customModule: Boolean = false
)

class FeatureBase(
    commando: Commando,
    val classRef: KClass<*>,
    context: Feature
) : Node<Feature>(commando, null, context.name, context) {
    val identifier = classRef.java.simpleName
    val moduleConfig = classRef.java.declaredFields.find { it.name == "moduleConfig" }?.let {
        it.isAccessible = true
        it.get(null) as KClass<out ModuleSettings>
    }
}

class FeatureInitializer(
    commando: Commando,
    name: String,
    val function: KFunction<List<CommandData>>,
    parent: FeatureBase
) : Node<Feature>(commando, parent, name, parent.context) {
    val isGlobal = (function.parameters[0].type.classifier as KClass<*>) == ReadyEvent::class
}

class FeatureSettingsHook(
    commando: Commando,
    name: String,
    val function: KFunction<*>,
    parent: FeatureBase
) : Node<Feature>(commando, parent, name, parent.context)

class FeatureEvent<C>(
    commando: Commando,
    name: String,
    val event: KClass<out Event>,
    val function: KFunction<*>,
    parent: Node<C>
) : Node<C>(commando, parent, name, parent.context), EventListener {
    override fun onEvent(event: GenericEvent) {
        if (this.event.java.isAssignableFrom(event::class.java)) {
            function.isAccessible = true

            try {
                if (function.isSuspend)
                    scope.launch {
                        try {
                            function.callSuspend(event)
                        } catch (exception: Exception) {
                            handleError(event, exception)
                        }
                    }
                else function.call(event)
            } catch (exception: Exception) {
                handleError(event, exception)
            }
        }
    }
}

class FeatureInteractionType(val commando: Commando, val jda: JDA) : InteractionType<Unit, Feature>() {
    override fun createRootNode(parent: KClass<*>): Node<Feature> {
        val context = parent.java.getAnnotation(Feature::class.java)
        return FeatureBase(commando, parent, context)
    }

    override fun parse(root: Node<Feature>, parent: KClass<*>, handle: FunctionHandle) {
        val name = handle.name

        if (handle.name == "initializeModule") {
            root.children += FeatureInitializer(commando, name, handle.reflector as KFunction<List<CommandData>>, root as FeatureBase)
            return
        }

        if (handle.name == "hookSettings") {
            root.children += FeatureSettingsHook(commando, name, handle.reflector, root as FeatureBase)
            return
        }

        val event = handle.parameters[0].first.type.kotlin as KClass<out Event>

        root.children += FeatureEvent(commando, name, event, handle.reflector, root as FeatureBase)
    }

    override fun postParse(root: Node<Feature>) {
        for (node in root.children) {
            when (node) {
                is FeatureEvent -> {
                    jda.addEventListener(node)
                }
            }
        }
    }

    override fun execute(root: Node<Feature>, context: Unit) = Result.success(null)

    override fun testParent(parent: KClass<*>): Result<Nothing?> {
        if (parent.java.annotations.none { it.annotationClass == Feature::class })
            return Result.failure(IllegalArgumentException("Missing @Feature()"))

        return Result.success(null)
    }

    override fun testFunction(parent: KClass<*>, handle: FunctionHandle): Result<Nothing?> {
        if (handle.name == "initializeModule") {
            val parameters = handle.parameters.size == 1 && (GuildInitializeEvent::class.java.isAssignableFrom(handle.parameters[0].first.type)
                    || ReadyEvent::class.java.isAssignableFrom(handle.parameters[0].first.type))

            if (!parameters)
                return Result.failure(IllegalArgumentException("Bad parameters"))

            if (handle.reflector.returnType != List::class.createType(listOf(KTypeProjection(KVariance.INVARIANT,
                    CommandData::class.createType()))))
                return Result.failure(IllegalArgumentException("Bad return type (${handle.reflector.returnType})"))

            return Result.success(null)
        }

        if (handle.name == "hookSettings") {
            val parameters = handle.parameters.size == 4 && ContainerBuilder::class.java == handle.parameters[0].first.type
                    && Interaction::class.java.isAssignableFrom(handle.parameters[1].first.type)
                    && SettingsStage::class.java == handle.parameters[3].first.type

            if (!parameters)
                return Result.failure(IllegalArgumentException("Bad parameters"))

            return Result.success(null)
        }

        val parameters = handle.parameters.size == 1 && Event::class.java.isAssignableFrom(handle.parameters[0].first.type)

        if (!parameters)
            return Result.failure(IllegalArgumentException("Bad parameters"))

        return Result.success(null)
    }
}