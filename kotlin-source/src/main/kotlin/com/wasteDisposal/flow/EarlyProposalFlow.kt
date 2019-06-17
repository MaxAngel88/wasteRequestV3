package com.wasteDisposal.flow

import co.paralleluniverse.fibers.Suspendable
import com.wasteDisposal.POJO.EarlyProposalPojo
import com.wasteDisposal.POJO.IssueProposalPojo
import com.wasteDisposal.contract.EarlyProposalContract
import com.wasteDisposal.contract.EarlyProposalContract.Companion.EARLY_PROPOSAL_CONTRACT_ID
import com.wasteDisposal.state.EarlyProposalState
import com.wasteDisposal.state.ProposalState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
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

object EarlyProposalFlow {

    @InitiatingFlow
    @StartableByRPC
    class Starter(
            val syndial : Party,
            val properties : EarlyProposalPojo) : FlowLogic<EarlyProposalState>() {
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
        override fun call() : EarlyProposalState {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val myLegalIdentity = serviceHub.myInfo.legalIdentities.first()

            if(myLegalIdentity.name.organisation != "Cliente"){
                throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" cannot start the flow")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val earlyProposalState = EarlyProposalState(
                    serviceHub.myInfo.legalIdentities.first(),
                    syndial,
                    properties.codCliente,
                    properties.requestDate,
                    properties.wasteType,
                    properties.wasteWeight,
                    properties.wasteDesc,
                    properties.wasteGps,
                    properties.status,
                    properties.requestDate.plusSeconds(60 * 10),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(EarlyProposalContract.Commands.Create(), earlyProposalState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(earlyProposalState, EARLY_PROPOSAL_CONTRACT_ID)
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

            var syndialFlow : FlowSession = initiateFlow(syndial)

            // Send the state to syndial and fornitore, then receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow), GATHERING_SIGS.childProgressTracker()))

            //DEBUG
            //logger.info(get("http://httpbin.org/ip").jsonObject.getString("origin"))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION

            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
            return earlyProposalState
        }
    }

    @InitiatedBy(Starter::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a EarlyProposal." using (output is EarlyProposalState)
                }
            }

            return subFlow(signTransactionFlow)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Issuer(
            val fornitore : Party,
            val properties : IssueProposalPojo
            ) : FlowLogic<ProposalState>() {
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
        override fun call(): ProposalState {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val myLegalIdentity = serviceHub.myInfo.legalIdentities.first()

            if(myLegalIdentity.name.organisation != "Syndial"){
                throw FlowException("node "+serviceHub.myInfo.legalIdentities.first()+" cannot start the flow")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            var criteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val customCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(properties.idEarlyProposal)))
            criteria = criteria.and(customCriteria)

            val earlyProposalStates = serviceHub.vaultService.queryBy<EarlyProposalState>(
                    criteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if(earlyProposalStates.size > 1 || earlyProposalStates.size == 0) throw FlowException("no proposal state with UUID "+UUID.fromString(properties.idEarlyProposal)+" found")

            val earlyProposalStateRef = earlyProposalStates.get(0)
            val earlyProposalState = earlyProposalStateRef.state.data

            val idEarlyProposal = earlyProposalState.linearId.id.toString()

            val proposalState = ProposalState(
                    earlyProposalState.cliente,
                    fornitore,
                    serviceHub.myInfo.legalIdentities.first(),
                    earlyProposalState.codCliente,
                    properties.codFornitore,
                    earlyProposalState.requestDate,
                    earlyProposalState.wasteType,
                    earlyProposalState.wasteWeight,
                    earlyProposalState.wasteDesc,
                    properties.wasteDescAmm,
                    earlyProposalState.wasteGps,
                    idEarlyProposal,
                    "pending",
                    earlyProposalState.requestDate.plusSeconds(60 * 20),
                    UniqueIdentifier(id = UUID.randomUUID()))


            val earlyProposalCommand = Command(EarlyProposalContract.Commands.Issue(), proposalState.participants.map { it.owningKey })
            val proposalBuilder = TransactionBuilder(notary)
                    .addInputState(earlyProposalStateRef)
                    .addOutputState(proposalState, EARLY_PROPOSAL_CONTRACT_ID)
                    .addCommand(earlyProposalCommand)


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

            var clienteFlow : FlowSession = initiateFlow(proposalState.cliente)
            var fornitoreFlow : FlowSession = initiateFlow(proposalState.fornitore)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(clienteFlow, fornitoreFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.

            subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
            return proposalState
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

    @SchedulableFlow
    @InitiatingFlow
    class EndEarlyProposal(private val stateRef: StateRef) : FlowLogic<Unit>(){

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

        @Suspendable
        override fun call() {

            // Obtain a reference to the notary we want to use.

            val stateAndRef = serviceHub.toStateAndRef<EarlyProposalState>(stateRef)
            val earlyProposalState = stateAndRef.state.data

            // Stage 1.
            if(serviceHub.myInfo.legalIdentities.first() == earlyProposalState.cliente) {
                val notary = serviceHub.networkMapCache.notaryIdentities[0]
                progressTracker.currentStep = GENERATING_TRANSACTION
                // Generate an unsigned transaction.
                val txCommand = Command(EarlyProposalContract.Commands.End(), earlyProposalState.participants.map { it.owningKey })
                val txBuilder = TransactionBuilder(notary)
                        .addInputState(stateAndRef)
                        .addCommand(txCommand)

                progressTracker.currentStep = VERIFYING_TRANSACTION
                // Verify that the transaction is valid.
                txBuilder.verify(serviceHub)

                // Stage 3.
                progressTracker.currentStep = SIGNING_TRANSACTION
                // Sign the transaction.
                val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

                // Stage 4.
                progressTracker.currentStep = GATHERING_SIGS
                var syndialFlow : FlowSession = initiateFlow(earlyProposalState.syndial)

                // Send the state to the other nodes, and receive it back with their signature.
                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(syndialFlow), GATHERING_SIGS.childProgressTracker()))

                // Stage 5.
                progressTracker.currentStep = FINALISING_TRANSACTION
                // Notarise and record the transaction in both parties' vaults.
                subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
                logger.info("stop scheduled end for "+ earlyProposalState.linearId.id.toString())
            }
        }
    }

    @InitiatedBy(EndEarlyProposal::class)
    class AcceptorEnd(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>(){
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