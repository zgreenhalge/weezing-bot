package commands

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private val logger = KotlinLogging.logger {}

class Skronk(private val event: SlashCommandInteractionEvent) {

    companion object {
        private const val OPTION_NAME = "name"
        private const val OPTION_MSG = "message"
        private const val ROLE_NAME = "SKRONK'd"
        private const val TIMEOUT = 60000L

        private val SKRONK_TIMES = mutableMapOf<Long, Long>()
        private val lock = Mutex() //Used to control access to SKRONK_TIMES

        val definition = Commands.slash("skronk", "Skronk a user")
            .setGuildOnly(true)
            .addOption(OptionType.USER, OPTION_NAME, "The user to skronk", true)
            .addOption(OptionType.STRING, OPTION_MSG, "The reason for the skronking")
    }

    private val targetUser = event.getOption(OPTION_NAME, OptionMapping::getAsUser)!!
    private val guild = event.guild
    private val skronkd = getRole(guild)
    private var misfireSkronk = false

    /**
     * The main execution block for skronk.
     * Checks if the skronking can happen, determines the targets, then spins off any coroutines needed
     */
    suspend fun process() {
        logger.debug { "Processing skronk request for ${targetUser.effectiveName}" }

        //If there is no skronkd role, leave
        if(skronkd == null){
            logger.warn { "Unable to skronk, as no role is found" }
            return
        }

        guild?.let { guild ->
            guild.loadMembers() //Required to cache all members of the guild
            val skronker = guild.getMember(event.user)!!
            val skronkee = guild.getMember(targetUser)!!

            // If skronker is skronk'd, they can't skronk
            if(skronker.roles.contains(skronkd)) {
                event.reply("Can't skronk if you're skronk'd!").queue()
            } else if(skronkee.effectiveName == event.jda.selfUser.effectiveName) {
                misfireSkronk = true
                executeSkronking(skronker)
            } else {
                executeSkronking(skronkee)
            }
        }
    }

    /**
     *  This function executes asynchronously~!
     */
    private suspend fun executeSkronking(skronkee: Member) {
        var shouldRemove = true
        safeAccess(SKRONK_TIMES) {
            shouldRemove = !SKRONK_TIMES.contains(skronkee.idLong)
        }

        applySkronk(skronkee, event.getOption(OPTION_MSG, OptionMapping::getAsString))
        if (shouldRemove) {
            removeSkronk(skronkee)
        } else logger.debug { "Skronk removal skipped" }
    }

    /**
     * Calculate skronk duration, then add the role to the passed member and send a sassy reply
     */
    private suspend fun applySkronk(skronkee: Member, reason: String?) {
        logger.debug { "Attempting to apply skronk" }

        // Cumulative skronk calculation
        var duration: Long = TIMEOUT
        safeAccess(SKRONK_TIMES) {
            if(SKRONK_TIMES.contains(skronkee.idLong)) {
                duration = SKRONK_TIMES[skronkee.idLong]!! + TIMEOUT
                logger.debug { "Extending duration of skronk for ${skronkee.effectiveName}" }
            }
            SKRONK_TIMES[skronkee.idLong] = duration
        }

        // Build the response message
        val msgBuilder = StringBuilder()
        if(misfireSkronk){
            msgBuilder.append("YOU TRYNA SKRONK ME?!?")
            msgBuilder.appendLine()
        }
        msgBuilder.append("GET SKRONK'D ${skronkee.asMention}")
        if (reason != null && !misfireSkronk) {
            msgBuilder.appendLine()
            msgBuilder.append("$reason")
        }
        msgBuilder.appendLine()
        msgBuilder.append("(See you in ${duration/1000} seconds)")

        //Both guild and role should be resolved by now
        guild!!.addRoleToMember(skronkee, skronkd!!).queue()

        logger.debug { "${skronkee.effectiveName} is skronk'd for ${duration}ms" }
        event.reply(msgBuilder.toString()).queue()
    }

    /**
     * Waits for TIMEOUT milliseconds and then calculates remaining skronk duration.
     * If duration <= 0, the role is removed
     */
    private suspend fun removeSkronk(skronkee: Member) {
        var timeLeft = TIMEOUT
        val guild = skronkee.guild

        logger.debug { "Sleeping for ${TIMEOUT}ms" }
        delay(TIMEOUT)
        safeAccess(SKRONK_TIMES) {
            timeLeft = (SKRONK_TIMES[skronkee.idLong] ?: 0) - TIMEOUT
            if(timeLeft > 0) {
                logger.debug { "${skronkee.effectiveName} has ${timeLeft}ms of skronk left" }
                SKRONK_TIMES[skronkee.idLong] = timeLeft
            } else {
                logger.debug { "${skronkee.effectiveName} is released from skronk!" }
                SKRONK_TIMES.remove(skronkee.idLong)
            }
        }

        if (timeLeft <= 0) {
            guild.removeRoleFromMember(skronkee, skronkd!!).queue()
        } else {
            //Recurse until there is no time left
            removeSkronk(skronkee)
        }
    }

    /**
     * Gets the SKRONK'D role from the guild in question. Can return null!!
     */
    private fun getRole(guild: Guild?): Role? {
        if(guild == null) {
            logger.warn { "Unable to skronk ${targetUser.effectiveName} outside of a guild" }
            return null
        }

        val roles = guild.getRolesByName(ROLE_NAME, true)
        return if (roles.isEmpty()) {
            logger.warn("$ROLE_NAME role in ${guild.name} not found!")
            null
        } else roles[0]
    }

    /**
     * Safely access some arbitrary resource.
     * @param resource - Any Object to obtain the lock against
     * @param block - A code block to execute once the lock is obtained
     */
    private suspend fun safeAccess(resource: Any, block: () -> Unit) {
        lock.lock(resource)
        try {
            block.invoke()
        } finally {
            lock.unlock(resource)
        }
    }
}