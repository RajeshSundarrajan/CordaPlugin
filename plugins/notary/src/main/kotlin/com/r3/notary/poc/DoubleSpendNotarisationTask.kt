package com.r3.notary.poc

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.notary.poc.async.FlowOutcomeChecker
import com.r3.notary.poc.cli.DoubleSpend
import com.r3.notary.poc.httprpc.client.StartFlowHttpRpcClient
import com.r3.notary.poc.httprpc.model.DoubleSpendNotarisationRequest
import com.r3.notary.poc.httprpc.model.FlowOutcome
import com.r3.notary.poc.httprpc.model.FlowStatus
import com.r3.notary.poc.workflows.HttpSpendTransactionDigest
import com.r3.notary.poc.workflows.ResultWithStateStatus
import com.r3.notary.poc.workflows.SpendNotarisationFlow
import net.corda.v5.base.concurrent.getOrThrow
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.*

/**
 * Class designed to be run as a thread by the double spend client for sending notarisation
 * transactions to a specific node.
 */
class DoubleSpendNotarisationTask(
    private val txns: List<HttpSpendTransactionDigest>,
    private val networkHostAndPort: String,
    private val username: String,
    private val password: String,
) : Callable<SpendNotarisationFlow.Result> {

    companion object {
        private val flowOutcomeChecker = FlowOutcomeChecker()
        private val startFlowClient = StartFlowHttpRpcClient()
        private val logger = LoggerFactory.getLogger(DoubleSpendNotarisationTask::class.java)
        private val objectMapper = ObjectMapper()

        private const val REPORT_PERIOD_SECONDS = 30L
    }

    @Suppress("UNCHECKED_CAST")
    override fun call(): SpendNotarisationFlow.Result {

        val nodeResult = SpendNotarisationFlow.Result()
        val stateStatus = ConcurrentHashMap<String, Boolean>()

        DoubleSpend.logger.info("Sending notarisation requests to $networkHostAndPort.")

        // Used to run a helper thread in the background to provide periodic status updates
        val monitorExecutor = Executors.newSingleThreadScheduledExecutor()

        // Used to run threads for sending flows / handling responses
        val messageExecutor = Executors.newFixedThreadPool(2)

        try {
            monitorExecutor.scheduleAtFixedRate({
                DoubleSpend.logger.info(
                    "Notarisation task progress for $networkHostAndPort: " +
                            "${txns.size} total / ${nodeResult.numSent} sent / " +
                            "${nodeResult.getNumResponses()} responded"
                )
            }, REPORT_PERIOD_SECONDS, REPORT_PERIOD_SECONDS, TimeUnit.SECONDS)

            val responseQueue: BlockingQueue<Pair<Future<FlowOutcome>, HttpSpendTransactionDigest>> =
                LinkedBlockingDeque()

            val startTime = Instant.now()

            messageExecutor.submit {
                txns.forEach {
                    ++nodeResult.numSent

                    val doubleSpendRequestPayload = DoubleSpendNotarisationRequest(
                        it.tx.txId,
                        it.isConflicting.toString()
                    )

                    val asyncResponse = startFlowClient.startFlow(
                        username,
                        password,
                        "http://$networkHostAndPort/api/v1/flowstarter/startflow",
                        // TODO REVIEW
                        "net.corda.test.notarytest.workflows.SpendNotarisationFlow",
//                        SpendNotarisationFlow::class.java.canonicalName,
                        "${UUID.randomUUID()}",
                        doubleSpendRequestPayload
                    )

                    logger.debug(
                        "Double spend notarisation flow started " +
                                "with id ${asyncResponse.flowId.uuid}"
                    )

                    val spendTxOutputAsync = flowOutcomeChecker.checkFlowOutcomeAsync(
                        username,
                        password,
                        "http://$networkHostAndPort/api/v1/flowstarter/flowoutcome",
                        asyncResponse.flowId.uuid
                    )

                    responseQueue.put(Pair(spendTxOutputAsync, it))
                }
            }

            /* FIXME: Since we are not getting the result of this submission only shutting it down it will swallow
                every exception that happens in here which can be really painful when debugging, maybe we can find a better
                way to do this
            */
            messageExecutor.submit {
                while (nodeResult.getNumResponses() < txns.size) {
                    val response = responseQueue.take()

                    val result = response.first.getOrThrow()

                    when (result.status) {
                        FlowStatus.FAILED -> ++nodeResult.numFailedSpendErrors
                        FlowStatus.COMPLETED -> {
                            val notarisationResult = objectMapper.readValue(
                                result.resultJson,
                                ResultWithStateStatus::class.java
                            )

                            notarisationResult.stateIdsAndStatus.forEach {

                                // Check if any states were spent already, if yes it is a double spend so we need to mark it
                                if (stateStatus[it.key] == true && it.value) {
                                    ++nodeResult.numDoubleSpendErrors
                                }

                                // If state is already spent but we didn't spend it this time, it's ok
                                // However, we don't set it to false
                                if (stateStatus[it.key] == true && !it.value) {
                                    ++nodeResult.numOk
                                }

                                // If a state wasn't spent or not present we set it to the current value
                                if (stateStatus[it.key] == false || stateStatus[it.key] == null) {
                                    stateStatus[it.key] = it.value
                                    ++nodeResult.numOk
                                }
                            }
                        }
                        else -> throw IllegalStateException("Unexpected flow status received: ${result.status}")
                    }
                }
            }

            messageExecutor.join()

            val endTime = Instant.now()

            monitorExecutor.shutdown()

            nodeResult.flowsPerSecond = BigDecimal(
                nodeResult.numSent /
                        Duration.between(startTime, endTime).seconds.toDouble()
            ).setScale(2, RoundingMode.HALF_UP)

            // Check if all states were actually spent
            stateStatus
                .filter { !it.value }
                .map { it.key }
                .distinct()
                .forEach {
                    logger.warn("Error: $it was not spent.")
                    // Only adjust the statistics once for each transaction that contains a failed
                    // state spend, as these are at transaction level
                    ++nodeResult.numFailedSpendErrors
                    --nodeResult.numOk
                }

        } catch (e: Exception) {
            monitorExecutor.shutdown()
            messageExecutor.shutdown()
            throw e
        }

        logger.info(
            "Notarisation task for $networkHostAndPort finished with $nodeResult"
        )

        return nodeResult
    }
}

/**
 * This function is going to wait for all the threads in the executor to finish
 * It is inherited from C4 but it's not in the C5 codebase anymore
 */
fun ExecutorService.join() {
    shutdown()
    while (!awaitTermination(1, TimeUnit.SECONDS)) {
    }
}