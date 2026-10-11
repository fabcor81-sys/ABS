package com.lotus.abs.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import com.lotus.abs.data.entity.DTCRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DTCDao {
    @Insert
    suspend fun insert(record: DTCRecord)

    @Query("SELECT * FROM dtc_records ORDER BY timestamp DESC")
    fun getAllDTCs(): Flow<List<DTCRecord>>

    @Query("DELETE FROM dtc_records")
    suspend fun clearAll()

    @Delete
    suspend fun delete(record: DTCRecord)
}
