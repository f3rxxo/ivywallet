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
 *
 * IMPORTANT: OCR wraps text into arbitrary physical lines that don't
 * line up with sentence boundaries — "at MARIANOS #543" and "LOMBARD
 * USA on card ending in 2033" can easily land on two different lines
 * even though they're one sentence. Every extraction strategy here
 * (except the receipt-logo first-lines heuristic, which genuinely needs
 * line structure) runs against a FLATTENED version of the text — all
 * lines joined with spaces — specifically so a merchant phrase spanning
 * a line break still matches. See [flatten].
 */
@Singleton
class ReceiptParser @Inject constructor() {

    fun parse(rawText: String): ParsedReceipt {
        val flattened = flatten(rawText)
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val totalAmount = extractTotalAmount(flattened)
        val date = extractDate(flattened)
        val merchant = extractMerchant(flattened, lines)
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
     * Splits the FLATTENED text into chunks anchored at each money-value
     * match (bank-phrasing-agnostic — doesn't need to know each bank's
     * exact wording, unlike a per-sentence regex would). A chunk only
     * counts as a genuine separate transaction if it ALSO yields a real
     * merchant match via phrasing or domain extraction — this guards
     * against two failure modes:
     *   1. A single alert with a decoy second dollar amount (e.g. Chase's
     *      "...was more than the $0.00 limit in your Alerts settings")
     *      incorrectly being split into two fake transactions.
     *   2. A normal itemized receipt ("Milk 3.49", "Bread 2.99", "TOTAL
     *      12.48") being exploded into fake line-item "transactions" —
     *      those lines have no "at MERCHANT" phrasing at all, so they
     *      never produce a qualifying chunk.
     */
    fun parseAll(rawText: String): List<ParsedReceipt> {
        val flattened = flatten(rawText)
        val moneyMatches = moneyRegex.findAll(flattened).toList()

        if (moneyMatches.size < 2) {
            return listOf(parse(rawText))
        }

        data class Candidate(val amount: BigDecimal?, val merchant: String?, val chunk: String)

        val candidates = moneyMatches.mapIndexed { index, match ->
            val chunkStart = match.range.first
            val chunkEnd = moneyMatches.getOrNull(index + 1)?.range?.first ?: flattened.length
            val chunk = flattened.substring(chunkStart, chunkEnd)
            val amount = match.value.toNormalizedBigDecimal()
            // Strict extraction only (phrasing/domain) — deliberately skips
            // the first-lines fallback here, since that heuristic isn't
            // meaningful on an arbitrary substring chunk and would produce
            // false positives.
            val merchant = extractMerchantFromNotificationPhrasing(chunk)
                ?: extractMerchantFromDomain(chunk)
            Candidate(amount, merchant?.let { applyFuzzyLookup(it) }, chunk)
        }

        val qualifying = candidates.filter { it.amount != null && it.merchant != null }

        if (qualifying.size < 2) {
            return listOf(parse(rawText))
        }

        val sharedDate = extractDate(flattened) // dates are often only on a header/timestamp line
        val sharedCurrency = extractCurrencySymbol(rawText)

        return qualifying.map { candidate ->
            ParsedReceipt(
                rawText = candidate.chunk,
                merchantGuess = candidate.merchant,
                totalAmountGuess = candidate.amount,
                dateGuess = sharedDate,
                currencyGuess = sharedCurrency,
                confidence = if (sharedDate != null) ParseConfidence.HIGH else ParseConfidence.MEDIUM
            )
        }
    }

    /** Joins OCR'd lines into one string so a phrase split across a line
     * wrap (e.g. by notification text reflow) can still be matched as one
     * continuous sentence. */
    private fun flatten(rawText: String): String =
        rawText.lines().joinToString(" ") { it.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()

    // ---- Total amount -------------------------------------------------

    private val totalKeywords = listOf(
        "total", "amount due", "grand total", "balance due", "total due", "amount"
    )

    // Matches things like: 12.99 | 1,234.99 | 12,99 (EU decimal comma) | $12.99
    private val moneyRegex = Regex("""[-+]?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}""")

    private fun extractTotalAmount(text: String): BigDecimal? {
        // Pass 1: a "total"-style keyword with a money value reasonably
        // close after it (same line/sentence, not the whole rest of the text).
        val lowerText = text.lowercase(Locale.getDefault())
        for (keyword in totalKeywords) {
            val keywordIndex = lowerText.indexOf(keyword)
            if (keywordIndex == -1) continue
            val window = text.substring(keywordIndex, minOf(text.length, keywordIndex + keyword.length + 20))
            moneyRegex.find(window)?.let { return it.value.toNormalizedBigDecimal() }
        }

        // Pass 2: fallback — take the largest money-looking value anywhere
        // in the text. Totals (and real transaction amounts, vs. decoy
        // alert-limit values like "$0.00") are usually the largest number.
        return moneyRegex.findAll(text)
            .mapNotNull { it.value.toNormalizedBigDecimal() }
            .maxOrNull()
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

    private fun extractDate(text: String): LocalDate? {
        for (match in dateRegex.findAll(text)) {
            for (formatter in dateFormatters) {
                try {
                    return LocalDate.parse(match.value, formatter)
                } catch (e: Exception) {
                    // try next formatter
                }
            }
        }
        return null
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

    // Matches "...at MERCHANT was/is/on card/in card/view/<4-digit-ref>".
    // Different banks phrase the tail differently (Citi: "on card ending
    // in 2033"; Capital One: "was approved") — this covers the common ones.
    private val atMerchantRegex = Regex(
        """\bat\s+([A-Z0-9][A-Z0-9 &.'#-]{1,50}?)(?=\s+(?:was|is|on card|in card|at\b|view\b|\d{4}\b))""",
        RegexOption.IGNORE_CASE
    )

    // Matches "...transfer to MERCHANT on/at ..." — Chase-style phrasing
    // that doesn't use "at MERCHANT" at all (it's "to MERCHANT" instead).
    private val toMerchantRegex = Regex(
        """\btransfer\s+to\s+([A-Z0-9][A-Z0-9 &.'#-]{1,50}?)(?=\s+(?:on|at|was|is)\b)""",
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
        "wm supercenter" to "Walmart",
        "samsclub mstrcrd" to "Sam's Club",
        "samsclub" to "Sam's Club",
        "sams club" to "Sam's Club"
    )

    // System-UI noise that can leak into OCR text from a full-screen
    // (not cropped-to-notification) screenshot — status bar clock, quick
    // settings tile labels, etc. Used only to skip obviously-wrong
    // first-lines fallback candidates, not a general block on merchant names.
    private val systemUiNoiseRegex = Regex(
        """^\d{1,2}:\d{2}\s|^(mon|tue|wed|thu|fri|sat|sun),|^(internet|bluetooth|wi-?fi|airplane|silent|do not disturb|clear all)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun extractMerchant(flattened: String, lines: List<String>): String? {
        extractMerchantFromDomain(flattened)?.let { return applyFuzzyLookup(it) }
        extractMerchantFromNotificationPhrasing(flattened)?.let { return applyFuzzyLookup(it) }
        return extractMerchantFromFirstLines(lines)?.let { applyFuzzyLookup(it) }
    }

    private fun extractMerchantFromNotificationPhrasing(text: String): String? {
        val match = atMerchantRegex.find(text) ?: toMerchantRegex.find(text) ?: return null
        var name = match.groupValues[1].trim()

        // Strip trailing "<City> USA" / "<City>, USA" style location
        // suffixes BEFORE stripping a trailing store number — otherwise
        // "MARIANOS #543 LOMBARD USA" never gets its "#543" removed,
        // since at that point the string doesn't yet END in the number.
        name = name.replace(Regex("""[,]?\s+[A-Z]{2,}\s+USA$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""\s*#\d+$"""), "")

        return name.ifBlank { null }
    }

    private fun extractMerchantFromDomain(text: String): String? {
        val match = domainRegex.find(text) ?: return null
        val name = match.groupValues[1]
        if (name.lowercase(Locale.getDefault()) in domainExclusions) return null
        if (name.length < 2) return null
        return name
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
        //
        // NOTE: this heuristic is inherently unreliable on a FULL-SCREEN
        // screenshot (status bar, quick-settings tiles, etc. all count as
        // "first lines" too) — cropping to just the notification content
        // before scanning gives much better results.
        val boilerplate = setOf("receipt", "invoice", "tax invoice", "order confirmation")
        return lines.take(8).firstOrNull { line ->
            val lower = line.lowercase(Locale.getDefault())
            line.length in 3..40 &&
                boilerplate.none { lower.contains(it) } &&
                !systemUiNoiseRegex.containsMatchIn(line) &&
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
