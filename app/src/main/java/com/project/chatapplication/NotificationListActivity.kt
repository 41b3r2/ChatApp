package com.project.chatapplication

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.project.chatapplication.databinding.ActivityNotificationListBinding

class NotificationListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationListBinding
    private val TAG = "NotificationListAct"
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"
    private val auth = FirebaseAuth.getInstance()

    private val pendingList = ArrayList<PendingRequest>()
    private lateinit var adapter: NotificationListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "Chat Requests"
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = NotificationListAdapter(this, pendingList) { pending ->
            showDecisionDialog(pending)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadPendingRequests()
    }

    private fun loadPendingRequests() {
        val myUid = auth.currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("chat_requests")

        // query pending requests where receiverId == myUid
        dbRef.orderByChild("receiverId").equalTo(myUid).addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pendingList.clear()
                for (s in snapshot.children) {
                    val req = s.getValue(ChatRequest::class.java) ?: continue
                    if (req.status != "pending") continue
                    // fetch sender user
                    val senderId = req.senderId ?: continue
                    FirebaseDatabase.getInstance(DB_URL).getReference("users").child(senderId)
                        .get().addOnSuccessListener { userSnap ->
                            val senderUser = userSnap.getValue(User::class.java)
                            if (senderUser != null) {
                                if (senderUser.uid.isNullOrEmpty()) senderUser.uid = userSnap.key
                                pendingList.add(PendingRequest(req, senderUser))
                                adapter.notifyDataSetChanged()
                            }
                        }.addOnFailureListener { e -> Log.w(TAG, "Failed to load sender user", e) }
                }
                if (pendingList.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                } else {
                    binding.emptyText.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "loadPendingRequests cancelled", error.toException())
            }
        })
    }

    private fun showDecisionDialog(pending: PendingRequest) {
        val sender = pending.sender
        val request = pending.request

        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_user_profile, null)
        builder.setView(view)

        // customize dialog content a bit
        val dialog = builder.create()
        dialog.show()

        // set texts/images inside dialog
        try {
            val nameTv = view.findViewById<android.widget.TextView>(R.id.dialog_user_name)
            val profileImg = view.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.dialog_profile_image)
            nameTv.text = sender.name
            val profileRef = if (!sender.profileImageUrl.isNullOrEmpty() && sender.profileImageUrl!!.startsWith("/")) java.io.File(sender.profileImageUrl!!) else sender.profileImageUrl
            com.bumptech.glide.Glide.with(this).load(profileRef).placeholder(R.drawable.default_avatar).into(profileImg)
        } catch (e: Exception) { /* ignore missing views */ }

        // Add Interested / Not Interested buttons below dialog (custom)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Interested") { _, _ ->
            respondToRequest(request, "accepted")
            pendingList.remove(pending)
            adapter.notifyDataSetChanged()
            dialog.dismiss()
        }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Not Interested") { _, _ ->
            respondToRequest(request, "declined")
            pendingList.remove(pending)
            adapter.notifyDataSetChanged()
            dialog.dismiss()
        }
    }

    private fun respondToRequest(request: ChatRequest, response: String) {
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("chat_requests").child(request.requestId!!)
        dbRef.child("status").setValue(response).addOnSuccessListener {
            // notify sender
            val notificationData = mapOf(
                "senderId" to request.receiverId,
                "receiverId" to request.senderId,
                "type" to "chat_response",
                "response" to response,
                "timestamp" to System.currentTimeMillis()
            )
            FirebaseDatabase.getInstance(DB_URL).getReference("notifications").child(request.senderId!!).push().setValue(notificationData)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    data class PendingRequest(val request: ChatRequest, val sender: User)
}
