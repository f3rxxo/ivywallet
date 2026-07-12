package com.ivy.receiptscanner.category

import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a merchant/receipt text to a real CategoryId, or null if there's
 * no confident match. Never guesses when unsure — a wrong auto-assigned
 * category is worse than none, since the review screen lets the user pick
 * one anyway.
 *
 * Priority:
 *  1. User-taught overrides (MerchantCategoryOverrideStore) — these win,
 *     since they reflect an explicit correction the person already made.
 *  2. Built-in dictionary (DefaultMerchantCategoryDictionary).
 *
 * Either way, the *name* still has to resolve to a category the user
 * actually has — categories are fully user-defined in Ivy Wallet, so
 * "Groceries" only works if such a category exists. Matching re-uses the
 * same case-insensitive name comparison as the existing CSV importer
 * (see CSVImporterV2.mapCategory) for consistency.
 */
@Singleton
class MerchantCategoryMatcher @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val overrideStore: MerchantCategoryOverrideStore
) {

    suspend fun matchCategory(merchantOrReceiptText: String?): CategoryId? {
        if (merchantOrReceiptText.isNullOrBlank()) return null

        // 1. User-taught override — already stores a concrete CategoryId.
        overrideStore.findCategoryId(merchantOrReceiptText)?.let { return CategoryId(it) }

        // 2. Built-in dictionary suggests a category *name*; resolve it
        //    against the user's real categories.
        val suggestedName = DefaultMerchantCategoryDictionary
            .suggestCategoryName(merchantOrReceiptText) ?: return null

        val categories = categoryRepository.findAll()
        return categories.firstOrNull { category ->
            category.name.value.equals(suggestedName, ignoreCase = true)
        }?.id
    }

    /** Call after the user confirms a transaction, to teach future matches. */
    suspend fun learn(merchantText: String, categoryId: CategoryId) {
        overrideStore.learn(merchantText, categoryId.value)
    }
}
