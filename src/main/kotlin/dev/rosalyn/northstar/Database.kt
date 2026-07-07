package dev.rosalyn.northstar

import dev.rosalyn.northstar.config.configToml
import dev.rosalyn.northstar.schema.Guilds
import dev.rosalyn.northstar.schema.Members
import dev.rosalyn.northstar.schema.RoleplayMessages
import dev.rosalyn.northstar.schema.RoleplayProfiles
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

lateinit var database: Database; private set

fun connectDatabase() {
    database = Database.connect(
        "jdbc:" + configToml.postgresUrl,
        driver = "org.postgresql.Driver",
        user = "postgres"
    )

    transaction {
        SchemaUtils.create(RoleplayProfiles, RoleplayMessages, Guilds, Members)
    }

    logger.info("Database initialized.")
}