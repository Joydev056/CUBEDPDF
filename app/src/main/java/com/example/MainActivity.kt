package com.example

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.analytics.AnalyticsHelper
import com.example.data.remote.WorkspaceRole
import com.example.data.remote.authStateFlow
import com.example.data.remote.reauthenticateWithEmailPassword
import com.example.data.remote.reauthenticateWithGoogle
import com.example.data.remote.signOutWithCredentialClear
import com.example.ui.screens.AccountProfileDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PdfToolsDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AccountViewModel
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.RecentFilesViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

private const val SESSION_TAG = "CubedSession"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AnalyticsHelper.initialize(this)
    AnalyticsHelper.logScreenView("cubedpdf_home")

    // Configure edge-to-edge ONCE before setContent so WindowManager completes a single
    // clean SurfaceSyncGroup transaction on startup instead of overlapping relayouts.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }

    setContent {
      var isDarkTheme by rememberSaveable { mutableStateOf(true) }
      var hasAppliedInitialBars by remember { mutableStateOf(false) }

      // Update bar icon luminance in-place via WindowInsetsControllerCompat only when the
      // user toggles themes, avoiding redundant enableEdgeToEdge() window relayouts.
      LaunchedEffect(isDarkTheme) {
        if (!hasAppliedInitialBars && isDarkTheme) {
          hasAppliedInitialBars = true
          return@LaunchedEffect
        }
        hasAppliedInitialBars = true
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
      }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        AppNavigation(
          isDarkTheme = isDarkTheme,
          onToggleTheme = {
            isDarkTheme = !isDarkTheme
            AnalyticsHelper.logThemeToggled(isDarkTheme)
          }
        )
      }
    }
  }
}

/**
 * Root authentication router powered by FirebaseAuth's persistent session storage and
 * live AuthStateListener. Returning authenticated users launch directly into the CubedPDF
 * dashboard until they explicitly sign out or their session is invalidated.
 */
@Composable
fun AppNavigation(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  auth: FirebaseAuth? = null
) {
  val authInstance = remember(auth) { auth ?: Firebase.auth }
  val authFlow = remember(authInstance) { authInstance.authStateFlow() }
  val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = authInstance.currentUser)

  var pendingFirstName by remember { mutableStateOf<String?>(null) }
  var pendingLastName by remember { mutableStateOf<String?>(null) }
  var pendingDisplayName by remember { mutableStateOf<String?>(null) }
  var pendingRole by remember { mutableStateOf<WorkspaceRole?>(null) }
  var pendingProvider by remember { mutableStateOf<String?>(null) }
  var isGuestMode by rememberSaveable { mutableStateOf(false) }

  val user = currentUser
  LaunchedEffect(user) {
    if (user != null) {
      isGuestMode = false
    }
  }

  if (user == null && !isGuestMode) {
    AuthScreen(
      isDarkTheme = isDarkTheme,
      onToggleTheme = onToggleTheme,
      onPrepareEmailSignUp = { firstName, lastName, displayName ->
        pendingFirstName = firstName
        pendingLastName = lastName
        pendingDisplayName = displayName
        pendingRole = WorkspaceRole.PERSONAL
        pendingProvider = "password"
      },
      onAuthSuccess = { firstName, lastName, displayName, role, provider ->
        isGuestMode = false
        pendingFirstName = firstName
        pendingLastName = lastName
        pendingDisplayName = displayName
        pendingRole = role
        pendingProvider = provider
      },
      onContinueAsGuest = {
        isGuestMode = true
      }
    )
  } else if (user == null && isGuestMode) {
    GuestWorkspaceHost(
      isDarkTheme = isDarkTheme,
      onToggleTheme = onToggleTheme,
      onExitGuestToSignIn = { isGuestMode = false }
    )
  } else if (user != null) {
    // Background validation of persisted session so disabled/deleted accounts sign out cleanly
    LaunchedEffect(user.uid) {
      runCatching { user.reload().await() }
        .onFailure { err ->
          if (err is FirebaseAuthInvalidUserException) {
            Log.w(SESSION_TAG, "Persisted session invalidated by server; signing out.", err)
            authInstance.signOut()
          }
        }
    }

    key(user.uid) {
      AuthenticatedWorkspaceHost(
        currentUserId = user.uid,
        fallbackDisplayName = user.displayName ?: user.email?.substringBefore('@') ?: "CubedPDF User",
        userEmail = user.email,
        isEmailVerified = user.isEmailVerified,
        preferredFirstName = pendingFirstName,
        preferredLastName = pendingLastName,
        preferredDisplayName = pendingDisplayName,
        preferredRole = pendingRole,
        preferredProvider = pendingProvider,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme
      )
    }
  }
}

