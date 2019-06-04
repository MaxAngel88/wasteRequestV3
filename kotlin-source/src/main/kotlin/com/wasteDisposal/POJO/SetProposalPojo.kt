package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class SetProposalPojo(
        val id : String = "",
        val newStatus : String = ""
)