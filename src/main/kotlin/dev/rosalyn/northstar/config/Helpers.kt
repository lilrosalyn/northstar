package dev.rosalyn.northstar.config

import cc.ekblad.toml.configuration.TomlMapperConfigurator
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.transcoding.TomlDecoder
import cc.ekblad.toml.util.InternalAPI
import kotlin.reflect.KClass
import kotlin.reflect.KType

typealias Decoder = Pair<KClass<*>, TomlDecoder.(KType, TomlValue) -> Any?>

@OptIn(InternalAPI::class)
fun TomlMapperConfigurator.use(decoder: Decoder) {
    this.decoder(kClass = decoder.first, decoder = decoder.second)
}

fun <T> TomlValue.value(): T {
    return when (this) {
        is TomlValue.List -> elements
        is TomlValue.Map -> properties
        is TomlValue.Bool -> value
        is TomlValue.Double -> value
        is TomlValue.Integer -> value
        is TomlValue.LocalDate -> value
        is TomlValue.LocalDateTime -> value
        is TomlValue.LocalTime -> value
        is TomlValue.OffsetDateTime -> value
        is TomlValue.String -> value
    } as T
}