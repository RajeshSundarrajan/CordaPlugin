package com.r3.notary.poc.workflows

import net.corda.systemflows.FinalityFlow
import com.r3.notary.poc.contracts.DummyCommand
import com.r3.notary.poc.contracts.DummyState
import net.corda.v5.application.flows.*
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.flows.flowservices.FlowIdentity
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.services.NotaryLookupService
import net.corda.v5.ledger.transactions.*
import java.io.*
import java.util.*

/**
 * Flow that creates a number of dummy states, and returns a list of transaction digests to consume
 * these states.
 *
 * @param numInitialTxns      The number of initial spend transactions to generate
 * @param numStatesPerTxn        The number of states to spend in each transaction
 *
 * @return List of [HttpSignedTransactionDigest] objects containing transactions IDs that will be used for spend transaction
 * generation in [SpendNotarisationFlow].
 */
@InitiatingFlow
@StartableByRPC
class GenerateIssueTxnsFlow @JsonConstructor constructor(private val params: RpcStartFlowRequestParameters) :
    Flow<List<HttpSignedTransactionDigest>> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowIdentity: FlowIdentity

    @CordaInject
    lateinit var transactionBuilderFactory: TransactionBuilderFactory

    @CordaInject
    lateinit var notaryLookup: NotaryLookupService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): List<HttpSignedTransactionDigest> {
        val mapOfParams: Map<String, String> = jsonMarshallingService.parseJson(params.parametersInJson)

        val numInitialTxns = with(mapOfParams["numInitialTxns"] ?: throw BadRpcStartFlowRequestException("Parameter \"numInitialTxns\" missing.")) {
            this.toInt()
        }

        val numStatesPerTxn = with(mapOfParams["numStatesPerTxn"] ?: throw BadRpcStartFlowRequestException("Parameter \"numStatesPerTxn\" missing.")) {
            this.toInt()
        }

        val notary = notaryLookup.notaryIdentities.first()

        val txns = mutableListOf<HttpSignedTransactionDigest>()

        repeat(numInitialTxns) {
            val issueTxBuilder = transactionBuilderFactory.create()
                .setNotary(notary)
                .addCommand(DummyCommand(), listOf(flowIdentity.ourIdentity.owningKey))

            repeat(numStatesPerTxn) {
                issueTxBuilder.addOutputState(DummyState(listOf(flowIdentity.ourIdentity)))
            }

            issueTxBuilder.verify()
            val signedIssueTx = issueTxBuilder.sign()
            recordTransaction(signedIssueTx)

            txns.add(signedIssueTx.toDigest())
        }

        return txns
    }

    private fun recordTransaction(signedTx: SignedTransaction) {
        flowEngine.subFlow(
            FinalityFlow(
                signedTx, listOf()
            )
        )
    }
}