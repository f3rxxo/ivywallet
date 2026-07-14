package com.ivy.receiptscanner.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PendingScannedTransaction(
    val merchant: String?,
    val amount: String?, // BigDecimal as plain string
    val dateIso: String?, // yyyy-MM-dd
    val categoryId: String?, // UUID as string
    val type: String // TransactionType.name
)

/**
 * Simple single-slot handoff: the notification listener service (running
 * in the background, possibly with no Activity/Compose context available)
 * writes one pending item here and posts a system notification. When the
 * user taps that notification and the app opens, MainScreen reads and
 * clears this slot, then navigates to the pre-filled review screen —
 * same review-before-save flow as scanning a receipt.
 *
 * A DataStore-backed single slot (rather than in-memory) survives the
 * process being killed between the notification firing and the user
 * tapping it.
 */
@Singleton
class PendingScannedTransactionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringPreferencesKey("receipt_scanner_pending_scanned_transaction")

    suspend fun set(transaction: PendingScannedTransaction) {
        dataStore.edit { prefs ->
            prefs[key] = Json.encodeToString(transaction)
        }
    }

    /** Reads and clears the pending item, if any. */
    suspend fun consume(): PendingScannedTransaction? {
        val raw = dataStore.data.first()[key] ?: return null
        dataStore.edit { prefs -> prefs.remove(key) }
        return try {
            Json.decodeFromString<PendingScannedTransaction>(raw)
        } catch (e: Exception) {
            null
        }
    }
}
