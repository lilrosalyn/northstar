package dev.rosalyn.northstar.schema

import dev.rosalyn.northstar.feature.AutoRoles
import dev.rosalyn.northstar.feature.Lock
import dev.rosalyn.northstar.feature.Partnerships
import dev.rosalyn.northstar.feature.Roleplay
import dev.rosalyn.northstar.feature.Verification
import dev.rosalyn.northstar.feature.Welcome
import dev.rosalyn.northstar.json
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.json
import net.dv8tion.jda.api.entities.Guild as JDAGuild

private val guildCache by CacheWithPeriod<JDAGuild, Guild>()

object Guilds : Table() {
    val id = long("id").uniqueIndex()
    val modules = json<Guild.Modules>("modules", json).default(Guild.Modules())
    override val primaryKey = PrimaryKey(id, name = "Guilds_id")
}

@Serializable
data class Guild(
    val id: Long,
    var modules: Modules,
    val partnerships: Map<Long, Int>? = null
) {
    @Serializable
    data class Modules(
        @SerialName("LockKt") val lock: Lock = Lock(false),
        @SerialName("PartnershipsKt") val partnerships: Partnerships = Partnerships(false),
        @SerialName("RoleplayKt") val roleplay: Roleplay = Roleplay(false),
        @SerialName("WelcomeKt") val welcome: Welcome = Welcome(false),
        @SerialName("VerificationKt") val verification: Verification = Verification(false),
        @SerialName("AutoRolesKt") val autoRoles: AutoRoles = AutoRoles(false)
    )

    constructor(resultRow: ResultRow) : this(
        resultRow[Guilds.id],
        resultRow[Guilds.modules]
    )
}

suspend fun createGuild(guild: JDAGuild): Guild {
    return suspendTransaction {
        val resultRow = Guilds.insertReturning {
            it[Guilds.id] = guild.idLong
        }.first()

        val guildData = Guild(resultRow)
        guildCache[guild] = guildData
        guildData
    }
}

suspend fun editGuild(guild: Guild) {
    suspendTransaction {
        Guilds.update({ Guilds.id eq guild.id }) {
            it[Guilds.modules] = guild.modules
        }
    }
}

suspend fun getGuild(guild: JDAGuild): Guild {
    guildCache[guild]?.let { return it }

    val resultRow = suspendTransaction {
        Guilds.selectAll().where { Guilds.id eq guild.idLong }.firstOrNull()
    } ?: return createGuild(guild)

    val guildData = Guild(resultRow)
    guildCache[guild] = guildData
    return guildData
}