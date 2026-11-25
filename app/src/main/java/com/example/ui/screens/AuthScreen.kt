package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.remote.EmailRegistrationResult
import com.example.data.remote.EmailSignInResult
import com.example.data.remote.WorkspaceRole
import com.example.data.remote.attemptAutoSignIn
import com.example.data.remote.isValidEmailAddress
import com.example.data.remote.onGoogleSignInClicked
import com.example.data.remote.openExternalUrl
import com.example.data.remote.registerWithEmailAndPassword
import com.example.data.remote.sendAccountRecoveryEmail
import com.example.data.remote.signInWithEmailCredentials
import com.example.data.remote.validateRegistrationInputs
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.WebAccountDeletionDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthMode {
  SIGN_IN,
  SIGN_UP,
  RECOVERY
}

private const val GOOGLE_RECOVERY_URL = "https://accounts.google.com/signin/recovery"
private const val FIREBASE_PROVIDERS_CONSOLE_URL =
  "https://console.firebase.google.com/project/gen-lang-client-0042941445/authentication/providers"

// Neon palette tokens matching the CubedPDF Neon Login & Sign-Up design
private val NeonViolet = Color(0xFFC048FF)
private val NeonIndigo = Color(0xFF7B61FF)
private val NeonCyan = Color(0xFF38BDF8)
private val NeonPinkLink = Color(0xFFF43F85)
private val NeonPurpleLink = Color(0xFFA855F7)
private val NeonPdfAccentSignIn = Color(0xFFA78BFA)
private val NeonPdfAccentSignUp = Color(0xFF7C9BFF)

private val PopupTextDark = Color(0xFF0F172A)
private val PopupBorderLight = Color(0xFFCBD5E1)
private val PopupPrimaryAccent = Color(0xFF4F46E5)

