package com.wasteDisposal.POJO

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class SimplePojo(
        val key : String = "",
        val value : String = ""
)