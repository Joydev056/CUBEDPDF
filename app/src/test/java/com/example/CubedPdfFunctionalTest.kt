package com.example

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecentFileEntity
import com.example.data.remote.CloudRecentDoc
import com.example.data.remote.UserProfile
import com.example.data.remote.WorkspaceRole
import com.example.data.remote.isValidEmailAddress
import com.example.data.remote.validateRegistrationInputs
import com.example.data.repository.RecentFileRepository
import com.example.model.ToolCategory
import com.example.model.pdfToolsList
import com.example.pdf.PageSizeMode
import com.example.pdf.PdfEngine
import com.example.pdf.SignaturePosition
import com.example.ui.screens.AccountProfileDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PdfToolsDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.RecentFilesViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class CubedPdfFunctionalTest {

  @get:Rule val composeTestRule = createComposeRule()

  // =========================================================================
  // 1. CATALOG, MODELS & AUTH VALIDATION FUNCTIONAL TESTS
  // =========================================================================

  @Test
  fun `all 24 pdf tools have unique ids valid categories and non-empty metadata`() {
    assertEquals(24, pdfToolsList.size)
    val uniqueIds = pdfToolsList.map { it.id }.toSet()
    assertEquals(24, uniqueIds.size)

    pdfToolsList.forEach { tool ->
      assertTrue("Tool id blank", tool.id.isNotBlank())
      assertTrue("Tool name blank for ${tool.id}", tool.name.isNotBlank())
      assertTrue("Tool shortTag blank for ${tool.id}", tool.shortTag.isNotBlank())
      assertTrue("Tool description blank for ${tool.id}", tool.description.isNotBlank())
      assertTrue("Tool category should not be ALL for ${tool.id}", tool.category != ToolCategory.ALL)
    }

    // Verify WorkspaceRole parsing
    assertEquals(WorkspaceRole.PERSONAL, WorkspaceRole.fromLabel(null))
    assertEquals(WorkspaceRole.PERSONAL, WorkspaceRole.fromLabel("unknown-role"))
    WorkspaceRole.entries.forEach { role ->
      assertEquals(role, WorkspaceRole.fromLabel(role.label))
      assertEquals(role, WorkspaceRole.fromLabel(role.label.lowercase()))
    }
  }

  @Test
  fun `email and registration validator handles all providers and edge cases cleanly`() {
    val validEmails = listOf(
      "user@gmail.com",
      "alex@outlook.com",
      "founder@proton.me",
      "designer@icloud.com",
      "team.lead+cubed@enterprise.co.uk",
      "qa_01@sub.domain.org"
    )
    validEmails.forEach { email ->
      assertTrue("Expected valid email: $email", isValidEmailAddress(email))
    }

    val invalidEmails = listOf("", "   ", "plainaddress", "missing@domain", "@no-user.com", "a b@test.com")
    invalidEmails.forEach { email ->
      assertFalse("Expected invalid email: $email", isValidEmailAddress(email))
    }

    assertEquals("Please enter your First Name.", validateRegistrationInputs("", "Lin", "maya@proton.me", "Cubed#2026", "Cubed#2026"))
    assertEquals("Please enter your Last Name.", validateRegistrationInputs("Maya", " ", "maya@proton.me", "Cubed#2026", "Cubed#2026"))
    assertEquals("Please enter a valid email address from any provider.", validateRegistrationInputs("Maya", "Lin", "bad-email", "Cubed#2026", "Cubed#2026"))
    assertNotNull(validateRegistrationInputs("Maya", "Lin", "maya@proton.me", "Ab1!", "Ab1!"))
    assertNotNull(validateRegistrationInputs("Maya", "Lin", "maya@proton.me", "OnlyLettersHere", "OnlyLettersHere"))
    assertNotNull(validateRegistrationInputs("Maya", "Lin", "maya@proton.me", "1234567890", "1234567890"))
    assertEquals("Passwords do not match. Please confirm your password.", validateRegistrationInputs("Maya", "Lin", "maya@proton.me", "Cubed#2026", "Cubed#2027"))
    assertNull(validateRegistrationInputs("Maya", "Lin", "maya@proton.me", "Cubed#2026", "Cubed#2026"))
  }

  // =========================================================================
  // 2. ROOM DATABASE RECENT FILES FUNCTIONAL TESTS
  // =========================================================================

  @Test
  fun `room recent files supports insert upsert ordering delete and clear`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = RecentFileRepository(db.recentFileDao())

      val id1 = repo.recordRecentFile(
        fileName = "Contract.pdf",
        fileUriString = "file:///tmp/contract.pdf",
        mimeType = "application/pdf",
        sizeBytes = 10_240L,
        pageCount = 4,
        lastToolId = "sign",
        lastToolName = "Sign PDF",
        summaryNote = "Signed Page 4"
      )
      Thread.sleep(15)
      val id2 = repo.recordRecentFile(
        fileName = "Invoice.pdf",
        fileUriString = "file:///tmp/invoice.pdf",
        mimeType = "application/pdf",
        sizeBytes = 8_192L,
        pageCount = 2,
        lastToolId = "compress",
        lastToolName = "Compress PDF",
        summaryNote = "Compressed 40%"
      )

      val afterTwo = repo.recentFiles.first()
      assertEquals(2, afterTwo.size)
      assertEquals("Invoice.pdf", afterTwo.first().fileName)

      // Upsert existing URI updates in place without creating duplicates
      Thread.sleep(15)
      repo.recordRecentFile(
        fileName = "Contract_Signed.pdf",
        fileUriString = "file:///tmp/contract.pdf",
        mimeType = "application/pdf",
        sizeBytes = 11_000L,
        pageCount = 4,
        lastToolId = "watermark",
        lastToolName = "Add Watermark",
        summaryNote = "Watermarked"
      )
      val afterUpsert = repo.recentFiles.first()
      assertEquals(2, afterUpsert.size)
      assertEquals("Contract_Signed.pdf", afterUpsert.first().fileName)

      // Delete one item
      repo.deleteById(id2)
      val afterDelete = repo.recentFiles.first()
      assertEquals(1, afterDelete.size)
      assertEquals(id1, afterDelete.first().id)

      // Clear all
      repo.clearAll()
      assertTrue(repo.recentFiles.first().isEmpty())
    } finally {
      db.close()
    }
  }

  // =========================================================================
  // 3. PDF ENGINE VALIDATION, RANGE PARSER, FILE OPS & AES-256 VAULT TESTS
  // =========================================================================

  @Test
  fun `pdf engine split range parser formatting and all input validation guards behave safely`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // File size formatting edge cases
    assertEquals("0 B", PdfEngine.formatFileSize(-50))
    assertEquals("0 B", PdfEngine.formatFileSize(0))
    assertEquals("1.0 KB", PdfEngine.formatFileSize(1024))
    assertEquals("2.00 MB", PdfEngine.formatFileSize(2L * 1024 * 1024))

    // Split range parser edge cases (valid, blank, reversed, clamped, malformed)
    assertEquals(listOf(listOf(0), listOf(1), listOf(2)), PdfEngine.parseRangeGroups("", 3))
    assertEquals(listOf(listOf(0, 1), listOf(2)), PdfEngine.parseRangeGroups("1-2, 3", 3))
    assertEquals(listOf(listOf(0, 1, 2)), PdfEngine.parseRangeGroups("3-1", 3))
    assertEquals(listOf(listOf(0, 1, 2)), PdfEngine.parseRangeGroups("1-99", 3))
    assertTrue(PdfEngine.parseRangeGroups("abc, -, 99, -2", 3).isEmpty())

    // Validation guards across all tools never crash and return clean failure messages
    val dummyUri = Uri.fromFile(File(context.cacheDir, "nonexistent_test.pdf"))
    assertTrue(PdfEngine.createPdf(context, "  ", "", "   ", PageSizeMode.A4, Color.BLUE, emptyList()).isFailure)
    assertTrue(PdfEngine.mergePdfs(context, emptyList()).isFailure)
    assertTrue(PdfEngine.mergePdfs(context, listOf(dummyUri)).isFailure)
    assertTrue(PdfEngine.imagesToPdf(context, emptyList(), PageSizeMode.A4).isFailure)
    assertTrue(PdfEngine.signPdf(context, dummyUri, 0, emptyList(), Color.BLACK, SignaturePosition.BOTTOM_RIGHT).isFailure)
    assertTrue(PdfEngine.protectPdf(context, dummyUri, "123").isFailure)
    assertTrue(PdfEngine.unlockPdf(context, dummyUri, "").isFailure)
    assertTrue(PdfEngine.extractPages(context, dummyUri, emptySet()).isFailure)
    assertTrue(PdfEngine.rearrangePages(context, dummyUri, emptyList()).isFailure)
    assertTrue(PdfEngine.addWatermark(context, dummyUri, "   ", 0.3f, -35f, Color.RED).isFailure)
    assertTrue(PdfEngine.redactPdf(context, dummyUri, 0, emptyList()).isFailure)
    assertTrue(PdfEngine.webpageToPdf(context, "   ").isFailure)
    assertTrue(PdfEngine.inspectPdf(context, dummyUri).isFailure)
  }

  @Test
  fun `pdf engine AES-256 protect and unlock round-trip preserves exact bytes and rejects wrong passwords`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Write a real minimal PDF payload to disk for cryptographic vault round-trip testing
    val originalBytes = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".toByteArray(Charsets.UTF_8)
    val sourceFile = File(context.cacheDir, "vault_source.pdf")
    sourceFile.writeBytes(originalBytes)
    val sourceUri = Uri.fromFile(sourceFile)

    // Protect with short password fails cleanly
    assertTrue(PdfEngine.protectPdf(context, sourceUri, "123").isFailure)

    // Protect with strong password succeeds
    val protectedRes = PdfEngine.protectPdf(context, sourceUri, "CubedVault#2026")
    assertTrue("protectPdf failed: ${protectedRes.exceptionOrNull()}", protectedRes.isSuccess)
    val encryptedFile = protectedRes.getOrThrow().primaryFile.file
    assertTrue(encryptedFile.exists() && encryptedFile.length() > originalBytes.size)
    assertFalse("Encrypted bytes must not equal plaintext", encryptedFile.readBytes().contentEquals(originalBytes))

    val encryptedUri = Uri.fromFile(encryptedFile)

    // Unlock with wrong password fails with clear message
    val wrongPassRes = PdfEngine.unlockPdf(context, encryptedUri, "WrongPass#999")
    assertTrue(wrongPassRes.isFailure)
    assertTrue(
      wrongPassRes.exceptionOrNull()?.message?.contains("Incorrect password", ignoreCase = true) == true
    )

    // Unlock with correct password restores exact original bytes
    val unlockedRes = PdfEngine.unlockPdf(context, encryptedUri, "CubedVault#2026")
    assertTrue("unlockPdf failed: ${unlockedRes.exceptionOrNull()}", unlockedRes.isSuccess)
    val restoredBytes = unlockedRes.getOrThrow().primaryFile.file.readBytes()
    assertArrayEquals(originalBytes, restoredBytes)

    // SaveFileToUri & persistLocalSnapshotForRecents
    val exportedDest = File(context.cacheDir, "exported_copy.pdf")
    val saveRes = PdfEngine.saveFileToUri(context, unlockedRes.getOrThrow().primaryFile.file, Uri.fromFile(exportedDest))
    assertTrue("saveFileToUri failed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)
    assertArrayEquals(originalBytes, exportedDest.readBytes())

    val persistedUri = PdfEngine.persistLocalSnapshotForRecents(context, sourceUri, "vault_source.pdf")
    assertEquals(sourceUri, persistedUri)
  }

  // =========================================================================
  // 4. AUTH SCREEN UI FUNCTIONAL TESTS (DARK & LIGHT + INLINE VALIDATION)
  // =========================================================================

  @Test
  fun `auth screen validates sign in sign up recovery and toggles light and dark themes cleanly`() {
    var darkTheme by mutableStateOf(true)
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = darkTheme) {
        AuthScreen(
          isDarkTheme = darkTheme,
          onToggleTheme = { darkTheme = !darkTheme },
          onAuthSuccess = { _, _, _, _, _ -> },
          enableAutoSignIn = false
        )
      }
    }

    composeTestRule.waitForIdle()
    // Toggle theme Dark -> Light -> Dark
    composeTestRule.onNodeWithTag("auth_theme_toggle_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertFalse(darkTheme)
    composeTestRule.onNodeWithTag("auth_theme_toggle_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(darkTheme)

    // Sign In inline validation: invalid email
    composeTestRule.onNodeWithTag("sign_in_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Please enter a valid email address.").assertIsDisplayed()

    // Sign In inline validation: short password
    composeTestRule.onNodeWithTag("signin_email_input").performScrollTo().performTextInput("user@proton.me")
    composeTestRule.onNodeWithTag("signin_password_input").performScrollTo().performTextInput("123")
    composeTestRule.onNodeWithTag("sign_in_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Please enter your password (at least 6 characters).").performScrollTo().assertIsDisplayed()

    // Switch to Create Account and test every registration validation step
    composeTestRule.onNodeWithTag("go_to_create_account_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Create Your Account \u2728").assertIsDisplayed()

    // Empty submit -> First Name error
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Please enter your First Name.").performScrollTo().assertIsDisplayed()

    // Enter First Name only -> Last Name error
    composeTestRule.onNodeWithTag("signup_first_name_input").performScrollTo().performTextInput("Maya")
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Please enter your Last Name.").performScrollTo().assertIsDisplayed()

    // Enter Last Name + invalid email -> Email error
    composeTestRule.onNodeWithTag("signup_last_name_input").performScrollTo().performTextInput("Patel")
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("invalid-email")
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Please enter a valid email address from any provider.").performScrollTo().assertIsDisplayed()

    // Fix email + mismatched passwords -> Confirm password error
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("maya@outlook.com")
    composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("Different#2026")
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Passwords do not match. Please confirm your password.").performScrollTo().assertIsDisplayed()

    // Go back to Sign In -> Recovery -> test invalid recovery email
    composeTestRule.onNodeWithTag("auth_back_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("forgot_password_link").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("recovery_email_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("send_reset_email_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Please enter a valid email address.").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `create account authenticates instantly without google redirect and retries cleanly when provider toggled`() {
    var attempts = 0
    var preparedFirst = ""
    var preparedLast = ""
    var successFirst: String? = null
    var successLast: String? = null
    var successDisplay: String? = null
    var successProvider: String? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AuthScreen(
          isDarkTheme = true,
          onToggleTheme = {},
          onPrepareEmailSignUp = { f, l, _ ->
            preparedFirst = f
            preparedLast = l
          },
          emailRegistrar = { first, last, email, _, _ ->
            attempts++
            if (attempts == 1) {
              com.example.data.remote.EmailRegistrationResult.ProviderNotEnabledInConsole
            } else {
              com.example.data.remote.EmailRegistrationResult.Success(
                uid = "uid-instant-1",
                firstName = first,
                lastName = last,
                displayName = "$first $last",
                email = email.trim().lowercase(),
                emailVerified = false
              )
            }
          },
          onAuthSuccess = { f, l, d, _, provider ->
            successFirst = f
            successLast = l
            successDisplay = d
            successProvider = provider
          },
          enableAutoSignIn = false
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("go_to_create_account_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("signup_first_name_input").performScrollTo().performTextInput("Aarav")
    composeTestRule.onNodeWithTag("signup_last_name_input").performScrollTo().performTextInput("Sharma")
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("aarav@company.com")
    composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // First attempt returns ProviderNotEnabledInConsole: must NEVER show Google Sign-In redirect button
    assertEquals("Aarav", preparedFirst)
    assertEquals("Sharma", preparedLast)
    composeTestRule.onNodeWithTag("popup_retry_create_account_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("popup_open_firebase_console_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("popup_continue_with_google_button").assertDoesNotExist()

    // Retry Create Account succeeds immediately with password provider and exact Name/Last Name
    composeTestRule.onNodeWithTag("popup_retry_create_account_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, attempts)
    assertEquals("Aarav", successFirst)
    assertEquals("Sharma", successLast)
    assertEquals("Aarav Sharma", successDisplay)
    assertEquals("password", successProvider)
  }

  @Test
  fun `duplicate email during create account shows recovery actions and pre-fills sign in`() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AuthScreen(
          isDarkTheme = true,
          onToggleTheme = {},
          emailRegistrar = { _, _, email, _, _ ->
            com.example.data.remote.EmailRegistrationResult.EmailAlreadyInUse(email.trim().lowercase())
          },
          onAuthSuccess = { _, _, _, _, _ -> },
          enableAutoSignIn = false
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("go_to_create_account_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("signup_first_name_input").performScrollTo().performTextInput("Aarav")
    composeTestRule.onNodeWithTag("signup_last_name_input").performScrollTo().performTextInput("Sharma")
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("existing@company.com")
    composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("duplicate_email_sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("duplicate_email_reset_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("duplicate_email_change_button").assertIsDisplayed()

    // Tapping Sign In switches to Welcome Back with the email pre-populated
    composeTestRule.onNodeWithTag("duplicate_email_sign_in_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Welcome Back \uD83D\uDC4B").assertIsDisplayed()
    composeTestRule.onNodeWithText("existing@company.com").assertIsDisplayed()
  }

  @Test
  fun `email sign in authenticates directly without showing firebase console popup`() {
    var signedInDisplay: String? = null
    var signedInProvider: String? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AuthScreen(
          isDarkTheme = true,
          onToggleTheme = {},
          emailSignInHandler = { email, _ ->
            com.example.data.remote.EmailSignInResult.Success(
              uid = "uid-email-signin-1",
              displayName = "Aarav Sharma",
              email = email,
              emailVerified = true
            )
          },
          onAuthSuccess = { _, _, display, _, provider ->
            signedInDisplay = display
            signedInProvider = provider
          },
          enableAutoSignIn = false
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("signin_email_input").performScrollTo().performTextInput("aarav@company.com")
    composeTestRule.onNodeWithTag("signin_password_input").performScrollTo().performTextInput("Cubed#2026")
    composeTestRule.onNodeWithTag("sign_in_submit_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Must authenticate directly without popping up the Firebase Console dialog
    composeTestRule.onNodeWithTag("popup_continue_with_google_button").assertDoesNotExist()
    assertEquals("Aarav Sharma", signedInDisplay)
    assertEquals("password", signedInProvider)
  }

  @Test
  fun `auth screen shows login as guest below google button and powered by ai footer`() {
    var guestClicked = false
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AuthScreen(
          isDarkTheme = true,
          onToggleTheme = {},
          onAuthSuccess = { _, _, _, _, _ -> },
          onContinueAsGuest = { guestClicked = true },
          enableAutoSignIn = false
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("guest_login_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Login as Guest").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_powered_by_ai_label").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Powered By AI").assertIsDisplayed()

    composeTestRule.onNodeWithTag("guest_login_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(guestClicked)
  }

  // =========================================================================
  // 5. ACCOUNT PROFILE DIALOG FUNCTIONAL TESTS
  // =========================================================================

  @Test
  fun `account profile dialog handles email verification profile edits sync and delete confirmation`() {
    var savedFirst = ""
    var savedLast = ""
    var savedRole: WorkspaceRole? = null
    var verifyEmailCalled = false
    var refreshStatusCalled = false
    var syncCalled = false
    var switchCalled = false
    var signOutCalled = false
    var deleteCloudCalled = false

    val sampleProfile = UserProfile(
      uid = "uid-123",
      userId = "uid-123",
      firstName = "Maya",
      lastName = "Patel",
      displayName = "Maya Patel",
      email = "maya@outlook.com",
      workspaceRole = WorkspaceRole.CREATIVE.label,
      provider = "password",
      emailVerified = false
    )
    val localRecent = listOf(
      RecentFileEntity(
        id = 1L,
        fileName = "Brief.pdf",
        fileUriString = "file:///tmp/brief.pdf",
        mimeType = "application/pdf",
        sizeBytes = 12_000L,
        pageCount = 2,
        lastToolId = "edit",
        lastToolName = "Edit PDF",
        summaryNote = "Edited",
        updatedAt = System.currentTimeMillis()
      )
    )
    val cloudDocs = listOf(
      CloudRecentDoc(
        id = "doc_1",
        userId = "uid-123",
        fileName = "Brief.pdf",
        mimeType = "application/pdf",
        sizeBytes = 12_000L,
        pageCount = 2,
        lastToolId = "edit",
        lastToolName = "Edit PDF",
        summaryNote = "Edited"
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AccountProfileDialog(
          userDisplayName = "Maya Patel",
          userEmail = "maya@outlook.com",
          isEmailVerified = false,
          profileState = CloudUiState.Success(sampleProfile),
          cloudDocsState = CloudUiState.Success(cloudDocs),
          localRecentFiles = localRecent,
          isBusy = false,
          statusMessage = "Connected to Cloud Firestore.",
          onDismiss = {},
          onSaveProfile = { f, l, r ->
            savedFirst = f
            savedLast = l
            savedRole = r
          },
          onSendVerificationEmail = { verifyEmailCalled = true },
          onRefreshVerification = { refreshStatusCalled = true },
          onSyncLocalFiles = { syncCalled = true },
          onDeleteCloudDoc = {},
          onSwitchAccount = { switchCalled = true },
          onSignOut = { signOutCalled = true },
          onDeleteCloudData = { deleteCloudCalled = true }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("account_profile_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Email & Password • Unverified").assertIsDisplayed()

    composeTestRule.onNodeWithTag("resend_verification_email_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("refresh_verification_status_button").performScrollTo().performClick()
    assertTrue(verifyEmailCalled)
    assertTrue(refreshStatusCalled)

    composeTestRule.onNodeWithTag("profile_first_name_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("profile_first_name_input").performTextInput("Elena")
    composeTestRule.onNodeWithTag("profile_last_name_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("profile_last_name_input").performTextInput("Rossi")
    composeTestRule.onNodeWithTag("save_profile_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("Elena", savedFirst)
    assertEquals("Rossi", savedLast)
    assertEquals(WorkspaceRole.CREATIVE, savedRole)

    composeTestRule.onNodeWithTag("sync_recent_files_button").performScrollTo().performClick()
    assertTrue(syncCalled)
    composeTestRule.onNodeWithTag("switch_account_button").performScrollTo().performClick()
    assertTrue(switchCalled)
    composeTestRule.onNodeWithTag("sign_out_button").performScrollTo().performClick()
    assertTrue(signOutCalled)

    // Delete Account & Cloud Data confirmation modal: Cancel first, then Confirm
    composeTestRule.onNodeWithTag("delete_cloud_profile_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Delete Account & Cloud Data?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()
    assertFalse(deleteCloudCalled)

    composeTestRule.onNodeWithTag("delete_cloud_profile_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Delete").performClick()
    composeTestRule.waitForIdle()
    assertTrue(deleteCloudCalled)
  }

  // =========================================================================
  // 6. DASHBOARD & MULTIPLE WORKSPACES END-TO-END FUNCTIONAL CUJ TEST
  // =========================================================================

  @Test
  fun `dashboard theme toggle category filters search and tool workspace validation work cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = RecentFileRepository(db.recentFileDao())
      val viewModel = RecentFilesViewModel(repo)
      var darkTheme by mutableStateOf(true)
      var accountClicked = false

      composeTestRule.setContent {
        MyApplicationTheme(darkTheme = darkTheme) {
          PdfToolsDashboard(
            isDarkTheme = darkTheme,
            onToggleTheme = { darkTheme = !darkTheme },
            recentFilesViewModel = viewModel,
            accountDisplayName = "Maya Patel",
            onOpenAccount = { accountClicked = true }
          )
        }
      }

      composeTestRule.waitForIdle()

      // Account pill callback
      composeTestRule.onNodeWithTag("dashboard_account_button").assertIsDisplayed().performClick()
      assertTrue(accountClicked)

      // Theme toggle works in Dashboard (Dark -> Light -> Dark)
      composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed().performClick()
      composeTestRule.waitForIdle()
      assertFalse(darkTheme)
      composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
      composeTestRule.waitForIdle()
      assertTrue(darkTheme)

      // Filter across all 6 category chips without errors
      ToolCategory.entries.forEach { cat ->
        composeTestRule.onNodeWithTag("category_chip_${cat.name.lowercase()}").performScrollTo().performClick()
        composeTestRule.waitForIdle()
      }
      composeTestRule.onNodeWithTag("category_chip_all").performScrollTo().performClick()
      composeTestRule.waitForIdle()

      // Search with no results shows empty state cleanly, then clears
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextInput("zzzz_not_a_tool")
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextClearance()
      composeTestRule.waitForIdle()

      // Open Split PDF spotlight -> click Execute with no file -> shows clean validation error
      composeTestRule.onNodeWithTag("primary_spotlight_split").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select a PDF file first.").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("workspace_back_button").performClick()
      composeTestRule.waitForIdle()

      // Open Compress PDF spotlight -> click Execute with no file -> shows clean validation error
      composeTestRule.onNodeWithTag("primary_spotlight_compress").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select a PDF file first.").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("workspace_back_button").performClick()
      composeTestRule.waitForIdle()

      // Search for Protect PDF -> open workspace -> verify validation error without crashing
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextInput("Protect")
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("tool_card_protect").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select a PDF file first.").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("workspace_back_button").performClick()
      composeTestRule.waitForIdle()

      // Search for Compare PDF -> open workspace -> verify 2-doc validation error without crashing
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextClearance()
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextInput("Compare")
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("tool_card_compare").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select both Document A and Document B.").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("workspace_back_button").performClick()
      composeTestRule.waitForIdle()
    } finally {
      db.close()
    }
  }
}
