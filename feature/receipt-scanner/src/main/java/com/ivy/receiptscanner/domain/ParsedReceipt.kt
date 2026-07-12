package com.ivy.receiptscanner.domain

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Best-effort extraction from a receipt photo.
 *
 * Every field is nullable/has a confidence flag because OCR + parsing
 * is never 100% reliable — the UI layer MUST let the user review and
 * correct these values before a transaction is actually saved. Never
 * auto-submit straight from this model.
 */
data class ParsedReceipt(
    val rawText: String,
    val merchantGuess: String?,
    val totalAmountGuess: BigDecimal?,
    val dateGuess: LocalDate?,
    val currencyGuess: String?,
    val confidence: ParseConfidence
)

enum class ParseConfidence {
    HIGH,   // total + date + merchant all matched with strong signals
    MEDIUM, // total found, but date or merchant missing/ambiguous
    LOW     // little more than raw OCR text; user must fill in manually
}
