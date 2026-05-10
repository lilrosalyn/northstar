package dev.rosalyn.northstar.lib

import kotlin.time.Clock

enum class TimeUnit(val scale: Long, val displayName: String) {
    Seconds(1, "second"),
    Minutes(60, "minute"),
    Hours(3600, "hour"),
    Days(86400, "day"),
    Weeks(604800, "week"),
    Months(2592000, "month"),
    Years(31536000, "year")
}

/**
 * @param time A unix timestamp in seconds.
 * @return The time elapsed since `time`.
 */
fun calculateAge(time: Long): String {
    val now = Clock.System.now().epochSeconds
    val difference = now - time

    val largestUnit = TimeUnit.entries.asReversed().find { difference / it.scale > 0 } ?: TimeUnit.Seconds
    val value = difference / largestUnit.scale
    val name = largestUnit.displayName + if (value != 1L) "s" else ""

    return "$value $name"
}