package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class IssueWasteRequestPojo(
        val id : String = ""
)