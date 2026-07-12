package com.ivy.receiptscanner.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import com.ivy.receiptscanner.category.MerchantCategoryMatcher
import com.ivy.receiptscanner.domain.ParsedReceipt
import com.ivy.receiptscanner.ocr.ReceiptOcrProcessor
import com.ivy.receiptscanner.parser.ReceiptParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReceiptScanState {
    data object Idle : ReceiptScanState
    data object Processing : ReceiptScanState
    data class ReviewNeeded(
        val receipt: ParsedReceipt,
        // Auto-matched from the merchant name; null if nothing matched.
        // Shown as a suggestion on the review screen — never silently
        // applied without the user seeing it first.
        val suggestedCategoryId: CategoryId?,
        val suggestedCategoryName: String?
    ) : ReceiptScanState
    data class Failed(val reason: String) : ReceiptScanState
}

/**
 * Drives the "scan receipt -> OCR -> parse -> match category -> review" flow.
 *
 * IMPORTANT: this ViewModel intentionally does NOT save a transaction
 * itself. It only produces a ParsedReceipt (+ a category suggestion) for
 * a review screen to show. Wire the "confirm" action on that review screen
 * to Ivy Wallet's existing AddEditTransaction ViewModel/use case, pre-filled
 * with these values. Never auto-save straight from OCR output.
 */
@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    private val ocrProcessor: ReceiptOcrProcessor,
    private val parser: ReceiptParser,
    private val categoryMatcher: MerchantCategoryMatcher,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ReceiptScanState>(ReceiptScanState.Idle)
    val state: StateFlow<ReceiptScanState> = _state.asStateFlow()

    fun onReceiptImageCaptured(context: Context, uri: Uri) {
        _state.value = ReceiptScanState.Processing
        viewModelScope.launch {
            val text = try {
                ocrProcessor.extractText(context, uri)
            } catch (e: Exception) {
                _state.value = ReceiptScanState.Failed("Couldn't read the image: ${e.message}")
                return@launch
            }

            if (text.isNullOrBlank()) {
                _state.value = ReceiptScanState.Failed(
                    "No text found. Try a clearer, well-lit photo of the receipt."
                )
                return@launch
            }

            val parsed = parser.parse(text)

            // Match on the merchant guess first; fall back to the raw OCR
            // text (e.g. dictionary keywords may appear elsewhere on the
            // receipt even if merchant-line detection missed).
            val matchInput = parsed.merchantGuess ?: parsed.rawText
            val categoryId = categoryMatcher.matchCategory(matchInput)
            val categoryName = categoryId?.let { categoryRepository.findById(it)?.name?.value }

            _state.value = ReceiptScanState.ReviewNeeded(
                receipt = parsed,
                suggestedCategoryId = categoryId,
                suggestedCategoryName = categoryName
            )
        }
    }

    /** Teach the matcher from what the user actually confirmed. */
    fun learnCategory(merchantText: String, categoryId: CategoryId) {
        viewModelScope.launch {
            categoryMatcher.learn(merchantText, categoryId)
        }
    }

    fun reset() {
        _state.value = ReceiptScanState.Idle
    }
}
