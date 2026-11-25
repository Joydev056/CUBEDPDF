package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.RecentFileRepository
import com.example.model.pdfToolsList
import com.example.pdf.CompressionLevel
import com.example.pdf.PageSizeMode
import com.example.pdf.PdfEngine
import com.example.ui.screens.PdfToolsDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RecentFilesViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read app_name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CubedPDF", appName)
  }

  @Test
  fun `dashboard displays primary tools filters categories and opens workspaces`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val viewModel = RecentFilesViewModel(RecentFileRepository(db.recentFileDao()))

    var darkState = true
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = darkState) {
        PdfToolsDashboard(
          isDarkTheme = darkState,
          onToggleTheme = { darkState = !darkState },
          recentFilesViewModel = viewModel
        )
      }
    }

    composeTestRule.onNodeWithText("CubedPDF").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cubed_pdf_logo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("recent_files_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("primary_spotlight_merge").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("execute_tool_button").assertIsDisplayed()
    // Clicking execute with <2 files shows clean validation error instead of crashing
    composeTestRule.onNodeWithTag("execute_tool_button").performClick()
    composeTestRule.onNodeWithText("Please select at least 2 PDF files to merge.").assertIsDisplayed()

    composeTestRule.onNodeWithTag("workspace_back_button").performClick()
    composeTestRule.onNodeWithTag("primary_spotlight_split").assertIsDisplayed()
    composeTestRule.onNodeWithTag("primary_spotlight_compress").assertIsDisplayed()

    // Test category filtering and search
    composeTestRule.onNodeWithTag("category_chip_organize").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("tool_card_merge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("category_chip_all").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextInput("OCR")
    composeTestRule.onNodeWithTag("tool_card_ocr").performScrollTo().assertIsDisplayed()
    db.close()
  }

  @Test
  fun `recent files room database stores displays and resumes documents`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repository = RecentFileRepository(db.recentFileDao())
    val viewModel = RecentFilesViewModel(repository)

    val sampleUri = PdfEngine.createSamplePdf(context, "Quarterly Report", pages = 3)
      .getOrElse { android.net.Uri.parse("file:///data/local/tmp/Quarterly_Report.pdf") }
    val editTool = pdfToolsList.first { it.id == "edit" }

    val insertedId = repository.recordRecentFile(
      fileName = "Quarterly_Report.pdf",
      fileUriString = sampleUri.toString(),
      mimeType = "application/pdf",
      sizeBytes = 24576L,
      pageCount = 3,
      lastToolId = editTool.id,
      lastToolName = editTool.name,
      summaryNote = "Edited annotations on Page 1"
    )

    val listAfterInsert = repository.recentFiles.first()
    assertEquals(1, listAfterInsert.size)
    assertEquals("Quarterly_Report.pdf", listAfterInsert.first().fileName)

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        PdfToolsDashboard(
          isDarkTheme = true,
          onToggleTheme = {},
          recentFilesViewModel = viewModel
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Recent Files").assertIsDisplayed()
    composeTestRule.onNodeWithText("Quarterly_Report.pdf").assertIsDisplayed()
    composeTestRule.onNodeWithTag("resume_recent_file_$insertedId").assertIsDisplayed().performClick()

    // Verify clicking Resume opens the Edit PDF workspace with the document ready
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Edit PDF").assertIsDisplayed()
    composeTestRule.onNodeWithTag("execute_tool_button").assertIsDisplayed()

    db.close()
  }

  @Test
  fun `pdf engine formatting and validation helpers work reliably`() = runBlocking {
    assertEquals("0 B", PdfEngine.formatFileSize(0))
    assertEquals("512.0 KB", PdfEngine.formatFileSize(512 * 1024L))
    assertEquals("1.50 MB", PdfEngine.formatFileSize((1.5 * 1024 * 1024).toLong()))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val emptyMerge = PdfEngine.mergePdfs(context, emptyList())
    assertTrue(emptyMerge.isFailure)

    val emptyImages = PdfEngine.imagesToPdf(context, emptyList(), PageSizeMode.A4)
    assertTrue(emptyImages.isFailure)

    val sampleRes = PdfEngine.createSamplePdf(context, "Sample", pages = 2)
    if (sampleRes.isSuccess) {
      val uri = sampleRes.getOrThrow()
      val compressRes = PdfEngine.compressPdf(context, uri, CompressionLevel.BALANCED)
      assertTrue(compressRes.isSuccess)
      val protectRes = PdfEngine.protectPdf(context, uri, "secret123")
      assertTrue(protectRes.isSuccess)
    }
  }

  @Test
  fun `auth screen displays Neon Sign In Create Your Account and Recovery flows`() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        com.example.ui.screens.AuthScreen(
          isDarkTheme = true,
          onToggleTheme = {},
          onAuthSuccess = { _, _, _, _, _ -> },
          emailSignInHandler = { _, _ -> com.example.data.remote.EmailSignInResult.ProviderNotEnabledInConsole },
          enableAutoSignIn = false
        )
      }
    }

    // Screen 1: Welcome Back (Sign In)
    composeTestRule.onNodeWithTag("auth_cubed_pdf_logo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Welcome Back \uD83D\uDC4B").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signin_email_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signin_password_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("sign_in_submit_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()

    // Test Sign In validation + white-button popup dialog
    composeTestRule.onNodeWithTag("signin_email_input").performScrollTo().performTextInput("alex@outlook.com")
    composeTestRule.onNodeWithTag("signin_password_input").performScrollTo().performTextInput("StrongP@ss1")
    composeTestRule.onNodeWithTag("sign_in_submit_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("popup_continue_with_google_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("popup_close_dialog_button").performClick()

    // Screen 2: Create Your Account (Sign Up)
    composeTestRule.onNodeWithTag("go_to_create_account_button").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Create Your Account \u2728").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signup_first_name_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signup_last_name_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signup_email_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_account_submit_button").performScrollTo().assertIsDisplayed()

    // Navigate back to Sign In via circular back button -> Forgot password -> Recovery
    composeTestRule.onNodeWithTag("auth_back_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("forgot_password_link").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("google_account_recovery_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("recovery_email_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("send_reset_email_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `registration validation accepts any email provider and enforces password policy`() {
    assertTrue(com.example.data.remote.isValidEmailAddress("user@outlook.com"))
    assertTrue(com.example.data.remote.isValidEmailAddress("founder@proton.me"))
    assertTrue(com.example.data.remote.isValidEmailAddress("team.lead+pdf@company.co.uk"))
    assertTrue(!com.example.data.remote.isValidEmailAddress("not-an-email"))

    // Valid registration returns null error
    assertEquals(
      null,
      com.example.data.remote.validateRegistrationInputs(
        firstName = "Maya",
        lastName = "Patel",
        email = "maya@proton.me",
        password = "Cubed#2026",
        confirmPassword = "Cubed#2026"
      )
    )

    // Mismatched confirm password fails cleanly
    assertTrue(
      com.example.data.remote.validateRegistrationInputs(
        firstName = "Maya",
        lastName = "Patel",
        email = "maya@proton.me",
        password = "Cubed#2026",
        confirmPassword = "Different#1"
      ) != null
    )

    // Weak password fails cleanly
    assertTrue(
      com.example.data.remote.validateRegistrationInputs(
        firstName = "Maya",
        lastName = "Patel",
        email = "maya@proton.me",
        password = "short",
        confirmPassword = "short"
      ) != null
    )
  }
}
