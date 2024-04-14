package adapters

import commands.Skronk
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

private val logger = KotlinLogging.logger {}

class SlashCommandAdapter: ListenerAdapter() {

    // Companion objects are used in Kotlin in place of static methods or classes
    // See https://kotlinlang.org/docs/object-declarations.html#companion-objects
    // And https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields
    companion object {

        private val COMMANDS = listOf(
            Skronk.definition
        )

        fun updateCommands(client: JDA) {
            for(command in COMMANDS) {
                logger.debug { "Adding slash command ${command.name} (${command.description})" }
            }
            client.updateCommands().addCommands(COMMANDS).complete()
        }

        fun clearCommands(client: JDA) {
            for(command in client.retrieveCommands().complete()) {
                logger.debug { "Deleting $command" }
                client.deleteCommandById(command.idLong).complete()
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        logger.info { "Received event ${event.commandString}" }
        GlobalScope.launch(CoroutineName(event.commandString)) {
            try {
                when (event.name) {
                    "skronk" -> Skronk(event).process()
                }
            } catch(e: Exception) {
                event.reply(":x: Ran into a(n) *${e::class.simpleName}*").queue()
                logger.error("Exception when trying to execute ${event.commandString}:", e)
            }
        }
    }

}