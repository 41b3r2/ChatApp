package com.project.chatapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.project.chatapplication.databinding.ActivityRegistrationBinding

class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private var selectedImageUri: Uri? = null
    private lateinit var progressDialog: ModernProgressDialog
    private lateinit var imageUploadHelper: ImageUploadHelper
    private val TAG = "RegistrationActivity"
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"

    // Modern Activity Result API
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profileImage.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        imageUploadHelper = ImageUploadHelper(this)

        progressDialog = ModernProgressDialog(this).create()

        // Make the profile image visible and clickable again
        binding.profileImage.visibility = View.VISIBLE
        binding.profileImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.registerButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressDialog.setMessage("🔐 Creating Your Account...").show()

            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                val uid = task.result?.user?.uid ?: ""
                saveUserAndThenUploadImage(uid, name, email)
            }
        }
    }

    private fun saveUserAndThenUploadImage(uid: String, name: String, email: String) {
        progressDialog.setMessage("💾 Saving Your Profile...")
        
        // Step 1: Save the user with a blank image URL. This guarantees the user will appear in the list.
        val user = User(name, email, uid, "")
        val ref = FirebaseDatabase.getInstance(DB_URL).getReference("/users/$uid")
        
        ref.setValue(user).addOnSuccessListener {
            // Step 2: If the user is saved, THEN try to upload the image if one was selected.
            if (selectedImageUri != null) {
                uploadImage(uid)
            } else {
                // If no image was selected, just go to main activity.
                goToMainActivity()
            }        }.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "❌ Failed to save user to database: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadImage(uid: String) {
        progressDialog.setMessage("📸 Uploading Your Photo...")
        
        imageUploadHelper.uploadProfileImage(
            imageUri = selectedImageUri!!,
            userId = uid,
            onSuccess = { downloadUrl ->
                // Step 3: If image upload is successful, update the user's profileImageUrl.
                FirebaseDatabase.getInstance(DB_URL).getReference("/users/$uid/profileImageUrl")
                    .setValue(downloadUrl)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Profile created successfully!", Toast.LENGTH_SHORT).show()
                        goToMainActivity()
                    }
            },
            onFailure = { exception ->
                // IMPORTANT: Even if image fails, we still go to main activity because the user is already saved.
                Toast.makeText(this, "⚠️ Image upload failed: ${exception.message}. You can update it later.", Toast.LENGTH_LONG).show()
                goToMainActivity()
            },
            onProgress = { progress ->
                progressDialog.setProgress(progress)
            }
        )
    }

    private fun goToMainActivity() {
        progressDialog.dismiss()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToLogin(){
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}