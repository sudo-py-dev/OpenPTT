package com.openptt.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginState())
    val uiState: StateFlow<LoginState> = _uiState.asStateFlow()

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value, error = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            error = null
        )
    }

    fun submit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = if (_uiState.value.isRegisterMode) {
                authRepository.register(
                    username = _uiState.value.username,
                    password = _uiState.value.password,
                    displayName = _uiState.value.displayName.ifBlank { _uiState.value.username }
                )
            } else {
                authRepository.login(
                    username = _uiState.value.username,
                    password = _uiState.value.password
                )
            }

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Authentication failed"
                    )
                }
            )
        }
    }
}
