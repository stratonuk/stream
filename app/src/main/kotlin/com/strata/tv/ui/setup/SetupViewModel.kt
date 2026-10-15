package com.strata.tv.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strata.tv.data.settings.AppSettings
import com.strata.tv.data.settings.ProviderConfig
import com.strata.tv.data.settings.SettingsRepository
import com.strata.tv.data.settings.XtreamApi
import com.strata.tv.data.settings.XtreamServerUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * IPTV Smarters Pro–compatible "Xtreme Codes" login.
 *
 * Single form: playlist name, server URL, username, password → Connect.
 * No provider presets. Uses universal player_api.php authentication.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val xtreamApi: XtreamApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun updateField(field: Field, value: String) {
        _state.update { current ->
            when (field) {
                Field.PlaylistName -> current.copy(playlistName = value)
                Field.Host -> current.copy(providerHost = value)
                Field.Username -> current.copy(username = value)
                Field.Password -> current.copy(password = value)
            }
        }
    }

    /**
     * Authenticate against player_api.php then persist credentials.
     * Runs a short diagnostic so catalogue failures are not reported as
     * login failures.
     */
    fun connect() {
        val s = _state.value
        val normalized = XtreamServerUrl.normalize(s.providerHost)
        val host = normalized.getOrElse { err ->
            _state.update { it.copy(errorMessage = err.message ?: "Invalid server URL") }
            return
        }

        if (s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Username and password are required.") }
            return
        }

        val config = ProviderConfig(
            providerId = "custom_xtream",
            connectionName = s.playlistName.ifBlank { "Xtream" },
            host = host,
            username = s.username.trim(),
            password = s.password,
        )

        viewModelScope.launch {
            _state.update {
                it.copy(
                    testing = true,
                    errorMessage = null,
                    httpWarning = if (XtreamServerUrl.isHttp(host)) {
                        "Warning: credentials will be sent over HTTP (not encrypted)."
                    } else {
                        null
                    },
                    diagnostics = emptyList(),
                )
            }
            try {
                val steps = xtreamApi.runDiagnostics(config)
                val auth = steps.firstOrNull { it.name == "Xtream authentication" }
                if (auth?.ok != true) {
                    _state.update {
                        it.copy(
                            testing = false,
                            diagnostics = steps,
                            errorMessage = auth?.detail
                                ?: "Could not authenticate. Check server URL, username and password.",
                        )
                    }
                    return@launch
                }

                // Persist with UK/English defaults (same as previous wizard defaults).
                settings.setProvider(config)
                settings.setCountryWhitelist(AppSettings.DEFAULT_COUNTRY_WHITELIST)
                settings.setWantedLanguages(AppSettings.DEFAULT_WANTED_LANGUAGES)

                val catalogueOk = steps.any {
                    it.name in listOf("Live streams", "Movies", "Series") && it.ok
                }
                _state.update {
                    it.copy(
                        testing = false,
                        diagnostics = steps,
                        finished = true,
                        errorMessage = if (!catalogueOk) {
                            "Logged in, but catalogue probes were incomplete. Sync will retry."
                        } else {
                            null
                        },
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        testing = false,
                        errorMessage = "Connection failed: ${t.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private fun MutableStateFlow<SetupState>.update(block: (SetupState) -> SetupState) {
        value = block(value)
    }

    enum class Field { PlaylistName, Host, Username, Password }
}

data class SetupState(
    val playlistName: String = "My IPTV",
    val providerHost: String = "",
    val username: String = "",
    val password: String = "",
    val testing: Boolean = false,
    val errorMessage: String? = null,
    val httpWarning: String? = null,
    val diagnostics: List<XtreamApi.DiagnosticStep> = emptyList(),
    val finished: Boolean = false,
)
