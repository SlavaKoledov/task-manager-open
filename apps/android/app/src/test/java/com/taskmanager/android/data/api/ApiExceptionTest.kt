package com.taskmanager.android.data.api

import com.google.common.truth.Truth.assertThat
import java.net.SocketException
import org.junit.Test

class ApiExceptionTest {
    @Test
    fun mapUnexpectedApiFailure_mapsSocketEpermToUserFriendlyNetworkMessage() {
        val exception = mapUnexpectedApiFailure(SocketException("socket failed: EPERM (Operation not permitted)"))

        assertThat(exception.message).isEqualTo(NETWORK_API_ERROR_MESSAGE)
        assertThat(exception.technicalMessage).contains("EPERM")
        assertThat(exception.cause).isInstanceOf(SocketException::class.java)
    }

    @Test
    fun mapUnexpectedApiFailure_keepsGenericMessageForNonNetworkFailures() {
        val exception = mapUnexpectedApiFailure(IllegalStateException("boom"))

        assertThat(exception.message).isEqualTo("Request failed.")
        assertThat(exception.technicalMessage).isEqualTo("boom")
    }
}
