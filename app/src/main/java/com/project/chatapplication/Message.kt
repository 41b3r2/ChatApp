package com.project.chatapplication

// Upgraded Message data class to support different message types
data class Message(
    var messageId: String? = null,
    var senderId: String? = null,
    var message: String? = null, // For text messages
    var imageUrl: String? = null, // For image messages
    var timestamp: Long = 0,
    var messageType: String = "TEXT" // TEXT or IMAGE
) {
    constructor() : this(null, null, null, null, 0, "TEXT")
}