package com.r3.notary.poc.async

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.notary.poc.async.FlowOutcomeChecker.Companion.MAX_ATTEMPTS
import com.r3.notary.poc.httprpc.client.FlowOutcomeRpcClient
import com.r3.notary.poc.httprpc.model.FlowOutcome
import com.r3.notary.poc.httprpc.model.FlowStatus
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * This class is used to check a given flow's status over-and-over again.
 * Since flows are started in an async manner in C5 we somehow need to know when they finish.
 *
 * This class can check a flow's outcome in a synchronous or an asynchronous manner.
 * The [checkFlowOutcome] function will block the thread until the flow is finished or until [MAX_ATTEMPTS] tries is reached.
 *
 * The [checkFlowOutcomeAsync] function will work on a separate thread and check the flow's outcome in an asynchronous manner
 * and return a [Future] that can be queried for status in the future.
 */

// TODO: checkFlowOutcome and checkFlowOutcomeAsync use some common logic that could be separated
// TODO: checkFlowOutcomeAsync uses a simple retry-logic, could we replace that with a back-off logic like in the checkFlowOutcome?
class FlowOutcomeChecker {

    companion object {
        private const val MAX_ATTEMPTS = 10
        private const val INITIAL_INTERVAL = 5000L
        private const val BACKOFF_MULTIPLIER = 1.1
    }

    private val flowOutcomeHttpClient = FlowOutcomeRpcClient()
    private val objectMapper = ObjectMapper()
    private val logger = LoggerFactory.getLogger(FlowOutcomeChecker::class.java)
    private val executor = Executors.newFixedThreadPool(10)

    /**
     * This function will query the given URL using the [FlowOutcomeRpcClient]
     * and use the provided username and password for basic authentication.
     *
     * This is a synchronous function and will block until the flow has finished.
     *
     * If the flow finished with a [FlowStatus.COMPLETED] status we will parse the result
     * and return it.
     *
     * If the flow finished with a [FlowStatus.FAILED] status an exception is thrown.
     *
     * If the flow didn't finish in [MAX_ATTEMPTS] tries an exception is thrown.
     */
    fun <T> checkFlowOutcome(
        username: String,
        password: String,
        url: String,
        flowId: String,
        responseClass: Class<T>
    ): T {

        logger.debug("Checking flow $flowId status maximum $MAX_ATTEMPTS times.")

        val intervalFn = IntervalFunction.ofExponentialBackoff(INITIAL_INTERVAL, BACKOFF_MULTIPLIER)

        val retryConfig = RetryConfig.custom<FlowOutcome>().retryOnResult {
            it.status != FlowStatus.COMPLETED && it.status != FlowStatus.FAILED
        }
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(intervalFn)
            .build()

        val retry = Retry.of("$flowId-outcome", retryConfig)

        val decorate = Retry.decorateSupplier(retry) {
            flowOutcomeHttpClient.getFlowOutcome(username, password, url, flowId)
        }

        val result = decorate.get()

        logger.debug("Flow $flowId finished")

        if (result.status == FlowStatus.FAILED) {
            logger.error("Flow with id $flowId failed, reason: ${result.exceptionDigest}")
            throw IllegalStateException("Flow with id $flowId failed, reason: ${result.exceptionDigest}")
        }
        return objectMapper.readValue(decorate.get().resultJson, responseClass)
    }

    /**
     * This function will query the given URL using the [FlowOutcomeRpcClient]
     * and use the provided username and password for basic authentication.
     *
     * This is an asynchronous function and will not block until the flow has finished.
     *
     * It will return a [Future] of type [FlowOutcome] which can be used to check the flow's status in an async manner.
     *
     * If the flow didn't finish in [MAX_ATTEMPTS] tries an exception is thrown.
     */
    fun checkFlowOutcomeAsync(
        username: String,
        password: String,
        url: String,
        flowId: String
    ): Future<FlowOutcome> {

        return executor.submit<FlowOutcome> {
            var currentTry = 0
            var outcome: FlowOutcome? = null

            while (currentTry < MAX_ATTEMPTS && outcome?.status != FlowStatus.COMPLETED) {
                ++currentTry
                outcome = flowOutcomeHttpClient.getFlowOutcome(username, password, url, flowId)

                Thread.sleep(INITIAL_INTERVAL)
            }

            if (outcome == null || outcome.status == FlowStatus.RUNNING) {
                logger.error("Flow with id $flowId didn't finish in $MAX_ATTEMPTS tries.")
                throw IllegalStateException("Flow with id $flowId didn't finish in $MAX_ATTEMPTS tries.")
            }

            outcome
        }
    }
}