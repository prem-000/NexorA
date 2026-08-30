package com.fury.peerconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,        // CONNECTION, NETWORK, TRANSFER, QUEUED, DISCOVERY, SOS
    val title: String,
    val description: String,
    val peerName: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
    val alertId: String? = null,
    val attachmentPath: String? = null
)
