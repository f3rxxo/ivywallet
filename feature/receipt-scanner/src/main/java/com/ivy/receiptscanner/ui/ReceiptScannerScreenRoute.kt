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
        onConfirm = { transactions ->
            if (transactions.isNotEmpty()) {
                // Pop this scanner screen off the back stack BEFORE pushing
                // any Edit Transaction screens, so that after the LAST one
                // is saved, it returns to Home instead of back to this scan
                // screen. (EditTransactionViewModel's save() just calls
                // nav.back() — whatever is directly under it on the stack.)
                nav.back()

                // Chain multiple transactions (e.g. several grouped bank
                // alerts in one screenshot) by pre-loading the back stack
                // in REVERSE order, so the first item ends up as the
                // visible screen, and each subsequent Save naturally
                // advances to the next one: pushing [last, ..., second]
                // onto the stack before finally navigating to [first]
                // means back() from first pops to second, back() from
                // second pops to third, etc., with the final back()
                // landing on whatever was under this scanner screen
                // originally (Home). This reuses EditTransactionScreen's
                // own battle-tested save path unmodified for every
                // transaction — no separate bulk-save logic to get subtly
                // wrong.
                transactions.asReversed().forEach { trn ->
                    nav.navigateTo(
                        EditTransactionScreen(
                            initialTransactionId = null,
                            type = trn.type,
                            accountId = trn.accountId?.value,
                            // Category only makes sense for expense/income, not transfers.
                            categoryId = if (trn.type == TransactionType.TRANSFER) {
                                null
                            } else {
                                trn.categoryId?.value
                            },
                            initialAmount = trn.amount?.toDouble(),
                            initialTitle = trn.merchant.ifBlank { null },
                            initialDateTime = trn.dateIso?.toInstantOrNull()
                        )
                    )
                }
            }
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
