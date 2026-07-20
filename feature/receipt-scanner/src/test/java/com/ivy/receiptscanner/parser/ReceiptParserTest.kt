package com.ivy.receiptscanner.parser

import com.ivy.receiptscanner.domain.ParseConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

class ReceiptParserTest {

    private val parser = ReceiptParser()

    @Test
    fun `extracts total amount from keyword line`() {
        val text = """
            Trader Joe's
            123 Main St
            Milk           3.49
            Bread          2.99
            TOTAL         12.48
            Thank you!
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(BigDecimal("12.48"), result.totalAmountGuess)
    }

    @Test
    fun `falls back to largest amount when no total keyword found`() {
        val text = """
            Corner Cafe
            Coffee   4.50
            Muffin   3.25
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(BigDecimal("4.50"), result.totalAmountGuess)
    }

    @Test
    fun `handles european decimal comma format`() {
        val text = """
            Bakkerij Jansen
            Totaal   12,50
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(BigDecimal("12.50"), result.totalAmountGuess)
    }

    @Test
    fun `guesses merchant from first non-boilerplate line`() {
        val text = """
            Whole Foods Market
            RECEIPT
            Total 20.00
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals("Whole Foods Market", result.merchantGuess)
    }

    @Test
    fun `confidence is HIGH when merchant, date and amount all found`() {
        val text = """
            Joe's Diner
            2024-03-15
            Total 15.00
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(ParseConfidence.HIGH, result.confidence)
        assertNotNull(result.dateGuess)
    }

    @Test
    fun `confidence is LOW when nothing useful found`() {
        val text = "garbled unreadable text with no numbers"

        val result = parser.parse(text)

        assertEquals(ParseConfidence.LOW, result.confidence)
    }

    @Test
    fun `extracts merchant from Citi-style notification with card-ending phrasing`() {
        // Regression test: the original "was|is" only lookahead couldn't
        // match this phrasing at all, since neither word follows "at ...".
        val text = "A \$6.06 transaction was made at MARIANOS #543 LOMBARD USA " +
            "on card ending in 2033."

        val result = parser.parse(text)

        assertEquals("Mariano's", result.merchantGuess)
        assertEquals(BigDecimal("6.06"), result.totalAmountGuess)
    }

    @Test
    fun `strips trailing store number even when a city name follows it`() {
        // Regression test: stripping the trailing city suffix must happen
        // BEFORE stripping the trailing store number, or the number
        // survives (e.g. "COSTCO #123" instead of "Costco") for any
        // merchant not covered by the fuzzy dictionary's contains() fallback.
        val text = "A \$99.10 transaction was made at COSTCO WHSE #123 LOMBARD USA " +
            "on card ending in 2033."

        val result = parser.parse(text)

        assertEquals("Costco Whse", result.merchantGuess)
    }

    @Test
    fun `fuzzy dictionary normalizes corporate suffix variants`() {
        val text = "A \$35.52 transaction was made at ALDI INC LOMBARD USA " +
            "on card ending in 2033."

        val result = parser.parse(text)

        assertEquals("Aldi", result.merchantGuess)
        assertEquals(BigDecimal("35.52"), result.totalAmountGuess)
    }

    @Test
    fun `parseAll detects multiple grouped notification alerts`() {
        val text = """
            Citi Alert: Transaction Exceeds
            A ${'$'}6.06 transaction was made at MARIANOS #543 LOMBARD USA on card ending in 2033.
            Citi Alert: Transaction Exceeds
            A ${'$'}35.52 transaction was made at ALDI INC LOMBARD USA on card ending in 2033.
        """.trimIndent()

        val results = parser.parseAll(text)

        assertEquals(2, results.size)
        assertEquals("Mariano's", results[0].merchantGuess)
        assertEquals(BigDecimal("6.06"), results[0].totalAmountGuess)
        assertEquals("Aldi", results[1].merchantGuess)
        assertEquals(BigDecimal("35.52"), results[1].totalAmountGuess)
    }

    @Test
    fun `parseAll does not explode a normal itemized receipt into fake transactions`() {
        // Regression guard: line items like "Milk 3.49" must NOT match the
        // notification-style splitting, since they have no "at MERCHANT"
        // phrasing. A normal receipt should still come back as ONE result.
        val text = """
            Trader Joe's
            123 Main St
            Milk           3.49
            Bread          2.99
            TOTAL         12.48
            Thank you!
        """.trimIndent()

        val results = parser.parseAll(text)

        assertEquals(1, results.size)
        assertEquals(BigDecimal("12.48"), results[0].totalAmountGuess)
    }

    @Test
    fun `parseAll falls back to single result for a lone notification alert`() {
        val text = "A \$6.06 transaction was made at MARIANOS #543 LOMBARD USA " +
            "on card ending in 2033."

        val results = parser.parseAll(text)

        assertEquals(1, results.size)
        assertEquals("Mariano's", results[0].merchantGuess)
    }
}
