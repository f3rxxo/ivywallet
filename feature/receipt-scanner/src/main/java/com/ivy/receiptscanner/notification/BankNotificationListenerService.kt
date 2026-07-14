package com.ivy.receiptscanner.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.receiptscanner.category.MerchantCategoryMatcher
import com.ivy.receiptscanner.parser.ReceiptParser
import com.ivy.receiptscanner.parser.TransactionTypeClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads notifications ONLY from apps you explicitly add via
 * NotificationListenerConfigStore (e.g. your banking apps' package names).
 * Every other notification on the phone is ignored immediately.
 *
 * This requires the user to manually grant "Notification access" for
 * Ivy Wallet in system Settings -> Apps -> Special app access ->
 * Notification access. That can't be requested via a normal runtime
 * permission dialog — deep-link them there with:
 *   startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
 *
 * Never auto-saves a transaction. It only extracts + posts a summary
 * notification; tapping it opens the app's pre-filled review screen,
 * same as the manual receipt-scan flow.
 */
@AndroidEntryPoint
class BankNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var configStore: NotificationListenerConfigStore
    @Inject lateinit var pendingStore: PendingScannedTransactionStore
    @Inject lateinit var parser: ReceiptParser
    @Inject lateinit var typeClassifier: TransactionTypeClassifier
    @Inject lateinit var categoryMatcher: MerchantCategoryMatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Never process our own summary notifications, or we'd loop.
        if (sbn.packageName == packageName) return

        serviceScope.launch {
            if (!configStore.isEnabled()) return@launch
            val allowedPackages = configStore.getAllowedPackages()
            if (sbn.packageName !in allowedPackages) return@launch

            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val combined = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString("\n")

            if (combined.isBlank()) return@launch

            val parsed = parser.parse(combined)
            if (parsed.totalAmountGuess == null) return@launch // nothing actionable

            val type = typeClassifier.classify(combined)
            val matchInput = parsed.merchantGuess ?: combined
            val categoryId = if (type == TransactionType.EXPENSE) {
                categoryMatcher.matchCategory(matchInput)
            } else {
                null
            }

            pendingStore.set(
                PendingScannedTransaction(
                    merchant = parsed.merchantGuess,
                    amount = parsed.totalAmountGuess.toPlainString(),
                    dateIso = parsed.dateGuess?.toString(),
                    categoryId = categoryId?.value?.toString(),
                    type = type.name
                )
            )

            postReviewNotification(
                merchant = parsed.merchantGuess ?: "Transaction",
                amount = parsed.totalAmountGuess.toPlainString(),
                type = type
            )
        }
    }

    private fun postReviewNotification(
        merchant: String,
        amount: String,
        type: TransactionType
    ) {
        val channelId = "receipt_scanner_notification_detected"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Detected transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown when a bank notification looks like a transaction to add"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Opens the app normally; MainScreen picks up the pending item from
        // PendingScannedTransactionStore and navigates to the review screen.
        // (No custom deep-link Intent extras needed — the DataStore slot
        // is the handoff mechanism, so it survives the app being closed.)
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = type.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_add) // swap for the app's own icon resource
            .setContentTitle("Detected $typeLabel: $merchant")
            .setContentText("$amount — tap to review and add")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(merchant.hashCode(), notification)
    }
}
