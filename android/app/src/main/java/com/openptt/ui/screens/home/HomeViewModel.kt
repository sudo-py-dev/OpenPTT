package com.openptt.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.data.remote.api.GroupDto
import com.openptt.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val groups: List<GroupDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val showFabMenu: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = groupRepository.listGroups()
            result.fold(
                onSuccess = { groups ->
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        isLoading = false,
                        error = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun toggleFabMenu(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFabMenu = show)
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, showFabMenu = false)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun showJoinDialog() {
        _uiState.value = _uiState.value.copy(showJoinDialog = true, showFabMenu = false)
    }

    fun dismissJoinDialog() {
        _uiState.value = _uiState.value.copy(showJoinDialog = false)
    }

    fun createGroup(name: String, description: String?) {
        viewModelScope.launch {
            val result = groupRepository.createGroup(name, description, true)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(showCreateDialog = false)
                loadGroups()
            }
        }
    }

    fun joinGroup(groupId: String, inviteCode: String?) {
        viewModelScope.launch {
            val result = groupRepository.joinGroup(groupId, inviteCode)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(showJoinDialog = false)
                loadGroups()
            }
        }
    }
}
