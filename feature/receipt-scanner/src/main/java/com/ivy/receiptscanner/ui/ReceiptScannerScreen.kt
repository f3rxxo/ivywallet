package com.ivy.receiptscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.receiptscanner.ocr.ReceiptImageCapture
import java.math.BigDecimal

/** What the review screen hands back for ONE transaction the user confirmed. */
data class ConfirmedTransaction(
    val merchant: String,
    val amount: BigDecimal?,
    val dateIso: String?,
    val categoryId: CategoryId?,
    val type: TransactionType,
    val accountId: AccountId?
)

/**
 * Entry point screen: user picks/takes a photo of a receipt (or a bank
 * notification screenshot — possibly containing SEVERAL grouped alerts),
 * we OCR + parse it, then hand the pre-filled fields to `onConfirm` so the
 * caller can push them into Ivy Wallet's existing Add/Edit Transaction
 * flow, one per confirmed item.
 *
 * Wire this up wherever Ivy Wallet's "Add Transaction" entry point lives
 * (e.g. an extra button/FAB option "Scan receipt").
 */
@Composable
fun ReceiptScannerScreen(
    onConfirm: (transactions: List<ConfirmedTransaction>) -> Unit,
    onCancel: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenCardMappingSettings: () -> Unit,
    viewModel: ReceiptScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Holds the Uri we asked the camera app to write the full-res photo into.
    // Must be remembered across the permission-request step, since the user
    // may be asked for CAMERA permission before the camera intent even launches.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onReceiptImageCaptured(context, uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val uri = ReceiptImageCapture.createTempImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
        // If denied, we simply stay on the Idle screen — no crash, no
        // silent camera launch attempt without permission.
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val uri = ReceiptImageCapture.createTempImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onReceiptImageCaptured(context, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scan a receipt", style = MaterialTheme.typography.headlineSmall)

        when (val s = state) {
            is ReceiptScanState.Idle -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { launchCamera() }) {
                        Text("Take photo")
                    }
                    OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                        Text("Choose from gallery")
                    }
                }
            }

            is ReceiptScanState.Processing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Reading receipt…")
                }
            }

            is ReceiptScanState.ReviewNeeded -> {
                ReceiptReviewList(
                    reviewState = s,
                    onConfirm = { confirmedList, learnedCategories ->
                        learnedCategories.forEach { (merchant, categoryId) ->
                            viewModel.learnCategory(merchant, categoryId)
                        }
                        onConfirm(confirmedList)
                    },
                    onRetry = { viewModel.reset() }
                )
            }

            is ReceiptScanState.Failed -> {
                Text(s.reason, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.reset() }) {
                    Text("Try again")
                }
            }
        }

        TextButton(onClick = onOpenCardMappingSettings) {
            Text("Manage card/account matching")
        }

        TextButton(onClick = onOpenNotificationSettings) {
            Text("Auto-detect from bank notifications")
        }

        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

/**
 * Per-item mutable form state. A plain class (not a data class) holding
 * Compose state objects, created once per scan via `remember(items)` so
 * edits persist across recomposition but reset on a new scan.
 */
private class ReceiptItemUiState(item: ReceiptReviewItem) {
    val item = item
    var included by mutableStateOf(true)
    var merchant by mutableStateOf(item.receipt.merchantGuess.orEmpty())
    var amountText by mutableStateOf(item.receipt.totalAmountGuess?.toPlainString().orEmpty())
    var useSuggestedCategory by mutableStateOf(item.suggestedCategoryId != null)
    var selectedType by mutableStateOf(item.suggestedType)
    var selectedAccountId by mutableStateOf(item.suggestedAccountId)
}

/**
 * Review/edit list shown after OCR + parsing. ALWAYS shown before saving —
 * OCR/parsing is a best guess, not ground truth. Same goes for the
 * auto-matched category/account: they're checkable suggestions, never
 * auto-applied. Usually a single item (a normal receipt); shows several
 * when the screenshot had multiple grouped bank/card alerts.
 */
