package com.ivy.receiptscanner.notification

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt-injectable wrapper so Compose screens outside this module
 * (e.g. MainScreen) can check for a pending notification-detected
 * transaction without needing their own DataStore wiring.
 */
@HiltViewModel
class PendingScanViewModel @Inject constructor(
    private val pendingStore: PendingScannedTransactionStore
) : ViewModel() {
    suspend fun consumePending(): PendingScannedTransaction? = pendingStore.consume()
}
