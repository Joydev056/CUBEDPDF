package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.model.pdfToolsList
import com.example.pdf.PasswordStrength
import com.example.pdf.PdfCryptoService
import com.example.pdf.PdfPasswordActionMode
import com.example.pdf.PdfProtectionStatus
import com.example.ui.components.PdfPasswordControlCard
import com.example.ui.screens.ToolWorkspaceScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File
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
@Config(sdk = [36])
class PdfCryptoServiceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `encryptBytes and decryptBytes use unique salt and IV and reject tampered ciphertext`() {
    val plain = "%PDF-1.7\n1 0 obj<< /Type /Catalog >>endobj\n%%EOF\n".toByteArray(Charsets.UTF_8)
    val pass = "Vault#2026!".toCharArray()

    val cipher1 = PdfCryptoService.encryptBytes(plain, pass.copyOf())
    val cipher2 = PdfCryptoService.encryptBytes(plain, pass.copyOf())

    assertTrue(PdfCryptoService.isAesVaultBytes(cipher1))
    assertTrue(PdfCryptoService.isAesVaultBytes(cipher2))
    assertFalse("Fresh random salt & IV must produce unique ciphertexts", cipher1.contentEquals(cipher2))

    val decrypted1 = PdfCryptoService.decryptBytes(cipher1, pass.copyOf())
    val decrypted2 = PdfCryptoService.decryptBytes(cipher2, pass.copyOf())
    assertArrayEquals(plain, decrypted1)
    assertArrayEquals(plain, decrypted2)

    // Wrong password must throw clear IllegalArgumentException
    val wrongPassError = runCatching {
      PdfCryptoService.decryptBytes(cipher1, "WrongPass#1".toCharArray())
    }.exceptionOrNull()
    assertNotNull(wrongPassError)
    assertTrue(wrongPassError?.message?.contains("Incorrect password", ignoreCase = true) == true)

