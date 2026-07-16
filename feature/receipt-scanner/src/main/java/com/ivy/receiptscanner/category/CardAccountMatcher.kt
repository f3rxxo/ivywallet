package com.ivy.receiptscanner.category

import com.ivy.data.model.AccountId
import com.ivy.data.repository.AccountRepository
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardAccountMatcher @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mappingStore: CardAccountMappingStore
) {

    // Matches masked card numbers: "...0502", "****0502", "xxxx0502", "x0502"
    private val maskedNumberRegex = Regex(
        """(?:\.{3,}|x{2,}|\*{2,}|#)\s?(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * @return a matched AccountId, or null if nothing in [text] matches a
     * saved mapping. Checks masked last-4-digit numbers first (most
     * specific/reliable), then falls back to any stored nickname/bank-name
     * substring appearing anywhere in the text.
     */
    suspend fun matchAccount(text: String?): AccountId? {
        if (text.isNullOrBlank()) return null
        val mappings = mappingStore.getAll()
        if (mappings.isEmpty()) return null

        val upper = text.uppercase(Locale.ROOT)

        // 1. Masked last-4 digits, e.g. "Savor Credit Card...0502"
        for (match in maskedNumberRegex.findAll(text)) {
            val last4 = match.groupValues[1]
            val mapped = mappings.firstOrNull { it.matchText == last4 }
            if (mapped != null) {
                toAccountIdOrNull(mapped.accountId)?.let { return it }
            }
        }

        // 2. Free-text nickname/bank-name match, e.g. "CAPITAL ONE", "SAVOR"
        val mapped = mappings.firstOrNull { upper.contains(it.matchText) }
        if (mapped != null) {
            toAccountIdOrNull(mapped.accountId)?.let { return it }
        }

        return null
    }

    private suspend fun toAccountIdOrNull(raw: String): AccountId? = try {
        val id = AccountId(UUID.fromString(raw))
        // Verify the account still exists (wasn't deleted since mapping was made).
        if (accountRepository.findById(id) != null) id else null
    } catch (e: IllegalArgumentException) {
        null
    }
}
