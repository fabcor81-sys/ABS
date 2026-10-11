package com.lotus.abs.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lotus.abs.data.entity.DTCRecord
import com.lotus.abs.data.entity.SensorData
import com.lotus.abs.data.dao.DTCDao
import com.lotus.abs.data.dao.SensorDao

@Database(
    entities = [DTCRecord::class, SensorData::class],
    version = 1,
    exportSchema = false
)
abstract class LotusAbsDatabase : RoomDatabase() {
    abstract fun dtcDao(): DTCDao
    abstract fun sensorDao(): SensorDao

    companion object {
        @Volatile
        private var instance: LotusAbsDatabase? = null

        fun getInstance(context: Context): LotusAbsDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LotusAbsDatabase::class.java,
                    "lotus_abs_db"
                ).build().also { instance = it }
            }
        }
    }
}
