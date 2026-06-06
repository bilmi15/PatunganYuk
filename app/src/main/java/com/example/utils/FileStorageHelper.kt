package com.example.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileStorageHelper {

    /**
     * Copies a content URI source to a safe place in internal storage.
     * Guaranteed to retain permanent read permission for the lifetime of the application.
     * Returns the absolute file path.
     */
    fun copyUriToInternalStorage(context: Context, sourceUri: Uri, folderName: String): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
            if (inputStream == null) return null

            val outputDir = File(context.filesDir, folderName).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            // Generate a secure, unique filename to prevent namespace collision
            val extension = getFileExtension(context, sourceUri) ?: "jpg"
            val targetFile = File(outputDir, "${UUID.randomUUID()}.$extension")

            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            inputStream.close()
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses an image URI string. Returns a [File] helper if it represents an absolute local path,
     * or a standard [Uri] if it is a remote/content/file schema URI.
     */
    fun parseImageUri(uriStr: String?): Any? {
        if (uriStr.isNullOrEmpty()) return null
        return if (!uriStr.contains("://")) {
            File(uriStr)
        } else {
            Uri.parse(uriStr)
        }
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            if (mimeType != null) {
                val index = mimeType.lastIndexOf('/')
                if (index != -1 && index + 1 < mimeType.length) {
                    mimeType.substring(index + 1)
                } else {
                    null
                }
            } else {
                uri.path?.let { path ->
                    val index = path.lastIndexOf('.')
                    if (index != -1) path.substring(index + 1) else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
