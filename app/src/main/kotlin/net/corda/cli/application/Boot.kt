package net.corda.cli.application

import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.DefaultPluginManager
import org.pf4j.ManifestPluginDescriptorFinder
import net.corda.cli.api.CordaCliCommand
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(vararg args: String) {
    Boot.runDemo(*args)
}

@CommandLine.Command(
    name = "corda"
)
class App

/**
 * A boot class that starts picocli and loads in plugin sub commands.
 */
object Boot {
    private class PluginManager(importPaths: List<Path>) : DefaultPluginManager(importPaths) {
        override fun createPluginDescriptorFinder(): CompoundPluginDescriptorFinder {
            return CompoundPluginDescriptorFinder()
                .add(ManifestPluginDescriptorFinder());
        }
    }

    fun runDemo(vararg args: String) {
        val start = System.currentTimeMillis()
        val pluginsDir = System.getProperty("pf4j.pluginsDir", "./plugins")
        // create the plugin manager
        val pluginManager = PluginManager(listOf(Paths.get(pluginsDir)))

        val loadedTime = System.currentTimeMillis()
        println("framework loaded in ${loadedTime - start}ms")
        // load the plugins
        pluginManager.loadPlugins()

        val pluginLoadedTime = System.currentTimeMillis()
        println("plugins loaded in ${pluginLoadedTime - loadedTime}ms")
        // start (active/resolved) the plugins
        pluginManager.startPlugins()

        // retrieves the extensions for Greeting extension point
        val cordaCliCommands: List<CordaCliCommand> = pluginManager.getExtensions(CordaCliCommand::class.java)

        val commandLine = CommandLine(App())
        cordaCliCommands.forEach { cordaCommand ->

            commandLine.addSubcommand(cordaCommand)
        }

        val pluginBooted = System.currentTimeMillis()
        println("plugins booted in ${pluginBooted - pluginLoadedTime}ms")

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        pluginManager.stopPlugins()
        println("total time ${System.currentTimeMillis() - start}ms")
        exitProcess(commandResult)
    }
}
