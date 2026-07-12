package com.ivy.receiptscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.data.model.CategoryId
import com.ivy.receiptscanner.domain.ParsedReceipt
import com.ivy.receiptscanner.ocr.ReceiptImageCapture
import java.math.BigDecimal

/**
 * Entry point screen: user picks/takes a photo of a receipt, we OCR + parse it,
 * then hand the pre-filled fields to `onConfirm` so the caller can push them
 * into Ivy Wallet's existing Add/Edit Transaction flow.
 *
 * Wire this up wherever Ivy Wallet's "Add Transaction" entry point lives
 * (e.g. an extra button/FAB option "Scan receipt").
 */
@Composable
fun ReceiptScannerScreen(
    onConfirm: (
        merchant: String,
        amount: BigDecimal?,
        dateIso: String?,
        categoryId: CategoryId?
    ) -> Unit,
    onCancel: () -> Unit,
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
                ReceiptReviewForm(
                    receipt = s.receipt,
                    suggestedCategoryId = s.suggestedCategoryId,
                    suggestedCategoryName = s.suggestedCategoryName,
                    onConfirm = { merchant, amount, dateIso, categoryId, acceptedSuggestion ->
                        if (acceptedSuggestion && categoryId != null && merchant.isNotBlank()) {
                            viewModel.learnCategory(merchant, categoryId)
                        }
                        onConfirm(merchant, amount, dateIso, categoryId)
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

        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

/**
 * Review/edit form shown after OCR + parsing. ALWAYS shown before saving —
 * OCR/parsing is a best guess, not ground truth. Same goes for the
 * auto-matched category: it's a checkable suggestion, not an auto-apply.
 */
@Composable
private fun ReceiptReviewForm(
    receipt: ParsedReceipt,
    suggestedCategoryId: CategoryId?,
    suggestedCategoryName: String?,
    onConfirm: (
        merchant: String,
        amount: BigDecimal?,
        dateIso: String?,
        categoryId: CategoryId?,
        acceptedSuggestion: Boolean
    ) -> Unit,
    onRetry: () -> Unit
) {
    var merchant by remember { mutableStateOf(receipt.merchantGuess.orEmpty()) }
    var amountText by remember {
        mutableStateOf(receipt.totalAmountGuess?.toPlainString().orEmpty())
    }
    var useSuggestedCategory by remember(suggestedCategoryId) {
        mutableStateOf(suggestedCategoryId != null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Confirm details (auto-filled, edit anything that's wrong)",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Date detected: ${receipt.dateGuess ?: "not found — please set manually"}",
            style = MaterialTheme.typography.bodySmall
        )

        if (suggestedCategoryId != null && suggestedCategoryName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useSuggestedCategory,
                    onCheckedChange = { useSuggestedCategory = it }
                )
                Text("Use category: $suggestedCategoryName")
            }
        } else {
            Text(
                "No matching category found — you'll be able to pick one on the next screen.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val amount = amountText.toBigDecimalOrNull()
                val categoryId = if (useSuggestedCategory) suggestedCategoryId else null
                onConfirm(
                    merchant,
                    amount,
                    receipt.dateGuess?.toString(),
                    categoryId,
                    useSuggestedCategory
                )
            }) {
                Text("Add transaction")
            }
            OutlinedButton(onClick = onRetry) {
                Text("Retake photo")
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
