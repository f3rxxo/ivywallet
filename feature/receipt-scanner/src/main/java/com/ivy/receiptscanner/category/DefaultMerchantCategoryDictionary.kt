package com.ivy.receiptscanner.category

import java.util.Locale

/**
 * Built-in, best-effort merchant -> category-name dictionary.
 * Matching is substring-based and case-insensitive, since OCR'd merchant
 * text is messy (e.g. "ALDI SUD 4471" still needs to match "ALDI").
 *
 * This only returns a category *name* (e.g. "Groceries"). Resolving that
 * name to a real CategoryId (which depends on the user's own categories)
 * happens in MerchantCategoryMatcher.
 */
object DefaultMerchantCategoryDictionary {

    // Keep entries UPPERCASE; matching lowercases both sides.
    private val rules: List<Pair<String, String>> = listOf(
        // Groceries
        "ALDI" to "Groceries",
        "LIDL" to "Groceries",
        "WALMART" to "Groceries",
        "TESCO" to "Groceries",
        "TRADER JOE" to "Groceries",
        "WHOLE FOODS" to "Groceries",
        "KROGER" to "Groceries",
        "SAFEWAY" to "Groceries",
        "COSTCO" to "Groceries",
        "CARREFOUR" to "Groceries",
        "SAINSBURY" to "Groceries",

        // Dining / coffee
        "STARBUCKS" to "Dining",
        "MCDONALD" to "Dining",
        "BURGER KING" to "Dining",
        "KFC" to "Dining",
        "SUBWAY" to "Dining",
        "DOMINO" to "Dining",
        "PIZZA HUT" to "Dining",

        // Transport / fuel
        "SHELL" to "Transport",
        "BP " to "Transport",
        "EXXON" to "Transport",
        "CHEVRON" to "Transport",
        "UBER" to "Transport",
        "LYFT" to "Transport",

        // Pharmacy / health
        "WALGREENS" to "Health",
        "CVS" to "Health",
        "BOOTS" to "Health",

        // Home / general retail
        "IKEA" to "Household",
        "HOME DEPOT" to "Household",
        "TARGET" to "Household",

        // Entertainment
        "NETFLIX" to "Entertainment",
        "SPOTIFY" to "Entertainment",
        "CINEMA" to "Entertainment"
    )

    /** @return a suggested category name, or null if nothing matched. */
    fun suggestCategoryName(merchantOrReceiptText: String?): String? {
        if (merchantOrReceiptText.isNullOrBlank()) return null
        val upper = merchantOrReceiptText.uppercase(Locale.ROOT)
        return rules.firstOrNull { (pattern, _) -> upper.contains(pattern) }?.second
    }
}
