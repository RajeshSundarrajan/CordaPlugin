package com.r3.notary.poc.httprpc.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.notary.poc.httprpc.model.FlowOutcome
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

/**
 * This client is used to check a given flow's outcome.
 *
 * The [getFlowOutcome] function will send a single HTTP GET request to the given URL using
 * basic authentication with the provided username and password. The flowId parameter will be
 * used as a path parameter in the URL.
 *
 * The response will be returned as a [FlowOutcome] object which contains the exception (if there's any), the status and
 * the body payload (if there's any).
 */
class FlowOutcomeRpcClient {

    companion object {
        private val httpClient = OkHttpClient()
        private val objectMapper = ObjectMapper()
        private val logger = LoggerFactory.getLogger(FlowOutcomeRpcClient::class.java)
    }

    fun getFlowOutcome(username: String,
                       password: String,
                       url: String,
                       flowId: String): FlowOutcome {
        logger.debug("Building flow outcome request for flow with id $flowId")
        val req = createRequest(username, password, url, flowId)

        logger.debug("Sending flow outcome request to $url")

        val call = httpClient.newCall(req)

        val response = call.execute()
        val responsePayload = response.body()?.string()

        logger.debug("Received flow outcome response from $url")

        if (response.isSuccessful) {
            val outcome = objectMapper.readValue(responsePayload, FlowOutcome::class.java)
            logger.debug("Flow $flowId outcome received: $outcome")

            return outcome
        } else {
            logger.error("Flow $flowId outcome check failed. Cause: $responsePayload, " +
                    "error code ${response.code()}")

            throw IllegalStateException("Flow $flowId outcome check failed. Cause: $responsePayload, " +
                    "error code ${response.code()}")
        }

    }
    private fun createRequest(username: String,
                              password: String,
                              url: String,
                              flowId: String): Request {
        return Request.Builder()
            .url("$url/$flowId")
            .addHeader("Authorization", Credentials.basic(username, password))
            .get()
            .build()
    }
}