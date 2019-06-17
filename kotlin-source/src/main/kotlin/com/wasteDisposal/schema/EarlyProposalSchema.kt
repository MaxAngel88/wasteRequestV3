package com.wasteDisposal.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object EarlyProposalSchema

object EarlyProposalSchemaV1 : MappedSchema(
        schemaFamily = EarlyProposalSchema.javaClass,
        version = 1,
        mappedTypes = listOf(EarlyPersistentProposal::class.java)) {

    @Entity
    @Table(name = "early_proposal_states")
    class EarlyPersistentProposal(
            @Column(name = "cliente")
            var cliente: String,

            @Column(name = "syndial")
            var syndial: String,

            @Column(name = "codCliente")
            var codCliente: String,

            @Column(name = "requestDate")
            var requestDate: Instant,

            @Column(name = "wasteType")
            var wasteType: String,

            @Column(name = "wasteWeight")
            var wasteWeight: Double,

            @Column(name = "wasteDesc")
            var wasteDesc: String,

            @Column(name = "wasteGps")
            var wasteGps: String,

            @Column(name = "status")
            var status: String,

            @Column(name = "validity")
            var validity: Instant,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        //constructor(): this("", "", "", , UUID.randomUUID())
        constructor() : this("", "", "", Instant.now(), "", 0.0, "", "", "", Instant.now(), UUID.randomUUID())
    }
}