package com.taskmanager.android.data.api

import java.io.IOException

const val NETWORK_API_ERROR_MESSAGE =
    "Connection unavailable."

class ApiException(
    override val message: String,
    val statusCode: Int? = null,
    val technicalMessage: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun mapUnexpectedApiFailure(error: Throwable): ApiException {
    val technicalMessage = error.message ?: error.javaClass.simpleName
    val userMessage = if (isLikelyNetworkError(error)) NETWORK_API_ERROR_MESSAGE else "Request failed."

    return ApiException(
        message = userMessage,
        technicalMessage = technicalMessage,
        cause = error,
    )
}

internal fun isLikelyNetworkError(error: Throwable): Boolean {
    if (error is IOException || error is SecurityException) {
        return true
    }

    val rawMessage = error.message?.lowercase() ?: return false
    return rawMessage.contains("eperm") ||
        rawMessage.contains("operation not permitted") ||
        rawMessage.contains("missing internet permission") ||
        rawMessage.contains("cleartext") ||
        rawMessage.contains("failed to connect") ||
        rawMessage.contains("connection refused")
}
