package com.example.datavault.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_bookmarks")
data class FileBookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val fileType: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
