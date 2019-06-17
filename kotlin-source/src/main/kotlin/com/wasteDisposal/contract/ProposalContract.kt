package com.wasteDisposal.contract

import com.wasteDisposal.state.ProposalState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.io.File
import java.security.PublicKey


class ProposalContract : Contract {
    companion object {
        @JvmStatic
        val PROPOSAL_CONTRACT_ID = "com.wasteDisposal.contract.ProposalContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands) {
            val setOfSigners = command.signers.toSet()
            when (command.value) {
                is Commands.IssueUpdate -> verifyIssueUpdate(tx, setOfSigners)
                is Commands.End -> verifyEnd(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    interface Commands : CommandData {
        class IssueUpdate : Commands, TypeOnlyCommandData()
        class End : Commands, TypeOnlyCommandData()
        //class Settle : TypeOnlyCommandData(), Commands
    }

    private fun verifyIssueUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //oldProposal
        "there must be only one input" using (tx.inputStates.size == 1)
        val oldProposal = tx.inputsOfType<ProposalState>().single()

        //newProposal
        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val newProposal = tx.outputsOfType<ProposalState>().single()
        "cliente must be the same" using (oldProposal.cliente == newProposal.cliente)
        "fornitore must be the same" using (oldProposal.fornitore == newProposal.fornitore)
        "wasteType must be the same" using (oldProposal.wasteType == newProposal.wasteType)
        "wasteWeight must be the same" using (oldProposal.wasteWeight == newProposal.wasteWeight)
        "wasteGps must be the same" using (oldProposal.wasteGps == newProposal.wasteGps)
        "oldProposal: "+oldProposal.requestDate+" is not valid" using (oldProposal.requestDate < oldProposal.validity)
        "status cannot be the same" using (oldProposal.status != newProposal.status)
        "newProposal status must be 'denied'" using (newProposal.status.equals("denied", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(newProposal.participants.map { it.owningKey }))
    }

    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "one input when ending a proposal." using (tx.inputStates.size == 1)
        "no proposal state should be created." using (tx.outputStates.isEmpty())
        val proposal = tx.inputsOfType<ProposalState>().single()
        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))
    }
}