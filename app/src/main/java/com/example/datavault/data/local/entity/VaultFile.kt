package com.example.datavault.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalPath: String,
    val encryptedFileName: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSize: Long,
    val addedAt: Long = System.currentTimeMillis()
)
