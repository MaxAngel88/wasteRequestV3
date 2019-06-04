package com.wasteDisposal.flow

import co.paralleluniverse.fibers.Suspendable
import com.wasteDisposal.POJO.WasteRequestPojo
import com.wasteDisposal.contract.WasteRequestContract
import com.wasteDisposal.contract.WasteRequestContract.Companion.WASTE_REQUEST_CONTRACT_ID
import com.wasteDisposal.state.ProposalState
import com.wasteDisposal.state.WasteRequestState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*


/**
 * MAKE A SAME COPY OF THIS PACKAGE UNDER: contract-kotlin/src/com.example/ TO PERMIT IMPORT OF PROPOSALFLOW
 *
 **/


object WasteRequestFlow {

    @InitiatingFlow
    @StartableByRPC
    class Starter(
            val cliente: Party,
            val fornitore: Party,
            val syndial: Party,
            val properties: WasteRequestPojo) : FlowLogic<WasteRequestState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): WasteRequestState {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val wasteRequestState = WasteRequestState(
                    cliente,
                    fornitore,
                    syndial,
                    properties.codCliente,
                    properties.codFornitore,
                    properties.requestDate,
                    properties.wasteType,
                    properties.wasteWeight,
                    properties.wasteDesc,
                    properties.wasteDescAmm,
                    properties.wasteGps,
                    properties.idProposal,
                    properties.status,

                    UniqueIdentifier(properties.externalId, UUID.randomUUID()))
            val txCommand = Command(WasteRequestContract.Commands.Create(), wasteRequestState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(wasteRequestState, WASTE_REQUEST_CONTRACT_ID)
                    .addCommand(txCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS


            var firstFlow : FlowSession? = null
            var secondFlow : FlowSession? = null

            // Send the state to the counterparty, and receive it back with their signature.
            when(serviceHub.myInfo.legalIdentities.first()){
                cliente -> {
                    firstFlow = initiateFlow(syndial)
                    secondFlow = initiateFlow(fornitore)
                }
                fornitore -> {
                    firstFlow = initiateFlow(syndial)
                    secondFlow = initiateFlow(cliente)
                }
                syndial -> {
                    firstFlow = initiateFlow(cliente)
                    secondFlow = initiateFlow(fornitore)
                }

                else -> throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" not partecipating to the transaction")
            }


            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(firstFlow!!, secondFlow!!), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
            return wasteRequestState
        }
    }

    @InitiatedBy(Starter::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a transaction." using (output is WasteRequestState)
                }
            }

            return subFlow(signTransactionFlow)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Issuer(
            val proposalId: String) : FlowLogic<WasteRequestState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): WasteRequestState {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val myLegalIdentity = serviceHub.myInfo.legalIdentities.first()

            if(myLegalIdentity.name.organisation != "Fornitore"){
                throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" cannot start the flow")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(proposalId)))
            criteria = criteria.and(customCriteria)

            val proposalStates = serviceHub.vaultService.queryBy<ProposalState>(
                    criteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if(proposalStates.size > 1 || proposalStates.size == 0) throw FlowException("no proposal state with UUID "+UUID.fromString(proposalId)+" found")

            val proposalStateRef = proposalStates.get(0)
            val proposalState = proposalStateRef.state.data

            val idProposal = proposalState.linearId.id.toString()

            val wasteRequestState = WasteRequestState(
                    proposalState.cliente,
                    proposalState.fornitore,
                    proposalState.syndial,
                    proposalState.codCliente,
                    proposalState.codFornitore,
                    proposalState.requestDate,
                    proposalState.wasteType,
                    proposalState.wasteWeight,
                    proposalState.wasteDesc,
                    proposalState.wasteDescAmm,
                    proposalState.wasteGps,
                    idProposal,
                    "ongoing",
                    UniqueIdentifier(id = UUID.randomUUID()))


            val proposalCommand = Command(WasteRequestContract.Commands.Issue(), wasteRequestState.participants.map { it.owningKey })
            val proposalBuilder = TransactionBuilder(notary)
                    .addInputState(proposalStateRef)
                    .addOutputState(wasteRequestState, WASTE_REQUEST_CONTRACT_ID)
                    .addCommand(proposalCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            proposalBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(proposalBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            var syndialFlow : FlowSession = initiateFlow(proposalState.syndial)
            var clienteFlow : FlowSession = initiateFlow(proposalState.cliente)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow, clienteFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.

            subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
            return wasteRequestState
        }
    }

    @InitiatedBy(Issuer::class)
    class IssuerAcceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }

            return subFlow(signTransactionFlow)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class IssuerUpdateWasteRequest(
            val wasteRequestId: String,
            val newStatus : String) : FlowLogic<WasteRequestState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): WasteRequestState {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val myLegalIdentity = serviceHub.myInfo.legalIdentities.first()

            if(myLegalIdentity.name.organisation != "Fornitore"){
                throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" cannot start the flow")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(wasteRequestId)))
            criteria = criteria.and(customCriteria)

            val oldWasteRequestStates = serviceHub.vaultService.queryBy<WasteRequestState>(
                    criteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if(oldWasteRequestStates.size > 1 || oldWasteRequestStates.size == 0) throw FlowException("no proposal state with UUID "+UUID.fromString(wasteRequestId)+" found")

            val oldWasteRequestStateRef = oldWasteRequestStates.get(0)
            val oldWasteRequestState = oldWasteRequestStateRef.state.data

            val newWasteRequestState = WasteRequestState(
                    oldWasteRequestState.cliente,
                    oldWasteRequestState.fornitore,
                    oldWasteRequestState.syndial,
                    oldWasteRequestState.codCliente,
                    oldWasteRequestState.codFornitore,
                    oldWasteRequestState.requestDate,
                    oldWasteRequestState.wasteType,
                    oldWasteRequestState.wasteWeight,
                    oldWasteRequestState.wasteDesc,
                    oldWasteRequestState.wasteDescAmm,
                    oldWasteRequestState.wasteGps,
                    oldWasteRequestState.idProposal,
                    newStatus,
                    UniqueIdentifier(id = UUID.randomUUID()))


            val wasteRequestCommand = Command(WasteRequestContract.Commands.IssueUpdate(), newWasteRequestState.participants.map { it.owningKey })
            val proposalBuilder = TransactionBuilder(notary)
                    .addInputState(oldWasteRequestStateRef)
                    .addOutputState(newWasteRequestState, WASTE_REQUEST_CONTRACT_ID)
                    .addCommand(wasteRequestCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            proposalBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(proposalBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            var syndialFlow : FlowSession = initiateFlow(newWasteRequestState.syndial)
            var clienteFlow : FlowSession = initiateFlow(newWasteRequestState.cliente)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow, clienteFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.

            subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
            return newWasteRequestState
        }
    }

    @InitiatedBy(IssuerUpdateWasteRequest::class)
    class IssuerUpdateAcceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}