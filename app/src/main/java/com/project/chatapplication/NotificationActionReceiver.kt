package com.project.chatapplication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase

class NotificationActionReceiver : BroadcastReceiver() {
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("requestId") ?: return
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("chat_requests").child(requestId)        // Cancel the notification
        val notificationHelper = NotificationHelper(context)
        notificationHelper.cancelNotification(requestId.hashCode())

        when (intent.action) {
            "INTERESTED" -> {
                // Update the status to "accepted"
                dbRef.child("status").setValue("accepted").addOnSuccessListener {
                    // Send confirmation notification back to sender
                    sendResponseNotification(context, requestId, "accepted")
                    Toast.makeText(context, "Chat request accepted! 💬", Toast.LENGTH_SHORT).show()
                }
            }
            "NOT_INTERESTED" -> {
                // Update the status to "declined"
                dbRef.child("status").setValue("declined").addOnSuccessListener {
                    // Send rejection notification back to sender
                    sendResponseNotification(context, requestId, "declined")
                    Toast.makeText(context, "Chat request declined", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendResponseNotification(context: Context, requestId: String, response: String) {
        val dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("chat_requests").child(requestId)
        
        dbRef.get().addOnSuccessListener { snapshot ->
            val request = snapshot.getValue(ChatRequest::class.java)
            if (request != null) {
                val notificationData = mapOf(
                    "senderId" to request.receiverId,
                    "receiverId" to request.senderId,
                    "type" to "chat_response",
                    "response" to response,
                    "timestamp" to System.currentTimeMillis()
                )
                
                // Store notification in database for the sender to see
                FirebaseDatabase.getInstance(DB_URL)
                    .getReference("notifications")
                    .child(request.senderId!!)
                    .push()
                    .setValue(notificationData)
            }
        }
    }
}