package com.project.chatapplication

// Data class para sa chat request
data class ChatRequest(
    var requestId: String? = null, // Unique ID for the request itself
    var senderId: String? = null,
    var receiverId: String? = null,
    var status: String = "pending" // pending, accepted, declined
) {
    // Empty constructor na kailangan ng Firebase
    constructor() : this(null, null, null, "pending")
}