@Composable
fun AuthScreen(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onAuthSuccess: (
    firstName: String?,
    lastName: String?,
    displayName: String?,
    preferredRole: WorkspaceRole?,
    provider: String?
  ) -> Unit,
  onPrepareEmailSignUp: (
    firstName: String,
    lastName: String,
    displayName: String
  ) -> Unit = { _, _, _ -> },
  emailRegistrar: (suspend (
    firstName: String,
    lastName: String,
    email: String,
    password: String,
    confirmPassword: String
  ) -> EmailRegistrationResult)? = null,
  emailSignInHandler: (suspend (
    email: String,
    password: String
  ) -> EmailSignInResult)? = null,
  onContinueAsGuest: () -> Unit = {},
  enableAutoSignIn: Boolean = true,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManagerLazy = remember(context) { lazy { CredentialManager.create(context) } }

  val scrollState = rememberScrollState()
  var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var infoMessage by remember { mutableStateOf<String?>(null) }
  var duplicateEmail by remember { mutableStateOf<String?>(null) }
  var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
  var showWebDeletionDialog by remember { mutableStateOf(false) }

  // Sign In fields
  var signInEmail by rememberSaveable { mutableStateOf("") }
  var signInPassword by rememberSaveable { mutableStateOf("") }
  var signInPasswordVisible by remember { mutableStateOf(false) }

  // Sign Up fields
  var firstName by rememberSaveable { mutableStateOf("") }
  var lastName by rememberSaveable { mutableStateOf("") }
  var signUpEmail by rememberSaveable { mutableStateOf("") }
  var signUpPassword by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var signUpPasswordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }

  // Password Recovery fields
  var recoveryEmail by rememberSaveable { mutableStateOf("") }
  var isSendingRecovery by remember { mutableStateOf(false) }

  // Dialog state (all popup buttons rendered with pure white backgrounds)
  var showProviderDialog by remember { mutableStateOf(false) }
  var providerDialogForSignUp by remember { mutableStateOf(false) }

  // Keep error/info feedback visible immediately even when scrolled down to Submit
  LaunchedEffect(errorMessage, infoMessage, duplicateEmail) {
    if (errorMessage != null || infoMessage != null || duplicateEmail != null) {
      scrollState.animateScrollTo(0)
    }
  }

  BackHandler(enabled = mode != AuthMode.SIGN_IN) {
    errorMessage = null
    infoMessage = null
    duplicateEmail = null
    mode = AuthMode.SIGN_IN
  }

  // Wait for the initial frame to commit and SurfaceSyncGroup to finish before running
  // silent Credential Manager auto-sign-in, preventing startup window sync timeouts.
  // Never trigger Google auto-sign-in if the user has switched to Create Account or Recovery.
  LaunchedEffect(enableAutoSignIn) {
    if (enableAutoSignIn) {
      withFrameNanos { }
      delay(350L)
      if (mode == AuthMode.SIGN_IN) {
        attemptAutoSignIn(
          context = context,
          credentialManager = credentialManagerLazy.value,
          onAuthSuccess = { onAuthSuccess(null, null, null, null, "google.com") },
          onUnauthenticated = {},
          scope = scope
        )
      }
    }
  }

  fun switchMode(target: AuthMode) {
    errorMessage = null
    infoMessage = null
    duplicateEmail = null
    mode = target
  }

  fun submitEmailSignIn() {
    val cleanEmail = signInEmail.trim()
    when {
      !isValidEmailAddress(cleanEmail) -> {
        errorMessage = "Please enter a valid email address."
        return
      }
      signInPassword.length < 6 -> {
        errorMessage = "Please enter your password (at least 6 characters)."
        return
      }
    }

    errorMessage = null
    infoMessage = null
    isLoading = true

    scope.launch {
      val result = if (emailSignInHandler != null) {
        emailSignInHandler(cleanEmail, signInPassword)
      } else {
        signInWithEmailCredentials(cleanEmail, signInPassword)
      }
      when (result) {
        is EmailSignInResult.Success -> {
          isLoading = false
          onAuthSuccess(
            null,
            null,
            result.displayName,
            null,
            "password"
          )
        }
        EmailSignInResult.ProviderNotEnabledInConsole -> {
          isLoading = false
          providerDialogForSignUp = false
          showProviderDialog = true
        }
        is EmailSignInResult.Error -> {
          isLoading = false
          errorMessage = result.message
        }
      }
    }
  }

  fun submitEmailSignUp() {
    val validationError = validateRegistrationInputs(
      firstName = firstName,
      lastName = lastName,
      email = signUpEmail,
      password = signUpPassword,
      confirmPassword = confirmPassword
    )
    if (validationError != null) {
      duplicateEmail = null
      errorMessage = validationError
      return
    }

    val cleanFirst = firstName.trim().take(40)
    val cleanLast = lastName.trim().take(40)
    val cleanDisplayName = "$cleanFirst $cleanLast".trim().take(80)

    errorMessage = null
    infoMessage = null
    duplicateEmail = null
    isLoading = true
    // Pre-stage profile fields so the instant Firebase AuthStateListener routes to Dashboard,
    // the workspace immediately has the user's First Name, Last Name, and "password" provider.
    onPrepareEmailSignUp(cleanFirst, cleanLast, cleanDisplayName)

    scope.launch {
      val result = if (emailRegistrar != null) {
        emailRegistrar(
          cleanFirst,
          cleanLast,
          signUpEmail,
          signUpPassword,
          confirmPassword
        )
      } else {
        registerWithEmailAndPassword(
          context = context,
          firstName = cleanFirst,
          lastName = cleanLast,
          email = signUpEmail,
          password = signUpPassword,
          confirmPassword = confirmPassword
        )
      }
      when (result) {
        is EmailRegistrationResult.Success -> {
          isLoading = false
          duplicateEmail = null
          onAuthSuccess(
            result.firstName,
            result.lastName,
            result.displayName,
            WorkspaceRole.PERSONAL,
            "password"
          )
        }
        is EmailRegistrationResult.EmailAlreadyInUse -> {
          isLoading = false
          signInEmail = result.email
          recoveryEmail = result.email
          duplicateEmail = result.email
          errorMessage =
            "An account with ${result.email} already exists. Sign in to your existing account, reset your password, or use another email."
        }
        EmailRegistrationResult.ProviderNotEnabledInConsole -> {
          isLoading = false
          providerDialogForSignUp = true
          showProviderDialog = true
        }
        is EmailRegistrationResult.Error -> {
          isLoading = false
          errorMessage = result.message
        }
      }
    }
  }

  fun launchGoogleAuth(
    clearExistingFirst: Boolean = false,
    preferredFirst: String? = null,
    preferredLast: String? = null
  ) {
    errorMessage = null
    infoMessage = null
    isLoading = true
    val cleanFirst = preferredFirst?.trim()?.ifBlank { null }
    val cleanLast = preferredLast?.trim()?.ifBlank { null }
    val combinedName = listOfNotNull(cleanFirst, cleanLast).joinToString(" ").ifBlank { null }
    onGoogleSignInClicked(
      context = context,
      credentialManager = credentialManagerLazy.value,
      onAuthSuccess = {
        isLoading = false
        onAuthSuccess(cleanFirst, cleanLast, combinedName, WorkspaceRole.PERSONAL, "google.com")
      },
      onAuthError = { msg ->
        isLoading = false
        errorMessage = msg
      },
      scope = scope,
      onAuthCancelled = {
        isLoading = false
        infoMessage = "Sign-in was dismissed. Tap Continue with Google or create an email account."
      },
      clearExistingFirst = clearExistingFirst
    )
  }

  NeonAuthCanvasBackground(isDarkTheme = isDarkTheme, modifier = modifier) {
    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .imePadding(),
        contentAlignment = Alignment.TopCenter
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Top Action Row (Back button on secondary modes + Theme toggle on right)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (mode != AuthMode.SIGN_IN) {
              NeonCircleIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back to Sign In",
                isDarkTheme = isDarkTheme,
                onClick = { switchMode(AuthMode.SIGN_IN) },
                testTag = "auth_back_button"
              )
            } else {
              Spacer(modifier = Modifier.size(48.dp))
            }

            NeonCircleIconButton(
              icon = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
              contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
              isDarkTheme = isDarkTheme,
              onClick = onToggleTheme,
              testTag = "auth_theme_toggle_button"
            )
          }

          Spacer(Modifier.height(6.dp))

          // Hero Brand Header (Squircle Logo + Dual-Tone CubedPDF + Tagline)
          NeonBrandHeader(
            mode = mode,
            isDarkTheme = isDarkTheme
          )

          Spacer(Modifier.height(20.dp))

          // Inline Error / Info Feedback Banners
          errorMessage?.let { err ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              AuthFeedbackBanner(
                message = err,
                isError = true,
                isDarkTheme = isDarkTheme,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("auth_error_banner")
              )

              if (duplicateEmail != null && mode == AuthMode.SIGN_UP) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { switchMode(AuthMode.SIGN_IN) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color.White,
                      contentColor = PopupPrimaryAccent
                    ),
                    border = BorderStroke(1.2.dp, PopupPrimaryAccent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 44.dp)
                      .testTag("duplicate_email_sign_in_button")
                  ) {
                    Text("Sign In", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                  }

                  Button(
                    onClick = { switchMode(AuthMode.RECOVERY) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color.White,
                      contentColor = PopupTextDark
                    ),
                    border = BorderStroke(1.dp, PopupBorderLight),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 44.dp)
                      .testTag("duplicate_email_reset_button")
                  ) {
                    Text("Reset", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                  }

                  Button(
                    onClick = {
                      signUpEmail = ""
                      duplicateEmail = null
                      errorMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color.White,
                      contentColor = PopupTextDark
                    ),
                    border = BorderStroke(1.dp, PopupBorderLight),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                      .weight(1.25f)
                      .heightIn(min = 44.dp)
                      .testTag("duplicate_email_change_button")
                  ) {
                    Text("New Email", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                  }
                }
              }
            }
          }

          infoMessage?.let { info ->
            AuthFeedbackBanner(
              message = info,
              isError = false,
              isDarkTheme = isDarkTheme,
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("auth_info_banner")
            )
          }

          // Animated Auth Content (Sign In / Create Your Account / Recovery)
          AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "neonAuthTransition",
            modifier = Modifier.fillMaxWidth()
          ) { currentMode ->
            when (currentMode) {
              AuthMode.SIGN_IN -> {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  NeonGlassCard(isDarkTheme = isDarkTheme) {
                    SignInCardContent(
                      email = signInEmail,
                      password = signInPassword,
                      passwordVisible = signInPasswordVisible,
                      isLoading = isLoading,
                      isDarkTheme = isDarkTheme,
                      onEmailChange = { signInEmail = it },
                      onPasswordChange = { signInPassword = it },
                      onTogglePasswordVisible = { signInPasswordVisible = !signInPasswordVisible },
                      onForgotPassword = {
                        recoveryEmail = signInEmail.trim()
                        switchMode(AuthMode.RECOVERY)
                      },
                      onSubmitSignIn = { submitEmailSignIn() },
                      onContinueWithGoogle = { launchGoogleAuth(clearExistingFirst = false) },
                      onContinueAsGuest = onContinueAsGuest
                    )
                  }

                  Spacer(Modifier.height(18.dp))

                  // Bottom Footer beneath card (matches Sign In mockup)
                  AuthSwitchFooter(
                    prompt = "Don't have an account?",
                    actionLabel = "Create account",
                    isDarkTheme = isDarkTheme,
                    onAction = { switchMode(AuthMode.SIGN_UP) },
                    testTag = "go_to_create_account_button"
                  )
                }
              }

              AuthMode.SIGN_UP -> {
                NeonGlassCard(isDarkTheme = isDarkTheme) {
                  SignUpCardContent(
                    firstName = firstName,
                    lastName = lastName,
                    email = signUpEmail,
                    password = signUpPassword,
                    confirmPassword = confirmPassword,
                    passwordVisible = signUpPasswordVisible,
                    confirmPasswordVisible = confirmPasswordVisible,
                    isLoading = isLoading,
                    isDarkTheme = isDarkTheme,
                    onFirstNameChange = { if (it.length <= 40) firstName = it },
                    onLastNameChange = { if (it.length <= 40) lastName = it },
                    onEmailChange = {
                      signUpEmail = it
                      if (duplicateEmail != null) {
                        duplicateEmail = null
                        errorMessage = null
                      }
                    },
                    onPasswordChange = { signUpPassword = it },
                    onConfirmPasswordChange = { confirmPassword = it },
                    onTogglePasswordVisible = { signUpPasswordVisible = !signUpPasswordVisible },
                    onToggleConfirmPasswordVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                    onSubmitSignUp = { submitEmailSignUp() },
                    onSwitchToSignIn = { switchMode(AuthMode.SIGN_IN) }
                  )
                }
              }

              AuthMode.RECOVERY -> {
                NeonGlassCard(isDarkTheme = isDarkTheme) {
                  RecoveryCardContent(
                    recoveryEmail = recoveryEmail,
                    isSending = isSendingRecovery,
                    isDarkTheme = isDarkTheme,
                    onEmailChange = { recoveryEmail = it },
                    onOpenGoogleRecovery = {
                      openExternalUrl(context, GOOGLE_RECOVERY_URL).onFailure {
                        errorMessage = "Unable to open browser for Google Account Recovery."
                      }
                    },
                    onSendResetEmail = {
                      errorMessage = null
                      infoMessage = null
                      isSendingRecovery = true
                      scope.launch {
                        sendAccountRecoveryEmail(recoveryEmail)
                          .onSuccess {
                            infoMessage =
                              "If an email-linked account exists for ${recoveryEmail.trim()}, a password reset link has been sent."
                          }
                          .onFailure { err ->
                            errorMessage = err.message ?: "Unable to send password reset email."
                          }
                        isSendingRecovery = false
                      }
                    },
                    onBackToSignIn = { switchMode(AuthMode.SIGN_IN) }
                  )
                }
              }
            }
          }

          Spacer(Modifier.height(16.dp))

          // Powered By AI footer sentence
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .testTag("auth_powered_by_ai_label")
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = if (isDarkTheme) NeonCyan else PopupPrimaryAccent,
              modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Powered By AI",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 0.4.sp,
              color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
            )
          }

          // Google Play Policy: accessible Privacy Policy & Account Deletion links on Auth screen
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
          ) {
            TextButton(
              onClick = { showPrivacyPolicyDialog = true },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("auth_privacy_policy_link")
            ) {
              Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkTheme) NeonCyan else PopupPrimaryAccent
              )
            }
            Text(
              text = "•",
              style = MaterialTheme.typography.labelSmall,
              color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
            TextButton(
              onClick = { showWebDeletionDialog = true },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("auth_account_deletion_link")
            ) {
              Text(
                text = "Account Deletion",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkTheme) NeonCyan else PopupPrimaryAccent
              )
            }
          }

          Spacer(Modifier.height(14.dp))
          Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
      }
    }
  }

  if (showPrivacyPolicyDialog) {
    PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
  }

  if (showWebDeletionDialog) {
    WebAccountDeletionDialog(onDismiss = { showWebDeletionDialog = false })
  }

  // Popup Dialog (all buttons styled with pure white backgrounds and clear dark lettering)
  if (showProviderDialog) {
    AlertDialog(
      onDismissRequest = { showProviderDialog = false },
      title = {
        Text(
          text = if (providerDialogForSignUp) "Enable Email/Password in Firebase" else "Sign In to CubedPDF",
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          if (providerDialogForSignUp) {
            "CubedPDF is ready to create your account instantly with Email & Password (no Google Sign-In required), " +
              "but the Email/Password sign-in method is currently disabled in Firebase project gen-lang-client-0042941445.\n\n" +
              "1. Tap Firebase Console below\n" +
              "2. Enable Email/Password and tap Save\n" +
              "3. Return here and tap Retry Create Account"
          } else {
            "To enable Email/Password authentication in Firebase, turn on the Email/Password provider in Firebase Console -> Authentication -> Sign-in method. " +
              "You can also tap Continue with Google right now to open your PDF workspace."
          }
        )
      },
      confirmButton = {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          if (providerDialogForSignUp) {
            // Never redirect Email/Password Sign-Up to Google Sign-In: let the user retry instant registration directly.
            Button(
              onClick = {
                showProviderDialog = false
                submitEmailSignUp()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PopupPrimaryAccent
              ),
              border = BorderStroke(1.5.dp, PopupPrimaryAccent),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("popup_retry_create_account_button")
            ) {
              Text(
                text = "Retry Create Account",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
            }
          } else {
            Button(
              onClick = {
                showProviderDialog = false
                launchGoogleAuth(clearExistingFirst = false)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PopupPrimaryAccent
              ),
              border = BorderStroke(1.5.dp, PopupPrimaryAccent),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("popup_continue_with_google_button")
            ) {
              GoogleMarkIcon(size = 18.dp)
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Continue with Google Now",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Button(
              onClick = {
                showProviderDialog = false
                openExternalUrl(context, FIREBASE_PROVIDERS_CONSOLE_URL).onFailure {
                  errorMessage = "Could not open browser for Firebase Console."
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PopupTextDark
              ),
              border = BorderStroke(1.dp, PopupBorderLight),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("popup_open_firebase_console_button")
            ) {
              Text("Firebase Console", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Button(
              onClick = { showProviderDialog = false },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = PopupTextDark
              ),
              border = BorderStroke(1.dp, PopupBorderLight),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("popup_close_dialog_button")
            ) {
              Text("Close", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
          }
        }
      }
    )
  }
}

/**
 * Lightweight, allocation-free neon wave canvas background rendered with drawWithCache.
 */
@Composable
private fun NeonAuthCanvasBackground(
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)

        if (isDarkTheme) {
          val baseGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF07091B), Color(0xFF0B0E2B), Color(0xFF070B22))
          )
          val topLeftPurplePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.62f, 0f)
            cubicTo(w * 0.36f, h * 0.14f, w * 0.18f, h * 0.22f, 0f, h * 0.36f)
            close()
          }
          val topLeftPurpleBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF7E22CE).copy(alpha = 0.58f), Color(0xFF3B0764).copy(alpha = 0.18f)),
            start = Offset(0f, 0f),
            end = Offset(w * 0.5f, h * 0.35f)
          )
          val rightBlueWavePath = Path().apply {
            moveTo(w, h * 0.04f)
            cubicTo(w * 0.62f, h * 0.18f, w * 0.52f, h * 0.34f, w, h * 0.56f)
            close()
          }
          val rightBlueWaveBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF2563EB).copy(alpha = 0.68f), Color(0xFF1D4ED8).copy(alpha = 0.24f), Color.Transparent),
            center = Offset(w * 0.94f, h * 0.28f),
            radius = w * 0.85f
          )
          val bottomPurpleGlow = Brush.radialGradient(
            colors = listOf(Color(0xFF6D28D9).copy(alpha = 0.30f), Color.Transparent),
            center = Offset(w * 0.12f, h * 0.88f),
            radius = w * 0.72f
          )
          val bottomCyanGlow = Brush.radialGradient(
            colors = listOf(Color(0xFF0284C7).copy(alpha = 0.25f), Color.Transparent),
            center = Offset(w * 0.92f, h * 0.92f),
            radius = w * 0.65f
          )

          onDrawBehind {
            drawRect(brush = baseGradient)
            drawPath(path = topLeftPurplePath, brush = topLeftPurpleBrush)
            drawPath(path = rightBlueWavePath, brush = rightBlueWaveBrush)
            drawCircle(brush = bottomPurpleGlow, radius = w * 0.72f, center = Offset(w * 0.12f, h * 0.88f))
            drawCircle(brush = bottomCyanGlow, radius = w * 0.65f, center = Offset(w * 0.92f, h * 0.92f))
          }
        } else {
          val lightBase = Brush.verticalGradient(
            colors = listOf(Color(0xFFF3F0FF), Color(0xFFEEF4FF), Color(0xFFF8FAFC))
          )
          val lightPurpleOrb = Brush.radialGradient(
            colors = listOf(Color(0xFFC084FC).copy(alpha = 0.32f), Color.Transparent),
            center = Offset(w * 0.10f, h * 0.12f),
            radius = w * 0.75f
          )
          val lightBlueOrb = Brush.radialGradient(
            colors = listOf(Color(0xFF60A5FA).copy(alpha = 0.30f), Color.Transparent),
            center = Offset(w * 0.92f, h * 0.26f),
            radius = w * 0.78f
          )
          onDrawBehind {
            drawRect(brush = lightBase)
            drawCircle(brush = lightPurpleOrb, radius = w * 0.75f, center = Offset(w * 0.10f, h * 0.12f))
            drawCircle(brush = lightBlueOrb, radius = w * 0.78f, center = Offset(w * 0.92f, h * 0.26f))
          }
        }
      }
  ) {
    content()
  }
}

