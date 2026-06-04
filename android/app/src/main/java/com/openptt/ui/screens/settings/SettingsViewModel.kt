package com.openptt.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.data.local.SettingsDataStore
import com.openptt.data.local.TokenManager
import com.openptt.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val username: String = "",
    val displayName: String = "",
    val serverUrl: String = "",
    val audioQuality: String = "normal",
    val theme: String = "system",
    val showUrlDialog: Boolean = false,
    val showQualityDialog: Boolean = false,
    val showThemeDialog: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            username = tokenManager.username ?: "",
            displayName = tokenManager.displayName ?: ""
        )

        viewModelScope.launch {
            settingsDataStore.serverUrlFlow.collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url)
            }
        }

        viewModelScope.launch {
            settingsDataStore.audioQualityFlow.collect { quality ->
                _uiState.value = _uiState.value.copy(audioQuality = quality)
            }
        }

        viewModelScope.launch {
            settingsDataStore.themeFlow.collect { theme ->
                _uiState.value = _uiState.value.copy(theme = theme)
            }
        }
    }

    fun showUrlDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showUrlDialog = show)
    }

    fun showQualityDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showQualityDialog = show)
    }

    fun showThemeDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showThemeDialog = show)
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url)
            showUrlDialog(false)
        }
    }

    fun updateAudioQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setAudioQuality(quality)
            showQualityDialog(false)
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            settingsDataStore.setTheme(theme)
            showThemeDialog(false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            settingsDataStore.clearAll()
        }
    }
}
