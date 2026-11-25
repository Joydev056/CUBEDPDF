package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

private val GENERAL_EMAIL_REGEX = Regex(
  "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
)

fun isValidEmailAddress(email: String): Boolean {
  val clean = email.trim()
  return clean.length in 5..160 && GENERAL_EMAIL_REGEX.matches(clean)
}

fun validateRegistrationInputs(
  firstName: String,
  lastName: String,
  email: String,
  password: String,
  confirmPassword: String
): String? {
  val cleanFirst = firstName.trim()
  val cleanLast = lastName.trim()
  val cleanEmail = email.trim()
  val hasLetter = password.any { it.isLetter() }
  val hasNumberOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }

  return when {
    cleanFirst.isEmpty() -> "Please enter your First Name."
    cleanFirst.length > 40 -> "First Name must be 40 characters or fewer."
    cleanLast.isEmpty() -> "Please enter your Last Name."
    cleanLast.length > 40 -> "Last Name must be 40 characters or fewer."
    !isValidEmailAddress(cleanEmail) -> "Please enter a valid email address from any provider."
    password.length < 8 || !hasLetter || !hasNumberOrSymbol ->
      "Use at least 8 characters with a mix of letters, numbers and symbols."
    password != confirmPassword -> "Passwords do not match. Please confirm your password."
    else -> null
  }
}

sealed interface EmailRegistrationResult {
  data class Success(
    val uid: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val email: String,
    val emailVerified: Boolean
  ) : EmailRegistrationResult

  data class EmailAlreadyInUse(val email: String) : EmailRegistrationResult
  data object ProviderNotEnabledInConsole : EmailRegistrationResult
  data class Error(val message: String) : EmailRegistrationResult
}

sealed interface EmailSignInResult {
  data class Success(
    val uid: String,
    val displayName: String?,
    val email: String,
    val emailVerified: Boolean
  ) : EmailSignInResult

  data object ProviderNotEnabledInConsole : EmailSignInResult
  data class Error(val message: String) : EmailSignInResult
}

/**
 * Observes Firebase Authentication's persistent session state across app launches.
 * Firebase Auth SDK on Android persists user sessions securely on disk until explicit signOut()
 * or server-side token invalidation.
 */
internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

/**
 * Creates a Firebase Authentication account with any valid email address and password,
 * immediately authenticates the user, updates their displayName, triggers email verification,
 * and saves their profile (without any password) in Cloud Firestore.
 */
