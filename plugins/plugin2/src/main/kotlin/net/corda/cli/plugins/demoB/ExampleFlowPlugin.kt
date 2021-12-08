package net.corda.cli.plugins.demoB

import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliCommand
import picocli.CommandLine

class ExampleFlowPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {

    }

    override fun stop() {
    }


    @Extension
    @CommandLine.Command(name="flow", subcommands = [StartFlowCommand::class, ListFlowsCommand::class])
    class WelcomeCordaCliCommand : CordaCliCommand {
        override val pluginID: String
            get() = "ExampleFlowPlugin"
    }
}

@CommandLine.Command(name = "start", description = ["Starts a flow on the connected node."])
class StartFlowCommand(): Runnable {
    override fun run() {
        println("Status: Connected")
    }
}

@CommandLine.Command(name = "listAvailable", description = ["Lists all flows available on the connected node."])
class ListFlowsCommand(): Runnable {
    override fun run() {
        println("flows: [")
        println("net.corda.flows.myFlow,")
        println("net.corda.flows.yourFlow,")
        println("net.corda.flows.ourFlow")
        println("]")
    }
}


