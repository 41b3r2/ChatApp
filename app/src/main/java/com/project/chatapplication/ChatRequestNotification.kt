package com.project.chatapplication

data class ChatRequestNotification(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderImageUrl: String = "",
    val receiverId: String = "",
    val message: String = "wants to chat with you!",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending" // pending, accepted, rejected
)