suspend fun registerWithEmailAndPassword(
  context: Context,
  firstName: String,
  lastName: String,
  email: String,
  password: String,
  confirmPassword: String,
  auth: FirebaseAuth = Firebase.auth
): EmailRegistrationResult {
  val validationError = validateRegistrationInputs(firstName, lastName, email, password, confirmPassword)
  if (validationError != null) {
    return EmailRegistrationResult.Error(validationError)
  }

  val cleanFirst = firstName.trim().take(40)
  val cleanLast = lastName.trim().take(40)
  val cleanDisplayName = "$cleanFirst $cleanLast".trim().take(80)
  val cleanEmail = email.trim().lowercase()

  return try {
    val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
    val user = authResult.user
      ?: return EmailRegistrationResult.Error("Account created, but session initialization failed. Please retry.")

    // AuthStateListener immediately transitions MainActivity from AuthScreen to the Dashboard
    // once createUserWithEmailAndPassword succeeds. Run profile + Firestore writes in
    // NonCancellable so unmounting AuthScreen never cancels displayName or Firestore persistence.
    withContext(NonCancellable) {
      // Update Firebase Auth display name
      runCatching {
        user.updateProfile(
          userProfileChangeRequest {
            displayName = cleanDisplayName
          }
        ).await()
      }.onFailure {
        Log.w(AUTH_TAG, "Unable to set Firebase Auth displayName: ${it.message}")
      }

      // Best-effort verification email without blocking dashboard redirect
      runCatching {
        user.sendEmailVerification().await()
      }.onFailure {
        Log.w(AUTH_TAG, "Verification email dispatch skipped/failed: ${it.message}")
      }

      // Persist UID, firstName, lastName, displayName, email, createdAt, provider, emailVerified in Firestore (NEVER password)
      val repo = UserAccountRepository(context.applicationContext)
      repo.ensureUserProfile(
        preferredFirstName = cleanFirst,
        preferredLastName = cleanLast,
        preferredDisplayName = cleanDisplayName,
        preferredRole = WorkspaceRole.PERSONAL,
        preferredProvider = "password"
      ).onFailure { err ->
        Log.w(AUTH_TAG, "Initial Firestore profile write will retry in dashboard: ${err.message}")
      }
    }

    EmailRegistrationResult.Success(
      uid = user.uid,
      firstName = cleanFirst,
      lastName = cleanLast,
      displayName = cleanDisplayName,
      email = user.email ?: cleanEmail,
      emailVerified = user.isEmailVerified
    )
  } catch (e: CancellationException) {
    throw e
  } catch (_: FirebaseAuthUserCollisionException) {
    Log.i(AUTH_TAG, "Duplicate email blocked during registration: $cleanEmail")
    EmailRegistrationResult.EmailAlreadyInUse(cleanEmail)
  } catch (e: FirebaseAuthWeakPasswordException) {
    EmailRegistrationResult.Error(
      e.reason ?: "Password is too weak. Use at least 8 characters with letters, numbers, and symbols."
    )
  } catch (_: FirebaseAuthInvalidCredentialsException) {
    EmailRegistrationResult.Error("Please enter a valid email address.")
  } catch (e: FirebaseAuthException) {
    val code = e.errorCode
    val msg = e.message.orEmpty()
    val isDuplicateEmail =
      code.equals("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) ||
        msg.contains("already in use", ignoreCase = true) ||
        msg.contains("EMAIL_EXISTS", ignoreCase = true)
    val isProviderDisabled =
      code.equals("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
        msg.contains("PASSWORD_LOGIN_DISABLED", ignoreCase = true)
    when {
      isDuplicateEmail -> {
        Log.i(AUTH_TAG, "Duplicate email reported via FirebaseAuthException ($code): $cleanEmail")
        EmailRegistrationResult.EmailAlreadyInUse(cleanEmail)
      }
      isProviderDisabled -> {
        Log.w(AUTH_TAG, "Firebase email provider disabled ($code): $msg")
        EmailRegistrationResult.ProviderNotEnabledInConsole
      }
      else -> {
        Log.w(AUTH_TAG, "Firebase email registration failed ($code): $msg", e)
        EmailRegistrationResult.Error(e.localizedMessage ?: "Unable to create account. Please try again.")
      }
    }
  } catch (e: Exception) {
    val msg = e.message.orEmpty()
    if (msg.contains("already in use", ignoreCase = true) || msg.contains("EMAIL_EXISTS", ignoreCase = true)) {
      Log.i(AUTH_TAG, "Duplicate email reported: $cleanEmail")
      EmailRegistrationResult.EmailAlreadyInUse(cleanEmail)
    } else {
      Log.e(AUTH_TAG, "Email registration failed", e)
      EmailRegistrationResult.Error(e.localizedMessage ?: "Unable to create account. Please check your connection.")
    }
  }
}

/**
 * Authenticates an existing Email & Password Firebase account using EmailAuthProvider
 * credentials via FirebaseAuth.signInWithCredential().
 */
suspend fun signInWithEmailCredentials(
  email: String,
  password: String,
  auth: FirebaseAuth = Firebase.auth
): EmailSignInResult {
  val cleanEmail = email.trim().lowercase()
  if (!isValidEmailAddress(cleanEmail)) {
    return EmailSignInResult.Error("Please enter a valid email address.")
  }
  if (password.length < 6) {
    return EmailSignInResult.Error("Please enter your password (at least 6 characters).")
  }

  return try {
    val credential = EmailAuthProvider.getCredential(cleanEmail, password)
    val authResult = auth.signInWithCredential(credential).await()
    val user = authResult.user
      ?: return EmailSignInResult.Error("Sign-in succeeded, but session initialization failed. Please retry.")

    EmailSignInResult.Success(
      uid = user.uid,
      displayName = user.displayName,
      email = user.email ?: cleanEmail,
      emailVerified = user.isEmailVerified
    )
  } catch (e: CancellationException) {
    throw e
  } catch (_: FirebaseAuthInvalidUserException) {
    EmailSignInResult.Error(
      "No account found for $cleanEmail. Tap Create account to register."
    )
  } catch (_: FirebaseAuthInvalidCredentialsException) {
    EmailSignInResult.Error(
      "Incorrect email or password. If this email was registered with Google, tap Continue with Google or Forgot password."
    )
  } catch (e: FirebaseAuthException) {
    val code = e.errorCode
    val msg = e.message.orEmpty()
    val isProviderDisabled =
      code.equals("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
        msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
        msg.contains("PASSWORD_LOGIN_DISABLED", ignoreCase = true)
    when {
      isProviderDisabled -> {
        Log.w(AUTH_TAG, "Firebase email sign-in provider disabled ($code): $msg")
        EmailSignInResult.ProviderNotEnabledInConsole
      }
      code.contains("TOO_MANY_REQUESTS", ignoreCase = true) -> {
        EmailSignInResult.Error("Too many sign-in attempts. Please wait a moment or reset your password.")
      }
      else -> {
        Log.w(AUTH_TAG, "Firebase email sign-in failed ($code): $msg", e)
        EmailSignInResult.Error(e.localizedMessage ?: "Unable to sign in. Please try again.")
      }
    }
  } catch (e: Exception) {
    Log.e(AUTH_TAG, "Email sign-in failed", e)
    EmailSignInResult.Error(e.localizedMessage ?: "Unable to sign in. Please check your connection.")
  }
}

/**
 * Silent Auto-Sign-In on startup using GetGoogleIdOption exclusively (one option per request).
 */
fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onUnauthenticated()
    return
  }
  if (clientId.isBlank()) {
    onUnauthenticated()
    return
  }
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  scope.launch {
    try {
      val activityContext = context.findActivity() ?: context
      val credential = credentialManager.getCredential(activityContext, request).credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption exclusively (one option per request).
 */
fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
  clearExistingFirst: Boolean = false
) {
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
    return
  }
  val activity = context.findActivity()
  if (activity == null) {
    onAuthError("Unable to locate active screen for Google Sign-In.")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      if (clearExistingFirst) {
        runCatching {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
      }
      val credential = credentialManager.getCredential(activity, request).credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type received from Credential Manager.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(AUTH_TAG, "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
    }
  }
}

/**
 * Safe Sign-Out inside NonCancellable so Credential Manager state clears cleanly before Firebase signOut.
 */
fun signOutWithCredentialClear(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {}
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}

/**
 * Sends a verification email for the currently signed-in Firebase user.
 */
suspend fun sendVerificationEmailToCurrentUser(auth: FirebaseAuth = Firebase.auth): Result<Unit> {
  return try {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("No active user session."))
    user.sendEmailVerification().await()
    Result.success(Unit)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(AUTH_TAG, "Verification email failed: ${e.message}", e)
    Result.failure(e)
  }
}

/**
 * Triggers Firebase password reset email for email-linked recovery accounts.
 */
suspend fun sendAccountRecoveryEmail(email: String): Result<Unit> {
  val trimmed = email.trim()
  if (!isValidEmailAddress(trimmed)) {
    return Result.failure(IllegalArgumentException("Please enter a valid email address."))
  }
  return try {
    Firebase.auth.sendPasswordResetEmail(trimmed).await()
    Result.success(Unit)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(AUTH_TAG, "Password reset request failed: ${e.message}", e)
    Result.failure(
      IllegalStateException(
        e.localizedMessage
          ?: "Password reset emails require Email/Password provider enabled in Firebase Console."
      )
    )
  }
}

/**
 * Re-authenticates the currently signed-in email/password user before sensitive account deletion.
 */
suspend fun reauthenticateWithEmailPassword(
  password: String,
  auth: FirebaseAuth = Firebase.auth
): Result<Unit> {
  val user = auth.currentUser ?: return Result.failure(IllegalStateException("No active user session."))
  val email = user.email?.trim().orEmpty()
  if (email.isBlank()) {
    return Result.failure(IllegalStateException("Account email is unavailable for password re-verification."))
  }
  if (password.isBlank()) {
    return Result.failure(IllegalArgumentException("Enter your account password to confirm deletion."))
  }
  return try {
    val credential = EmailAuthProvider.getCredential(email, password)
    user.reauthenticate(credential).await()
    Result.success(Unit)
  } catch (e: CancellationException) {
    throw e
  } catch (_: FirebaseAuthInvalidCredentialsException) {
    Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
  } catch (e: Exception) {
    Log.w(AUTH_TAG, "Email re-authentication failed: ${e.message}", e)
    Result.failure(IllegalStateException(e.localizedMessage ?: "Unable to verify your password."))
  }
}

/**
 * Re-authenticates the currently signed-in Google user via Jetpack Credential Manager
 * so FirebaseAuth.currentUser.delete() succeeds after long-lived sessions.
 */
suspend fun reauthenticateWithGoogle(
  context: Context,
  credentialManager: CredentialManager,
  auth: FirebaseAuth = Firebase.auth
): Result<Unit> {
  val user = auth.currentUser ?: return Result.failure(IllegalStateException("No active user session."))
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    return Result.failure(IllegalStateException("Google Sign-In configuration is unavailable."))
  }
  val activity = context.findActivity()
    ?: return Result.failure(IllegalStateException("Unable to locate active screen for Google verification."))

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  return try {
    val credential = credentialManager.getCredential(activity, request).credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
      user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
      Result.success(Unit)
    } else {
      Result.failure(IllegalStateException("Unexpected credential received from Google verification."))
    }
  } catch (e: GetCredentialCancellationException) {
    Result.failure(IllegalStateException("Google verification was cancelled."))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(AUTH_TAG, "Google re-authentication failed: ${e.message}", e)
    Result.failure(IllegalStateException(e.localizedMessage ?: "Google verification failed. Please try again."))
  }
}

/**
 * Safely launches an HTTPS external browser URL (e.g., Privacy Policy, Web Account Deletion, or Security).
 */
fun openExternalUrl(context: Context, url: String): Result<Unit> {
  return runCatching {
    val trimmed = url.trim()
    require(trimmed.startsWith("https://")) { "Only HTTPS links can be opened externally." }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
