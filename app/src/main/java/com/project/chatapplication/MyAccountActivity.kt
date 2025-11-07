package com.project.chatapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.project.chatapplication.databinding.ActivityMyAccountBinding

class MyAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyAccountBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var imageUploadHelper: ImageUploadHelper
    private var currentUser: User? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        imageUploadHelper = ImageUploadHelper(this)

        setupUI()
        loadUserData()
        setupClickListeners()
    }

    private fun setupUI() {
        supportActionBar?.apply {
            title = "My Account"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            showImagePickerDialog()
        }

        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.privacySettingsButton.setOnClickListener {
            showPrivacySettings()
        }

        binding.notificationSettingsButton.setOnClickListener {
            showNotificationSettings()
        }

        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun loadUserData() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("user").child(currentUserId)
        
        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Loading account info...")
        progressDialog.show()

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
                Toast.makeText(this@MyAccountActivity, "❌ Failed to load account info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(user: User) {
        binding.apply {
            userNameText.text = user.name
            userEmailText.text = auth.currentUser?.email
            
            // Load profile image
            if (!user.profileImageUrl.isNullOrEmpty()) {
                Glide.with(this@MyAccountActivity)
                    .load(user.profileImageUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(profileImageView)
            }

            // Update account stats (you can customize these)
            accountCreatedText.text = "Account created: ${getFormattedDate(user.uid ?: "")}"
            totalChatsText.text = "Active conversations: Loading..."
            
            // Load conversation count
            loadConversationCount()
        }
    }

    private fun loadConversationCount() {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatsRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("chats")
        
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var conversationCount = 0
                for (chatSnapshot in snapshot.children) {
                    if (chatSnapshot.key?.contains(currentUserId) == true) {
                        conversationCount++
                    }
                }
                binding.totalChatsText.text = "Active conversations: $conversationCount"
            }

            override fun onCancelled(error: DatabaseError) {
                binding.totalChatsText.text = "Active conversations: N/A"
            }
        })
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("📷 Take Photo", "🖼️ Choose from Gallery", "❌ Cancel")
        
        AlertDialog.Builder(this)
            .setTitle("Update Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        // Camera functionality can be added later
                        Toast.makeText(this, "Camera feature coming soon!", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val intent = Intent(Intent.ACTION_PICK)
                        intent.type = "image/*"
                        pickImage.launch(intent)
                    }
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun uploadProfileImage(imageUri: Uri) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Updating profile picture...")
        progressDialog.show()

        imageUploadHelper.uploadProfileImage(
            imageUri = imageUri,
            userId = currentUserId,
            onSuccess = { downloadUrl ->
                updateProfileImageInDatabase(downloadUrl)
                progressDialog.dismiss()
            },
            onFailure = { exception ->
                progressDialog.dismiss()
                Toast.makeText(this, "❌ Failed to update profile picture: ${exception.message}", Toast.LENGTH_LONG).show()
            },
            onProgress = { progress ->
                progressDialog.setProgress(progress)
            }
        )
    }

    private fun updateProfileImageInDatabase(imageUrl: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("user").child(currentUserId)

        dbRef.child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                // Reload the image
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.profileImageView)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "❌ Failed to save profile picture: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showChangePasswordDialog() {
        val dialogBinding = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogBinding.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordInput = dialogBinding.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordInput = dialogBinding.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogBinding)
            .setPositiveButton("Update") { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (validatePasswordChange(currentPassword, newPassword, confirmPassword)) {
                    changePassword(currentPassword, newPassword)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePasswordChange(current: String, new: String, confirm: String): Boolean {
        if (current.isEmpty() || new.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "❌ All fields are required", Toast.LENGTH_SHORT).show()
            return false
        }

        if (new.length < 6) {
            Toast.makeText(this, "❌ New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        if (new != confirm) {
            Toast.makeText(this, "❌ New passwords don't match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Password updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "❌ Failed to update password: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPrivacySettings() {
        val options = arrayOf(
            "🔒 Block Users",
            "👁️ Profile Visibility", 
            "💬 Message Privacy",
            "📍 Location Sharing"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Privacy Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Block users feature coming soon!", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Profile visibility settings coming soon!", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Message privacy settings coming soon!", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "Location sharing settings coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showNotificationSettings() {
        val options = arrayOf(
            "🔔 Push Notifications",
            "🔊 Sound Settings",
            "⏰ Quiet Hours",
            "📱 Message Previews"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Notification Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Push notification settings coming soon!", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Sound settings coming soon!", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Quiet hours feature coming soon!", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "Message preview settings coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Account")
            .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                confirmDeleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAccount() {
        // Additional confirmation
        AlertDialog.Builder(this)
            .setTitle("⚠️ Final Confirmation")
            .setMessage("This will permanently delete your account, messages, and all associated data. Type 'DELETE' to confirm.")
            .setView(R.layout.dialog_delete_confirmation)
            .setPositiveButton("Delete Account") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userId = user.uid
        
        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Deleting account...")
        progressDialog.show()

        // Delete user data from database
        val dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/")
            .getReference("user").child(userId)
        
        dbRef.removeValue()
            .addOnSuccessListener {
                // Delete authentication account
                user.delete()
                    .addOnSuccessListener {
                        progressDialog.dismiss()
                        Toast.makeText(this, "✅ Account deleted successfully", Toast.LENGTH_SHORT).show()
                        
                        // Redirect to login
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { exception ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "❌ Failed to delete account: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { exception ->
                progressDialog.dismiss()
                Toast.makeText(this, "❌ Failed to delete user data: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚪 Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "👋 Logged out successfully!", Toast.LENGTH_SHORT).show()
        
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun getFormattedDate(uid: String): String {
        // Simple date formatting based on UID (you can enhance this)
        return "Recently" // Placeholder
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
