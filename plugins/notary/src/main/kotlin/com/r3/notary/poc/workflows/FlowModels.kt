package com.r3.notary.poc.workflows

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.toBase64
import net.corda.v5.ledger.transactions.SignedTransaction
import net.corda.v5.ledger.transactions.SignedTransactionDigest

/**
 * Data class used to wrap a transaction and describe whether it's conflicting or not.
 * This class has a default constructor so it can be serialized via Jackson.
 */
@CordaSerializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class HttpSpendTransactionDigest(
    val tx: HttpSignedTransactionDigest = HttpSignedTransactionDigest("", emptyList(), emptyList()),
    val isConflicting: Boolean = false
)

/**
 * Data class used to replace [SignedTransactionDigest] since [SignedTransactionDigest] is not serializable via Jackson because
 * it has no default constructor.
 */
data class HttpSignedTransactionDigest(
    val txId: String = "",
    val outputs: List<String> = emptyList(),
    val signatures: List<String> = emptyList()
)

fun SignedTransaction.toDigest(): HttpSignedTransactionDigest {
    return HttpSignedTransactionDigest(
        id.toString(),
        tx.outputs.map { it.data.toString() },
        sigs.map { it.bytes.toBase64() }
    )
}

/**
 * A data class representing a notarisation result which includes the states in that given transaction
 * and whether they were spent or not (Boolean).
 */
data class ResultWithStateStatus(val stateIdsAndStatus: Map<String, Boolean> = emptyMap())