package com.vix.one.ms

data class User(
    val id: String,
    val tag: String,
    val name: String,
    val created: Long = System.currentTimeMillis()
)

data class ChatPreview(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val membersCount: Int = 1
)

data class Message(
    val id: String,
    val senderId: String,
    val senderTag: String,
    val text: String,          // already decrypted for UI
    val encrypted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text"  // text | file | link
)
