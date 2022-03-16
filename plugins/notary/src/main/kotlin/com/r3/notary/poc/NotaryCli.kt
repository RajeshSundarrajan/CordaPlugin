package com.r3.notary.poc

import com.r3.notary.poc.cli.DoubleSpend
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine


class NotaryCli(wrapper: PluginWrapper) : Plugin(wrapper) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(NotaryCli::class.java)
    }

    override fun start() {
        logger.debug("NotaryCli.start()")
    }

    override fun stop() {
        logger.debug("NotaryCli.stop()")
    }

    @Extension
    @CommandLine.Command(
        name = "notary",
        subcommands = [DoubleSpend::class],
        description = ["[POC] Notary commands for running notary tests."]
    )
    class NotaryCliEntry : CordaCliPlugin
}