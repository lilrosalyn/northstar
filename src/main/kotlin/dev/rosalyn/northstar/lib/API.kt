package dev.rosalyn.northstar.lib

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse

fun Guild.tryRetrieveMember(snowflake: UserSnowflake): Member? {
    try {
        return retrieveMember(snowflake).complete()
    } catch (exception: ErrorResponseException) {
        if (exception.errorResponse != ErrorResponse.UNKNOWN_MEMBER)
            throw exception
    }

    return null
}