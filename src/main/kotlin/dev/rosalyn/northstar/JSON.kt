package dev.rosalyn.northstar

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel

typealias SerializableChannel = @Serializable(with = GuildChannelSerializer::class) GuildChannelPartial
typealias SerializableRole = @Serializable(with = RoleSerializer::class) RolePartial

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    ignoreUnknownKeys = true
}

data class GuildChannelPartial(val id: Long) : Partial<GuildChannel>() {
    override fun fetch() = jda.getGuildChannelById(id)
}

object GuildChannelSerializer : KSerializer<GuildChannelPartial> {
    override val descriptor = PrimitiveSerialDescriptor("dev.rosalyn.northstar.Channel", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: GuildChannelPartial) {
        val id = value.id
        encoder.encodeLong(id)
    }

    override fun deserialize(decoder: Decoder): GuildChannelPartial {
        val id = decoder.decodeLong()
        return GuildChannelPartial(id)
    }
}

data class RolePartial(val id: Long) : Partial<Role>() {
    override fun fetch() = jda.getRoleById(id)
}

abstract class Partial<T : Any> {
    private var cachedValue: T? = null

    fun get(): T? {
        if (cachedValue != null)
            return cachedValue

        val value = fetch()
        cachedValue = fetch()
        return value
    }

    abstract fun fetch(): T?
}

object RoleSerializer : KSerializer<RolePartial> {
    override val descriptor = PrimitiveSerialDescriptor("dev.rosalyn.northstar.Role", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: RolePartial) {
        val id = value.id
        encoder.encodeLong(id)
    }

    override fun deserialize(decoder: Decoder): RolePartial {
        val id = decoder.decodeLong()
        return RolePartial(id)
    }
}