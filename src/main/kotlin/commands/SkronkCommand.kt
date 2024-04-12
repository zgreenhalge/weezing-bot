package commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

class SkronkCommand(private val event: SlashCommandInteractionEvent) {

    companion object {
        private const val OPTION_NAME = "name"
        private const val OPTION_MSG = "message"
        private const val ROLE_NAME = "SKRONK'd"
        private const val TIMEOUT = 300000L

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

    /**
     * The main execution block for skronk.
     * Checks if the skronking can happen, determines the targets, then spins off any coroutines needed
     */
    fun process() = runBlocking {
        //If there is no skronkd role, leave
        if(skronkd == null){
            logger.warn { "Unable to skronk, as no role is found" }
            return@runBlocking
        }

        guild?.let { guild ->
            guild.loadMembers() //Required to cache all members of the guild
            val skronker = guild.getMember(event.user)!!
            val skronkee = guild.getMember(targetUser)!!

            // If skronker is skronk'd, they can't skronk
            if(skronker.roles.contains(skronkd)) {
                event.reply("Can't skronk if you're skronk'd!")
            } else if(skronkee.idLong == event.jda.selfUser.idLong) {
                event.reply("YOU TRYNA SKRONK ME?!?").queue()
                executeSkronking(this, skronker, skronkd)
            } else {
                executeSkronking(this, skronkee, skronkd)
            }
        }
    }

    /**
     *  This function executes asynchronously~!
     */
    private fun executeSkronking(coroutineScope: CoroutineScope, skronkee: Member, skronkd: Role) = coroutineScope.launch {
        var shouldRemove = true
        safeAccess(SKRONK_TIMES) {
            shouldRemove = !SKRONK_TIMES.contains(skronkee.idLong)
        }

        applySkronk(skronkee, event.getOption(OPTION_MSG, OptionMapping::getAsString))
        if (shouldRemove) {
            removeSkronk(skronkee, skronkd)
        }
    }

    /**
     * Calculate skronk duration, then add the role to the passed member and send a sassy reply
     */
    private suspend fun applySkronk(skronkee: Member, reason: String?) {
        // Cumulative skronk calculation
        var duration: Long = TIMEOUT
        safeAccess(SKRONK_TIMES) {
            if(SKRONK_TIMES.contains(skronkee.idLong)) {
                duration = SKRONK_TIMES[skronkee.idLong]!! + TIMEOUT
            }
            SKRONK_TIMES[skronkee.idLong] = duration
        }

        // Build the response message
        val msgBuilder = StringBuilder("GET SKRONK'D ${skronkee.asMention}")
        if (reason != null) {
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
    private suspend fun removeSkronk(skronkee: Member, skronkd: Role) {
        var timeLeft = TIMEOUT
        val guild = skronkee.guild

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
            guild.removeRoleFromMember(skronkee, skronkd).queue()
        } else {
            removeSkronk(skronkee, skronkd)
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
     * Safely access some arbitrary resources.
     * @param obj - Any Object to obtain the lock against
     * @param block - A code block to execute inside the lock
     */
    private suspend fun safeAccess(obj: Any, block: () -> Unit) {
        lock.lock(obj)
        try {
           block.invoke()
        } finally {
            lock.unlock(obj)
        }
    }
}