@Composable
private fun NeonCircleIconButton(
  icon: ImageVector,
  contentDescription: String,
  isDarkTheme: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val bg = if (isDarkTheme) Color(0xFF2B2250).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.86f)
  val border = if (isDarkTheme) Color(0xFFA78BFA).copy(alpha = 0.38f) else Color(0xFFCBD5E1)
  val tint = if (isDarkTheme) Color.White else Color(0xFF1E293B)

  Box(
    modifier = Modifier
      .size(48.dp)
      .clip(CircleShape)
      .background(bg)
      .border(1.dp, border, CircleShape)
      .clickable(onClick = onClick)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
  }
}

@Composable
private fun NeonBrandHeader(
  mode: AuthMode,
  isDarkTheme: Boolean
) {
  val frameShape = RoundedCornerShape(22.dp)
  val borderBrush = remember(isDarkTheme) {
    Brush.linearGradient(
      colors = if (isDarkTheme) {
        listOf(Color(0xFFD8B4FE).copy(alpha = 0.85f), Color(0xFF60A5FA).copy(alpha = 0.65f))
      } else {
        listOf(Color(0xFF7C3AED).copy(alpha = 0.55f), Color(0xFF2563EB).copy(alpha = 0.55f))
      }
    )
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .clip(frameShape)
        .background(if (isDarkTheme) Color(0xFF0E122B) else Color.White)
        .border(1.6.dp, borderBrush, frameShape)
        .padding(8.dp),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_cubed_pdf_logo),
        contentDescription = "CubedPDF Logo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(15.dp))
          .testTag("auth_cubed_pdf_logo")
      )
    }

    Text(
      text = buildAnnotatedString {
        withStyle(
          SpanStyle(
            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
            fontWeight = FontWeight.ExtraBold
          )
        ) {
          append("Cubed")
        }
        withStyle(
          SpanStyle(
            color = if (mode == AuthMode.SIGN_UP) NeonPdfAccentSignUp else NeonPdfAccentSignIn,
            fontWeight = FontWeight.ExtraBold
          )
        ) {
          append("PDF")
        }
      },
      fontSize = 31.sp,
      letterSpacing = (-0.5).sp,
      textAlign = TextAlign.Center
    )

    Text(
      text = when (mode) {
        AuthMode.SIGN_IN -> "Sign in to access your PDF workspace."
        AuthMode.SIGN_UP -> "Join CubedPDF and unlock your PDF workspace."
        AuthMode.RECOVERY -> "Reset your password or recover your account."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun NeonGlassCard(
  isDarkTheme: Boolean,
  content: @Composable () -> Unit
) {
  val cardShape = RoundedCornerShape(26.dp)
  val bgBrush = remember(isDarkTheme) {
    if (isDarkTheme) {
      Brush.verticalGradient(
        colors = listOf(Color(0xFF171C42).copy(alpha = 0.80f), Color(0xFF0F1330).copy(alpha = 0.84f))
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.92f), Color(0xFFF8FAFC).copy(alpha = 0.90f))
      )
    }
  }
  val borderBrush = remember(isDarkTheme) {
    if (isDarkTheme) {
      Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.26f), Color(0xFF6366F1).copy(alpha = 0.24f))
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(Color(0xFFDDD6FE), Color(0xFFBFDBFE))
      )
    }
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(bgBrush)
      .border(1.dp, borderBrush, cardShape)
      .padding(horizontal = 20.dp, vertical = 22.dp)
  ) {
    content()
  }
}

