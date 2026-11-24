package com.example.datavault.data.local.dao

import androidx.room.*
import com.example.datavault.data.local.entity.VaultFile
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY addedAt DESC")
    fun getAllVaultFiles(): Flow<List<VaultFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaultFile: VaultFile)

    @Delete
    suspend fun delete(vaultFile: VaultFile)

    @Query("SELECT * FROM vault_files WHERE id = :id")
    suspend fun getById(id: Int): VaultFile?
}
