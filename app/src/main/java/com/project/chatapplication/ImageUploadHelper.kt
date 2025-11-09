package com.project.chatapplication

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * ImageUploadHelper now saves images to the app's external files directory under folder "image".
 * This avoids using Firebase Storage and stores a local file path that is saved to the database.
 */
class ImageUploadHelper(private val context: Context) {
    fun uploadProfileImage(
        imageUri: Uri,
        userId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        try {
            val resolver = context.contentResolver
            val input: InputStream? = resolver.openInputStream(imageUri)
            if (input == null) {
                onFailure(Exception("Unable to open image input"))
                return
            }

            val imagesDir = File(context.getExternalFilesDir(null), "image")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val fileName = "${System.currentTimeMillis()}_profile.jpg"
            val outFile = File(imagesDir, fileName)

            FileOutputStream(outFile).use { out ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                var total: Long = 0
                val available = input.available().toLong().coerceAtLeast(1L)
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    total += read
                    val progress = ((total * 100) / available).toInt().coerceIn(0, 100)
                    onProgress(progress)
                }
                out.flush()
            }

            input.close()

            // Return the absolute file path as the image URL that will be stored in the DB
            onSuccess(outFile.absolutePath)

        } catch (e: Exception) {
            onFailure(e)
        }
    }

    // For conversation images we also save to the same folder and return the file path
    fun uploadConversationImage(
        imageUri: Uri,
        senderId: String,
        receiverId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        // reuse uploadProfileImage logic but give a different filename
        try {
            val resolver = context.contentResolver
            val input: InputStream? = resolver.openInputStream(imageUri)
            if (input == null) {
                onFailure(Exception("Unable to open image input"))
                return
            }

            val imagesDir = File(context.getExternalFilesDir(null), "image")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val fileName = "${System.currentTimeMillis()}_${imageUri.lastPathSegment ?: "img"}"
            val outFile = File(imagesDir, fileName)

            FileOutputStream(outFile).use { out ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                var total: Long = 0
                val available = input.available().toLong().coerceAtLeast(1L)
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    total += read
                    val progress = ((total * 100) / available).toInt().coerceIn(0, 100)
                    onProgress(progress)
                }
                out.flush()
            }

            input.close()

            onSuccess(outFile.absolutePath)
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}