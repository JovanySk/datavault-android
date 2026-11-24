package com.example.datavault.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.datavault.data.local.entity.FileAction
import kotlinx.coroutines.flow.Flow

@Dao
interface FileActionDao {

    @Query("SELECT * FROM file_actions ORDER BY timestamp DESC")
    fun getAllActions(): Flow<List<FileAction>>

    @Query("SELECT * FROM file_actions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentActions(limit: Int): Flow<List<FileAction>>

    @Query("SELECT * FROM file_actions WHERE actionType = :type ORDER BY timestamp DESC")
    fun getActionsByType(type: String): Flow<List<FileAction>>

    @Insert
    suspend fun insert(action: FileAction)

    @Delete
    suspend fun delete(action: FileAction)

    @Query("DELETE FROM file_actions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM file_actions")
    fun getCount(): Flow<Int>
}
