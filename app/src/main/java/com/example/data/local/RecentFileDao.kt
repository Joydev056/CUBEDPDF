package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {

  @Query("SELECT * FROM recent_files ORDER BY updatedAt DESC LIMIT 25")
  fun getRecentFiles(): Flow<List<RecentFileEntity>>

  @Query("SELECT * FROM recent_files WHERE fileUriString = :fileUriString LIMIT 1")
  suspend fun findByUri(fileUriString: String): RecentFileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRecentFile(item: RecentFileEntity): Long

  @Query("DELETE FROM recent_files WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM recent_files")
  suspend fun clearAll()
}
