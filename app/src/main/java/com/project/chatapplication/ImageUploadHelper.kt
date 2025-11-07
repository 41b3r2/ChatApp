package com.project.chatapplication

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class ImageUploadHelper(private val context: Context) {    fun uploadProfileImage(
        imageUri: Uri,
        userId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        val fileName = "${System.currentTimeMillis()}_profile_image"
        val storageRef: StorageReference = FirebaseStorage.getInstance().getReference("profile_images/$userId/$fileName")

        storageRef.putFile(imageUri)
            .addOnSuccessListener { _ ->
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    onSuccess(uri.toString())
                }.addOnFailureListener {
                    onFailure(it)
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress(progress)
            }
    }

    fun uploadConversationImage(
        imageUri: Uri,
        senderId: String,
        receiverId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        val folderName = if (senderId > receiverId) "$senderId-$receiverId" else "$receiverId-$senderId"
        val fileName = "${System.currentTimeMillis()}_${imageUri.lastPathSegment}"
        val storageRef: StorageReference = FirebaseStorage.getInstance().getReference("conversation_images/$folderName/$fileName")

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    onSuccess(uri.toString())
                }.addOnFailureListener {
                    onFailure(it)
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress(progress)
            }
    }
}