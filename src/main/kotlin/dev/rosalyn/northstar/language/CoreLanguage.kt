package dev.rosalyn.northstar.language

data class CoreLanguage(
    val about: About,
    val modules: Map<String, Map<String, Field>>
) {
    data class About(
        val info: String,
        val configuration: String,
        val adminsOnly: String,
        val discord: String,
        val github: String
    )

    data class Field(
        val name: String,
        val description: String
    )
}