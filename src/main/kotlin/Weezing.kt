import adapters.SlashCommandAdapter
import listeners.StartupShutdownListener
import mu.KotlinLogging
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag

private val logger = KotlinLogging.logger {}

/**
 * Entry point to the application.
 * Initializes the Java Discord API object (see https://github.com/discord-jda/JDA)
 * Expects the auth token to be passed in as an arg
 */
fun main(args: Array<String>) {
    if(args.isEmpty())
        throw Exception("No auth token provided for the client!")

    // Create the API client with the token and set our activity
    val builder: JDABuilder = JDABuilder
        .create(args[0], GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
        .setActivity(Activity.watching("MY ASS"))

    //Add our listeners & adapters!
    // This is where all our functionality comes from
    builder.addEventListeners(
        StartupShutdownListener(),
        SlashCommandAdapter()
    )

    val jda = builder.build()
    SlashCommandAdapter.updateCommands(jda)
}