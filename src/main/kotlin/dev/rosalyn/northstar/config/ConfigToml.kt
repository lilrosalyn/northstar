package dev.rosalyn.northstar.config

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import java.io.File
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.name

lateinit var configToml: ConfigToml; private set

data class ConfigToml(
    val token: String,
    val postgresUrl: String,
    val masterGuildId: Long
)

fun loadConfigToml() {
    val file = File("config.toml").toPath()
    val mapper = tomlMapper {}

    if (!file.exists()) {
        val inputStream = ConfigToml::class.java.getResourceAsStream("/${file.name}")!!
        Files.copy(inputStream, file)
        inputStream.close()
    }

    configToml = mapper.decode(file)
}