package com.ivy.receiptscanner.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates a content:// Uri (via FileProvider) pointing at a fresh temp file
 * in the app's cache dir, for ActivityResultContracts.TakePicture() to write
 * the full-resolution photo into.
 *
 * Using a FileProvider Uri (instead of ActivityResultContracts.TakePicturePreview,
 * which asks the camera app to hand back a small in-memory thumbnail through
 * the Binder/Intent extras) is the reliable approach: some OEM camera apps
 * crash or refuse to return data at all when no output Uri is provided.
 */
object ReceiptImageCapture {

    fun createTempImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "receipt_scans").apply { mkdirs() }
        val fileName = "receipt_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val file = File(dir, fileName)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.receiptscanner.fileprovider",
            file
        )
    }
}
