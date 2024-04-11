package adapters

import commands.SkronkCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class SlashCommandAdapter: ListenerAdapter() {

    // Companion objects are used in Kotlin in place of static methods or classes
    // See https://kotlinlang.org/docs/object-declarations.html#companion-objects
    // And https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields
    companion object {

        fun updateCommands(client: JDA) {
            client.updateCommands().addCommands(
                SkronkCommand.definition
            )
        }

    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when(event.name) {
            "skronk" -> SkronkCommand(event).process()
        }
    }

}