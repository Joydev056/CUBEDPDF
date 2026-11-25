package com.example.data.repository

import com.example.data.local.RecentFileDao
import com.example.data.local.RecentFileEntity
import kotlinx.coroutines.flow.Flow

class RecentFileRepository(
  private val recentFileDao: RecentFileDao
) {
  val recentFiles: Flow<List<RecentFileEntity>> = recentFileDao.getRecentFiles()

  suspend fun recordRecentFile(
    fileName: String,
    fileUriString: String,
    mimeType: String,
    sizeBytes: Long,
    pageCount: Int,
    lastToolId: String,
    lastToolName: String,
    summaryNote: String,
    updatedAt: Long = System.currentTimeMillis()
  ): Long {
    val existing = recentFileDao.findByUri(fileUriString)
    val entity = RecentFileEntity(
      id = existing?.id ?: 0L,
      fileName = fileName,
      fileUriString = fileUriString,
      mimeType = mimeType,
      sizeBytes = sizeBytes,
      pageCount = pageCount,
      lastToolId = lastToolId,
      lastToolName = lastToolName,
      summaryNote = summaryNote,
      updatedAt = updatedAt
    )
    return recentFileDao.insertRecentFile(entity)
  }

  suspend fun deleteById(id: Long) {
    recentFileDao.deleteById(id)
  }

  suspend fun clearAll() {
    recentFileDao.clearAll()
  }
}
