package com.strata.tv.data.settings

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream Codes `player_api.php` client — same workflow IPTV Smarters Pro
 * uses for "Xtreme Codes" login.
 *
 * Auth: `{server}/player_api.php?username=…&password=…`
 * Catalogue actions: get_live_categories, get_live_streams, get_vod_*,
 * get_series_*, get_short_epg / get_simple_data_table (best-effort).
 *
 * Never logs passwords or authenticated URLs.
 */
@Singleton
class XtreamApi @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun accountInfo(config: ProviderConfig): AccountInfo? =
        withContext(Dispatchers.IO) {
            val url = config.toPlayerApiUrl() ?: return@withContext null
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", config.apiUserAgent())
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val wrapper = json.decodeFromString<XtreamResponse>(body)
                    AccountInfo.from(wrapper)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Xtream accountInfo failed: ${e.javaClass.simpleName}")
                null
            }
        }

    suspend fun testConnection(config: ProviderConfig): Boolean =
        accountInfo(config)?.isAuthenticated == true

    /**
     * Step-by-step diagnostic used by the Connect button / Settings.
     * Does not include credentials in messages.
     */
    suspend fun runDiagnostics(config: ProviderConfig): List<DiagnosticStep> =
        withContext(Dispatchers.IO) {
            val steps = mutableListOf<DiagnosticStep>()
            val base = config.host.trimEnd('/')
            if (base.isBlank() || config.username.isBlank() || config.password.isBlank()) {
                return@withContext listOf(
                    DiagnosticStep("Credentials", false, "Server URL, username and password are required"),
                )
            }

            // 1. Server reachability (player_api without trusting body yet)
            val authUrl = config.toPlayerApiUrl()
            if (authUrl == null) {
                return@withContext listOf(
                    DiagnosticStep("Authentication", false, "Not an Xtream endpoint"),
                )
            }
            val reachable = headOrGetOk(authUrl, config.apiUserAgent())
            steps += DiagnosticStep(
                "Server accessibility",
                reachable.ok,
                if (reachable.ok) "Reached player_api.php" else reachable.detail,
            )
            if (!reachable.ok) return@withContext steps

            // 2. Auth
            val account = accountInfo(config)
            val authed = account?.isAuthenticated == true
            steps += DiagnosticStep(
                "Xtream authentication",
                authed,
                when {
                    account == null -> "No usable auth response"
                    !authed -> {
                        val status = account.status.ifBlank { "unknown" }
                        "Rejected (status=$status)"
                    }
                    else -> buildString {
                        append("OK")
                        account.expiresAt?.let { append(", expires $it") }
                        if (account.allowedOutputs.isNotEmpty()) {
                            append(", outputs=${account.allowedOutputs.joinToString(",")}")
                        }
                    }
                },
            )
            if (!authed) return@withContext steps

            // Catalogue probes (count only — never log URLs)
            steps += probeAction(config, "get_live_categories", "Live categories")
            steps += probeAction(config, "get_live_streams", "Live streams")
            steps += probeAction(config, "get_vod_categories", "Movie categories")
            steps += probeAction(config, "get_vod_streams", "Movies")
            steps += probeAction(config, "get_series_categories", "Series categories")
            steps += probeAction(config, "get_series", "Series")
            steps += probeAction(config, "get_short_epg", "Short EPG", optional = true)
            steps += probeAction(config, "get_simple_data_table", "EPG table", optional = true)

            steps
        }

    private fun probeAction(
        config: ProviderConfig,
        action: String,
        label: String,
        optional: Boolean = false,
    ): DiagnosticStep {
        val url = config.toPlayerApiUrl(action) ?: return DiagnosticStep(label, false, "No URL")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", config.apiUserAgent())
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful -> DiagnosticStep(
                        label,
                        optional,
                        if (optional) "Not available (HTTP ${response.code})"
                        else "HTTP ${response.code}",
                    )
                    body.isBlank() -> DiagnosticStep(
                        label,
                        optional,
                        if (optional) "Empty (optional)" else "Empty response",
                    )
                    body.trimStart().startsWith("{") || body.trimStart().startsWith("[") -> {
                        val countHint = when {
                            body.contains("\"category_id\"") || body.contains("\"stream_id\"") ||
                                body.contains("\"series_id\"") -> "JSON catalogue received"
                            else -> "JSON response received"
                        }
                        DiagnosticStep(label, true, countHint)
                    }
                    else -> DiagnosticStep(
                        label,
                        optional,
                        if (optional) "Non-JSON (optional)" else "Unexpected response shape",
                    )
                }
            }
        } catch (e: Throwable) {
            DiagnosticStep(
                label,
                optional,
                if (optional) "Skipped (${e.javaClass.simpleName})"
                else e.javaClass.simpleName,
            )
        }
    }

    private fun headOrGetOk(url: String, userAgent: String): Reachability {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 400..499) {
                    // 401/403 still means the host answered — auth checked next.
                    Reachability(true, "HTTP ${response.code}")
                } else {
                    Reachability(false, "HTTP ${response.code}")
                }
            }
        } catch (e: Throwable) {
            Reachability(false, e.javaClass.simpleName)
        }
    }

    data class DiagnosticStep(
        val name: String,
        val ok: Boolean,
        val detail: String,
    )

    private data class Reachability(val ok: Boolean, val detail: String)

    data class AccountInfo(
        val username: String,
        val status: String,
        val isAuthenticated: Boolean,
        val expiresAt: Instant?,
        val activeConnections: Int,
        val maxConnections: Int,
        val allowedOutputs: List<String> = emptyList(),
    ) {
        companion object {
            internal fun from(resp: XtreamResponse): AccountInfo {
                val user = resp.userInfo
                val expiresAt = user?.expDate?.toLongOrNull()?.let { Instant.ofEpochSecond(it) }
                val outputs = user?.allowedOutput
                    ?.split(",", "|", ";", " ")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                return AccountInfo(
                    username = user?.username.orEmpty(),
                    status = user?.status.orEmpty(),
                    isAuthenticated = (user?.auth ?: 0) == 1,
                    expiresAt = expiresAt,
                    activeConnections = user?.activeCons?.toIntOrNull() ?: 0,
                    maxConnections = user?.maxConnections?.toIntOrNull() ?: 0,
                    allowedOutputs = outputs,
                )
            }
        }
    }

    @Serializable
    internal data class XtreamResponse(
        @SerialName("user_info") val userInfo: UserInfo? = null,
    )

    @Serializable
    internal data class UserInfo(
        val username: String? = null,
        val status: String? = null,
        val auth: Int = 0,
        @SerialName("exp_date") val expDate: String? = null,
        @SerialName("active_cons") val activeCons: String? = null,
        @SerialName("max_connections") val maxConnections: String? = null,
        @SerialName("allowed_output") val allowedOutput: String? = null,
    )

    companion object {
        private const val TAG = "XtreamApi"
    }
}
