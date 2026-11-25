package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "recent_files",
  indices = [Index(value = ["fileUriString"], unique = true)]
)
data class RecentFileEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val fileName: String,
  val fileUriString: String,
  val mimeType: String = "application/pdf",
  val sizeBytes: Long,
  val pageCount: Int,
  val lastToolId: String,
  val lastToolName: String,
  val summaryNote: String,
  val updatedAt: Long = System.currentTimeMillis()
)
