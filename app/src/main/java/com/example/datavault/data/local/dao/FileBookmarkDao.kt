package com.example.datavault.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.datavault.data.local.entity.FileBookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface FileBookmarkDao {

    @Query("SELECT * FROM file_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<FileBookmark>>

    @Query("SELECT * FROM file_bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: Int): FileBookmark?

    @Query("SELECT * FROM file_bookmarks WHERE filePath = :path LIMIT 1")
    suspend fun getBookmarkByPath(path: String): FileBookmark?

    @Query("SELECT EXISTS(SELECT 1 FROM file_bookmarks WHERE filePath = :path)")
    fun isBookmarked(path: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: FileBookmark)

    @Update
    suspend fun update(bookmark: FileBookmark)

    @Delete
    suspend fun delete(bookmark: FileBookmark)

    @Query("DELETE FROM file_bookmarks WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM file_bookmarks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM file_bookmarks")
    fun getCount(): Flow<Int>
}
