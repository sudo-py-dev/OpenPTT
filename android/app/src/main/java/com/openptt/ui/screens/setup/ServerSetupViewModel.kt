package com.openptt.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.data.local.SettingsDataStore
import com.openptt.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSetupState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSetupState())
    val uiState: StateFlow<ServerSetupState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.serverUrlFlow.collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url)
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            isConnected = false,
            statusMessage = null,
            error = null
        )
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.testConnection(_uiState.value.serverUrl)
            result.fold(
                onSuccess = { health ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = true,
                        statusMessage = "Connected! Server v${health.version}",
                        error = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = false,
                        statusMessage = "Connection failed: ${e.message}",
                        error = e.message
                    )
                }
            )
        }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(_uiState.value.serverUrl)
        }
    }
}
