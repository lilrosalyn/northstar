package dev.rosalyn.northstar.interaction

import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Clock

private const val expiryPeriod = 60 * 30
private const val checkPeriod = 1000L * 60 * 5
val handlerRecord = mutableMapOf<UUID, Pair<Long, ComponentHandler>>()

fun registerHandler(uuid: UUID, handler: ComponentHandler) {
    val timestamp = Clock.System.now().epochSeconds
    handlerRecord[uuid] = timestamp to handler
}

suspend fun cleanupOldHandlers() {
    while (true) {
        delay(checkPeriod)
        val now = Clock.System.now().epochSeconds
        val expired = mutableListOf<UUID>()

        for ((id, handler) in handlerRecord) {
            val (timestamp) = handler

            if (now - timestamp >= expiryPeriod)
                expired += id
        }

        for (id in expired)
            handlerRecord -= id
    }
}