package net.corda.cli.plugins.demoA

import org.apache.commons.lang3.StringUtils
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class ExampleNodePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {
    }

    override fun stop() {

    }

    @Extension
    @CommandLine.Command(name = "node", subcommands = [NodeStatusCommand::class, NodeAddressCommand::class])
    class WelcomeCordaCliCommand : CordaCliCommand {
        override val pluginID: String
            get() = "ExampleNodePlugin"
    }

}

@CommandLine.Command(name = "status", description = ["Prints the status of the connected node."])
class NodeStatusCommand(): Runnable {
    override fun run() {
        println("Status: Connected")
    }
}

@CommandLine.Command(name = "address", description = ["Prints the address of the connected node."])
class NodeAddressCommand(): Runnable {
    override fun run() {
        println("Address: 1.1.1.1")
    }
}