@Composable
private fun ReceiptReviewList(
    reviewState: ReceiptScanState.ReviewNeeded,
    onConfirm: (
        confirmed: List<ConfirmedTransaction>,
        learnedCategories: List<Pair<String, CategoryId>>
    ) -> Unit,
    onRetry: () -> Unit
) {
    val itemStates = remember(reviewState.items) {
        reviewState.items.map { ReceiptItemUiState(it) }
    }
    val includedCount = itemStates.count { it.included }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (itemStates.size > 1) {
                "Found ${itemStates.size} transactions — confirm each (auto-filled, edit anything that's wrong)"
            } else {
                "Confirm details (auto-filled, edit anything that's wrong)"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (itemStates.size > 1) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(itemStates.size) { index ->
                    ReceiptItemEditor(
                        index = index,
                        total = itemStates.size,
                        state = itemStates[index],
                        accounts = reviewState.accounts
                    )
                    HorizontalDivider()
                }
            }
        } else {
            itemStates.firstOrNull()?.let { itemState ->
                ReceiptItemEditor(
                    index = 0,
                    total = 1,
                    state = itemState,
                    accounts = reviewState.accounts
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = includedCount > 0,
                onClick = {
                    val confirmed = mutableListOf<ConfirmedTransaction>()
                    val learned = mutableListOf<Pair<String, CategoryId>>()

                    itemStates.filter { it.included }.forEach { s ->
                        val amount = s.amountText.toBigDecimalOrNull()
                        val categoryId = if (s.useSuggestedCategory) s.item.suggestedCategoryId else null

                        confirmed.add(
                            ConfirmedTransaction(
                                merchant = s.merchant,
                                amount = amount,
                                dateIso = s.item.receipt.dateGuess?.toString(),
                                categoryId = categoryId,
                                type = s.selectedType,
                                accountId = s.selectedAccountId
                            )
                        )

                        if (s.useSuggestedCategory && categoryId != null && s.merchant.isNotBlank()) {
                            learned.add(s.merchant to categoryId)
                        }
                    }

                    onConfirm(confirmed, learned)
                }
            ) {
                Text(
                    if (itemStates.size > 1) {
                        "Add $includedCount transaction${if (includedCount == 1) "" else "s"}"
                    } else {
                        "Add transaction"
                    }
                )
            }
            OutlinedButton(onClick = onRetry) {
                Text("Retake photo")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptItemEditor(
    index: Int,
    total: Int,
    state: ReceiptItemUiState,
    accounts: List<Account>
) {
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (total > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.included,
                    onCheckedChange = { state.included = it }
                )
                Text(
                    "Transaction ${index + 1} of $total",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        if (total > 1 && !state.included) {
            // Collapsed/disabled look when excluded from a multi-item batch —
            // still shows nothing further so the list stays scannable.
            return
        }

        // Type selector — always shown and editable. Bank notification
        // wording ("transfer", "payment", "deposit") is a hint, not ground
        // truth, so this defaults to the guess but is never silently applied.
        Column {
            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.selectedType == type,
                        onClick = { state.selectedType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            if (state.selectedType == TransactionType.TRANSFER) {
                Text(
                    "You'll be asked to pick the destination account on the next screen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Account picker — auto-selected if a card/account mapping matched,
        // but always changeable, and always available even with no match
        // so there's nothing left to configure on the next screen.
        if (accounts.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = accountDropdownExpanded,
                onExpandedChange = { accountDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = accounts.firstOrNull { it.id == state.selectedAccountId }?.name?.value
                        ?: "Select account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false }
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name.value) },
                            onClick = {
                                state.selectedAccountId = account.id
                                accountDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            if (state.item.suggestedAccountId != null) {
                Text(
                    "Auto-matched from a saved card mapping.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = state.merchant,
            onValueChange = { state.merchant = it },
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.amountText,
            onValueChange = { state.amountText = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Date detected: ${state.item.receipt.dateGuess ?: "not found — please set manually"}",
            style = MaterialTheme.typography.bodySmall
        )

        if (state.selectedType == TransactionType.EXPENSE) {
            val suggestedCategoryName = state.item.suggestedCategoryName
            if (state.item.suggestedCategoryId != null && suggestedCategoryName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.useSuggestedCategory,
                        onCheckedChange = { state.useSuggestedCategory = it }
                    )
                    Text("Use category: $suggestedCategoryName")
                }
            } else {
                Text(
                    "No matching category found — you'll be able to pick one on the next screen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