@Composable
private fun SignInCardContent(
  email: String,
  password: String,
  passwordVisible: Boolean,
  isLoading: Boolean,
  isDarkTheme: Boolean,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onTogglePasswordVisible: () -> Unit,
  onForgotPassword: () -> Unit,
  onSubmitSignIn: () -> Unit,
  onContinueWithGoogle: () -> Unit,
  onContinueAsGuest: () -> Unit
) {
  val headingColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
  val dividerColor = if (isDarkTheme) Color.White.copy(alpha = 0.16f) else Color(0xFFCBD5E1)
  val orTextColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF64748B)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = "Welcome Back \uD83D\uDC4B",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = headingColor
    )

    NeonAuthTextField(
      value = email,
      onValueChange = onEmailChange,
      placeholder = "Email Address",
      leadingIcon = Icons.Outlined.Email,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Email,
      testTag = "signin_email_input"
    )

    NeonAuthTextField(
      value = password,
      onValueChange = onPasswordChange,
      placeholder = "Password",
      leadingIcon = Icons.Outlined.Lock,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Password,
      isPassword = true,
      passwordVisible = passwordVisible,
      onTogglePasswordVisible = onTogglePasswordVisible,
      testTag = "signin_password_input"
    )

    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.CenterEnd
    ) {
      TextButton(
        onClick = onForgotPassword,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = Modifier
          .heightIn(min = 42.dp)
          .testTag("forgot_password_link")
      ) {
        Text(
          text = "Forgot password?",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = NeonPinkLink,
          maxLines = 1,
          softWrap = false
        )
      }
    }

    NeonGradientActionButton(
      label = "Sign In",
      isLoading = isLoading,
      onClick = onSubmitSignIn,
      testTag = "sign_in_submit_button"
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
      Text(
        text = "OR",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = orTextColor,
        maxLines = 1,
        softWrap = false
      )
      HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
    }

    GoogleContinueButton(
      label = "Continue with Google",
      isLoading = isLoading,
      onClick = onContinueWithGoogle,
      testTag = "google_sign_in_button"
    )

    // Login as Guest button directly below Continue with Google
    OutlinedButton(
      onClick = onContinueAsGuest,
      enabled = !isLoading,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(
        width = 1.2.dp,
        brush = Brush.horizontalGradient(
          colors = if (isDarkTheme) {
            listOf(NeonViolet.copy(alpha = 0.65f), NeonCyan.copy(alpha = 0.65f))
          } else {
            listOf(Color(0xFF7C3AED).copy(alpha = 0.55f), Color(0xFF0284C7).copy(alpha = 0.55f))
          }
        )
      ),
      colors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (isDarkTheme) Color(0xFF1E224A).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.90f),
        contentColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
      ),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 50.dp)
        .testTag("guest_login_button")
    ) {
      Icon(
        imageVector = Icons.Outlined.Person,
        contentDescription = null,
        tint = if (isDarkTheme) NeonCyan else PopupPrimaryAccent,
        modifier = Modifier.size(19.dp)
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = "Login as Guest",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun SignUpCardContent(
  firstName: String,
  lastName: String,
  email: String,
  password: String,
  confirmPassword: String,
  passwordVisible: Boolean,
  confirmPasswordVisible: Boolean,
  isLoading: Boolean,
  isDarkTheme: Boolean,
  onFirstNameChange: (String) -> Unit,
  onLastNameChange: (String) -> Unit,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onConfirmPasswordChange: (String) -> Unit,
  onTogglePasswordVisible: () -> Unit,
  onToggleConfirmPasswordVisible: () -> Unit,
  onSubmitSignUp: () -> Unit,
  onSwitchToSignIn: () -> Unit
) {
  val headingColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
  val passwordMeetsPolicy = password.length >= 8 &&
    password.any { it.isLetter() } &&
    password.any { it.isDigit() || !it.isLetterOrDigit() }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "Create Your Account \u2728",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = headingColor
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      NeonAuthTextField(
        value = firstName,
        onValueChange = onFirstNameChange,
        placeholder = "First Name",
        leadingIcon = Icons.Outlined.Person,
        isDarkTheme = isDarkTheme,
        modifier = Modifier.weight(1f),
        testTag = "signup_first_name_input"
      )
      NeonAuthTextField(
        value = lastName,
        onValueChange = onLastNameChange,
        placeholder = "Last Name",
        leadingIcon = Icons.Outlined.Person,
        isDarkTheme = isDarkTheme,
        modifier = Modifier.weight(1f),
        testTag = "signup_last_name_input"
      )
    }

    NeonAuthTextField(
      value = email,
      onValueChange = onEmailChange,
      placeholder = "Email Address",
      leadingIcon = Icons.Outlined.Email,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Email,
      testTag = "signup_email_input"
    )

    NeonAuthTextField(
      value = password,
      onValueChange = onPasswordChange,
      placeholder = "Password",
      leadingIcon = Icons.Outlined.Lock,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Password,
      isPassword = true,
      passwordVisible = passwordVisible,
      onTogglePasswordVisible = onTogglePasswordVisible,
      testTag = "signup_password_input"
    )

    Text(
      text = "Use at least 8 characters with a mix of letters, numbers and symbols.",
      style = MaterialTheme.typography.bodySmall,
      color = when {
        password.isEmpty() -> if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
        passwordMeetsPolicy -> Color(0xFF10B981)
        else -> Color(0xFFF59E0B)
      },
      modifier = Modifier.padding(horizontal = 4.dp)
    )

    NeonAuthTextField(
      value = confirmPassword,
      onValueChange = onConfirmPasswordChange,
      placeholder = "Confirm Password",
      leadingIcon = Icons.Outlined.Lock,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Password,
      isPassword = true,
      passwordVisible = confirmPasswordVisible,
      onTogglePasswordVisible = onToggleConfirmPasswordVisible,
      testTag = "signup_confirm_password_input"
    )

    Spacer(Modifier.height(4.dp))

    NeonGradientActionButton(
      label = "Create Account",
      isLoading = isLoading,
      onClick = onSubmitSignUp,
      testTag = "create_account_submit_button"
    )

    Spacer(Modifier.height(2.dp))

    AuthSwitchFooter(
      prompt = "Already have an account?",
      actionLabel = "Sign In",
      isDarkTheme = isDarkTheme,
      onAction = onSwitchToSignIn,
      testTag = "go_to_sign_in_button"
    )
  }
}

