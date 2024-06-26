import adapters.SlashCommandAdapter
import listeners.StartupShutdownListener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import sun.misc.Signal

var client: JDA? = null
var botUser: User? = null

/**
 * Entry point to the application.
 * Initializes the Java Discord API object (see https://github.com/discord-jda/JDA)
 * Expects the auth token to be passed in as an arg
 */
fun main(args: Array<String>) {
    // Register the method to handle SIGINT (ctrl+c, ctrl+x, etc)
    Signal.handle(Signal("INT")) {
        shutdown()
    }

    // This will add coroutine names to the thread names while a coroutine is using the thread!
    System.setProperty("kotlinx.coroutines.debug", "on")

    // Create the API client with the token and set our activity
    val builder: JDABuilder = JDABuilder
        .create(getToken(args), GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
        .setActivity(Activity.watching("MY ASS"))

    //Add our listeners & adapters!
    // This is where all our functionality comes from
    builder.addEventListeners(
        StartupShutdownListener(),
        SlashCommandAdapter()
    )

    client = builder.build()
    botUser = client!!.selfUser
    SlashCommandAdapter.updateCommands(client!!)
}

fun getToken(args: Array<String>): String {
    val token = if(args.isEmpty())
        System.getProperty("authToken")
    else args[0]

    if(token.isNullOrEmpty())
        throw Exception("No auth token provided for the client!")

    return token
}

/**
 * All operations required to gracefully shutdown
 */
fun shutdown() {
    client?.let {
        SlashCommandAdapter.clearCommands(it)
        it.shutdown()
    }
}