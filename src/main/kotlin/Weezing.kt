import adapters.SlashCommandAdapter
import listeners.StartupShutdownListener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity

class Weezing {

    // Not sure if this actually needs to be visible to anything else or even available
    private var jda: JDA? = null

    /**
     * Entry point to the application.
     * Initializes the Java Discord API object (see https://github.com/discord-jda/JDA)
     * Expects the auth token to be passed in as an arg
     */
    fun main(args: Array<String>) {
        if(args.isEmpty())
            throw Exception("No auth token provided for the client!")

        // Create the API client with the token and set our activity
        val builder: JDABuilder = JDABuilder.createLight(args[0])
        builder.setActivity(Activity.watching("MY ASS"))

        //Add our listeners & adapters!
        // This is where all our functionality comes from
        builder.addEventListeners(
            StartupShutdownListener(),
            SlashCommandAdapter()
        )

        jda = builder.build()

        SlashCommandAdapter.updateCommands(jda!!)
    }
}