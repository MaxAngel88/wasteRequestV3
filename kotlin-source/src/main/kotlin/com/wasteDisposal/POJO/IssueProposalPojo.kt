package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class IssueProposalPojo(
        val idEarlyProposal : String = "",
        val codFornitore : String = "",
        val wasteDescAmm : String = "",
        val fornitore : String = ""
)