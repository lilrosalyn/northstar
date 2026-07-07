package dev.rosalyn.northstar.schema

import dev.rosalyn.northstar.jda
import dev.rosalyn.northstar.scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.replace
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private val profileCache by CacheWithPeriod<User, MutableList<RoleplayProfile>>()

object RoleplayProfiles : Table() {
    val id = integer("id").autoIncrement()
    val owner = long("owner").index()
    val name = varchar("name", 50)
    val avatar = varchar("avatar", 250)
    val prefix = varchar("prefix", 10)
    val suffix = varchar("suffix", 10).nullable()
}

object RoleplayMessages : Table() {
    val messageId = long("message_id").uniqueIndex()
    val author = long("author")
}

data class RoleplayProfile(
    val id: Int,
    val owner: User,
    var name: String,
    var avatar: String,
    var prefix: String,
    var suffix: String?
) {
    constructor(row: ResultRow) : this(
        row[RoleplayProfiles.id],
        jda.getUserById(row[RoleplayProfiles.owner])
            ?: jda.retrieveUserById(row[RoleplayProfiles.owner]).complete(),
        row[RoleplayProfiles.name],
        row[RoleplayProfiles.avatar],
        row[RoleplayProfiles.prefix],
        row[RoleplayProfiles.suffix]
    )
}

suspend fun createRoleplayProfile(
    owner: User,
    name: String,
    avatarURL: Message.Attachment,
    prefix: String,
    suffix: String?
) {
    suspendTransaction {
        val resultRow = RoleplayProfiles.insertReturning {
            it[RoleplayProfiles.owner] = owner.idLong
            it[RoleplayProfiles.name] = name
            it[RoleplayProfiles.avatar] = avatarURL.proxyUrl
            it[RoleplayProfiles.prefix] = prefix
            it[RoleplayProfiles.suffix] = suffix
        }.first()

        val cachedProfiles = profileCache[owner] ?: listRoleplayProfiles(owner)

        if (owner !in profileCache)
            profileCache[owner] = cachedProfiles
        else cachedProfiles += RoleplayProfile(resultRow)
    }
}

suspend fun editRoleplayProfile(profile: RoleplayProfile) {
    suspendTransaction {
        RoleplayProfiles.update({ RoleplayProfiles.id eq profile.id }) {
            it[RoleplayProfiles.name] = profile.name
            it[RoleplayProfiles.avatar] = profile.avatar
            it[RoleplayProfiles.prefix] = profile.prefix
            it[RoleplayProfiles.suffix] = profile.suffix
        }
    }
}

suspend fun deleteRoleplayProfile(owner: User, id: Int) {
    suspendTransaction {
        RoleplayProfiles.deleteWhere { RoleplayProfiles.id eq id and (RoleplayProfiles.owner eq owner.idLong) }

        val cachedProfiles = profileCache[owner] ?: listRoleplayProfiles(owner)

        if (owner !in profileCache)
            profileCache[owner] = cachedProfiles
        else cachedProfiles.removeIf { it.id == id }
    }
}

suspend fun listRoleplayProfiles(user: User): MutableList<RoleplayProfile> {
    profileCache[user]?.let { return it }

    val profiles = suspendTransaction {
        val profiles = RoleplayProfiles.selectAll()
            .where { RoleplayProfiles.owner eq user.idLong }
            .map(::RoleplayProfile)
            .toMutableList()

        profileCache[user] = profiles
        profiles
    }

    return profiles
}

suspend fun createRoleplayMessage(user: User, message: Message) {
    suspendTransaction {
        RoleplayMessages.insert {
            it[RoleplayMessages.author] = user.idLong
            it[RoleplayMessages.messageId] = message.idLong
        }
    }
}

suspend fun probeRoleplayMessageAuthor(message: Message): User? {
    val resultRow = suspendTransaction {
        RoleplayMessages.select(RoleplayMessages.author).where { RoleplayMessages.messageId eq message.idLong }.firstOrNull()
    } ?: return null

    val userId = resultRow[RoleplayMessages.author]
    return jda.getUserById(userId) ?: withContext(Dispatchers.IO) {
        jda.retrieveUserById(userId).complete()
    }
}