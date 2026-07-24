package com.rotiropi.pos_erpnext.data.api

sealed class ApiFailure {
    data class ExpectedError(val code: String, val message: String, val retryAfterSeconds: Int?) : ApiFailure()
    data class TransportError(val statusCode: Int, val message: String, val retryAfterSeconds: Int?) : ApiFailure()
    data class ProtocolError(val message: String) : ApiFailure()
}