@Composable
private fun RecoveryCardContent(
  recoveryEmail: String,
  isSending: Boolean,
  isDarkTheme: Boolean,
  onEmailChange: (String) -> Unit,
  onOpenGoogleRecovery: () -> Unit,
  onSendResetEmail: () -> Unit,
  onBackToSignIn: () -> Unit
) {
  val headingColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
  val subtitleColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = "Account Recovery",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = headingColor
    )

    Text(
      text = "Recover your Google account directly or send a password reset email for an email-linked account.",
      style = MaterialTheme.typography.bodySmall,
      color = subtitleColor
    )

    NeonGradientActionButton(
      label = "Open Google Account Recovery",
      isLoading = false,
      onClick = onOpenGoogleRecovery,
      trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
      leadingIcon = Icons.Rounded.Key,
      testTag = "google_account_recovery_button"
    )

    HorizontalDivider(color = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else Color(0xFFCBD5E1))

    NeonAuthTextField(
      value = recoveryEmail,
      onValueChange = onEmailChange,
      placeholder = "Account Email Address",
      leadingIcon = Icons.Outlined.Email,
      isDarkTheme = isDarkTheme,
      keyboardType = KeyboardType.Email,
      testTag = "recovery_email_input"
    )

    OutlinedButton(
      onClick = onSendResetEmail,
      enabled = !isSending,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(
        1.2.dp,
        if (isDarkTheme) Color(0xFFA78BFA).copy(alpha = 0.55f) else Color(0xFF6366F1)
      ),
      colors = ButtonDefaults.outlinedButtonColors(
        contentColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
      ),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .testTag("send_reset_email_button")
    ) {
      if (isSending) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Sending Reset Link...", fontWeight = FontWeight.SemiBold)
      } else {
        Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send Reset Email", fontWeight = FontWeight.SemiBold)
      }
    }

    AuthSwitchFooter(
      prompt = "Remembered your password?",
      actionLabel = "Sign In",
      isDarkTheme = isDarkTheme,
      onAction = onBackToSignIn,
      testTag = "recovery_back_to_sign_in"
    )
  }
}

