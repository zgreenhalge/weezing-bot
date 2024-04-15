package commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

interface ISlashCommand {
    val commandName: String
    val definition: SlashCommandData

    fun process(event: SlashCommandInteractionEvent)
}
