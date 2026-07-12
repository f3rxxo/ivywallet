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
}
