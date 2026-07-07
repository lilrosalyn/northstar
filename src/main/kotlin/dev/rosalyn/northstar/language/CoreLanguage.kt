package dev.rosalyn.northstar.language

data class CoreLanguage(
    val modules: Map<String, Map<String, Field>>
) {
    data class Field(
        val name: String,
        val description: String
    )
}