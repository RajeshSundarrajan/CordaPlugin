package com.r3.notary.poc.cli

import com.r3.notary.poc.DoubleSpendNotarisationTask
import com.r3.notary.poc.async.FlowOutcomeChecker
import com.r3.notary.poc.httprpc.client.StartFlowHttpRpcClient
import com.r3.notary.poc.httprpc.model.GenerateIssueStatesRequest
import com.r3.notary.poc.workflows.HttpSignedTransactionDigest
import com.r3.notary.poc.workflows.HttpSpendTransactionDigest
import com.r3.notary.poc.workflows.SpendNotarisationFlow
import com.r3.notary.poc.workflows.SpendNotarisationFlow.Companion.ConflictPosition
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.NetworkHostAndPort
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@CommandLine.Command(
    name = "double-spend",
    description = arrayOf(
        "",
        "Tool that attempts to reveal notarisation issues when spending the same state " +
                "twice. This will always perform a fixed number of specified initial state " +
                "spends, and will probabilistically generate a corresponding double spend for " +
                "the same state based on the specified double spend ratio.",
        "",
        "Double spends can be attempted after all initial spends (simulating spending an " +
                "already spent state), or interleaved with the corresponding initial spend " +
                "(simulating trying to spend an unspent state twice in parallel)."
    )
)
class DoubleSpend : Callable<Int> {
    @CommandLine.Parameters(
        index = "0",
        description = ["host:port"]
    )
    var networkHostAndPort: String = ""

    @CommandLine.Parameters(
        index = "1",
        description = ["The RPC username"]
    )
    var username: String = ""

    @CommandLine.Parameters(
        index = "2",
        description = ["The RPC user password"]
    )
    var password: String = ""

    @CommandLine.Parameters(
        index = "3..*",
        description = ["The host:port pairs of the nodes to execute the command against"]
    )
    var networkHostAndPorts: Array<String> = emptyArray()

    @CommandLine.Option(
        names = ["--double-spend-ratio"],
        description = ["Probability of generating a double spend for a state, default 0.0 " +
                "(no double spending)"]
    )
    var doubleSpendRatio: Double = 0.0

    @CommandLine.Option(
        names = ["--double-spend-mode"],
        description = ["When to attempt double spends, either end (after all initial spends) " +
                "or interleaved (immediately following initial spend). Default end"]
    )
    var doubleSpendMode: String = "end"

    @CommandLine.Option(
        names = ["--timeout"],
        description = ["Flow timeout in seconds, default 300 seconds"]
    )
    var timeout: Int = 300

    @CommandLine.Option(
        names = ["--number-of-spends"],
        description = ["The number of initial spend operations, default 1"]
    )
    var nrSpends: Int = 1

    @CommandLine.Option(
        names = ["--states-per-txn"],
        description = ["The number of states for each initial spend transaction, default 1"]
    )
    var nrStatesPerTxn: Int = 1

    @CommandLine.Option(
        names = ["--rng-seed"],
        description = ["Random number generator seed, default 23"]
    )
    var rngSeed: Long = 23

