package com.wasteDisposal.state

import com.wasteDisposal.flow.EarlyProposalFlow
import com.wasteDisposal.flow.ProposalFlow
import com.wasteDisposal.schema.EarlyProposalSchemaV1
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

data class EarlyProposalState (
    val cliente : Party,
    val syndial : Party,
    val codCliente: String,
    val requestDate: Instant,
    val wasteType: String,
    val wasteWeight: Double,
    val wasteDesc: String,
    val wasteGps: String,
    val status: String,
    val validity: Instant,

    override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState, SchedulableState{

    override val participants: List<AbstractParty> get() = listOf(cliente, syndial)

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(flowLogicRefFactory.create(EarlyProposalFlow.EndEarlyProposal::class.java, thisStateRef), validity)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is EarlyProposalSchemaV1 -> EarlyProposalSchemaV1.EarlyPersistentProposal(
                    this.cliente.name.toString(),
                    this.syndial.name.toString(),
                    this.codCliente,
                    this.requestDate,
                    this.wasteType,
                    this.wasteWeight,
                    this.wasteDesc,
                    this.wasteGps,
                    this.status,
                    this.validity,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(EarlyProposalSchemaV1)
}

