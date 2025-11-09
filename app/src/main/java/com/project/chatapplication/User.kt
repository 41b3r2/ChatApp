package com.project.chatapplication

// Ang User data class na may kasamang profileImageUrl
data class User(
    var firstName: String? = null,
    var middleName: String? = null,
    var lastName: String? = null,
    var birthday: String? = null,
    var contact: String? = null,
    var email: String? = null,
    var username: String? = null,
    var uid: String? = null,
    var profileImageUrl: String? = "",
    // convenience full name kept for compatibility with existing UI
    var name: String? = null,
    // Transient flag used in UI to mark existing connections
    var isConnected: Boolean = false
) {
    // Empty constructor na kailangan ng Firebase
    // transient UI-only fields: provide defaults
    var pendingRequestCount: Int = 0

    constructor() : this(null, null, null, null, null, null, null, null, "", null, false) {
        pendingRequestCount = 0
    }

    fun computeFullName(): String {
        val parts = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
        return if (parts.isEmpty()) name ?: "" else parts.joinToString(" ")
    }
}