@Composable
private fun NeonAuthTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  leadingIcon: ImageVector,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
  isPassword: Boolean = false,
  passwordVisible: Boolean = false,
  onTogglePasswordVisible: (() -> Unit)? = null,
  testTag: String
) {
  val containerColor = if (isDarkTheme) Color(0xFF111532).copy(alpha = 0.72f) else Color(0xFFF8FAFC)
  val unfocusedBorder = if (isDarkTheme) Color(0xFF6366F1).copy(alpha = 0.42f) else Color(0xFFCBD5E1)
  val focusedBorder = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF6366F1)
  val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
  val hintColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
  val iconTint = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF475569)

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = {
      Text(
        text = placeholder,
        color = hintColor,
        maxLines = 1
      )
    },
    leadingIcon = {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp)
      )
    },
    trailingIcon = if (isPassword && onTogglePasswordVisible != null) {
      {
        IconButton(onClick = onTogglePasswordVisible) {
          Icon(
            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = if (passwordVisible) "Hide password" else "Show password",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    } else null,
    visualTransformation = if (isPassword && !passwordVisible) {
      PasswordVisualTransformation()
    } else {
      VisualTransformation.None
    },
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedContainerColor = containerColor,
      unfocusedContainerColor = containerColor,
      focusedBorderColor = focusedBorder,
      unfocusedBorderColor = unfocusedBorder,
      focusedTextColor = textColor,
      unfocusedTextColor = textColor,
      cursorColor = NeonCyan
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag(testTag)
  )
}

@Composable
private fun NeonGradientActionButton(
  label: String,
  isLoading: Boolean,
  onClick: () -> Unit,
  testTag: String,
  leadingIcon: ImageVector? = null,
  trailingIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
) {
  val gradient = remember {
    Brush.horizontalGradient(colors = listOf(NeonViolet, NeonIndigo, NeonCyan))
  }
  val shape = RoundedCornerShape(16.dp)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 54.dp)
      .clip(shape)
      .background(if (isLoading) Brush.horizontalGradient(listOf(NeonViolet.copy(0.6f), NeonCyan.copy(0.6f))) else gradient)
      .clickable(enabled = !isLoading, onClick = onClick)
      .testTag(testTag)
      .padding(horizontal = 18.dp, vertical = 14.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          color = Color.White,
          strokeWidth = 2.4.dp
        )
        Spacer(Modifier.width(10.dp))
      } else if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
      )
      if (!isLoading) {
        Spacer(Modifier.width(8.dp))
        Icon(trailingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
      }
    }
  }
}

