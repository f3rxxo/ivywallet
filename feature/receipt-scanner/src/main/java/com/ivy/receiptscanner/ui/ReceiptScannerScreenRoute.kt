package com.ivy.receiptscanner.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.navigation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Registers in IvyNavGraph as the destination for `ReceiptScannerScreen`.
 * Handles the actual navigation: on confirm, opens the existing Add
 * Transaction screen pre-filled with the scanned values so the user can
 * do a final check before saving. Nothing is written to the database here.
 */
@Composable
fun BoxWithConstraintsScope.ReceiptScannerScreenRoute() {
    val nav = navigation()

    ReceiptScannerScreen(
        onConfirm = { merchant, amount, dateIso, categoryId ->
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.EXPENSE,
                    categoryId = categoryId?.value,
                    initialAmount = amount?.toDouble(),
                    initialTitle = merchant.ifBlank { null },
                    initialDateTime = dateIso?.toInstantOrNull()
                )
            )
        },
        onCancel = { nav.back() }
    )
}

private fun String.toInstantOrNull(): Instant? = try {
    LocalDate.parse(this)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
} catch (e: Exception) {
    null
}
