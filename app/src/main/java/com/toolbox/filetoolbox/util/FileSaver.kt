package com.toolbox.filetoolbox.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Utility to save files to public directories via MediaStore.
 * Files saved this way are visible in the system file manager and gallery.
 *
 * - Images → Pictures/FileToolbox/
 * - Documents (PDF, DOCX, XLSX, CSV) → Downloads/FileToolbox/
 */
object FileSaver {

    private const val APP_FOLDER = "FileToolbox"

    // ──────────────────── Image saving ────────────────────

    /**
     * Save a Bitmap to Pictures/FileToolbox/ via MediaStore.
     * @return display name of the saved file, or null on failure.
     */
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): String? {
        return try {
            val mimeType = when (format) {
                Bitmap.CompressFormat.JPEG -> "image/jpeg"
                Bitmap.CompressFormat.PNG -> "image/png"
                else -> "image/png"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, quality, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ──────────────────── Document saving ────────────────────

    /**
     * Save a document file (PDF, DOCX, XLSX, etc.) to Downloads/FileToolbox/ via MediaStore.
     * The [writeContent] lambda receives an OutputStream to write into.
     * @return display name of the saved file, or null on failure.
     */
    fun saveDocument(
        context: Context,
        fileName: String,
        mimeType: String,
        writeContent: (OutputStream) -> Unit
    ): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : MediaStore Downloads
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                resolver.openOutputStream(uri)?.use { out ->
                    writeContent(out)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                fileName
            } else {
                // Android 9 and below : write to public Downloads directly
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    APP_FOLDER
                )
                downloadsDir.mkdirs()

                val file = File(downloadsDir, fileName)
                file.outputStream().use { out ->
                    writeContent(out)
                }
                fileName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save an existing temporary file to public Downloads/FileToolbox/.
     * Copies from the temp file then deletes it.
     */
    fun saveTempFileAsDocument(
        context: Context,
        tempFile: File,
        fileName: String,
        mimeType: String
    ): String? {
        return saveDocument(context, fileName, mimeType) { out ->
            FileInputStream(tempFile).use { input ->
                input.copyTo(out)
            }
            tempFile.delete()
        }
    }

    // ──────────────────── MIME type helpers ────────────────────

    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_PNG = "image/png"

    // ──────────────────── Camera helper ────────────────────

    /**
     * Create a temporary file for camera capture and return its FileProvider Uri.
     */
    fun createCameraTempUri(context: Context): Pair<File, Uri> {
        val tempFile = File.createTempFile(
            "camera_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        )
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(tempFile, uri)
    }
}
