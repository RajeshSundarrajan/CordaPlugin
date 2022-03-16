package com.r3.notary.poc.httprpc.model

/**
 * Data classes that represents a start flow request that is sent to the Node.
 * The wrapper classes are needed to maintain the JSON structure the node requires.
 */
data class HttpRpcStartFlowRequestWrapper(
    val rpcStartFlowRequest: HttpRpcStartFlowRequest
)

data class HttpRpcStartFlowRequest(
    val clientId: String,
    val flowName: String,
    val parameters: HttpRpcStartFlowParametersWrapper
)

data class HttpRpcStartFlowParametersWrapper(
    val parametersInJson: Any
)

/**
 * Data class for Double Spend notarisation request
 *
 * @param txId: Id of the issue transaction
 * @param conflicting: whether the transaction is conflicting or not
 */
data class DoubleSpendNotarisationRequest(
    val txId: String,
    val conflicting: String
)

/**
 * Data class for generating issue transactions request
 *
 * @param numInitialTxns: How many transactions we want to generate
 * @param numStatesPerTxn: How many states each transaction contain
 */
data class GenerateIssueStatesRequest(
    val numInitialTxns: String,
    val numStatesPerTxn: String,
)

/**
 * Simple representation of a flow identifier
 */
data class FlowId(
    val uuid: String = ""
)

/**
 * Data structure returned by the flowstarter/startflow endpoint.
 * The [flowId] can be used for further tracking of the flow's status.
 *
 * @param flowId: The id generated on the node side
 * @param clientId: The id generated on the client side
 */
data class HttpRpcAsyncFlowResponse(
    val flowId: FlowId = FlowId(""),
    val clientId: String = ""
)

/**
 * Representation of the flow's status, use [NONE] for initial/not-known status.
 */
enum class FlowStatus {
    NONE,
    RUNNING,
    FAILED,
    COMPLETED
}

/**
 * Data structure returned by the flowstarter/flowoutcome endpoint.
 * [resultJson] will be empty if the status is [FlowStatus.FAILED] and
 * [exceptionDigest] will be empty if the status is [FlowStatus.COMPLETED]
 */
data class FlowOutcome(
    val exceptionDigest: ExceptionDigest = ExceptionDigest("", ""),
    val resultJson: String = "",
    val status: FlowStatus = FlowStatus.NONE,
)

/**
 * Data class representing an exception returned from the flowstarter/flowoutcome endpoint.
 */
data class ExceptionDigest(
    val exceptionType: String = "",
    val message: String = ""
)