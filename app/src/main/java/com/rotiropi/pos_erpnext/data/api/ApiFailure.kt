package com.rotiropi.pos_erpnext.data.api

data class ApiTransportEvent(
    val endpoint: MobilePosEndpoint,
    val statusCode: Int?,
    val outcome: String,
    val requestId: String?
)

fun interface ApiTransportLogger {
    fun log(event: ApiTransportEvent)

    companion object {
        val NONE = ApiTransportLogger { }
    }
}
