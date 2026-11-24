package com.example.datavault.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_actions")
data class FileAction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String,
    val filePath: String,
    val fileName: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