    @CommandLine.Option(
        names = ["--notary"],
        description = ["The notary to use, defaults to first defined in configuration"]
    )
    var notaryName: String? = null

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DoubleSpend::class.java)
    }

    private val startFlowClient = StartFlowHttpRpcClient()
    private val flowOutcomeChecker = FlowOutcomeChecker()

    /**
     * This is a function to generate issue transactions on the node.
     *
     * This function works in a synchronous manner so it will block until the generation has finished.
     */
    private fun generateIssueTxns(urls: List<NetworkHostAndPort>)
            : List<List<HttpSignedTransactionDigest>> {

        logger.info("Generating conflicting transactions.")
        return urls.map {
            logger.debug("Generating transactions for host: $it")

            val generateRequestPayload = GenerateIssueStatesRequest(
                nrSpends.toString(),
                nrStatesPerTxn.toString(),
            )

            val response = startFlowClient.startFlow(
                username,
                password,
                "http://$it/api/v1/flowstarter/startflow", // FIXME hardcode
                clientId = "${UUID.randomUUID()}",
                // TODO REVIEW: This is required to correctly access the pre-installed CorDapp on a node.
                //  Using canonicalName will fail thus it has different package paths
                flowName = "net.corda.test.notarytest.workflows.GenerateIssueTxnsFlow",
                param = generateRequestPayload
            )

            logger.info("Generate txn flow successfully started with id: ${response.flowId.uuid}")
            logger.debug("Now checking flow status...")

            val outcome = flowOutcomeChecker.checkFlowOutcome(
                username,
                password,
                "http://$it/api/v1/flowstarter/flowoutcome", // FIXME hardcode
                response.flowId.uuid,
                Array<HttpSignedTransactionDigest>::class.java
            )

            logger.info("Generating transactions finished for url: $it, generated ${outcome.size} transactions.")

            outcome.toList()
        }
    }

    /**
     * Runs the double spend tests and prints the results. Tests can raise one of two types of
     * error:
     *
     * - Double spends: Two transactions spending the same state were successfully committed. This
     *                  should never happen as the notary should never spend an already spent state.
     * - Failed spends: A state was not spent at all. This should never happen as the tests should
     *                  always result in the state being spent exactly once.
     *
     * Exits with one of the following codes:
     *
     * - 0 if tests completed successfully
     * - 1 if tests completed with errors
     * - 2 if tests failed with an unexpected exception
     */
    override fun call(): Int {
        // FIXME: the default log level is ERROR.
        logger.info(
            "Running client to initiate double spends, $nrSpends initial spends, " +
                    "double spend ratio $doubleSpendRatio ($doubleSpendMode mode), " +
                    "timeout $timeout seconds"
        )

        if (rngSeed != 23.toLong()) {
            logger.info("RNG seed overridden to $rngSeed")
        }

        if (notaryName != null) {
            logger.info("Using notary $notaryName")
        }

        val allHostsAndPorts = (listOf(networkHostAndPort) + networkHostAndPorts).map {
            NetworkHostAndPort.parse(it)
        }

        val allSpendTxns = generateIssueTxns(allHostsAndPorts)
        val conflictPosition = ConflictPosition.valueOf(doubleSpendMode.toUpperCase())

        val rng = Random()
        rng.setSeed(rngSeed)

        val txnsWithDoubleSpends = allSpendTxns.map { txnList ->
            val txns = mutableListOf<HttpSpendTransactionDigest>()
            val conflictSpends = mutableListOf<HttpSignedTransactionDigest>()
            txnList.forEach { txn ->
                if (rng.nextDouble() < doubleSpendRatio) {
                    txns.add(HttpSpendTransactionDigest(txn, true))
                    if (conflictPosition == ConflictPosition.END) {
                        conflictSpends.add(txn)
                    } else {
                        // Interleaved
                        txns.add(HttpSpendTransactionDigest(txn, true))
                    }
                } else {
                    txns.add(HttpSpendTransactionDigest(txn, false))
                }
            }

            if (conflictPosition == ConflictPosition.END) {
                txns.addAll(conflictSpends.map { HttpSpendTransactionDigest(it, true) })
            }
            txns
        }

        logger.info("All issue transactions generated and double spends were added...")
        logger.info("Now generating spends and notarising, this might take a while.")

        val notarisationTasks = mutableListOf<Callable<SpendNotarisationFlow.Result>>()

        txnsWithDoubleSpends.forEachIndexed { idx, txns ->
            notarisationTasks.add(
                DoubleSpendNotarisationTask(
                    txns,
                    allHostsAndPorts[idx].toString(),
                    username,
                    password
                )
            )
        }

        val executor = Executors.newFixedThreadPool(notarisationTasks.count())

        val tasks = executor.invokeAll(notarisationTasks, timeout.toLong(), TimeUnit.SECONDS)

        val nodeResults = tasks.mapIndexed { idx, results ->
            try {
                results.getOrThrow()
            } catch (e: CancellationException) {
                logger.warn("Notarisation thread for ${allHostsAndPorts[idx]} timed out.")
                null
            } catch (e: Exception) {
                logger.error(
                    "Exception raised in notarisation thread for " +
                            "${allHostsAndPorts[idx]}: $e"
                )
                null
            }
        }

        executor.shutdownNow()

        val avgFPS = BigDecimal(
            nodeResults
                .filterNotNull()
                .map { it.flowsPerSecond.toDouble() }
                .average())
            .setScale(2, RoundingMode.HALF_UP)

        logger.info(
            "Notarisation complete. " +
                    "Total transactions: ${nodeResults.sumBy { it?.numSent ?: 0 }}, " +
                    "Number successful: ${nodeResults.sumBy { it?.numOk ?: 0 }}, " +
                    "Number of double spends: ${nodeResults.sumBy { it?.numDoubleSpendErrors ?: 0 }}, " +
                    "Number of failed spends: ${nodeResults.sumBy { it?.numFailedSpendErrors ?: 0 }}, " +
                    "Average flows per second: ${avgFPS.toPlainString()}"
        )

        return if (nodeResults.any { it?.numDoubleSpendErrors != 0 || it.numFailedSpendErrors != 0 }) 1 else 0
    }
}