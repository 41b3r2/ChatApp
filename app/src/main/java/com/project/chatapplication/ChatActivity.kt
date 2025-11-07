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

        supportActionBar?.title = name

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
