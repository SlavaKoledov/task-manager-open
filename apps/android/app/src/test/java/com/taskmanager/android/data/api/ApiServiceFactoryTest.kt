package com.taskmanager.android.data.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiServiceFactoryTest {
    @Test
    fun normalizeBaseUrl_defaultsToEmulatorApiRoot_whenBlank() {
        assertThat(ApiServiceFactory.normalizeBaseUrl("   ")).isEqualTo("http://10.0.2.2/api/")
    }

    @Test
    fun normalizeBaseUrl_keepsApiPathAndAddsTrailingSlash() {
        assertThat(ApiServiceFactory.normalizeBaseUrl("http://192.168.0.101/api")).isEqualTo("http://192.168.0.101/api/")
    }

    @Test
    fun normalizeBaseUrl_addsApiPathForPhysicalDeviceHostOnlyInput() {
        assertThat(ApiServiceFactory.normalizeBaseUrl("http://192.168.0.101")).isEqualTo("http://192.168.0.101/api/")
        assertThat(ApiServiceFactory.normalizeBaseUrl("http://192.168.0.101/")).isEqualTo("http://192.168.0.101/api/")
    }

    @Test
    fun normalizeBaseUrl_preservesCustomPathRoots() {
        assertThat(ApiServiceFactory.normalizeBaseUrl("http://192.168.0.101/custom")).isEqualTo(
            "http://192.168.0.101/custom/",
        )
    }
}
