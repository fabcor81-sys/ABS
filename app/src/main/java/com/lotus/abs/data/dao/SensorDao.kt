package com.lotus.abs.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lotus.abs.data.entity.SensorData
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(data: SensorData)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 100")
    fun getRecentData(): Flow<List<SensorData>>

    @Query("DELETE FROM sensor_data WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    @Query("DELETE FROM sensor_data")
    suspend fun clearAll()
}
