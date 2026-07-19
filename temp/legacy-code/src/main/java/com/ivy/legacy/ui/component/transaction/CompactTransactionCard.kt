package com.ivy.legacy.ui.component.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.data.AppBaseData
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.navigation.navigation
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.GradientIvy
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.wallet.AmountCurrencyB1

/**
 * A single-row, compact alternative to [TransactionCard]. Same signature
 * (drop-in replacement), but shows icon + title + category/account
 * subtitle + amount all in one ~64dp-tall row instead of a multi-section
 * card that can run 150dp+ per item. Trades off: no inline due-date
 * banner, no Pay/Get buttons, no tags row, no multi-currency second line
 * — tap the row to open the full Edit Transaction screen for any of that.
 */
@Composable
fun CompactTransactionCard(
    baseData: AppBaseData,
    transaction: Transaction,
    shouldShowAccountSpecificColorInTransactions: Boolean,
    onPayOrGet: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    onSkipTransaction: (Transaction) -> Unit = {},
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

    val subtitle = when (transaction.type) {
        TransactionType.TRANSFER -> listOfNotNull(account?.name, toAccount?.name)
            .joinToString(" -> ")
        else -> listOfNotNull(category?.name?.value, account?.name).joinToString(" • ")
    }

    val style = when (transaction.type) {
        TransactionType.INCOME -> CompactAmountStyle(
            icon = R.drawable.ic_income,
            gradient = GradientGreen,
            textColor = Green
        )

        TransactionType.EXPENSE -> CompactAmountStyle(
            icon = R.drawable.ic_expense,
            gradient = Gradient.black(),
            textColor = UI.colors.pureInverse
        )

        TransactionType.TRANSFER -> CompactAmountStyle(
            icon = R.drawable.ic_transfer,
            gradient = GradientIvy,
            textColor = Ivy
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .clickable {
                if (baseData.accounts.find { it.id == transaction.accountId } != null) {
                    onClick(transaction)
                }
            }
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IvyIcon(
            modifier = Modifier
                .size(36.dp)
                .background(style.gradient.asHorizontalBrush(), CircleShape)
                .padding(8.dp),
            icon = style.icon,
            tint = White
        )

        Spacer(Modifier.width(12.dp))

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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.Normal,
                        color = Gray
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        AmountCurrencyB1(
            amount = transaction.amount.toDouble(),
            currency = transactionCurrency,
            textColor = style.textColor
        )
    }
}

private data class CompactAmountStyle(
    val icon: Int,
    val gradient: Gradient,
    val textColor: androidx.compose.ui.graphics.Color
)
