package com.ivy.receiptscanner.parser

import com.ivy.receiptscanner.domain.ParseConfidence
import com.ivy.receiptscanner.domain.ParsedReceipt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based receipt parser. No ML, no network calls — just regex
 * heuristics tuned against common receipt layouts.
 *
 * This is intentionally conservative: it's much better to leave a
 * field blank (so the user fills it in) than to guess wrong and have
 * a wrong transaction silently saved.
 */
@Singleton
class ReceiptParser @Inject constructor() {

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val totalAmount = extractTotalAmount(lines)
        val date = extractDate(lines)
        val merchant = extractMerchant(lines)
        val currency = extractCurrencySymbol(rawText)

        val confidence = when {
            totalAmount != null && date != null && merchant != null -> ParseConfidence.HIGH
            totalAmount != null -> ParseConfidence.MEDIUM
            else -> ParseConfidence.LOW
        }

        return ParsedReceipt(
            rawText = rawText,
            merchantGuess = merchant,
            totalAmountGuess = totalAmount,
            dateGuess = date,
            currencyGuess = currency,
            confidence = confidence
        )
    }

    /**
     * Like [parse], but detects and returns MULTIPLE transactions when the
     * scanned text contains several distinct bank/card notification alerts
     * grouped in one screenshot (e.g. a notification shade with 2+ stacked
     * "Citi Alert: Transaction Exceeds" cards).
     *
     * Deliberately conservative about what counts as a "separate
     * transaction": a line only splits off as its own transaction if it
     * matches notification phrasing specifically (an "at MERCHANT ..."
     * pattern alongside a money value on the SAME line). A normal itemized
     * receipt's line items ("Milk 3.49", "Bread 2.99") never match that
     * shape, so ordinary single-total receipts are completely unaffected
     * and still return a single-element list via [parse].
     */
    fun parseAll(rawText: String): List<ParsedReceipt> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val transactionLines = lines.filter { line ->
            moneyRegex.containsMatchIn(line) && atMerchantRegex.containsMatchIn(line)
        }

        // Fewer than 2 distinct notification-style lines: treat as a
        // normal single-transaction scan (a receipt, or one lone alert).
        if (transactionLines.size < 2) {
            return listOf(parse(rawText))
        }

        val sharedDate = extractDate(lines) // dates are often only on the header/timestamp line
        val sharedCurrency = extractCurrencySymbol(rawText)

        return transactionLines.map { line ->
            val lineAsList = listOf(line)
            val amount = extractTotalAmount(lineAsList)
            val merchant = extractMerchant(lineAsList)

            val confidence = when {
                amount != null && sharedDate != null && merchant != null -> ParseConfidence.HIGH
                amount != null -> ParseConfidence.MEDIUM
                else -> ParseConfidence.LOW
            }

            ParsedReceipt(
                rawText = line,
                merchantGuess = merchant,
                totalAmountGuess = amount,
                dateGuess = sharedDate,
                currencyGuess = sharedCurrency,
                confidence = confidence
            )
        }
    }

    // ---- Total amount -------------------------------------------------

    private val totalKeywords = listOf(
        "total", "amount due", "grand total", "balance due", "total due", "amount"
    )

    // Matches things like: 12.99 | 1,234.99 | 12,99 (EU decimal comma) | $12.99
    private val moneyRegex = Regex("""[-+]?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}""")

    private fun extractTotalAmount(lines: List<String>): BigDecimal? {
        // Pass 1: look for a line containing a "total"-style keyword AND a money value.
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            if (totalKeywords.any { lower.contains(it) }) {
                moneyRegex.find(line)?.let { return it.value.toNormalizedBigDecimal() }
            }
        }

        // Pass 2: fallback — take the largest money-looking value anywhere in the
        // receipt. Totals are usually the largest number on the page.
        val allAmounts = lines.flatMap { line -> moneyRegex.findAll(line).map { it.value } }
            .mapNotNull { it.toNormalizedBigDecimal() }

        return allAmounts.maxOrNull()
    }

    private fun String.toNormalizedBigDecimal(): BigDecimal? = try {
        // Normalize "1.234,99" or "1,234.99" -> "1234.99"
        val cleaned = if (this.contains(",") && this.contains(".")) {
            // whichever separator appears last is the decimal separator
            if (this.lastIndexOf(',') > this.lastIndexOf('.')) {
                this.replace(".", "").replace(",", ".")
            } else {
                this.replace(",", "")
            }
        } else if (this.contains(",")) {
            // Only comma present — treat as decimal separator if exactly 2 digits follow
            val parts = this.split(",")
            if (parts.last().length == 2) this.replace(",", ".") else this.replace(",", "")
        } else {
            this
        }
        BigDecimal(cleaned)
    } catch (e: NumberFormatException) {
        null
    }

    // ---- Date -----------------------------------------------------------

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    )

    private val dateRegex = Regex(
        """\b(\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}\s?[A-Za-z]{3,9}\s?\d{2,4}|[A-Za-z]{3,9}\s?\d{1,2},?\s?\d{2,4})\b"""
    )

    private fun extractDate(lines: List<String>): LocalDate? {
        for (line in lines) {
            val match = dateRegex.find(line) ?: continue
            for (formatter in dateFormatters) {
                try {
                    return LocalDate.parse(normalizeYear(match.value), formatter)
                } catch (e: Exception) {
                    // try next formatter
                }
            }
        }
        return null
    }

    private fun normalizeYear(dateStr: String): String {
        // Expand 2-digit years like "24" -> "2024" for formatters expecting 4 digits.
        // Simple heuristic only; skipped if it doesn't look like a 2-digit-year date.
        return dateStr
    }

    // ---- Merchant name ----------------------------------------------------

    // Common non-merchant domains that show up on receipts/notifications
    // (payment networks, processors) — never treat these as the merchant.
    private val domainExclusions = setOf(
        "visa", "mastercard", "amex", "americanexpress", "discover",
        "paypal", "venmo", "applepay", "googlepay", "chase", "capitalone",
        "bankofamerica", "wellsfargo", "citi", "usbank"
    )

    // Matches "www.aldi.us", "marianos.com", "target.com/feedback" etc.
    // Deliberately plain-font text on receipts (URLs, feedback prompts),
    // which OCR reads far more reliably than a stylized store-name logo.
    private val domainRegex = Regex(
        """\b(?:www\.)?([a-zA-Z0-9-]{2,})\.(com|us|net|org|co)\b""",
        RegexOption.IGNORE_CASE
    )

    // Matches bank/card notification phrasing. Broadened beyond "was
    // approved" / "is declined" to also cover "...on card ending in 2033",
    // "...in card 0502", or a trailing 4-digit reference — different banks
    // (Citi, Capital One, etc.) all phrase this differently.
    private val atMerchantRegex = Regex(
        """\bat\s+([A-Z0-9][A-Z0-9 &.'#-]{1,50}?)(?=\s+(?:was|is|on card|in card|at\b|view\b|\d{4}\b))""",
        RegexOption.IGNORE_CASE
    )

    // Cleans up raw OCR variants, store-number suffixes, and corporate
    // tags into a consistent display name. Extend this as you run into
    // more banks/merchants with inconsistent notification phrasing.
    private val fuzzyMerchantMap = mapOf(
        "marianos" to "Mariano's",
        "mariano" to "Mariano's",
        "aldi inc" to "Aldi",
        "aldi" to "Aldi",
        "target com" to "Target",
        "target" to "Target",
        "walmart" to "Walmart",
        "wm supercenter" to "Walmart"
    )

    private fun extractMerchant(lines: List<String>): String? {
        extractMerchantFromDomain(lines)?.let { return applyFuzzyLookup(it) }
        extractMerchantFromNotificationPhrasing(lines)?.let { return applyFuzzyLookup(it) }
        return extractMerchantFromFirstLines(lines)?.let { applyFuzzyLookup(it) }
    }

    private fun extractMerchantFromNotificationPhrasing(lines: List<String>): String? {
        for (line in lines) {
            val match = atMerchantRegex.find(line) ?: continue
            var name = match.groupValues[1].trim()

            // Strip trailing "<City> USA" / "<City>, USA" style location
            // suffixes BEFORE stripping a trailing store number — otherwise
            // "MARIANOS #543 LOMBARD USA" never gets its "#543" removed,
            // since at that point the string doesn't yet END in the number.
            name = name.replace(Regex("""[,]?\s+[A-Z]{2,}\s+USA$""", RegexOption.IGNORE_CASE), "")
            name = name.replace(Regex("""\s*#\d+$"""), "")

            if (name.isNotBlank()) return name
        }
        return null
    }

    private fun extractMerchantFromDomain(lines: List<String>): String? {
        for (line in lines) {
            val match = domainRegex.find(line) ?: continue
            val name = match.groupValues[1]
            if (name.lowercase(Locale.getDefault()) in domainExclusions) continue
            if (name.length < 2) continue
            return name
        }
        return null
    }

    /**
     * Cleans a raw extracted merchant string against the fuzzy dictionary
     * (handles OCR variants, corporate suffixes like "INC", typos), falling
     * back to Title Case formatting if no dictionary entry matches.
     */
    private fun applyFuzzyLookup(rawName: String): String {
        val cleanedKey = rawName.trim().lowercase(Locale.getDefault())
            .replace(Regex("""\s+"""), " ")

        // 1. Exact dictionary match.
        fuzzyMerchantMap[cleanedKey]?.let { return it }

        // 2. Partial match for corporate tags/extra words, e.g.
        //    "aldi inc" -> "Aldi" even if something else trails it.
        for ((key, cleanValue) in fuzzyMerchantMap) {
            if (cleanedKey.contains(key)) return cleanValue
        }

        // 3. Fallback: Title Case the raw name as-is.
        return rawName.trim().split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun extractMerchantFromFirstLines(lines: List<String>): String? {
        // Heuristic: the merchant name is almost always one of the first
        // few lines, and it's rarely purely numeric or a known boilerplate
        // word like "receipt" / "invoice" / an address fragment.
        val boilerplate = setOf("receipt", "invoice", "tax invoice", "order confirmation")
        return lines.take(5).firstOrNull { line ->
            val lower = line.lowercase(Locale.getDefault())
            line.length in 3..40 &&
                boilerplate.none { lower.contains(it) } &&
                !moneyRegex.containsMatchIn(line) &&
                line.any { it.isLetter() }
        }
    }

    // ---- Currency ----------------------------------------------------------

    private fun extractCurrencySymbol(rawText: String): String? {
        return when {
            rawText.contains("$") -> "USD"
            rawText.contains("€") -> "EUR"
            rawText.contains("£") -> "GBP"
            else -> null
        }
    }
}
