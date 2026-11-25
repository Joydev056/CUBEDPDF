package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.RecentFileEntity
import com.example.data.remote.AccountDeletionOutcome
import com.example.data.remote.CloudRecentDoc
import com.example.data.remote.UserAccountRepository
import com.example.data.remote.UserProfile
import com.example.data.remote.WorkspaceRole
import com.example.data.remote.sendVerificationEmailToCurrentUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
  data object Loading : CloudUiState<Nothing>
  data class Success<T>(val data: T) : CloudUiState<T>
  data class Error(val message: String) : CloudUiState<Nothing>
}

class AccountViewModel(
  private val repository: UserAccountRepository,
  private val currentUserId: String,
  initialFirstName: String? = null,
  initialLastName: String? = null,
  initialPreferredDisplayName: String? = null,
  initialPreferredRole: WorkspaceRole? = null,
  initialProvider: String? = null
) : ViewModel() {

  val userProfileState: StateFlow<CloudUiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
    .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing user profile", rootError)
      emit(CloudUiState.Error(rootError.localizedMessage ?: "Failed to load cloud profile"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = CloudUiState.Loading
    )

  val cloudRecentDocsState: StateFlow<CloudUiState<List<CloudRecentDoc>>> = repository.observeCloudRecentDocs(currentUserId)
    .map<List<CloudRecentDoc>, CloudUiState<List<CloudRecentDoc>>> { CloudUiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing cloud recent docs", rootError)
      emit(CloudUiState.Error(rootError.localizedMessage ?: "Failed to load synced documents"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = CloudUiState.Loading
    )

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _reauthRequiredForDelete = MutableStateFlow<AccountDeletionOutcome.RequiresReauth?>(null)
  val reauthRequiredForDelete: StateFlow<AccountDeletionOutcome.RequiresReauth?> = _reauthRequiredForDelete.asStateFlow()

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(
        preferredFirstName = initialFirstName,
        preferredLastName = initialLastName,
        preferredDisplayName = initialPreferredDisplayName,
        preferredRole = initialPreferredRole,
        preferredProvider = initialProvider
      )
    }
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun updateProfile(firstName: String, lastName: String, role: WorkspaceRole) {
    val cleanFirst = firstName.trim().take(40)
    val cleanLast = lastName.trim().take(40)
    val combined = listOf(cleanFirst, cleanLast).filter { it.isNotBlank() }.joinToString(" ")
    if (combined.isEmpty() || combined.length > 80) {
      _statusMessage.value = "Name must be 1 to 80 characters."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateUserProfile(cleanFirst, cleanLast, role)
        .onSuccess { _statusMessage.value = "Account profile saved to Cloud Firestore." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not save profile." }
      _isBusy.value = false
    }
  }

  fun sendVerificationEmail() {
    viewModelScope.launch {
      _isBusy.value = true
      sendVerificationEmailToCurrentUser()
        .onSuccess { _statusMessage.value = "Verification email sent. Check your inbox and tap Refresh Status." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not send verification email." }
      _isBusy.value = false
    }
  }

  fun refreshVerificationStatus() {
    viewModelScope.launch {
      _isBusy.value = true
      repository.refreshVerificationStatus()
        .onSuccess { verified ->
          _statusMessage.value = if (verified) {
            "Email verified and synced to Cloud Firestore."
          } else {
            "Email is not verified yet. Open the verification link in your inbox first."
          }
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not refresh verification status." }
      _isBusy.value = false
    }
  }

  fun syncLocalRecentFiles(files: List<RecentFileEntity>) {
    if (files.isEmpty()) {
      _statusMessage.value = "No local recent files to sync yet."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      var syncedCount = 0
      var lastError: String? = null
      files.take(20).forEach { file ->
        val res = repository.syncRecentDoc(
          docKey = "doc_${file.id}",
          fileName = file.fileName,
          mimeType = file.mimeType,
          sizeBytes = file.sizeBytes,
          pageCount = file.pageCount,
          lastToolId = file.lastToolId,
          lastToolName = file.lastToolName,
          summaryNote = file.summaryNote
        )
        if (res.isSuccess) syncedCount++ else lastError = res.exceptionOrNull()?.localizedMessage
      }
      _statusMessage.value = if (lastError == null) {
        "Synced $syncedCount recent document(s) to your account."
      } else {
        "Synced $syncedCount document(s). Error: $lastError"
      }
      _isBusy.value = false
    }
  }

  fun mirrorSingleDocument(
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    pageCount: Int,
    lastToolId: String,
    lastToolName: String,
    summaryNote: String
  ) {
    viewModelScope.launch {
      val docKey = "doc_${fileName.lowercase().hashCode().toUInt()}"
      repository.syncRecentDoc(
        docKey = docKey,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        lastToolId = lastToolId,
        lastToolName = lastToolName,
        summaryNote = summaryNote
      )
    }
  }

  fun deleteCloudRecentDoc(docId: String) {
    viewModelScope.launch {
      repository.deleteCloudRecentDoc(docId)
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not delete cloud item." }
    }
  }

  fun dismissReauthPrompt() {
    _reauthRequiredForDelete.value = null
  }

  fun deleteCloudAccountData(onDeleted: () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteAccountAndCloudData()
        .onSuccess { outcome ->
          when (outcome) {
            AccountDeletionOutcome.Deleted -> {
              _reauthRequiredForDelete.value = null
              _statusMessage.value = "Account and cloud data permanently deleted."
              onDeleted()
            }
            is AccountDeletionOutcome.RequiresReauth -> {
              _reauthRequiredForDelete.value = outcome
              _statusMessage.value = "Cloud data cleared. Please re-verify your sign-in to permanently delete your account."
            }
          }
        }
        .onFailure {
          _statusMessage.value = it.localizedMessage ?: "Failed to delete account and cloud data."
        }
      _isBusy.value = false
    }
  }

  fun completeAccountDeletionAfterReauth(
    reauthAction: suspend () -> Result<Unit>,
    onDeleted: () -> Unit
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val reauthResult = reauthAction()
      if (reauthResult.isFailure) {
        _statusMessage.value = reauthResult.exceptionOrNull()?.localizedMessage
          ?: "Re-authentication failed. Please try again."
        _isBusy.value = false
        return@launch
      }
      repository.deleteAccountAndCloudData()
        .onSuccess { outcome ->
          when (outcome) {
            AccountDeletionOutcome.Deleted -> {
              _reauthRequiredForDelete.value = null
              _statusMessage.value = "Account and cloud data permanently deleted."
              onDeleted()
            }
            is AccountDeletionOutcome.RequiresReauth -> {
              _reauthRequiredForDelete.value = outcome
              _statusMessage.value = "Additional sign-in verification is required to complete account deletion."
            }
          }
        }
        .onFailure {
          _statusMessage.value = it.localizedMessage ?: "Failed to complete account deletion."
        }
      _isBusy.value = false
    }
  }

  companion object {
    private const val TAG = "AccountViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun factory(
      currentUserId: String,
      firstName: String? = null,
      lastName: String? = null,
      preferredDisplayName: String? = null,
      preferredRole: WorkspaceRole? = null,
      preferredProvider: String? = null
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        AccountViewModel(
          repository = UserAccountRepository(db),
          currentUserId = currentUserId,
          initialFirstName = firstName,
          initialLastName = lastName,
          initialPreferredDisplayName = preferredDisplayName,
          initialPreferredRole = preferredRole,
          initialProvider = preferredProvider
        )
      }
    }
  }
}
