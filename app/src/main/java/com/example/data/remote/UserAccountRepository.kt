package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserAccountRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  // Always connect to the provisioned named enterprise Firestore database
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before accessing Cloud Firestore.")
  }

  private fun resolveProviderId(user: FirebaseUser): String {
    val providerIds = user.providerData.map { it.providerId }
    return when {
      "password" in providerIds && "google.com" !in providerIds -> "password"
      "google.com" in providerIds -> "google.com"
      else -> "password"
    }
  }

  private fun splitDisplayName(fullName: String): Pair<String, String> {
    val trimmed = fullName.trim()
    if (trimmed.isBlank()) return "" to ""
    val first = trimmed.substringBefore(" ").take(40)
    val last = if (trimmed.contains(" ")) trimmed.substringAfter(" ").trim().take(40) else ""
    return first to last
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  fun observeCloudRecentDocs(userId: String): Flow<List<CloudRecentDoc>> = flow {
    val path = "users/$userId/recent_docs"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("recent_docs")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .limit(20)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudRecentDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  /**
   * Ensures the authenticated user's profile document exists in Cloud Firestore with
   * uid, firstName, lastName, displayName, email, createdAt, updatedAt, provider, and emailVerified.
   * Never stores passwords in Firestore.
   */
  suspend fun ensureUserProfile(
    preferredFirstName: String? = null,
    preferredLastName: String? = null,
    preferredDisplayName: String? = null,
    preferredRole: WorkspaceRole? = null,
    preferredProvider: String? = null
  ): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
    return try {
      val user = auth.currentUser
        ?: throw IllegalStateException("User must be signed in before syncing profile.")
      val uid = user.uid
      docPath = "users/$uid"
      val docRef = db.collection("users").document(uid)
      val existingSnapshot = docRef.get().await()

      val cleanPreferredFirst = preferredFirstName?.trim()?.take(40)?.takeIf { it.isNotBlank() }
      val cleanPreferredLast = preferredLastName?.trim()?.take(40)?.takeIf { it.isNotBlank() }
      val combinedPreferred = listOfNotNull(cleanPreferredFirst, cleanPreferredLast)
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotBlank() }

      val resolvedDisplayName = (combinedPreferred
        ?: preferredDisplayName?.trim()?.takeIf { it.isNotBlank() }
        ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "CubedPDF User").take(80)

      val (derivedFirst, derivedLast) = splitDisplayName(resolvedDisplayName)
      val resolvedFirstName = cleanPreferredFirst ?: derivedFirst
      val resolvedLastName = cleanPreferredLast ?: derivedLast

      val safeEmail = user.email?.trim()?.take(160)?.takeIf { it.isNotBlank() }
      val safePhotoUrl = user.photoUrl?.toString()?.take(500)?.takeIf { it.isNotBlank() }
      val resolvedRole = (preferredRole ?: WorkspaceRole.PERSONAL).label
      val resolvedProvider = preferredProvider
        ?.takeIf { it == "password" || it == "google.com" }
        ?: resolveProviderId(user)

      if (!existingSnapshot.exists()) {
        val createPayload = mutableMapOf<String, Any>(
          "uid" to uid,
          "userId" to uid,
          "firstName" to resolvedFirstName,
          "lastName" to resolvedLastName,
          "displayName" to resolvedDisplayName,
          "workspaceRole" to resolvedRole,
          "provider" to resolvedProvider,
          "emailVerified" to user.isEmailVerified,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        if (safeEmail != null) createPayload["email"] = safeEmail
        if (safePhotoUrl != null) createPayload["photoUrl"] = safePhotoUrl
        docRef.set(createPayload).await()
      } else {
        val updatePayload = mutableMapOf<String, Any>(
          "uid" to uid,
          "emailVerified" to user.isEmailVerified,
          "updatedAt" to FieldValue.serverTimestamp()
        )
        val hasExistingFirst = existingSnapshot.contains("firstName")
        val hasExistingLast = existingSnapshot.contains("lastName")
        if (cleanPreferredFirst != null || !hasExistingFirst) {
          updatePayload["firstName"] = resolvedFirstName
        }
        if (cleanPreferredLast != null || !hasExistingLast) {
          updatePayload["lastName"] = resolvedLastName
        }
        if (combinedPreferred != null || !preferredDisplayName.isNullOrBlank()) {
          updatePayload["displayName"] = resolvedDisplayName
        }
        if (preferredRole != null) {
          updatePayload["workspaceRole"] = preferredRole.label
        }
        if (safeEmail != null) updatePayload["email"] = safeEmail
        if (safePhotoUrl != null) updatePayload["photoUrl"] = safePhotoUrl
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateUserProfile(
    firstName: String,
    lastName: String,
    workspaceRole: WorkspaceRole
  ): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
    return try {
      val uid = requireUserId()
      val cleanFirst = firstName.trim().take(40)
      val cleanLast = lastName.trim().take(40)
      val cleanDisplayName = listOf(cleanFirst, cleanLast)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
      require(cleanDisplayName.length in 1..80) { "Name must be between 1 and 80 characters." }
      docPath = "users/$uid"
      val docRef = db.collection("users").document(uid)
      docRef.update(
        mapOf(
          "firstName" to cleanFirst,
          "lastName" to cleanLast,
          "displayName" to cleanDisplayName,
          "workspaceRole" to workspaceRole.label,
          "emailVerified" to (auth.currentUser?.isEmailVerified ?: false),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun refreshVerificationStatus(): Result<Boolean> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
    return try {
      val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
      user.reload().await()
      val verified = user.isEmailVerified
      docPath = "users/${user.uid}"
      db.collection("users").document(user.uid).update(
        mapOf(
          "emailVerified" to verified,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(verified)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun syncRecentDoc(
    docKey: String,
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    pageCount: Int,
    lastToolId: String,
    lastToolName: String,
    summaryNote: String
  ): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recent_docs"
    return try {
      val uid = requireUserId()
      val safeDocId = docKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(100).ifBlank { "doc_1" }
      val docRef = db.collection("users").document(uid).collection("recent_docs").document(safeDocId)
      docPath = docRef.path

      val cleanFileName = fileName.trim().ifBlank { "Document.pdf" }.take(200)
      val cleanMime = mimeType.trim().ifBlank { "application/pdf" }.take(100)
      val cleanSize = sizeBytes.coerceAtLeast(0L)
      val cleanPages = pageCount.coerceIn(1, 10000).toLong()
      val cleanToolId = lastToolId.trim().ifBlank { "edit" }.take(60)
      val cleanToolName = lastToolName.trim().ifBlank { "Edit PDF" }.take(80)
      val cleanSummary = summaryNote.trim().take(300)

      val existing = docRef.get().await()
      if (!existing.exists()) {
        val createPayload = mapOf(
          "userId" to uid,
          "fileName" to cleanFileName,
          "mimeType" to cleanMime,
          "sizeBytes" to cleanSize,
          "pageCount" to cleanPages,
          "lastToolId" to cleanToolId,
          "lastToolName" to cleanToolName,
          "summaryNote" to cleanSummary,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(createPayload).await()
      } else {
        val updatePayload = mapOf(
          "fileName" to cleanFileName,
          "mimeType" to cleanMime,
          "sizeBytes" to cleanSize,
          "pageCount" to cleanPages,
          "lastToolId" to cleanToolId,
          "lastToolName" to cleanToolName,
          "summaryNote" to cleanSummary,
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteCloudRecentDoc(docId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recent_docs/$docId"
    return try {
      val uid = requireUserId()
      require(docId.isNotBlank()) { "Document ID cannot be blank." }
      val docRef = db.collection("users").document(uid).collection("recent_docs").document(docId)
      docPath = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteAccountAndCloudData(): Result<AccountDeletionOutcome> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
    return try {
      val uid = requireUserId()
      val recentSnap = db.collection("users").document(uid).collection("recent_docs").get().await()
      for (doc in recentSnap.documents) {
        doc.reference.delete().await()
      }
      docPath = "users/$uid"
      db.collection("users").document(uid).delete().await()
      // Google Play Account Deletion Policy: also remove Firebase Auth account credentials
      val user = auth.currentUser
      if (user != null) {
        try {
          user.delete().await()
        } catch (_: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
          val providerId = user.providerData
            .firstOrNull { it.providerId == "google.com" || it.providerId == "password" }
            ?.providerId ?: "google.com"
          return Result.success(
            AccountDeletionOutcome.RequiresReauth(
              providerId = providerId,
              email = user.email
            )
          )
        }
      }
      Result.success(AccountDeletionOutcome.Deleted)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteCloudAccountData(): Result<Unit> {
    return deleteAccountAndCloudData().mapCatching { outcome ->
      when (outcome) {
        AccountDeletionOutcome.Deleted -> Unit
        is AccountDeletionOutcome.RequiresReauth -> {
          throw IllegalStateException("Please verify your sign-in to finish permanently deleting your account.")
        }
      }
    }
  }
}

sealed interface AccountDeletionOutcome {
  data object Deleted : AccountDeletionOutcome
  data class RequiresReauth(val providerId: String, val email: String?) : AccountDeletionOutcome
}
