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
    private lateinit var connectedAdapter: UserAdapter
    private val TAG = "MainActivity"
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"
    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var notificationHelper: NotificationHelper
    // map senderId -> pending count (requests sent to me)
    private val pendingRequestsBySender: MutableMap<String, Int> = mutableMapOf()
    // store all users pulled from DB
    private val allUsers: MutableMap<String, User> = mutableMapOf()
    // main lists as class-level so we can rebuild them when pending changes
    private val userList: ArrayList<User> = ArrayList()
    private val connectedList: ArrayList<User> = ArrayList()
    private var currentUid: String? = null

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

        // Notification inbox button opens the in-app NotificationListActivity
        binding.notificationInboxButton.setOnClickListener {
            val intent = Intent(this, NotificationListActivity::class.java)
            startActivity(intent)
        }

        // Setup search functionality
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    // Do NOT request system notification permission: we keep notifications inside the app only

    adapter = UserAdapter(this, userList)
    // Adapter for connected users
    connectedAdapter = UserAdapter(this, connectedList)
        binding.userRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.userRecyclerView.adapter = adapter
    binding.connectedRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    binding.connectedRecyclerView.adapter = connectedAdapter
    currentUid = currentUser.uid

        listenForUsers(currentUser.uid)
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

    private fun listenForUsers(myUid: String) {
        binding.statusTextView.text = "Loading users..."
        val usersRef = dbRef.child("users")
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allUsers.clear()
                if (!snapshot.exists()) {
                    binding.statusTextView.text = "No other users found."
                    return
                }
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    if (user != null) {
                        if (user.uid.isNullOrEmpty()) user.uid = userSnapshot.key
                        if (user.uid != myUid) allUsers[user.uid!!] = user
                    }
                }

                // Build connected set and then rebuild visible lists
                buildConnectedSet(myUid) { connectedSet ->
                    rebuildVisibleLists(myUid, connectedSet)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                binding.statusTextView.text = "DATABASE ERROR: ${error.message}"
            }
        })
    }

    private fun rebuildVisibleLists(myUid: String, connectedSet: Set<String>) {
        userList.clear()
        connectedList.clear()

        // Exclude users who have sent a pending request to me
        val pendingSenders = pendingRequestsBySender.keys

        for ((uid, u) in allUsers) {
            if (uid == myUid) continue
            if (pendingSenders.contains(uid)) continue // hide senders from home
            u.isConnected = connectedSet.contains(uid)
            if (u.isConnected) connectedList.add(u) else userList.add(u)
        }

        // Update visibility
        if (connectedList.isEmpty()) {
            binding.connectionsHeader.visibility = View.GONE
            binding.connectedRecyclerView.visibility = View.GONE
        } else {
            binding.connectionsHeader.visibility = View.VISIBLE
            binding.connectedRecyclerView.visibility = View.VISIBLE
        }

        if (userList.isEmpty()) {
            binding.statusTextView.text = "You have no other users to invite."
            binding.statusTextView.visibility = View.VISIBLE
            binding.userRecyclerView.visibility = View.GONE
        } else {
            binding.statusTextView.visibility = View.GONE
            binding.userRecyclerView.visibility = View.VISIBLE
        }

        adapter.updateUsers()
        connectedAdapter.updateUsers()
    }

    // Build a set of userIds that are "connected" to myUid either via accepted requests or existing chats
    private fun buildConnectedSet(myUid: String, onComplete: (Set<String>) -> Unit) {
        val connectedSet = HashSet<String>()

        // First, find accepted chat_requests where either sender or receiver is me
        dbRef.child("chat_requests").orderByChild("status").equalTo("accepted").get()
            .addOnSuccessListener { snapshot ->
                for (reqSnap in snapshot.children) {
                    val req = reqSnap.getValue(ChatRequest::class.java)
                    if (req != null) {
                        if (req.senderId == myUid && !req.receiverId.isNullOrEmpty()) connectedSet.add(req.receiverId!!)
                        if (req.receiverId == myUid && !req.senderId.isNullOrEmpty()) connectedSet.add(req.senderId!!)
                    }
                }
            }
            .addOnCompleteListener {
                // Also look at chats keys to find active conversations
                dbRef.child("chats").get().addOnSuccessListener { chatSnap ->
                    for (chatKeySnap in chatSnap.children) {
                        val key = chatKeySnap.key ?: continue
                        if (key.contains(myUid)) {
                            val other = key.replace(myUid, "")
                            if (other.isNotEmpty()) connectedSet.add(other)
                        }
                    }
                    onComplete(connectedSet)
                }.addOnFailureListener {
                    onComplete(connectedSet)
                }
            }
    }

    private fun listenForChatRequests(myUid: String) {
        val requestsRef = dbRef.child("chat_requests").orderByChild("receiverId").equalTo(myUid)
        requestsRef.addChildEventListener(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val request = snapshot.getValue(ChatRequest::class.java)
                if (request != null && request.status == "pending") {
                    // increment pending count for sender (in-app only)
                    val sid = request.senderId ?: return
                    pendingRequestsBySender[sid] = (pendingRequestsBySender[sid] ?: 0) + 1
                    updateUsersPendingCounts()
                    // no external/system notification — in-app badge and NotificationListActivity will surface this
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val request = snapshot.getValue(ChatRequest::class.java) ?: return
                val sid = request.senderId ?: return
                if (request.status != "pending") {
                    // request resolved -> remove count if present
                    val current = pendingRequestsBySender[sid] ?: 0
                    if (current > 1) pendingRequestsBySender[sid] = current - 1 else pendingRequestsBySender.remove(sid)
                    updateUsersPendingCounts()
                } else {
                    // still pending: ensure counted
                    pendingRequestsBySender[sid] = (pendingRequestsBySender[sid] ?: 0) + 1
                    updateUsersPendingCounts()
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val request = snapshot.getValue(ChatRequest::class.java) ?: return
                val sid = request.senderId ?: return
                val current = pendingRequestsBySender[sid] ?: 0
                if (current > 1) pendingRequestsBySender[sid] = current - 1 else pendingRequestsBySender.remove(sid)
                updateUsersPendingCounts()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "Chat request listener cancelled", error.toException()) }
        })
    }

    private fun updateUsersPendingCounts() {
        // Iterate current adapters' underlying lists and set pending counts
        val totalPending = pendingRequestsBySender.values.sum()

        // Update users in adapters' backing lists (they share instances with userList/connectedList)
        // We can't access those local lists directly here, but adapters' filtered lists are refreshed
        // by calling updateUsers() after we mutate the User objects. So we find all users displayed
        // in the adapters and update their pendingRequestCount.
        try {
            // update main adapter list
            val mainField = adapter.javaClass.getDeclaredField("userList")
            mainField.isAccessible = true
        } catch (e: Exception) {
            // fallback: iterate through adapter's filtered list via reflection or simply trigger full reload
        }

        // Simple and safe approach: fetch all users from DB and apply counts to matching user instances
        dbRef.child("users").get().addOnSuccessListener { snap ->
            for (uSnap in snap.children) {
                val u = uSnap.getValue(User::class.java) ?: continue
                // ensure uid
                if (u.uid.isNullOrEmpty()) u.uid = uSnap.key
                val count = pendingRequestsBySender[u.uid] ?: 0
                // Update user instances in adapters by searching their lists
                // Update in adapter's filtered list via adapter methods: we assume adapter holds references to same User instances from DB
                // To ensure the UI reflects changes, we'll iterate adapter lists and set the field where uid matches
                try {
                    // update filtered lists via adapter's exposed method updateUsers() after mutation
                    // mutate adapter entries
                    // main adapter
                    val filteredListField = adapter.javaClass.getDeclaredField("filteredUserList")
                    filteredListField.isAccessible = true
                    val listObj = filteredListField.get(adapter) as? ArrayList<User>
                    listObj?.forEach { if (it.uid == u.uid) it.pendingRequestCount = count }
                    // connected adapter
                    val filteredListField2 = connectedAdapter.javaClass.getDeclaredField("filteredUserList")
                    filteredListField2.isAccessible = true
                    val listObj2 = filteredListField2.get(connectedAdapter) as? ArrayList<User>
                    listObj2?.forEach { if (it.uid == u.uid) it.pendingRequestCount = count }
                } catch (ex: Exception) {
                    // If reflection fails (obfuscation, different field names), ignore and rely on full refresh
                }
            }

            // Notify adapters to refresh UI
            adapter.updateUsers()
            connectedAdapter.updateUsers()

            // Update header badge
            updateNotificationBadge(totalPending)
            // Rebuild visible lists so users who sent pending requests are hidden from home
            try {
                val uid = currentUid
                if (!uid.isNullOrEmpty()) {
                    buildConnectedSet(uid) { connectedSet ->
                        rebuildVisibleLists(uid, connectedSet)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun updateNotificationBadge(total: Int) {
        runOnUiThread {
            if (total <= 0) {
                binding.notificationBadge.visibility = View.GONE
            } else {
                binding.notificationBadge.visibility = View.VISIBLE
                binding.notificationBadge.text = if (total > 99) "99+" else total.toString()
            }
        }
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
        // support local file paths saved by ImageUploadHelper (absolute path) or remote URLs
        val profileRef = if (!user.profileImageUrl.isNullOrEmpty() && user.profileImageUrl!!.startsWith("/")) {
            java.io.File(user.profileImageUrl!!)
        } else {
            user.profileImageUrl
        }

        Glide.with(this).load(profileRef).placeholder(android.R.drawable.sym_def_app_icon).into(userProfileImage)

        val dialog = builder.create()

        if (user.isConnected) {
            // If already connected, allow opening chat instead of sending a new request
            letsChatButton.text = "💬 Open Chat"
            letsChatButton.setOnClickListener {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("name", user.name)
                intent.putExtra("uid", user.uid)
                startActivity(intent)
                dialog.dismiss()
            }
        } else {
            letsChatButton.text = "💬 Let's Chat?"
            letsChatButton.isEnabled = true
            letsChatButton.setOnClickListener {
                sendChatRequest(user)
                dialog.dismiss()
            }
        }

        dialog.show()
        // Wire "View Profile" button inside the dialog to open a full profile view Activity
        try {
            val viewProfileButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_view_profile_button)
            viewProfileButton?.setOnClickListener {
                val intent = Intent(this, ProfileViewActivity::class.java)
                intent.putExtra("uid", user.uid)
                intent.putExtra("name", user.name)
                startActivity(intent)
                dialog.dismiss()
            }
        } catch (e: Exception) {
            // safe fallback: ignore if binding/view not present
        }
    }

    private fun sendChatRequest(receiver: User) {
        val senderId = auth.currentUser?.uid
        if (senderId == null || receiver.uid == null) return
        // Check if users are already connected (accepted request) — don't allow another "Let's Chat"
        // Check accepted requests in both directions
        dbRef.child("chat_requests").orderByChild("senderId").equalTo(senderId).get()
            .addOnSuccessListener { sentByMeSnap ->
                for (s in sentByMeSnap.children) {
                    val r = s.getValue(ChatRequest::class.java)
                    if (r != null && r.receiverId == receiver.uid && r.status == "accepted") {
                        Toast.makeText(this, "✅ You are already connected with ${receiver.name}", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                }

                // Also check if the other user has an accepted request to me
                dbRef.child("chat_requests").orderByChild("senderId").equalTo(receiver.uid).get()
                    .addOnSuccessListener { sentByThemSnap ->
                        for (s2 in sentByThemSnap.children) {
                            val r2 = s2.getValue(ChatRequest::class.java)
                            if (r2 != null && r2.receiverId == senderId && r2.status == "accepted") {
                                Toast.makeText(this, "✅ You are already connected with ${receiver.name}", Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }
                        }

                        // If not connected, ensure there's no pending request from me to them
                        dbRef.child("chat_requests").orderByChild("senderId").equalTo(senderId)
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
                                        Toast.makeText(this, "💌 'Let's Chat?' request sent to ${receiver.name}!", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { 
                                        Toast.makeText(this, "❌ Failed to send request. Please try again.", Toast.LENGTH_SHORT).show()
                                    }
                            }
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
                        // Show an in-app dialog informing the user (no system notification)
                        dbRef.child("users").child(senderId).get().addOnSuccessListener { userSnapshot ->
                            val user = userSnapshot.getValue(User::class.java)
                            val userName = user?.name ?: "Someone"
                            runOnUiThread {
                                val msg = when (response) {
                                    "accepted" -> "$userName accepted your request. You can now chat."
                                    "declined" -> "$userName declined your request."
                                    else -> "$userName responded: $response"
                                }
                                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Chat Response")
                                    .setMessage(msg)
                                    .setPositiveButton(if (response == "accepted") "Open Chat" else "OK") { d, _ ->
                                        if (response == "accepted") {
                                            // open chat with the sender
                                            val intent = Intent(this@MainActivity, ChatActivity::class.java)
                                            intent.putExtra("name", user?.name)
                                            intent.putExtra("uid", user?.uid)
                                            startActivity(intent)
                                        }
                                        d.dismiss()
                                    }
                                    .show()
                            }
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