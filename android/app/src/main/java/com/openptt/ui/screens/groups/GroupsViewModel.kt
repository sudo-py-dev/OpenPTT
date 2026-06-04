package com.openptt.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.data.remote.api.ChannelDto
import com.openptt.data.remote.api.GroupDetailDto
import com.openptt.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsState(
    val group: GroupDetailDto? = null,
    val channels: List<ChannelDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateChannelDialog: Boolean = false
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsState())
    val uiState: StateFlow<GroupsState> = _uiState.asStateFlow()

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = groupRepository.getGroup(groupId)
            result.fold(
                onSuccess = { detail ->
                    _uiState.value = _uiState.value.copy(
                        group = detail,
                        channels = detail.channels,
                        isLoading = false
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

    fun showCreateChannelDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateChannelDialog = show)
    }

    fun createChannel(groupId: String, name: String, description: String?, isPriority: Boolean) {
        viewModelScope.launch {
            val type = if (isPriority) "priority" else "voice"
            val result = groupRepository.createChannel(groupId, name, description, type, 50)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(showCreateChannelDialog = false)
                loadGroup(groupId)
            }
        }
    }
}
