package com.ivy.receiptscanner.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import com.ivy.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardMappingUiState(
    val accounts: List<Account> = emptyList(),
    val mappings: List<CardAccountMapping> = emptyList()
)

@HiltViewModel
class CardAccountMappingViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mappingStore: CardAccountMappingStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardMappingUiState())
    val uiState: StateFlow<CardMappingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = CardMappingUiState(
                accounts = accountRepository.findAll(),
                mappings = mappingStore.getAll()
            )
        }
    }

    fun addMapping(matchText: String, accountId: String) {
        viewModelScope.launch {
            mappingStore.add(matchText, accountId)
            refresh()
        }
    }

    fun removeMapping(matchText: String) {
        viewModelScope.launch {
            mappingStore.remove(matchText)
            refresh()
        }
    }
}
