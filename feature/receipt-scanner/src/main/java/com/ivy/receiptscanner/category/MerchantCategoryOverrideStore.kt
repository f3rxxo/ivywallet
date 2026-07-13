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
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MerchantCategoryOverride(
    // Uppercased substring to match against merchant/receipt text, e.g. "ALDI"
    val merchantPattern: String,
    val categoryId: String // UUID as string
)

/**
 * Stores user corrections like "ALDI -> Household" (overriding the built-in
 * "ALDI -> Groceries" guess) so the app learns from what the person actually
 * picks on the review screen, without needing a new Room table/migration.
 *
 * Piggybacks on Ivy Wallet's existing app-wide Preferences DataStore
 * (see com.ivy.data.datastore.Datastore) — this class just owns one key
 * in it, JSON-encoded.
 */
@Singleton
class MerchantCategoryOverrideStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringPreferencesKey("receipt_scanner_merchant_category_overrides")

    suspend fun getAll(): List<MerchantCategoryOverride> {
        val raw = dataStore.data.first()[key] ?: return emptyList()
        return try {
            Json.decodeFromString<List<MerchantCategoryOverride>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Looks for a stored override whose pattern is contained in [text]. */
    suspend fun findCategoryId(text: String): UUID? {
        val upper = text.uppercase(Locale.ROOT)
        val match = getAll().firstOrNull { upper.contains(it.merchantPattern) } ?: return null
        return try {
            UUID.fromString(match.categoryId)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Call this when the user confirms a transaction with a merchant name
     * and a chosen category — teaches the matcher for next time.
     */
    suspend fun learn(merchantText: String, categoryId: UUID) {
        val pattern = merchantText.trim().uppercase(Locale.ROOT).take(40)
        if (pattern.isBlank()) return

        val current = getAll().filterNot { it.merchantPattern == pattern }
        val updated = current + MerchantCategoryOverride(
            merchantPattern = pattern,
            categoryId = categoryId.toString()
        )

        val serialized = Json.encodeToString<List<MerchantCategoryOverride>>(updated)
        dataStore.edit { prefs ->
            prefs[key] = serialized
        }
    }
}
