package com.project.chatapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHAT_REQUEST_CHANNEL_ID = "chat_request_channel"
        const val CHAT_RESPONSE_CHANNEL_ID = "chat_response_channel"
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Chat Request Channel
            val requestChannel = NotificationChannel(
                CHAT_REQUEST_CHANNEL_ID,
                "Chat Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat requests"
                enableLights(true)
                lightColor = 0x3498DB
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            // Chat Response Channel
            val responseChannel = NotificationChannel(
                CHAT_RESPONSE_CHANNEL_ID,
                "Chat Responses",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for chat request responses"
                enableLights(true)
                lightColor = 0x27AE60
            }

            notificationManager.createNotificationChannel(requestChannel)
            notificationManager.createNotificationChannel(responseChannel)
        }
    }

    fun showChatRequestNotification(
        requestId: String,
        senderName: String,
        senderImageUrl: String?
    ) {
        val interestedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "INTERESTED"
            putExtra("requestId", requestId)
        }
        val interestedPendingIntent = PendingIntent.getBroadcast(
            context, 
            requestId.hashCode(), 
            interestedIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notInterestedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "NOT_INTERESTED"
            putExtra("requestId", requestId)
        }
        val notInterestedPendingIntent = PendingIntent.getBroadcast(
            context, 
            requestId.hashCode() + 1, 
            notInterestedIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHAT_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle("💬 Let's Chat?")
            .setContentText("$senderName wants to chat with you!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$senderName sent you a chat request. Would you like to start chatting?"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setColor(0x3498DB)
            .addAction(R.drawable.ic_chat, "💚 Interested", interestedPendingIntent)
            .addAction(R.drawable.ic_close, "❌ Not Interested", notInterestedPendingIntent)
            .build()

        notificationManager.notify(requestId.hashCode(), notification)
    }

    fun showChatResponseNotification(response: String, userName: String) {
        val icon = if (response == "accepted") R.drawable.ic_chat else R.drawable.ic_close
        val title = if (response == "accepted") "🎉 Chat Request Accepted!" else "😔 Chat Request Declined"
        val text = if (response == "accepted") 
            "$userName is interested! You can now start chatting." 
        else 
            "$userName is not interested right now."

        val notification = NotificationCompat.Builder(context, CHAT_RESPONSE_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(if (response == "accepted") 0x27AE60 else 0xE74C3C)
            .build()

        notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
