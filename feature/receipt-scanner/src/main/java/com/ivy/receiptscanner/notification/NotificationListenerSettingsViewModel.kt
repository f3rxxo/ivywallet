package com.ivy.receiptscanner.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationListenerSettingsUiState(
    val enabled: Boolean = false,
    val packages: List<String> = emptyList()
)

@HiltViewModel
class NotificationListenerSettingsViewModel @Inject constructor(
    private val configStore: NotificationListenerConfigStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationListenerSettingsUiState())
    val uiState: StateFlow<NotificationListenerSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = NotificationListenerSettingsUiState(
                enabled = configStore.isEnabled(),
                packages = configStore.getAllowedPackages().sorted()
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configStore.setEnabled(enabled)
            refresh()
        }
    }

    fun addPackage(packageName: String) {
        viewModelScope.launch {
            configStore.addPackage(packageName)
            refresh()
        }
    }

    fun removePackage(packageName: String) {
        viewModelScope.launch {
            configStore.removePackage(packageName)
            refresh()
        }
    }
}
