package com.fury.peerconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderId: String,       // Who sent it?
    val receiverId: String,     // Who is it for?
    val text: String,
    val timestamp: Long,
    val isSent: Boolean = false, // TRUE = Delivered, FALSE = Pending (Resiliency!)
    val alertId: String? = null
)
