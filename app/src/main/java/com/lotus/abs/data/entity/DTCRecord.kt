package com.lotus.abs.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "dtc_records")
data class DTCRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val description: String,
    val status: String,
    val timestamp: Long = Date().time
)

@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rpm: Int,
    val speed: Int,
    val temperature: Float,
    val pressure: Float,
    val timestamp: Long = Date().time
)
