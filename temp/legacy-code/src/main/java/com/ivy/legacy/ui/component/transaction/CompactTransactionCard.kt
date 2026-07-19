package com.ivy.legacy.ui.component.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.data.AppBaseData
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.navigation.navigation
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White

/**
 * A single-row, borderless, compact alternative to [TransactionCard]:
 * merchant name, category (with its own saved color), account, and amount
 * — no card background, no border, no type icon. Trades off: no inline
 * due-date banner, no Pay/Get buttons, no tags row, no multi-currency
 * second line — tap the row to open the full Edit Transaction screen for
 * any of that.
 *
 * NOTE on color: text is hard-coded White per request, which assumes a
 * dark theme/background. If the app is ever used in light theme, white
 * text on a light background will be unreadable — swap White for
 * UI.colors.pureInverse (theme-aware) if that matters.
 *
 * NOTE on runningBalance: this is a RELATIVE running total computed only
 * from the transactions currently loaded in this list (see
 * historySection in Transactions.kt) — it is NOT anchored to your true
 * account balance. If the list is paginated or filtered, these numbers
 * won't match your actual bank-style running balance. Pass null to hide
 * the line entirely.
 */
@Composable
fun CompactTransactionCard(
    baseData: AppBaseData,
    transaction: Transaction,
    shouldShowAccountSpecificColorInTransactions: Boolean,
    onPayOrGet: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    onSkipTransaction: (Transaction) -> Unit = {},
    runningBalance: Double? = null,
    onClick: (Transaction) -> Unit,
) {
    val nav = navigation()

    val transactionCurrency =
        baseData.accounts.find { it.id == transaction.accountId }?.currency
            ?: baseData.baseCurrency

    val category = category(
        categoryId = transaction.categoryId,
        categories = baseData.categories
    )
    val account = account(
        accountId = transaction.accountId,
        accounts = baseData.accounts
    )
    val toAccount = account(
        accountId = transaction.toAccountId,
        accounts = baseData.accounts
    )

    val title = transaction.title?.takeIf { it.isNotNullOrBlank() }
        ?: category?.name?.value
        ?: account?.name

    val accountLabel = when (transaction.type) {
        TransactionType.TRANSFER -> listOfNotNull(account?.name, toAccount?.name)
            .joinToString(" -> ")
        else -> account?.name.orEmpty()
    }

    // + for income and transfer, - for expense (as requested).
    val (sign, amountColor) = when (transaction.type) {
        TransactionType.INCOME -> "+" to White
        TransactionType.TRANSFER -> "+" to White
        TransactionType.EXPENSE -> "-" to Red
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (baseData.accounts.find { it.id == transaction.accountId } != null) {
                    onClick(transaction)
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title ?: "",
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.Bold,
                    color = White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    // Reuses the app's existing category chip — keeps the
                    // category's own saved background color, not just its name.
                    CategoryBadgeDisplay(category, nav)
                }
                if (accountLabel.isNotBlank()) {
                    if (category != null) Spacer(Modifier.width(8.dp))
                    Text(
                        text = accountLabel,
                        style = UI.typo.c.style(
                            fontWeight = FontWeight.Normal,
                            color = White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$sign${transaction.amount.toDouble().format(transactionCurrency)} $transactionCurrency",
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                ),
                maxLines = 1
            )
            if (runningBalance != null) {
                Text(
                    text = "${runningBalance.format(transactionCurrency)} $transactionCurrency",
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.Normal,
                        color = White
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
