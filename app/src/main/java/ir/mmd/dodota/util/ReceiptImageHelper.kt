package ir.mmd.dodota.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ReceiptImageHelper {

    private fun getReceiptsDir(context: Context): File {
        val dir = File(context.filesDir, "receipts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copies and compresses a selected gallery image Uri into internal app storage
     * so it persists permanently across reboots and gallery deletions.
     */
    fun saveGalleryUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val scaledBitmap = scaleBitmapDown(originalBitmap, 1280)
            val destFile = File(getReceiptsDir(context), "receipt_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadReceiptBitmap(context: Context, pathOrUri: String?): Bitmap? {
        if (pathOrUri.isNullOrBlank()) return null
        return try {
            val file = File(pathOrUri)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encodes a saved receipt image into Base64 string so it can be embedded inside JSON backups.
     */
    fun encodeReceiptToBase64(context: Context, pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        return try {
            val file = File(pathOrUri)
            val bytes = if (file.exists()) {
                file.readBytes()
            } else {
                val bitmap = loadReceiptBitmap(context, pathOrUri) ?: return null
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restores a Base64-encoded receipt image from JSON backup back into internal storage
     * and returns its new local file path.
     */
    fun restoreBase64ToInternalFile(context: Context, base64Data: String?): String? {
        if (base64Data.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val destFile = File(getReceiptsDir(context), "receipt_restored_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
            destFile.writeBytes(bytes)
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun saveReceiptImageToInternalStorage(context: Context, uri: Uri): String? =
        saveGalleryUriToInternalStorage(context, uri)

    fun loadBitmap(context: Context, pathOrUri: String?): Bitmap? =
        loadReceiptBitmap(context, pathOrUri)

    fun encodeFileToBase64(context: Context, pathOrUri: String?): String? =
        encodeReceiptToBase64(context, pathOrUri)

    fun saveBase64ImageToInternalStorage(context: Context, base64Data: String?): String? =
        restoreBase64ToInternalFile(context, base64Data)

    fun deleteReceiptImage(pathOrUri: String?) {
        if (pathOrUri.isNullOrBlank()) return
        try {
            val file = File(pathOrUri)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
