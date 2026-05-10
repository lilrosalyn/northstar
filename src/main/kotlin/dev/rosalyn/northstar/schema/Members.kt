package dev.rosalyn.northstar.schema

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import net.dv8tion.jda.api.entities.Member as JDAMember

private val memberCache by CacheWithPeriod<JDAMember, Member>()

object Members : Table() {
    val id = long("id")
    val guildId = long("guild_id")
    val partnerships = integer("partnerships").default(0)

    init {
        uniqueIndex(id, guildId)
    }
}

data class Member(
    val id: Long,
    val guildId: Long,
    var partnerships: Int
) {
    constructor(resultRow: ResultRow) : this(
        resultRow[Members.id],
        resultRow[Members.guildId],
        resultRow[Members.partnerships]
    )
}

suspend fun createMember(member: JDAMember): Member {
    return suspendTransaction {
        val resultRow = Members.insertReturning {
            it[Members.id] = member.idLong
            it[Members.guildId] = member.guild.idLong
        }.first()

        val memberData = Member(resultRow)
        memberCache[member] = memberData
        memberData
    }
}

suspend fun editMember(member: Member) {
    suspendTransaction {
        Members.update({ Members.id eq member.id and (Members.guildId eq member.guildId) }) {
            it[Members.partnerships] = member.partnerships
        }
    }
}

suspend fun getMember(member: JDAMember): Member {
    memberCache[member]?.let { return it }

    val resultRow = suspendTransaction {
        Members.selectAll().where { Members.id eq member.idLong and (Members.guildId eq member.guild.idLong) }.firstOrNull()
    } ?: return createMember(member)

    val memberData = Member(resultRow)
    memberCache[member] = memberData
    return memberData
}