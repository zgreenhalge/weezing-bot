package adapters

import commands.Skronk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command

private val logger = KotlinLogging.logger {}

class SlashCommandAdapter: ListenerAdapter() {

    // Companion objects are used in Kotlin in place of static methods or classes
    // See https://kotlinlang.org/docs/object-declarations.html#companion-objects
    // And https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields
    companion object {
        fun updateCommands(client: JDA): List<Command> {
            return client.updateCommands().addCommands(
                Skronk.definition
            ).complete()
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        logger.info { "Received event ${event.fullCommandName} ${event.options}" }
        GlobalScope.launch {
            when (event.name) {
                "skronk" ->  Skronk(event).process()
            }
        }
    }

}