@Composable
private fun AuthenticatedWorkspaceHost(
  currentUserId: String,
  fallbackDisplayName: String,
  userEmail: String?,
  isEmailVerified: Boolean,
  preferredFirstName: String?,
  preferredLastName: String?,
  preferredDisplayName: String?,
  preferredRole: WorkspaceRole?,
  preferredProvider: String?,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { lazy { CredentialManager.create(context) } }

  val accountViewModel: AccountViewModel = viewModel(
    key = currentUserId,
    factory = AccountViewModel.factory(
      currentUserId = currentUserId,
      firstName = preferredFirstName,
      lastName = preferredLastName,
      preferredDisplayName = preferredDisplayName,
      preferredRole = preferredRole,
      preferredProvider = preferredProvider
    )
  )
  val recentFilesViewModel: RecentFilesViewModel = viewModel(
    factory = RecentFilesViewModel.Factory(context)
  )

  val profileState by accountViewModel.userProfileState.collectAsStateWithLifecycle()
  val cloudDocsState by accountViewModel.cloudRecentDocsState.collectAsStateWithLifecycle()
  val statusMessage by accountViewModel.statusMessage.collectAsStateWithLifecycle()
  val isBusy by accountViewModel.isBusy.collectAsStateWithLifecycle()
  val reauthRequiredForDelete by accountViewModel.reauthRequiredForDelete.collectAsStateWithLifecycle()
  val localRecentFiles by recentFilesViewModel.recentFiles.collectAsStateWithLifecycle()

  var showAccountDialog by remember { mutableStateOf(false) }

  val resolvedProfile = (profileState as? CloudUiState.Success)?.data
  val resolvedDisplayName = resolvedProfile?.displayName?.takeIf { it.isNotBlank() }
    ?: fallbackDisplayName
  val resolvedVerified = resolvedProfile?.emailVerified ?: isEmailVerified

  PdfToolsDashboard(
    isDarkTheme = isDarkTheme,
    onToggleTheme = onToggleTheme,
    recentFilesViewModel = recentFilesViewModel,
    accountDisplayName = resolvedDisplayName,
    onOpenAccount = {
      accountViewModel.clearStatusMessage()
      showAccountDialog = true
    },
    onMirrorRecentDoc = { fileName, mimeType, sizeBytes, pageCount, toolId, toolName, summaryNote ->
      accountViewModel.mirrorSingleDocument(
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        lastToolId = toolId,
        lastToolName = toolName,
        summaryNote = summaryNote
      )
    }
  )

  if (showAccountDialog) {
    AccountProfileDialog(
      userDisplayName = resolvedDisplayName,
      userEmail = userEmail,
      isEmailVerified = resolvedVerified,
      profileState = profileState,
      cloudDocsState = cloudDocsState,
      localRecentFiles = localRecentFiles,
      isBusy = isBusy,
      statusMessage = statusMessage,
      onDismiss = { showAccountDialog = false },
      onSaveProfile = { first, last, role ->
        accountViewModel.updateProfile(first, last, role)
      },
      onSendVerificationEmail = {
        accountViewModel.sendVerificationEmail()
      },
      onRefreshVerification = {
        accountViewModel.refreshVerificationStatus()
      },
      onSyncLocalFiles = {
        accountViewModel.syncLocalRecentFiles(localRecentFiles)
      },
      onDeleteCloudDoc = { docId ->
        accountViewModel.deleteCloudRecentDoc(docId)
      },
      onSwitchAccount = {
        showAccountDialog = false
        signOutWithCredentialClear(credentialManager.value, scope)
      },
      onSignOut = {
        showAccountDialog = false
        signOutWithCredentialClear(credentialManager.value, scope)
      },
      onDeleteCloudData = {
        accountViewModel.deleteCloudAccountData {
          showAccountDialog = false
          signOutWithCredentialClear(credentialManager.value, scope)
        }
      },
      reauthRequiredForDelete = reauthRequiredForDelete,
      onDismissReauthPrompt = {
        accountViewModel.dismissReauthPrompt()
      },
      onReauthWithGoogleAndDelete = {
        accountViewModel.completeAccountDeletionAfterReauth(
          reauthAction = { reauthenticateWithGoogle(context, credentialManager.value) },
          onDeleted = {
            showAccountDialog = false
            signOutWithCredentialClear(credentialManager.value, scope)
          }
        )
      },
      onReauthWithPasswordAndDelete = { password ->
        accountViewModel.completeAccountDeletionAfterReauth(
          reauthAction = { reauthenticateWithEmailPassword(password) },
          onDeleted = {
            showAccountDialog = false
            signOutWithCredentialClear(credentialManager.value, scope)
          }
        )
      }
    )
  }
}

