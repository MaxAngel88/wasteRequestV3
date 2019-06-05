package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class ProposalPojo(
        val cliente: String = "",
        val fornitore: String = "",
        val codCliente: String = "",
        val codFornitore: String = "",
        val requestDate: Instant = Instant.now().minusSeconds(200),
        val wasteType: String = "",
        val wasteWeight: Double = 0.0,
        val wasteDesc: String = "",
        val wasteDescAmm: String = "",
        val wasteGps: String = "",
        val status: String = "",
        val validity: Instant = requestDate.plusSeconds(60 * 15)
)