package com.ivy.legacy.ui.component.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor

/**
 * A single-row, borderless, compact alternative to [TransactionCard]:
 * merchant name, account, category (with its own saved color, no icon),
 * and amount — no card background, no border, no type icon. Trades off:
 * no inline due-date banner, no Pay/Get buttons, no tags row, no
 * multi-currency second line — tap the row to open the full Edit
 * Transaction screen for any of that.
 *
 * Text uses UI.colors.pureInverse (theme-aware: white in dark mode,
 * near-black in light mode) rather than a hard-coded white, so it stays
 * readable if the app is ever used in light theme.
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
    // Subtle 3-5% tint applied to alternating rows (the "nth-child(odd)"
    // zebra-striping effect) — pass true for every other row from the caller.
    isAlternateRow: Boolean = false,
    onClick: (Transaction) -> Unit,
) {
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
        TransactionType.INCOME -> "+" to MutedGreen
        TransactionType.TRANSFER -> "+" to UI.colors.pureInverse
        TransactionType.EXPENSE -> "-" to MutedRed
    }

    val rowBackground = if (isAlternateRow) {
        UI.colors.pureInverse.copy(alpha = 0.04f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
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
                    color = UI.colors.pureInverse
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Account name first...
                if (accountLabel.isNotBlank()) {
                    Text(
                        text = accountLabel,
                        style = UI.typo.c.style(
                            fontWeight = FontWeight.ExtraLight,
                            color = UI.colors.pureInverse
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // ...then the category pill AFTER it (no icon, uniform
                // compact padding/font size, keeps its own saved color).
                if (category != null) {
                    if (accountLabel.isNotBlank()) Spacer(Modifier.width(6.dp))
                    CategoryPill(
                        name = category.name.value,
                        backgroundColor = category.color.value.toComposeColor()
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
                        color = UI.colors.pureInverse
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Icon-less category chip. Same saved background color as before, but
 * no icon, a smaller/uniform font size, and tighter padding than the
 * icon-based CategoryBadgeDisplay — keeps every pill's proportions
 * looking consistent regardless of category name length.
 */
@Composable
private fun CategoryPill(name: String, backgroundColor: Color) {
    val contrastColor = findContrastTextColor(backgroundColor)
    Text(
        text = name,
        style = UI.typo.nC.style(
            fontWeight = FontWeight.Bold,
            color = contrastColor
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private val MutedGreen = Color(0xFF6FCF97)
private val MutedRed = Color(0xFFD98888)
