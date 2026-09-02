package com.fury.peerconnect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(msg: MessageEntity): Long

    @Update
    suspend fun updateMessage(msg: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :msgId LIMIT 1")
    suspend fun getMessageById(msgId: Long): MessageEntity?

    // Get chat history between ME and ONE FRIEND
    @Query("SELECT * FROM messages WHERE (senderId = :myId AND receiverId = :friendId) OR (senderId = :friendId AND receiverId = :myId) ORDER BY timestamp ASC")
    suspend fun getChatHistory(myId: String, friendId: String): List<MessageEntity>

    // Get all unsent messages for a specific friend (For Resiliency)
    @Query("SELECT * FROM messages WHERE receiverId = :friendId AND isSent = 0")
    suspend fun getUnsentMessages(friendId: String): List<MessageEntity>

    // Get thread messages belonging to a specific alert
    @Query("SELECT * FROM messages WHERE alertId = :alertId ORDER BY timestamp ASC")
    suspend fun getAlertThreadMessages(alertId: String): List<MessageEntity>

    // Mark a specific message as sent
    @Query("UPDATE messages SET isSent = 1 WHERE id = :msgId")
    suspend fun markAsSent(msgId: Int)

    // Get all file messages ordered by newest first
    @Query("SELECT * FROM messages WHERE text LIKE '[FILE]:%' OR text LIKE '📄 Shared a file:%' ORDER BY timestamp DESC")
    suspend fun getAllFileMessages(): List<MessageEntity>
}