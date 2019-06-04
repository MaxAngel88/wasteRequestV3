package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class SetWasteRequestPojo(
    val id : String = "",
    val newStatus : String = ""
)