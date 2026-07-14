package com.ivy.receiptscanner.parser

import com.ivy.base.model.TransactionType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort classification of INCOME vs EXPENSE vs TRANSFER from raw
 * OCR'd text (receipts, but also bank/payment app notification screenshots
 * — "Your $15.16 external transfer to SAMSCLUB MSTRCRD..." etc).
 *
 * This is a *suggestion* only — always shown as an editable/overridable
 * choice on the review screen, never silently applied. Bank wording for
 * "transfer" often actually means a credit-card payment or purchase, so
 * getting this wrong sometimes is expected; the point is to save a tap
 * in the common case, not to be authoritative.
 */
@Singleton
class TransactionTypeClassifier @Inject constructor() {

    // Order matters: checked top to bottom, first match wins.
    private val transferKeywords = listOf(
        "external transfer", "internal transfer", "transfer to", "transfer from",
        "moved to", "moved from", "sent to your", "account transfer"
    )

    private val incomeKeywords = listOf(
        "deposit", "received", "credited", "payroll", "direct deposit",
        "refund", "cash back", "reimbursement", "you received"
    )

    private val expenseKeywords = listOf(
        "purchase", "payment to", "debit", "charged", "spent at",
        "you paid", "withdrawal", "pos transaction"
    )

    fun classify(rawText: String): TransactionType {
        val lower = rawText.lowercase(Locale.getDefault())

        if (transferKeywords.any { lower.contains(it) }) return TransactionType.TRANSFER
        if (incomeKeywords.any { lower.contains(it) }) return TransactionType.INCOME
        if (expenseKeywords.any { lower.contains(it) }) return TransactionType.EXPENSE

        // Default: most scanned receipts are purchases.
        return TransactionType.EXPENSE
    }
}
