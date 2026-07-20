package com.ivy.receiptscanner.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.receiptscanner.category.CardAccountMatcher
import com.ivy.receiptscanner.category.MerchantCategoryMatcher
import com.ivy.receiptscanner.domain.ParsedReceipt
import com.ivy.receiptscanner.ocr.ReceiptOcrProcessor
import com.ivy.receiptscanner.parser.ReceiptParser
import com.ivy.receiptscanner.parser.TransactionTypeClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One transaction's worth of parsed data + auto-matched suggestions. */
data class ReceiptReviewItem(
    val receipt: ParsedReceipt,
    val suggestedCategoryId: CategoryId?,
    val suggestedCategoryName: String?,
    val suggestedType: TransactionType,
    val suggestedAccountId: AccountId?
)

sealed interface ReceiptScanState {
    data object Idle : ReceiptScanState
    data object Processing : ReceiptScanState
    data class ReviewNeeded(
        // Usually a single item (a normal receipt scan). More than one
        // when the screenshot contained several grouped bank/card alerts
        // (see ReceiptParser.parseAll).
        val items: List<ReceiptReviewItem>,
        // Full account list so the review screen can offer a manual
        // picker too (e.g. when nothing auto-matched).
        val accounts: List<Account>
    ) : ReceiptScanState
    data class Failed(val reason: String) : ReceiptScanState
}

/**
 * Drives the "scan receipt -> OCR -> parse -> classify -> match category & account -> review" flow.
 *
 * IMPORTANT: this ViewModel intentionally does NOT save a transaction
 * itself. It only produces parsed data (+ suggestions) for a review
 * screen to show. Wire the "confirm" action on that review screen to Ivy
 * Wallet's existing AddEditTransaction ViewModel/use case, pre-filled
 * with these values. Never auto-save straight from OCR output.
 */
@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    private val ocrProcessor: ReceiptOcrProcessor,
    private val parser: ReceiptParser,
    private val typeClassifier: TransactionTypeClassifier,
    private val categoryMatcher: MerchantCategoryMatcher,
    private val categoryRepository: CategoryRepository,
    private val accountMatcher: CardAccountMatcher,
    private val accountRepository: AccountRepository
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

            val parsedList = parser.parseAll(text)
            if (parsedList.all { it.totalAmountGuess == null }) {
                _state.value = ReceiptScanState.Failed(
                    "Couldn't find an amount. Try a clearer, well-lit photo."
                )
                return@launch
            }

            val accounts = accountRepository.findAll()
            val items = parsedList.map { parsed -> buildReviewItem(parsed, accounts) }

            _state.value = ReceiptScanState.ReviewNeeded(items = items, accounts = accounts)
        }
    }

    private suspend fun buildReviewItem(
        parsed: ParsedReceipt,
        accounts: List<Account>
    ): ReceiptReviewItem {
        val suggestedType = typeClassifier.classify(parsed.rawText)

        // Match on the merchant guess first; fall back to the raw OCR
        // text (e.g. dictionary keywords may appear elsewhere on the
        // receipt even if merchant-line detection missed).
        val matchInput = parsed.merchantGuess ?: parsed.rawText
        val categoryId = categoryMatcher.matchCategory(matchInput)
        val categoryName = categoryId?.let { categoryRepository.findById(it)?.name?.value }

        // Card/account matching looks at this item's own text (masked card
        // numbers and bank names are usually right there in the same
        // notification line/block this item was split from).
        val accountId = accountMatcher.matchAccount(parsed.rawText)

        return ReceiptReviewItem(
            receipt = parsed,
            suggestedCategoryId = categoryId,
            suggestedCategoryName = categoryName,
            suggestedType = suggestedType,
            suggestedAccountId = accountId
        )
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
