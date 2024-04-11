package commands

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private val logger = KotlinLogging.logger {}

class SkronkCommand(val event: SlashCommandInteractionEvent) {

    companion object {
        private val OPTION_NAME = "name"
        private val OPTION_MSG = "message"
        private val ROLE_NAME = "SKRONK'd"
        private val TIMEOUT = 300000L
        val definition = Commands.slash("skronk", "Skronk a user")
            .setGuildOnly(true)
            .addOption(OptionType.USER, OPTION_NAME, "The user to skronk", true)
            .addOption(OptionType.STRING, OPTION_MSG, "The reason for the skronking")
    }

    private val SKRONK_TIMES = mutableMapOf<Long, Long>()
    val targetUser = event.getOption(OPTION_NAME, OptionMapping::getAsUser)!!
    val guild = event.guild
    val skronkd = getRole(guild)

    fun process() = runBlocking {
        guild?.getMember(targetUser)?.let { skronkee ->
            val skronker = guild.getMember(event.user)

            //If there is no skronkd role, leave
            if(skronkd == null)
                return@let

            // If skronker is skronk'd, they can't skronk
            if(skronker!!.roles.contains(skronkd)) {
                event.reply("Can't skronk if you're skronk'd!")
                return@let
            }

            // Otherwise, give the skronk'd role to targetMember
            applySkronk(skronkee, event.getOption(OPTION_MSG, OptionMapping::getAsString))

            launch {
                // this block executes asynchronously~!
                delay(TIMEOUT)
                removeSkronk(skronkee, skronkd)
            }
        } ?: {
            logger.warn { "Unable to skronk  ${targetUser.effectiveName} outside of a guild" }
        }
    }

    private fun applySkronk(
        skronkee: Member,
        reason: String?
    ) {
        // Cumulative skronk calculation
        SKRONK_TIMES[skronkee.idLong] = if(SKRONK_TIMES.contains(skronkee.idLong)) {
             SKRONK_TIMES[skronkee.idLong]!! + TIMEOUT
        } else TIMEOUT

        logger.info { "${skronkee.effectiveName} is up to ${SKRONK_TIMES[skronkee.idLong]}ms of skronk" }

        val msgBuilder = StringBuilder("GET SKRONK'D ${skronkee.asMention}")
        if (reason != null) {
            msgBuilder.appendLine()
            msgBuilder.append("$reason")
        }
        msgBuilder.appendLine()
        msgBuilder.append("(See you in ${SKRONK_TIMES[skronkee.idLong]} seconds)")

        //Both guild and role should be resolved by now
        guild!!.addRoleToMember(skronkee, skronkd!!)
        event.reply(msgBuilder.toString()).queue()
    }

    private fun removeSkronk(skronkee: Member, skronkd: Role) {
        val guild = skronkee.guild
        val timeLeft = (SKRONK_TIMES[skronkee.idLong] ?: 0) - TIMEOUT

        if(timeLeft <= 0) {
            SKRONK_TIMES.remove(skronkee.idLong)
            guild.removeRoleFromMember(skronkee, skronkd)
        } else {
            logger.info { "${skronkee.effectiveName} has ${timeLeft}ms of skronk left" }
            SKRONK_TIMES[skronkee.idLong] = timeLeft
        }
    }

    private fun getRole(guild: Guild?): Role? {
        if(guild == null) {
            logger.warn { "Unable to get skronk role, event is not in a guild" }
            return null
        }

        val roles = guild.getRolesByName(ROLE_NAME, true)
        return if (roles.isEmpty()) {
            logger.warn("$ROLE_NAME role in ${guild.name} not found!")
            null
        } else roles[0]
    }
}