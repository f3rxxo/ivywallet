package com.ivy.receiptscanner.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import com.ivy.base.model.TransactionType
import com.ivy.navigation.CardAccountMappingScreen
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.NotificationListenerSettingsScreen
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
        onConfirm = { merchant, amount, dateIso, categoryId, type, accountId ->
            // Pop this scanner screen off the back stack BEFORE pushing the
            // Edit Transaction screen, so that when the user taps Save
            // there, it returns to Home instead of back to this scan
            // screen. (EditTransactionViewModel's save() just calls
            // nav.back() — whatever is directly under it on the stack.)
            nav.back()
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = type,
                    accountId = accountId?.value,
                    // Category only makes sense for expense/income, not transfers.
                    categoryId = if (type == TransactionType.TRANSFER) null else categoryId?.value,
                    initialAmount = amount?.toDouble(),
                    initialTitle = merchant.ifBlank { null },
                    initialDateTime = dateIso?.toInstantOrNull()
                )
            )
        },
        onCancel = { nav.back() },
        onOpenNotificationSettings = { nav.navigateTo(NotificationListenerSettingsScreen) },
        onOpenCardMappingSettings = { nav.navigateTo(CardAccountMappingScreen) }
    )
}

private fun String.toInstantOrNull(): Instant? = try {
    LocalDate.parse(this)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
} catch (e: Exception) {
    null
}
