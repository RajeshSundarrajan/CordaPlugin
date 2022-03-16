package com.r3.notary.poc.workflows

import com.fasterxml.jackson.annotation.JsonIgnore
import com.r3.notary.poc.contracts.DummyCommand
import com.r3.notary.poc.contracts.DummyState
import net.corda.systemflows.FinalityFlow
import net.corda.v5.application.flows.*
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.flows.flowservices.FlowIdentity
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.seconds
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.notary.NotaryException
import net.corda.v5.ledger.services.NotaryLookupService
import net.corda.v5.ledger.transactions.TransactionBuilderFactory
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * A notarisation flow that will generate a spend transaction from the given issue transaction.
 *
 * To generate the spend it will first query to vault to retrieve the issue transaction by TX Id. Once retrieved
 * it will copy the output states of the issue transactions as an input state to the new transaction.
 *
 * Once the spend is generated it will try and notarise it.
 * It will then return all the states in the transaction as a map and whether that state was spent. This is needed to
 * check whether all states has been spent.
 *
 * If a [NotaryException] was encountered but the transaction was conflicting it will be ignored since we expect that.
 */
@InitiatingFlow
@StartableByRPC
class SpendNotarisationFlow @JsonConstructor constructor(private val params: RpcStartFlowRequestParameters) :
    Flow<ResultWithStateStatus> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @CordaInject
    lateinit var transactionBuilderFactory: TransactionBuilderFactory

    @CordaInject
    lateinit var notaryLookup: NotaryLookupService

    @CordaInject
    lateinit var flowIdentity: FlowIdentity

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    companion object {
        /**
         * When generating conflicts, specifies in what order the conflicts are added to the list.
         * One of:
         * * __END__         - Conflicts are added after all initial spends. This likely means that
         *                     the conflicts will be trying to spend an already spent state
         * * __INTERLEAVED__ - Conflicts are added immediately after the corresponding initial
         *                     spend. If the client sends these transactions to FinalityFlow in
         *                     parallel, this means it is likely the two transactions will be
         *                     attempting to spend the same unspent state simultaneously
         */
        enum class ConflictPosition { END, INTERLEAVED }

        private val logger = LoggerFactory.getLogger(SpendNotarisationFlow::class.java)
    }

    data class Result(
        var numSent: Int = 0,
        var numOk: Int = 0,
        var numDoubleSpendErrors: Int = 0,
        var numFailedSpendErrors: Int = 0,
        var flowsPerSecond: BigDecimal = BigDecimal(0)
    ) {
        override fun toString(): String {
            return "Num sent: $numSent, Num ok: $numOk, Num double spends: $numDoubleSpendErrors, " +
                    ", Num failed spends: ${numFailedSpendErrors}, Flows per second: " +
                    flowsPerSecond.toPlainString()
        }

        @JsonIgnore
        fun getNumResponses(): Int {
            return numOk + numDoubleSpendErrors + numFailedSpendErrors
        }
    }

    @Suspendable
    override fun call(): ResultWithStateStatus {
        val mapOfParams: Map<String, String> = jsonMarshallingService.parseJson(params.parametersInJson)

        val txId = mapOfParams["txId"] ?: throw BadRpcStartFlowRequestException("Parameter \"txId\" missing.")
        val conflictingStr =
            mapOfParams["conflicting"] ?: throw BadRpcStartFlowRequestException("Parameter \"conflicting\" missing.")
        val conflicting = conflictingStr.toBoolean()

        val notary = notaryLookup.notaryIdentities.first()

        val cursor = persistenceService.query<StateAndRef<DummyState>>(
            "VaultState.findByTxIdIn",
            mapOf("txIds" to txId),
            "Corda.IdentityStateAndRefPostProcessor"
        )

        val dummyStates = cursor.poll(100, 20.seconds).values

        val inputTxBuilder = transactionBuilderFactory.create()
            .setNotary(notary)
            .addCommand(DummyCommand(), listOf(flowIdentity.ourIdentity.owningKey))

        dummyStates.forEach {
            inputTxBuilder.addInputState(it)
        }

        inputTxBuilder.verify()
        val stx = inputTxBuilder.sign()

        val txStateSpends: MutableMap<StateRef, Boolean> = stx.inputs
            .associateWith { false }
            .toMutableMap()

        try {
            val resp = flowEngine.subFlow(FinalityFlow(stx, emptyList()))
            for (state in resp.tx.inputs) {
                txStateSpends[state] = true
            }
        } catch (e: NotaryException) {
            if (conflicting) {
                // Swallow exception because this is expected
                logger.info("Encountered NotaryException for transaction: $txId however, this is expected.")
            } else {
                throw e
            }
        }

        return ResultWithStateStatus(txStateSpends.map { it.key.txhash.toString() to it.value }.toMap())
    }
}