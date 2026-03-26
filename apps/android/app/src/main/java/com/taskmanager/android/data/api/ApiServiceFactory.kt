package com.taskmanager.android.data.api

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.set
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Singleton
class ApiServiceFactory @Inject constructor(
    private val json: Json,
    private val okHttpClient: OkHttpClient,
) {
    private val services = linkedMapOf<String, TaskManagerApi>()

    fun create(baseUrl: String): TaskManagerApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        return services.getOrPut(normalizedBaseUrl) {
            Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TaskManagerApi::class.java)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2/api/"

        fun normalizeBaseUrl(baseUrl: String): String {
            val candidate = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
            val parsed = candidate.toHttpUrlOrNull()

            if (parsed == null) {
                return if (candidate.endsWith("/")) candidate else "$candidate/"
            }

            val normalizedPath = when (parsed.encodedPath) {
                "", "/" -> "/api/"
                "/api", "/api/" -> "/api/"
                else -> if (parsed.encodedPath.endsWith("/")) parsed.encodedPath else "${parsed.encodedPath}/"
            }

            return parsed.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath(normalizedPath)
                .build()
                .toString()
        }
    }
}
