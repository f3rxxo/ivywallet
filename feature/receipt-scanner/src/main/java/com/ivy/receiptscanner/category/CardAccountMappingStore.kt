package com.ivy.receiptscanner.category

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
data class CardAccountMapping(
    // What to look for in receipt/notification text: a last-4-digit
    // string ("0502"), a card nickname ("SAVOR"), or a bank name
    // ("CAPITAL ONE") — whatever uniquely identifies this account in
    // the text you actually see on your receipts/notifications.
    val matchText: String,
    val accountId: String // UUID as string
)

/**
 * Maps card/account identifiers you recognize (last-4 digits, card
 * nickname, bank name) to a real Ivy Wallet AccountId, so the scanner can
 * auto-select which account a transaction belongs to.
 *
 * Same DataStore-backed pattern as MerchantCategoryOverrideStore — no new
 * Room table/migration needed.
 */
@Singleton
class CardAccountMappingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringPreferencesKey("receipt_scanner_card_account_mappings")

    suspend fun getAll(): List<CardAccountMapping> {
        val raw = dataStore.data.first()[key] ?: return emptyList()
        return try {
            Json.decodeFromString<List<CardAccountMapping>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(matchText: String, accountId: String) {
        val trimmed = matchText.trim().uppercase()
        if (trimmed.isBlank()) return
        val current = getAll().filterNot { it.matchText == trimmed }
        val updated = current + CardAccountMapping(trimmed, accountId)
        val serialized = Json.encodeToString<List<CardAccountMapping>>(updated)
        dataStore.edit { prefs -> prefs[key] = serialized }
    }

    suspend fun remove(matchText: String) {
        val current = getAll().filterNot { it.matchText == matchText.trim().uppercase() }
        val serialized = Json.encodeToString<List<CardAccountMapping>>(current)
        dataStore.edit { prefs -> prefs[key] = serialized }
    }
}
