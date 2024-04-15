package adapters

import commands.SlashCommand
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

        fun updateCommands(client: JDA) {
            val commands = SlashCommand.entries.map { command ->
                logger.debug { "Adding slash command ${command.name}" }
                command.definition()
            }
            client.updateCommands().addCommands(commands).complete()
        }

        fun clearCommands(client: JDA) {
            for(command in client.retrieveCommands().complete()) {
                logger.debug { "Deleting $command" }
                client.deleteCommandById(command.idLong).complete()
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        logger.info { "Received event [${event.commandString}]" }
        try {
            val command = SlashCommand.fromString(event.name)
            command?.process(event) // Shouldn't have to worry about null here
        } catch(e: Exception) {
            event.reply(":x: Ran into a(n) *${e::class.simpleName}*").queue()
            logger.error("Exception when trying to execute ${event.commandString}:", e)
        }
    }

}