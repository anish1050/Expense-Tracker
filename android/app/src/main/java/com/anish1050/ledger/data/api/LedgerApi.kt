package com.anish1050.ledger.data.api

import com.anish1050.ledger.capture.parser.RuleSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class SyncRow(
    val table: String,
    @SerialName("row_id") val rowId: String,
    val version: Int,
    @SerialName("updated_at") val updatedAt: Long,
    val ciphertext: String,
    val nonce: String,
    val deleted: Boolean,
    /** Server-assigned on pull; absent on push. */
    val seq: Long = 0,
)

@Serializable
data class PushResult(val accepted: Int, val rejected: List<Rejected>) {
    @Serializable
    data class Rejected(@SerialName("row_id") val rowId: String, @SerialName("server_version") val serverVersion: Int)
}

@Serializable
data class PullResult(val rows: List<SyncRow>, @SerialName("next_since") val nextSince: Long)

@Serializable
data class AuthResult(@SerialName("access_token") val accessToken: String, val user: User) {
    @Serializable
    data class User(val id: String, val email: String, val name: String)
}

class ApiException(val status: Int, message: String) : IOException(message)

// Thin OkHttp wrapper over the backend; every call is snake_case JSON in and out.
class LedgerApi(private val baseUrl: String, private val auth: AuthStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun signInWithGoogle(idToken: String): AuthResult =
        post("/api/auth/google", """{"id_token":${json.encodeToString(kotlinx.serialization.serializer<String>(), idToken)}}""", authed = false)
            .let { json.decodeFromJsonElement(AuthResult.serializer(), it) }

    suspend fun fetchRules(sinceVersion: Int): RuleSet? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/rules?since_version=$sinceVersion").get().build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                304 -> null
                200 -> json.parseToJsonElement(response.body!!.string()).jsonObject["data"]?.jsonObject?.get("rules")
                    ?.let { json.decodeFromJsonElement(RuleSet.serializer(), it) }
                else -> throw ApiException(response.code, "rules fetch failed")
            }
        }
    }

    suspend fun push(deviceId: String, rows: List<SyncRow>): PushResult {
        // The server rejects unknown keys, and seq is its field to assign.
        val body = json.encodeToString(PushBody.serializer(), PushBody(deviceId, rows))
        return json.decodeFromJsonElement(PushResult.serializer(), post("/api/sync/push", body))
    }

    suspend fun pull(since: Long, limit: Int = 500): PullResult =
        json.decodeFromJsonElement(PullResult.serializer(), get("/api/sync/pull?since=$since&limit=$limit"))

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/me").delete().header("Authorization", "Bearer ${auth.accessToken}").build()
        client.newCall(request).execute().use { if (it.code != 204) throw ApiException(it.code, "delete failed") }
    }

    @Serializable
    private data class PushBody(@SerialName("device_id") val deviceId: String, val rows: List<SyncRow>)

    private suspend fun post(path: String, body: String, authed: Boolean = true): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON))
        if (authed) builder.header("Authorization", "Bearer ${auth.accessToken}")
        execute(builder.build())
    }

    private suspend fun get(path: String): JsonElement = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$baseUrl$path").get().header("Authorization", "Bearer ${auth.accessToken}").build())
    }

    private fun execute(request: Request): JsonElement = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw ApiException(response.code, text.take(200))
        json.parseToJsonElement(text).jsonObject["data"] ?: throw ApiException(response.code, "missing data")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
