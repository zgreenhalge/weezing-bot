package commands

import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private val logger = KotlinLogging.logger {}

class SkronkCommand {


    companion object {
        // Do everything statically, because I'm a lazy fuck
        // This doesn't scale in the slightest if the bot is in more than one guild

        private const val OPTION_NAME = "name"
        private const val OPTION_MSG = "message"
        private const val ROLE_NAME = "SKRONK'd"

        val definition = Commands.slash("skronk", "Skronk a user")
                .setGuildOnly(true)
                .addOption(OptionType.USER, OPTION_NAME, "The user to skronk", true)
                .addOption(OptionType.STRING, OPTION_MSG, "The reason for the skronking")
        fun process(event: SlashCommandInteractionEvent) {
            val targetUser = event.getOption(OPTION_NAME, OptionMapping::getAsUser)!!
            val reason = event.getOption(OPTION_MSG, OptionMapping::getAsString) ?: ""
            val guild = event.guild

            // Short circuit if this event doesn't come from a guild
            if(guild == null) {
                logger.warn { "Cannot skronk ${targetUser.effectiveName} as event has no guild"}
                return
            }

            val targetMember = guild.getMember(targetUser)
            val sourceMember = guild.getMember(event.user)
            val skronkdRole = getRole(guild)

            // Target user will be null if they aren't a member of the guild
            // Dunno how that would happen here, but 'tis the cost of Java interop
            targetMember?.let {
                // If sourceMember is skronk'd, they can't skronk
                // If targetMember is skronk'd, they can't be skronk'd again
                // Otherwise, give the skronk'd role to targetMember
                // And make sure to kick a timer to remove the skronk!
            } ?: {
                logger.warn {
                    "Unable to resolve skronk target: ${targetUser.effectiveName}"
                }
            }
        }

        private fun getRole(guild: Guild): Role? {
            val roles = guild.getRolesByName(ROLE_NAME, true)

            return if (roles.isEmpty()) {
                logger.warn("$ROLE_NAME role in ${guild.name} not found!")
                null
            } else roles[0]
        }
    }

}