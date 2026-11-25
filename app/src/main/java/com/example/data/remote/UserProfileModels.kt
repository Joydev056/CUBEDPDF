package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WorkspaceRole(val label: String) {
  PERSONAL("Personal"),
  BUSINESS("Business"),
  STUDENT("Student"),
  LEGAL_FINANCE("Legal & Finance"),
  CREATIVE("Creative");

  companion object {
    fun fromLabel(raw: String?): WorkspaceRole {
      return entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: PERSONAL
    }
  }
}

/**
 * Private user profile persisted in Cloud Firestore under /users/{uid}.
 * Never stores passwords or sensitive credentials.
 */
@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val userId: String = "",
  val firstName: String = "",
  val lastName: String = "",
  val displayName: String = "",
  val email: String? = null,
  val photoUrl: String? = null,
  val workspaceRole: String = WorkspaceRole.PERSONAL.label,
  val provider: String = "google.com",
  val emailVerified: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudRecentDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val fileName: String = "",
  val mimeType: String = "application/pdf",
  val sizeBytes: Long = 0L,
  val pageCount: Long = 1L,
  val lastToolId: String = "edit",
  val lastToolName: String = "Edit PDF",
  val summaryNote: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
