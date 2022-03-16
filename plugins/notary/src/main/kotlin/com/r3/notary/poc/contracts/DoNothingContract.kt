package com.r3.notary.poc.contracts

import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.utilities.JsonRepresentable
import net.corda.v5.ledger.contracts.BelongsToContract
import net.corda.v5.ledger.contracts.CommandData
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.transactions.LedgerTransaction

const val DO_NOTHING_PROGRAM_ID = "net.corda.test.notarytest.contracts.DoNothingContract"

class DoNothingContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
}

data class DummyCommand(val dummy: Int = 0) : CommandData

@BelongsToContract(DoNothingContract::class)
data class DummyState(override val participants: List<AbstractParty>) : ContractState, JsonRepresentable {
    override fun toJsonString(): String {
        return "DUMMY"
    }
}