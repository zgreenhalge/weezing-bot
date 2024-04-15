package commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

enum class SlashCommand {
    SKRONK { override val command = Skronk.Companion }

    ;

    abstract val command: ISlashCommand

    fun definition(): SlashCommandData {
        return command.definition
    }

    fun getName(): String {
        return command.commandName
    }

    fun process(event: SlashCommandInteractionEvent) {
        command.process(event)
    }

    companion object {
        fun fromString(value: String): SlashCommand? {
            for(command in entries){
                if(command.getName() == value){
                    return command
                }
            }
            return null
        }
    }
}