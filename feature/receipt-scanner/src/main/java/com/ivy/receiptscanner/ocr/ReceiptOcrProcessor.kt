package com.ivy.receiptscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around ML Kit's on-device text recognizer.
 * Runs fully offline — no network call, no API key needed.
 */
@Singleton
class ReceiptOcrProcessor @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Preferred entry point: reads directly from a content:// / file:// Uri.
     * ML Kit decodes and downsamples the image itself, so this avoids
     * loading a full-resolution Bitmap into memory yourself (which is what
     * was crashing on large gallery/camera photos via
     * MediaStore.Images.Media.getBitmap()).
     */
    suspend fun extractText(context: Context, uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        val text = result.text.trim()
        return text.ifEmpty { null }
    }

    /** Kept for cases where you already have a decoded Bitmap in hand. */
    suspend fun extractText(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, /* rotationDegrees = */ 0)
        val result = recognizer.process(image).await()
        val text = result.text.trim()
        return text.ifEmpty { null }
    }
}
