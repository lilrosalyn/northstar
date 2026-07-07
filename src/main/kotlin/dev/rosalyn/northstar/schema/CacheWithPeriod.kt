package dev.rosalyn.northstar.schema

import dev.rosalyn.northstar.scope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.milliseconds

const val defaultCachePeriod = 1000L * 60 * 60 * 3

class CacheWithPeriod<K : Any, V : Any>(
    val expiryPeriod: Long = defaultCachePeriod
) {
    val cache = mutableMapOf<K, V>()

    init {
        scope.launch {
            while (true) {
                delay(expiryPeriod.milliseconds)
                cache.clear()
            }
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableMap<K, V> {
        return cache
    }
}