package dev.rosalyn.northstar

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import kotlin.reflect.KClass

typealias SerializableChannel = @Serializable(with = GuildChannelSerializer::class) GuildChannel
typealias SerializableRole = @Serializable(with = RoleSerializer::class) Role

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    ignoreUnknownKeys = true
}

object GuildChannelSerializer : KSerializer<GuildChannel> {
    override val descriptor = PrimitiveSerialDescriptor("dev.rosalyn.northstar.Channel", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: GuildChannel) {
        val id = value.idLong
        encoder.encodeLong(id)
    }

    override fun deserialize(decoder: Decoder): GuildChannel {
        val id = decoder.decodeLong()
        return jda.getGuildChannelById(id)
            ?: throw SerializationException("Unknown channel id: $id")
    }
}

object RoleSerializer : KSerializer<Role> {
    override val descriptor = PrimitiveSerialDescriptor("dev.rosalyn.northstar.Role", PrimitiveKind.LONG)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Role) {
        val id = value.idLong
        encoder.encodeLong(id)
    }

    override fun deserialize(decoder: Decoder): Role {
        val id = decoder.decodeLong()
        return jda.getRoleById(id)
            ?: throw SerializationException("Unknown role id: $id")
    }
}