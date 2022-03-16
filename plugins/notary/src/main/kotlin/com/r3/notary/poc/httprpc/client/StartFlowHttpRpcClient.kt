package com.r3.notary.poc.httprpc.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.notary.poc.httprpc.model.HttpRpcAsyncFlowResponse
import com.r3.notary.poc.httprpc.model.HttpRpcStartFlowParametersWrapper
import com.r3.notary.poc.httprpc.model.HttpRpcStartFlowRequest
import com.r3.notary.poc.httprpc.model.HttpRpcStartFlowRequestWrapper
import okhttp3.*
import org.slf4j.LoggerFactory

/**
 * This client is used to start a flow.
 *
 * The [startFlow] function will send a single POST request to the given URL and using the provided parameters
 * to build a request payload.
 *
 * The response will always be immediate even if the flow didn't finish. The response will be a [HttpRpcAsyncFlowResponse]
 * type which contains the flowId that can be used to track the flow's status.
 *
 * The provided param is an [Any] type which will be added to the request as a JSON string.
 */
class StartFlowHttpRpcClient {

    companion object {
        private val httpClient = OkHttpClient()
        private val objectMapper = ObjectMapper()
        private val logger = LoggerFactory.getLogger(StartFlowHttpRpcClient::class.java)
    }

    fun startFlow(username: String,
                  password: String,
                  url: String,
                  flowName: String,
                  clientId: String,
                  param: Any): HttpRpcAsyncFlowResponse {
        logger.debug("Building start flow request: $flowName, with clientId: $clientId")
        val req = createRequest(username, password, url, flowName, clientId, param)

        logger.debug("Sending start flow request to: $url")

        val call = httpClient.newCall(req)
        val response = call.execute()
        val responsePayload = response.body()?.string()

        logger.debug("Received start flow response from $url")

        if (response.isSuccessful) {
            val outcome = objectMapper.readValue(responsePayload, HttpRpcAsyncFlowResponse::class.java)
            logger.debug("Starting flow $flowName was successful however, it's not finished yet. " +
                    "Tracking id: ${outcome.flowId.uuid}")

            return outcome
        } else {
            logger.error("Starting flow $flowName failed. Cause: $responsePayload, " +
                    "error code ${response.code()}")

            throw IllegalStateException("Starting flow $flowName failed. Cause: $responsePayload, " +
                    "error code ${response.code()}")
        }

    }

    private fun createRequest(username: String,
                              password: String,
                              url: String,
                              flowName: String,
                              clientId: String,
                              param: Any): Request {

        val requestWrapper = HttpRpcStartFlowRequestWrapper(
            HttpRpcStartFlowRequest(
                clientId,
                flowName,
                HttpRpcStartFlowParametersWrapper(objectMapper.writeValueAsString(param))
            )
        )

        val jsonBody = objectMapper.writeValueAsString(requestWrapper)

        val body = RequestBody.create(
            MediaType.parse("application/json"),
            jsonBody
        )

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", Credentials.basic(username, password))
            .post(body)
            .build()
    }
}