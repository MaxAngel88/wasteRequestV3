package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class EarlyProposalPojo(
        val cliente: String = "",
        val codCliente: String = "",
        val requestDate: Instant = Instant.now().minusSeconds(200),
        val wasteType: String = "",
        val wasteWeight: Double = 0.0,
        val wasteDesc: String = "",
        val wasteGps: String = "",
        val status: String = ""
)