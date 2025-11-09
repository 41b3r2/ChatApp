package com.project.chatapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.project.chatapplication.databinding.ActivityChatBinding
import java.util.Date

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var dbRef: DatabaseReference
    private lateinit var imageUploadHelper: ImageUploadHelper

    private var receiverRoom: String? = null
    private var senderRoom: String? = null
    private var receiverUid: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let {
                uploadImageToStorage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("name")
        receiverUid = intent.getStringExtra("uid")
        val senderUid = FirebaseAuth.getInstance().currentUser?.uid

        dbRef = FirebaseDatabase.getInstance("https://chatapp-9c53c-default-rtdb.firebaseio.com/").reference
        imageUploadHelper = ImageUploadHelper(this)

        // Populate custom header (back button, profile image, name)
        binding.headerUserName.text = name ?: ""

        // Wire header back button to finish
        binding.backButton.setOnClickListener { finish() }

        // Load partner user info (profile image and full name) if uid is available
        if (!receiverUid.isNullOrEmpty()) {
            dbRef.child("users").child(receiverUid!!).get().addOnSuccessListener { snap ->
                val user = snap.getValue(User::class.java)
                if (user != null) {
                    val fullName = user.computeFullName().ifBlank { user.name ?: "" }
                    binding.headerUserName.text = fullName
                    val imageRef = if (!user.profileImageUrl.isNullOrEmpty() && user.profileImageUrl!!.startsWith("/")) {
                        java.io.File(user.profileImageUrl!!)
                    } else {
                        user.profileImageUrl
                    }
                    com.bumptech.glide.Glide.with(this).load(imageRef).placeholder(R.drawable.default_avatar).into(binding.headerProfileImage)
                }
            }
        }

        senderRoom = receiverUid + senderUid
        receiverRoom = senderUid + receiverUid

        messageList = ArrayList()
        messageAdapter = MessageAdapter(this, messageList)

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = messageAdapter

        listenForMessages()

        binding.sendButton.setOnClickListener {
            val messageText = binding.messageEditText.text.toString()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText, "TEXT")
            }
        }

        binding.attachFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImage.launch(intent)
        }

        // Wire more/options button to show popup menu actions
        binding.moreOptionsButton.setOnClickListener { view ->
            try {
                val popup = android.widget.PopupMenu(this, view)
                popup.menuInflater.inflate(R.menu.menu_chat, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_view_profile -> {
                            // Open profile view
                            val intent = Intent(this, ProfileViewActivity::class.java)
                            intent.putExtra("uid", receiverUid)
                            startActivity(intent)
                            true
                        }
                        R.id.action_clear_chat -> {
                            // Clear local messages (remove node in both sender/receiver rooms)
                            if (!senderRoom.isNullOrEmpty()) dbRef.child("chats").child(senderRoom!!).child("messages").removeValue()
                            if (!receiverRoom.isNullOrEmpty()) dbRef.child("chats").child(receiverRoom!!).child("messages").removeValue()
                            messageList.clear()
                            messageAdapter.notifyDataSetChanged()
                            true
                        }
                        R.id.action_block_user -> {
                            // Simple placeholder: show toast
                            android.widget.Toast.makeText(this, "User blocked (not implemented)", android.widget.Toast.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            } catch (e: Exception) { }
        }
    }

    private fun listenForMessages() {
        dbRef.child("chats").child(senderRoom!!).child("messages")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val message = snapshot.getValue(Message::class.java)
                    if (message != null) {
                        messageList.add(message)
                        messageAdapter.notifyItemInserted(messageList.size - 1)
                        binding.chatRecyclerView.scrollToPosition(messageList.size - 1)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendMessage(messageContent: String, messageType: String) {
        val senderUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val messageId = dbRef.push().key!!
        val timestamp = Date().time

        val message = when (messageType) {
            "TEXT" -> Message(messageId, senderUid, messageContent, null, timestamp, "TEXT")
            "IMAGE" -> Message(messageId, senderUid, null, messageContent, timestamp, "IMAGE")
            else -> return
        }

        dbRef.child("chats").child(senderRoom!!).child("messages").child(messageId)
            .setValue(message).addOnSuccessListener {
                dbRef.child("chats").child(receiverRoom!!).child("messages").child(messageId)
                    .setValue(message)
            }
        binding.messageEditText.setText("")
    }
    private fun uploadImageToStorage(imageUri: Uri) {
        val senderUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentReceiverUid = receiverUid ?: return

        // Create modern progress dialog
        val progressDialog = ModernProgressDialog(this)
            .create()
            .setMessage("Sending image...")
        progressDialog.show()

        // Use ImageUploadHelper for organized folder structure
        imageUploadHelper.uploadConversationImage(
            imageUri = imageUri,
            senderId = senderUid,
            receiverId = currentReceiverUid,
            onSuccess = { downloadUrl ->
                progressDialog.dismiss()
                sendMessage(downloadUrl, "IMAGE")
                Toast.makeText(this, "📸 Image sent successfully!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { exception ->
                progressDialog.dismiss()
                Toast.makeText(this, "❌ Failed to send image: ${exception.message}", Toast.LENGTH_LONG).show()
            },
            onProgress = { progress ->
                progressDialog.setProgress(progress)
            }
        )
    }
}
