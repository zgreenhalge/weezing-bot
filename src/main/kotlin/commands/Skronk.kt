package commands

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}

class Skronk(private val event: SlashCommandInteractionEvent) {

    companion object: ISlashCommand {
        override val commandName = "skronk"
        private const val OPT_NAME = "name"
        private const val OPT_MESSAGE = "message"
        private const val OPT_TIMEOUT = "timeout"
        private const val ROLE_NAME = "SKRONK'd"
        private const val DEFAULT_TIMEOUT = 60L //In seconds

        private val SKRONK_TIMES = mutableMapOf<Long, Long>()

        override val definition = Commands.slash(commandName, "Skronk a user")
            .setGuildOnly(true)
            .addOption(OptionType.USER, OPT_NAME, "The user to skronk", true)
            .addOption(OptionType.STRING, OPT_MESSAGE, "The reason for the skronking")
            .addOption(OptionType.STRING, OPT_TIMEOUT, "How long to skronk")

        /**
         * Spins off processing of Skronk into a new, GlobalScope coroutine
         * This allows us to sleep until the role should be removed
         * but also means we are responsible for making sure the coroutine actually ends
         */
        @OptIn(DelicateCoroutinesApi::class)
        override fun process(event: SlashCommandInteractionEvent) {
            GlobalScope.launch(CoroutineName(event.commandString)) {
                Skronk(event).execute()
            }
        }
    }

    private val targetUser = event.getOption(OPT_NAME, OptionMapping::getAsUser)!!
    private val guild = event.guild
    private val skronkd = getRole(guild)
    private var timeoutDuration = -1L
    private val lock = Mutex() //Used to control access to SKRONK_TIMES

    /**
     * The main execution block for skronk.
     * Checks if the skronking can happen, determines the targets, then does the skronking
     */
    private suspend fun execute() {
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
                executeSkronking(skronker, "YA TRYNA SKRONK ME?!?")
            } else {
                executeSkronking(skronkee, event.getOption(OPT_MESSAGE, OptionMapping::getAsString))
            }
        }
    }

    /**
     *  Apply the skronk, then start removal loop if required
     */
    private suspend fun executeSkronking(skronkee: Member, message: String?) {
        // If there is already an entry, there is another coroutine waiting to remove already
        var shouldRemove = true
        safeAccess(SKRONK_TIMES) {
            shouldRemove = !SKRONK_TIMES.contains(skronkee.idLong)
        }

        applySkronk(skronkee, message)
        if (shouldRemove) {
            removeSkronk(skronkee, getTimeout())
        } else logger.debug { "Skronk removal was not queued" }
    }

    /**
     * Calculate skronk duration, then add the role to the passed member and send a sassy reply
     */
    private suspend fun applySkronk(skronkee: Member, reason: String?) {
        logger.debug { "Attempting to apply skronk" }

        // Cumulative skronk calculation
        var duration: Long = getTimeout()
        safeAccess(SKRONK_TIMES) {
            SKRONK_TIMES[skronkee.idLong]?.let {
                duration += it
                logger.debug { "Extending duration of skronk for ${skronkee.effectiveName} (by ${it}s)" }
            }
            SKRONK_TIMES[skronkee.idLong] = duration
        }

        // Build the response message
        val msgBuilder = StringBuilder()
        msgBuilder.append("GET SKRONK'D ${skronkee.asMention}")
        if (reason != null) {
            msgBuilder.appendLine()
            msgBuilder.append("$reason")
        }
        msgBuilder.appendLine()
        msgBuilder.append("(See you in ${formatTime(duration)})")

        //Both guild and role should be resolved by now
        guild!!.addRoleToMember(skronkee, skronkd!!).queue()

        logger.debug { "${skronkee.effectiveName} is skronk'd for ${formatTime(duration)}" }
        event.reply(msgBuilder.toString()).queue()
    }

    /**
     * Waits for TIMEOUT milliseconds and then calculates remaining skronk duration.
     * If duration <= 0, the role is removed
     */
    private suspend fun removeSkronk(skronkee: Member, sleep: Long) {
        var duration: Long = sleep
        val guild = skronkee.guild

        logger.debug { "Sleeping for ${formatTime(sleep)}" }
        delay(sleep*1000)
        safeAccess(SKRONK_TIMES) {
            duration = (SKRONK_TIMES[skronkee.idLong] ?: 0) - sleep
            if(duration > 0) {
                logger.debug { "${skronkee.effectiveName} has ${formatTime(duration)} of skronk left (after ${sleep}s sleep)" }
                SKRONK_TIMES[skronkee.idLong] = duration
            } else {
                logger.debug { "${skronkee.effectiveName} is released from skronk!" }
                SKRONK_TIMES.remove(skronkee.idLong)
            }
        }

        if (duration <= 0) {
            guild.removeRoleFromMember(skronkee, skronkd!!).queue()
        } else {
            // Recurse & sleep for remaining time
            removeSkronk(skronkee, duration)
        }
    }

    /**
     * Get the timeout
     * If 'timeout' option is passed, use Duration to parse the string and then turn to seconds
     * If parsing fails, try to interpret the option as raw seconds
     * Uses the DEFAULT_TIMEOUT value if all else fails, or the option is not provided
     */
    private fun getTimeout(): Long {
        // Short-circuit if we've gotten the timeout for this event already
        if(timeoutDuration > 0)
            return timeoutDuration

        val durationStr = event.getOption(OPT_TIMEOUT, OptionMapping::getAsString) ?: DEFAULT_TIMEOUT.toString()
        timeoutDuration = try {
            Duration.parse(durationStr).inWholeSeconds
        } catch(iae: IllegalArgumentException) {
            if(iae.message?.contains("Invalid duration string format") == true) {
                durationStr.toLong()
            } else {
                logger.warn("Could not determine timeout, using DEFAULT_TIMEOUT=$DEFAULT_TIMEOUT", iae)
                DEFAULT_TIMEOUT
            }
        }

        if(timeoutDuration <= 0){
            logger.warn { "Resolved an inappropriate timeout duration: $timeoutDuration, using DEFAULT_TIMEOUT=$DEFAULT_TIMEOUT" }
            timeoutDuration = DEFAULT_TIMEOUT
        }

        return timeoutDuration
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

    /**
     * Convert raw seconds to a pretty value
     */
    private fun formatTime(timeInSeconds: Long): String {
        val duration = timeInSeconds.toDuration(DurationUnit.SECONDS)
        return duration.toString()
    }
}