@Composable
private fun GoogleContinueButton(
  label: String,
  isLoading: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  Button(
    onClick = onClick,
    enabled = !isLoading,
    shape = RoundedCornerShape(16.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = Color.White,
      contentColor = Color(0xFF111827),
      disabledContainerColor = Color.White.copy(alpha = 0.78f),
      disabledContentColor = Color(0xFF4B5563)
    ),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 54.dp)
      .testTag(testTag)
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        color = Color(0xFF4285F4),
        strokeWidth = 2.4.dp
      )
      Spacer(Modifier.width(12.dp))
    } else {
      GoogleMarkIcon(size = 22.dp)
      Spacer(Modifier.width(12.dp))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF111827),
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun AuthSwitchFooter(
  prompt: String,
  actionLabel: String,
  isDarkTheme: Boolean,
  onAction: () -> Unit,
  testTag: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = prompt,
      style = MaterialTheme.typography.bodyMedium,
      color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
    )
    TextButton(
      onClick = onAction,
      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
      modifier = Modifier
        .heightIn(min = 44.dp)
        .testTag(testTag)
    ) {
      Text(
        text = actionLabel,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = NeonPurpleLink,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}

@Composable
private fun AuthFeedbackBanner(
  message: String,
  isError: Boolean,
  isDarkTheme: Boolean,
  modifier: Modifier = Modifier
) {
  val accent = if (isError) Color(0xFFFB7185) else Color(0xFF34D399)
  val bg = if (isDarkTheme) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.12f)
  val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(bg)
      .border(1.dp, accent.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
      contentDescription = if (isError) "Error" else "Info",
      tint = accent,
      modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(10.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = textColor
    )
  }
}

/**
 * Lightweight multicolor Google "G" emblem rendered with drawWithCache.
 */
@Composable
private fun GoogleMarkIcon(size: Dp) {
  Spacer(
    modifier = Modifier
      .size(size)
      .drawWithCache {
        val strokeWidth = this.size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        val arcStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        onDrawBehind {
          drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = arcStroke
          )
          drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = arcStroke
          )
          drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = arcStroke
          )
          drawArc(
            color = Color(0xFF4285F4),
            startAngle = -12f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = arcStroke
          )
          drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.52f, this.size.height * 0.5f),
            end = Offset(this.size.width - inset * 0.4f, this.size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
          )
        }
      }
  )
}
