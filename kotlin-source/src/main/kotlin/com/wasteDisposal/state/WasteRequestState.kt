package com.wasteDisposal.state

import com.wasteDisposal.schema.WasteRequestSchemaV1
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

data class WasteRequestState(
        val cliente : Party,
        val fornitore: Party,
        val syndial : Party,
        val codCliente: String,
        val codFornitore: String,
        val requestDate: Instant,
        val wasteType: String,
        val wasteWeight: Double,
        val wasteDesc: String,
        val wasteDescAmm: String,
        val wasteGps: String,
        val idProposal: String,
        val idEarlyProposal: String,
        val status: String,

        override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(cliente, fornitore, syndial)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is WasteRequestSchemaV1 -> WasteRequestSchemaV1.PersistentWasteRequest(
                    this.cliente.name.toString(),
                    this.fornitore.name.toString(),
                    this.syndial.name.toString(),
                    this.codCliente,
                    this.codFornitore,
                    this.requestDate,
                    this.wasteType,
                    this.wasteWeight,
                    this.wasteDesc,
                    this.wasteDescAmm,
                    this.wasteGps,
                    this.status,
                    this.idProposal,
                    this.idEarlyProposal,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(WasteRequestSchemaV1)
}
