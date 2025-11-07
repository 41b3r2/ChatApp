package com.project.chatapplication

// Ang User data class na may kasamang profileImageUrl
data class User(
    var name: String? = null,
    var email: String? = null,
    var uid: String? = null,
    var profileImageUrl: String? = ""
) {
    // Empty constructor na kailangan ng Firebase
    constructor() : this(null, null, null, "")
}