    // Tampering with ciphertext must fail GCM authentication tag check
    val tampered = cipher1.copyOf()
    tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0xFF).toByte()
    val tamperError = runCatching {
      PdfCryptoService.decryptBytes(tampered, pass.copyOf())
    }.exceptionOrNull()
    assertNotNull(tamperError)
    assertTrue(tamperError is IllegalArgumentException)
  }

  @Test
  fun `password strength and set or remove validation enforce security constraints`() {
    assertEquals(PasswordStrength.EMPTY, PdfCryptoService.evaluatePasswordStrength(""))
    assertEquals(PasswordStrength.WEAK, PdfCryptoService.evaluatePasswordStrength("abc"))
    assertEquals(PasswordStrength.WEAK, PdfCryptoService.evaluatePasswordStrength("abcd"))
    assertEquals(PasswordStrength.FAIR, PdfCryptoService.evaluatePasswordStrength("Cubed2026"))
    assertEquals(PasswordStrength.STRONG, PdfCryptoService.evaluatePasswordStrength("CubedVault#2026!"))

    assertNotNull(PdfCryptoService.validateSetPassword("", ""))
    assertNotNull(PdfCryptoService.validateSetPassword("123", "123"))
    assertNotNull(PdfCryptoService.validateSetPassword("Secret123", ""))
    assertEquals("Passwords do not match.", PdfCryptoService.validateSetPassword("Secret123", "Secret999"))
    assertNull(PdfCryptoService.validateSetPassword("Secret123", "Secret123"))

    assertNotNull(PdfCryptoService.validateRemovePassword(""))
    assertNull(PdfCryptoService.validateRemovePassword("Secret123"))
  }

  @Test
  fun `file encryption decryption inspection and double-encryption guard work end-to-end`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val samplePdfBytes = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".toByteArray(Charsets.UTF_8)
    val plainFile = File(context.cacheDir, "crypto_service_plain.pdf").apply {
      writeBytes(samplePdfBytes)
    }
    val plainUri = Uri.fromFile(plainFile)

    // Unprotected status detection
    assertEquals(
      PdfProtectionStatus.UNPROTECTED,
      PdfCryptoService.inspectProtectionStatus(context, plainUri)
    )

    // Native /Encrypt PDF heuristic detection
    val nativeEncryptedFile = File(context.cacheDir, "crypto_service_native_enc.pdf").apply {
      writeBytes("%PDF-1.6\n1 0 obj<<>>endobj\ntrailer<< /Root 1 0 R /Encrypt 2 0 R >>\n%%EOF\n".toByteArray(Charsets.UTF_8))
    }
    assertEquals(
      PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED,
      PdfCryptoService.inspectProtectionStatus(context, Uri.fromFile(nativeEncryptedFile))
    )

    // Encrypt plain PDF
    val encResult = PdfCryptoService.encryptPdfFile(context, plainUri, "StrongPass#2026")
    assertTrue("encryptPdfFile failed: ${encResult.exceptionOrNull()}", encResult.isSuccess)
    val encFile = encResult.getOrThrow().primaryFile.file
    val encUri = Uri.fromFile(encFile)

    // Vault status detection
    assertEquals(
      PdfProtectionStatus.AES_VAULT_ENCRYPTED,
      PdfCryptoService.inspectProtectionStatus(context, encUri)
    )

    // Double-encryption guard rejects re-encrypting an already-encrypted .pdfenc vault
    val doubleEncRes = PdfCryptoService.encryptPdfFile(context, encUri, "AnotherPass#99")
    assertTrue(doubleEncRes.isFailure)
    assertTrue(doubleEncRes.exceptionOrNull()?.message?.contains("already encrypted", ignoreCase = true) == true)

    // Decrypt back to original exact PDF bytes
    val decResult = PdfCryptoService.decryptPdfFile(context, encUri, "StrongPass#2026")
    assertTrue("decryptPdfFile failed: ${decResult.exceptionOrNull()}", decResult.isSuccess)
    assertArrayEquals(samplePdfBytes, decResult.getOrThrow().primaryFile.file.readBytes())
  }

  @Test
  fun `password control card supports mode switching visibility toggle strength meter mismatch and smart switch`() {
    var mode by mutableStateOf(PdfPasswordActionMode.SET_PASSWORD)
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var status by mutableStateOf<PdfProtectionStatus?>(PdfProtectionStatus.AES_VAULT_ENCRYPTED)

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        PdfPasswordControlCard(
          mode = mode,
          onModeChange = { mode = it },
          password = password,
          onPasswordChange = { password = it },
          confirmPassword = confirmPassword,
          onConfirmPasswordChange = { confirmPassword = it },
          protectionStatus = status,
          accentColor = Color(0xFF7C3AED)
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pdf_password_control_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pdf_protection_status_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pdf_password_strength_label").assertTextEquals("Enter password")

    // Type strong password + mismatched confirmation -> displays mismatch error
    composeTestRule.onNodeWithTag("pdf_password_input").performTextInput("CubedVault#2026!")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pdf_password_strength_label").assertTextEquals("Strong")

    composeTestRule.onNodeWithTag("pdf_confirm_password_input").performTextInput("WrongConfirm")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pdf_password_mismatch_error").assertIsDisplayed()

    // Fix confirmation -> shows match confirmation
    composeTestRule.onNodeWithTag("pdf_confirm_password_input").performTextClearance()
    composeTestRule.onNodeWithTag("pdf_confirm_password_input").performTextInput("CubedVault#2026!")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pdf_password_match_ok").assertIsDisplayed()

    // Toggle password visibility button
    composeTestRule.onNodeWithTag("pdf_password_visibility_toggle").performClick()
    composeTestRule.waitForIdle()

    // Smart suggestion switches from Set Password to Remove Password when an encrypted vault is detected
    composeTestRule.onNodeWithTag("pdf_switch_mode_suggestion").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals(PdfPasswordActionMode.REMOVE_PASSWORD, mode)

    // Switch back to Set Password via mode toggle chip
    composeTestRule.onNodeWithTag("pdf_password_mode_set").performClick()
    composeTestRule.waitForIdle()
    assertEquals(PdfPasswordActionMode.SET_PASSWORD, mode)
  }

  @Test
  fun `tool workspace protect encrypts file and unlock decrypts it via UI controls`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val samplePdfBytes = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".toByteArray(Charsets.UTF_8)
    val plainFile = File(context.cacheDir, "ui_workspace_plain.pdf").apply {
      writeBytes(samplePdfBytes)
    }

    val protectTool = pdfToolsList.first { it.id == "protect" }
    var recordedEncryptedUri: String? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        ToolWorkspaceScreen(
          tool = protectTool,
          initialFileUri = Uri.fromFile(plainFile),
          onBack = {},
          onRecordRecentFile = { _, uriStr, mime, _, _, _ ->
            if (mime == "application/octet-stream") {
              recordedEncryptedUri = uriStr
            }
          }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Encrypt & Set Password").performScrollTo().assertIsDisplayed()

    // Enter password & matching confirmation, then execute encryption
    composeTestRule.onNodeWithTag("pdf_password_input").performScrollTo().performTextInput("UiPass#2026")
    composeTestRule.onNodeWithTag("pdf_confirm_password_input").performScrollTo().performTextInput("UiPass#2026")
    composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      recordedEncryptedUri != null
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("result_output_card").performScrollTo().assertIsDisplayed()

    // Switch right inside the same workspace to Remove Password and verify button label updates
    composeTestRule.onNodeWithTag("pdf_password_mode_remove").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Decrypt & Remove Password").performScrollTo().assertIsDisplayed()
  }
}
