package listeners

import mu.KotlinLogging
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.EventListener

private val logger = KotlinLogging.logger {}

class StartupShutdownListener: EventListener {

    override fun onEvent(event: GenericEvent) {
        when(event) {
            is ReadyEvent -> logger.info { "Weezing is online!" }
            is ShutdownEvent -> logger.info { "Weezing is going offline." }
        }
    }
}