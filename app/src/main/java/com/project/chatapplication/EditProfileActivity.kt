package com.project.chatapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.project.chatapplication.databinding.ActivityEditProfileBinding

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var imageUploadHelper: ImageUploadHelper
    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                // Show the selected image
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.profileImageView)
                
                binding.changePhotoText.text = getString(R.string.photo_selected_message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        imageUploadHelper = ImageUploadHelper(this)

        setupUI()
        loadUserData()
        setupClickListeners()
    }

    private fun setupUI() {
        supportActionBar?.apply {
            title = "Edit Profile"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            selectNewImage()
        }

        binding.changePhotoButton.setOnClickListener {
            selectNewImage()
        }

        binding.saveChangesButton.setOnClickListener {
            saveProfileChanges()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun selectNewImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImage.launch(intent)
    }

    private fun loadUserData() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Loading profile...")
        progressDialog.show()

        val dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("users").child(currentUserId)
        
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressDialog.dismiss()
                currentUser = snapshot.getValue(User::class.java)
                currentUser?.let { user ->
                    updateUI(user)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressDialog.dismiss()
                Toast.makeText(this@EditProfileActivity, "❌ Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(user: User) {
        binding.apply {
            nameInput.setText(user.name)
            
            // Load current profile image
            if (!user.profileImageUrl.isNullOrEmpty()) {
                Glide.with(this@EditProfileActivity)
                    .load(user.profileImageUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(profileImageView)
            }

            // Set email (read-only)
            emailText.text = auth.currentUser?.email ?: "No email"
        }
    }

    private fun saveProfileChanges() {
        val newName = binding.nameInput.text.toString().trim()
        
        if (newName.isEmpty()) {
            binding.nameInputLayout.error = "Name cannot be empty"
            return
        }

        if (newName.length < 2) {
            binding.nameInputLayout.error = "Name must be at least 2 characters"
            return
        }

        binding.nameInputLayout.error = null

        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Saving changes...")
        progressDialog.show()

        val currentUserId = auth.currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("users").child(currentUserId)

        // If a new image is selected, upload it first
        if (selectedImageUri != null) {
            imageUploadHelper.uploadProfileImage(
                imageUri = selectedImageUri!!,
                userId = currentUserId,
                onSuccess = { downloadUrl: String ->
                    // Update both name and profile image
                    val updates = mapOf(
                        "name" to newName,
                        "profileImageUrl" to downloadUrl
                    )
                    
                    updateUserInDatabase(dbRef, updates, progressDialog)
                },
                onFailure = { exception: Exception ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "❌ Failed to upload image: ${exception.message}", Toast.LENGTH_LONG).show()
                },
                onProgress = { progress: Int ->
                    progressDialog.setProgress(progress)
                }
            )
        } else {
            // Update only the name
            val updates = mapOf("name" to newName)
            updateUserInDatabase(dbRef, updates, progressDialog)
        }
    }

    private fun updateUserInDatabase(
        dbRef: com.google.firebase.database.DatabaseReference,
        updates: Map<String, Any>,
        progressDialog: ModernProgressDialog
    ) {
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                
                // Set result and finish
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { exception ->
                progressDialog.dismiss()
                Toast.makeText(this, "❌ Failed to update profile: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}