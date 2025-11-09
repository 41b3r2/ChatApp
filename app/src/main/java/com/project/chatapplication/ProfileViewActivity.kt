package com.project.chatapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.project.chatapplication.databinding.ActivityProfileViewBinding

class ProfileViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileViewBinding
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra("uid")
        val name = intent.getStringExtra("name")

        binding.backButton.setOnClickListener { onBackPressed() }

        if (uid == null) {
            Toast.makeText(this, "Unable to load profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load user data from Firebase and populate UI
        val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance(DB_URL).reference
        binding.userName.text = name ?: "Profile"

        dbRef.child("users").child(uid).get().addOnSuccessListener { snap ->
            val user = snap.getValue(User::class.java)
            if (user != null) {
                binding.userName.text = user.name
                binding.userEmail.text = user.email
                // image path may be absolute file path or http url
                val imageRef = if (!user.profileImageUrl.isNullOrEmpty() && user.profileImageUrl!!.startsWith("/")) {
                    java.io.File(user.profileImageUrl!!)
                } else {
                    user.profileImageUrl
                }
                Glide.with(this).load(imageRef).placeholder(R.drawable.default_avatar).into(binding.profileImage)

                binding.sendMessageButton.setOnClickListener {
                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    val otherUid = user.uid
                    if (currentUid == null || otherUid.isNullOrEmpty()) {
                        Toast.makeText(this, "Unable to start chat", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Only allow opening chat if there is an accepted request (match)
                    val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance(DB_URL).reference
                    dbRef.child("chat_requests").orderByChild("status").equalTo("accepted").get()
                        .addOnSuccessListener { snap ->
                            var matched = false
                            for (s in snap.children) {
                                val r = s.getValue(ChatRequest::class.java)
                                if (r != null) {
                                    if ((r.senderId == currentUid && r.receiverId == otherUid) || (r.senderId == otherUid && r.receiverId == currentUid)) {
                                        matched = true
                                        break
                                    }
                                }
                            }
                            if (matched) {
                                val intent = Intent(this, ChatActivity::class.java)
                                intent.putExtra("name", user.name)
                                intent.putExtra("uid", user.uid)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "You can only chat after you both accept a 'Let's Chat?' request.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to check chat permissions: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }

                binding.blockUserButton.setOnClickListener {
                    // Simple placeholder: block user (could be implemented by adding to blocked list in DB)
                    Toast.makeText(this, "User blocked (not implemented)", Toast.LENGTH_SHORT).show()
                }

            } else {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load profile: ${it.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
