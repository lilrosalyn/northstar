package dev.rosalyn.northstar

import dev.rosalyn.commando.common.Commando
import dev.rosalyn.commando.common.platform.UserManager

class Commando : Commando(Commando::class) {
    override val userManager = object : UserManager<Unit>() {
        val exception = IllegalStateException()
        override fun createUser(accessor: Unit) = throw exception
    }
}