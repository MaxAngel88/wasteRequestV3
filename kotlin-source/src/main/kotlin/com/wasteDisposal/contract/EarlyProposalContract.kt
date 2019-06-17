package com.wasteDisposal.contract

import com.wasteDisposal.state.EarlyProposalState
import com.wasteDisposal.state.ProposalState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class EarlyProposalContract : Contract {
    companion object {
        @JvmStatic
        val EARLY_PROPOSAL_CONTRACT_ID = "com.wasteDisposal.contract.EarlyProposalContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands) {
            val setOfSigners = command.signers.toSet()
            when (command.value) {
                is Commands.Create -> verifyCreate(tx, setOfSigners)
                is Commands.Issue -> verifyIssue(tx, setOfSigners)
                is Commands.End -> verifyEnd(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    interface Commands : CommandData {
        class Create : Commands, TypeOnlyCommandData()
        class Issue : Commands,  TypeOnlyCommandData()
        class End : Commands, TypeOnlyCommandData()
        //class Settle : TypeOnlyCommandData(), Commands
    }


    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

        "No inputs should be consumed when creating a transaction." using (tx.inputStates.isEmpty())

        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val earlyProposal = tx.outputsOfType<EarlyProposalState>().single()
        "cliente and syndial cannot be the same" using (earlyProposal.cliente != earlyProposal.syndial)
        "validity must be grather than date" using (earlyProposal.validity > earlyProposal.requestDate)
        "wasteType cannot be empty" using (earlyProposal.wasteType.isNotEmpty())
        "wasteWeight must be grather than 0" using (earlyProposal.wasteWeight > 0.0)
        "wasteGps cannot be empty" using (earlyProposal.wasteGps.isNotEmpty())
        "earlyProposal status must be 'notassigned'" using (earlyProposal.status.equals("notassigned", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(earlyProposal.participants.map { it.owningKey }))
    }


    private fun verifyIssue(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //earlyProposal
        "there must be only one input" using (tx.inputStates.size == 1)
        val earlyProposal = tx.inputsOfType<EarlyProposalState>().single()

        //proposal
        "Only one transaction state should be created." using (tx.outputStates.size == 1)
        val proposal = tx.outputsOfType<ProposalState>().single()
        "cliente and fornitore cannot be the same" using (proposal.cliente != proposal.fornitore)
        "wasteType cannot be empty" using (proposal.wasteType.isNotEmpty())
        "wasteWeight must be grather than 0" using (proposal.wasteWeight > 0.0)
        "wasteGps cannot be empty" using (proposal.wasteGps.isNotEmpty())
        "wasteRequest status must be 'ongoing' or 'completed'" using (proposal.status.equals("pending", ignoreCase = true))

        "All of the participants must be signers." using (signers.containsAll(proposal.participants.map { it.owningKey }))

        //earlyProposal and proposal
        "earlyProposal's cliente must be the proposal's cliente" using (earlyProposal.cliente == proposal.cliente)
        ""+earlyProposal.requestDate+" is not valid" using (proposal.requestDate < earlyProposal.validity)
        "wasteType must be equal" using (earlyProposal.wasteType == proposal.wasteType)
        "wasteWeight must be equal" using (earlyProposal.wasteWeight == proposal.wasteWeight)
        "wasteGps must be equal" using (earlyProposal.wasteGps == proposal.wasteGps)
        "earlyProposal status must be 'notassigned'" using (earlyProposal.status.equals("notassigned", ignoreCase = true))
        "linearId must be idEarlyProposal" using (earlyProposal.linearId.id.toString() == proposal.idEarlyProposal)
    }


    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "one input when ending a proposal." using (tx.inputStates.size == 1)
        "no proposal state should be created." using (tx.outputStates.isEmpty())
        val earlyProposal = tx.inputsOfType<EarlyProposalState>().single()
        "All of the participants must be signers." using (signers.containsAll(earlyProposal.participants.map { it.owningKey }))
    }
}