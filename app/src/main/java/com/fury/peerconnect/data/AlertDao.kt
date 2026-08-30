package com.fury.peerconnect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AlertDao {
    @Insert
    suspend fun insertAlert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 100")
    suspend fun getAllAlerts(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE alertId = :alertId LIMIT 1")
    suspend fun getAlertByAlertId(alertId: String): AlertEntity?

    @Query("SELECT * FROM alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: Int): AlertEntity?

    @Query("UPDATE alerts SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM alerts")
    suspend fun clearAlerts()
}
