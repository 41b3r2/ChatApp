package com.project.chatapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.project.chatapplication.databinding.ActivityMainBinding
import de.hdodenhof.circleimageview.CircleImageView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UserAdapter
    private val TAG = "MainActivity"
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"
    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var notificationHelper: NotificationHelper

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications permission denied. You may miss chat requests.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        dbRef = FirebaseDatabase.getInstance(DB_URL).reference
        notificationHelper = NotificationHelper(this)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        binding.accountButton.setOnClickListener {
            startActivity(Intent(this, MyAccountActivity::class.java))
        }

        // Setup search functionality
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        askNotificationPermission()

        val userList = ArrayList<User>()
        adapter = UserAdapter(this, userList)
        binding.userRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.userRecyclerView.adapter = adapter

        listenForUsers(currentUser.uid, userList)
        listenForChatRequests(currentUser.uid)
        listenForAcceptedRequests(currentUser.uid)
        listenForNotificationResponses(currentUser.uid)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun listenForUsers(myUid: String, userList: ArrayList<User>) {
        binding.statusTextView.text = "Loading users..."
        val usersRef = dbRef.child("users")
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                if (!snapshot.exists()) {
                    binding.statusTextView.text = "No other users found."
                    return
                }
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    if (user != null && user.uid != myUid) {
                        userList.add(user)
                    }
                }
                if (userList.isEmpty()) {
                    binding.statusTextView.text = "You are the only user."
                } else {
                    binding.statusTextView.visibility = View.GONE
                    binding.userRecyclerView.visibility = View.VISIBLE
                }
                adapter.updateUsers()
            }
            override fun onCancelled(error: DatabaseError) {
                binding.statusTextView.text = "DATABASE ERROR: ${error.message}"
            }
        })
    }

    private fun listenForChatRequests(myUid: String) {
        val requestsRef = dbRef.child("chat_requests").orderByChild("receiverId").equalTo(myUid)
        requestsRef.addChildEventListener(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val request = snapshot.getValue(ChatRequest::class.java)
                if (request != null && request.status == "pending") {
                    showChatRequestNotification(request)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "Chat request listener cancelled", error.toException()) }
        })
    }

    private fun showChatRequestNotification(request: ChatRequest) {
        // Get sender info for better notification
        dbRef.child("users").child(request.senderId!!).get().addOnSuccessListener { userSnapshot ->
            val senderUser = userSnapshot.getValue(User::class.java)
            val senderName = senderUser?.name ?: "Someone"
            val senderImageUrl = senderUser?.profileImageUrl

            notificationHelper.showChatRequestNotification(
                request.requestId!!,
                senderName,
                senderImageUrl
            )
        }
    }



    fun showUserProfileDialog(user: User) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_user_profile, null)
        builder.setView(dialogView)

        val userProfileImage = dialogView.findViewById<CircleImageView>(R.id.dialog_profile_image)
        val userName = dialogView.findViewById<TextView>(R.id.dialog_user_name)
        val letsChatButton = dialogView.findViewById<Button>(R.id.dialog_lets_chat_button)

        userName.text = user.name
        Glide.with(this).load(user.profileImageUrl).placeholder(android.R.drawable.sym_def_app_icon).into(userProfileImage)

        val dialog = builder.create()

        letsChatButton.setOnClickListener {
            sendChatRequest(user)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun sendChatRequest(receiver: User) {
        val senderId = auth.currentUser?.uid
        if (senderId == null || receiver.uid == null) return
        
        // Check if there's already a pending request
        dbRef.child("chat_requests")
            .orderByChild("senderId").equalTo(senderId)
            .get().addOnSuccessListener { requestsSnapshot ->
                var hasExistingRequest = false
                for (snapshot in requestsSnapshot.children) {
                    val request = snapshot.getValue(ChatRequest::class.java)
                    if (request?.receiverId == receiver.uid && request?.status == "pending") {
                        hasExistingRequest = true
                        break
                    }
                }
                
                if (hasExistingRequest) {
                    Toast.makeText(this, "⏳ You already have a pending request with ${receiver.name}", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                
                // Create new request
                val requestId = dbRef.child("chat_requests").push().key ?: return@addOnSuccessListener
                val chatRequest = ChatRequest(requestId, senderId, receiver.uid!!, "pending")
                dbRef.child("chat_requests").child(requestId).setValue(chatRequest)
                    .addOnSuccessListener {
                        Toast.makeText(this, "💌 'Let's Chat?' request sent to ${receiver.name}!\nYou can send unlimited requests.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "❌ Failed to send request. Please try again.", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    
    private fun listenForAcceptedRequests(myUid: String) {
        val requestsRef = dbRef.child("chat_requests").orderByChild("senderId").equalTo(myUid)
        requestsRef.addChildEventListener(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) { /* Not used */ }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val request = snapshot.getValue(ChatRequest::class.java)
                if (request != null && request.status == "accepted") {
                    dbRef.child("users").child(request.receiverId!!).get().addOnSuccessListener {
                        val otherUser = it.getValue(User::class.java)
                        if(otherUser != null) {
                            val intent = Intent(this@MainActivity, ChatActivity::class.java)
                            intent.putExtra("name", otherUser.name)
                            intent.putExtra("uid", otherUser.uid)
                            startActivity(intent)
                            snapshot.ref.removeValue()
                        }
                    }
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) { /* Not used */ }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Not used */ }
            override fun onCancelled(error: DatabaseError) { /* Not used */ }
        })
    }

    private fun listenForNotificationResponses(myUid: String) {
        val notificationsRef = dbRef.child("notifications").child(myUid)
        notificationsRef.addChildEventListener(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                @Suppress("UNCHECKED_CAST")
                val notification = snapshot.getValue(Map::class.java) as? Map<String, Any>
                if (notification != null && notification["type"] == "chat_response") {
                    val response = notification["response"] as? String
                    val senderId = notification["senderId"] as? String
                    if (senderId != null) {
                        dbRef.child("users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                            val user = userSnapshot.getValue(User::class.java)
                            val userName = user?.name ?: "Someone"
                            notificationHelper.showChatResponseNotification(response ?: "", userName)
                        }
                    }
                    // Remove the notification after showing it
                    snapshot.ref.removeValue()
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun goToLogin(){
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}