/**
 * Local-only Guest Mode host providing full access to all 24 on-device PDF tools,
 * ML Kit OCR text extraction, and local Room Recent Files without calling Cloud Firestore.
 */
@Composable
private fun GuestWorkspaceHost(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onExitGuestToSignIn: () -> Unit
) {
  val context = LocalContext.current
  val recentFilesViewModel: RecentFilesViewModel = viewModel(
    factory = RecentFilesViewModel.Factory(context)
  )
  var showGuestDialog by remember { mutableStateOf(false) }

  PdfToolsDashboard(
    isDarkTheme = isDarkTheme,
    onToggleTheme = onToggleTheme,
    recentFilesViewModel = recentFilesViewModel,
    accountDisplayName = "Guest Mode",
    onOpenAccount = { showGuestDialog = true }
  )

  if (showGuestDialog) {
    AlertDialog(
      onDismissRequest = { showGuestDialog = false },
      containerColor = Color.White,
      titleContentColor = Color(0xFF0F172A),
      textContentColor = Color(0xFF334155),
      modifier = Modifier.testTag("guest_account_dialog"),
      title = {
        Text(
          text = "Guest Workspace",
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0F172A)
        )
      },
      text = {
        Text(
          text = "You are using CubedPDF as a Guest. All 24 on-device PDF tools, ML Kit OCR text extraction, and local Recent Files are unlocked. Sign in anytime to enable Cloud Firestore sync across devices.",
          color = Color(0xFF334155)
        )
      },
      confirmButton = {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Button(
            onClick = {
              showGuestDialog = false
              onExitGuestToSignIn()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = Color(0xFF4F46E5)
            ),
            border = BorderStroke(1.5.dp, Color(0xFF4F46E5)),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("guest_sign_in_button")
          ) {
            Text("Sign In / Create Account", fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = { showGuestDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = Color(0xFF0F172A)
            ),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("guest_close_dialog_button")
          ) {
            Text("Continue as Guest", fontWeight = FontWeight.Bold)
          }
        }
      }
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PdfToolsAppPreview() {
  MyApplicationTheme(darkTheme = true) {
    AuthScreen(
      isDarkTheme = true,
      onToggleTheme = {},
      onAuthSuccess = { _, _, _, _, _ -> },
      enableAutoSignIn = false
    )
  }
}
