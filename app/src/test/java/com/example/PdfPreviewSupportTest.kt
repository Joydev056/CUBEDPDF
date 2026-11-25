package com.example

import com.example.pdf.PdfProtectionStatus
import com.example.ui.components.PdfPreviewSupport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests verifying the 16 KB-compliant native Android PdfRenderer Compose previewer,
 * page & zoom clamping, error mapping, and ToolWorkspaceScreen wiring.
 */
class PdfPreviewSupportTest {

  private fun projectRoot(): File {
    val cwd = File(System.getProperty("user.dir") ?: ".")
    return if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile ?: cwd
  }

  @Test
  fun `canRenderInPdfView allows PDFs and blocks encrypted vaults or non-PDF files`() {
    assertTrue(PdfPreviewSupport.canRenderInPdfView("Contract.pdf", PdfProtectionStatus.UNPROTECTED))
    assertTrue(PdfPreviewSupport.canRenderInPdfView("Locked.pdf", PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED))
    assertTrue(PdfPreviewSupport.canRenderInPdfView("document", null))

    assertFalse(PdfPreviewSupport.canRenderInPdfView("secret.pdfenc", PdfProtectionStatus.AES_VAULT_ENCRYPTED))
    assertFalse(PdfPreviewSupport.canRenderInPdfView("secret.pdf", PdfProtectionStatus.AES_VAULT_ENCRYPTED))
    assertFalse(PdfPreviewSupport.canRenderInPdfView("notes.txt", null))
    assertFalse(PdfPreviewSupport.canRenderInPdfView("page.html", null))
    assertFalse(PdfPreviewSupport.canRenderInPdfView("photo.png", null))
  }

  @Test
  fun `clampPageIndex and clampZoom safely bound navigation and pinch-zoom values`() {
    assertEquals(0, PdfPreviewSupport.clampPageIndex(-3, 5))
    assertEquals(0, PdfPreviewSupport.clampPageIndex(0, 5))
    assertEquals(2, PdfPreviewSupport.clampPageIndex(2, 5))
    assertEquals(4, PdfPreviewSupport.clampPageIndex(99, 5))
    assertEquals(0, PdfPreviewSupport.clampPageIndex(2, 0))
    assertEquals(0, PdfPreviewSupport.clampPageIndex(-1, -2))

    assertEquals(1f, PdfPreviewSupport.clampZoom(0.25f), 0.001f)
    assertEquals(2.5f, PdfPreviewSupport.clampZoom(2.5f), 0.001f)
    assertEquals(4f, PdfPreviewSupport.clampZoom(12f), 0.001f)
  }

  @Test
  fun `mapRenderError produces clear user-friendly guidance for password and format errors`() {
    val securityMsg = PdfPreviewSupport.mapRenderError(SecurityException("Password required"))
    assertTrue(securityMsg.contains("password", ignoreCase = true))

    val formatMsg = PdfPreviewSupport.mapRenderError(IllegalStateException("File format not supported"))
    assertTrue(formatMsg.contains("standard PDF", ignoreCase = true))

    val genericMsg = PdfPreviewSupport.mapRenderError(RuntimeException("I/O error"))
    assertTrue(genericMsg.contains("Preview unavailable", ignoreCase = true))
  }

  @Test
  fun `native PdfRenderer previewer is 16KB compliant with zero legacy android-pdf-viewer dependency`() {
    val root = projectRoot()
    val appGradle = File(root, "app/build.gradle.kts").readText(Charsets.UTF_8)
    val previewCard = File(root, "app/src/main/java/com/example/ui/components/PdfDocumentPreviewCard.kt").readText(Charsets.UTF_8)
    val workspace = File(root, "app/src/main/java/com/example/ui/screens/ToolWorkspaceScreen.kt").readText(Charsets.UTF_8)

    assertFalse(
      "Legacy 4KB-aligned android-pdf-viewer must not be actively implemented",
      Regex("""^\s*implementation\(libs\.android\.pdf\.viewer\)""", RegexOption.MULTILINE).containsMatchIn(appGradle)
    )
    assertTrue(appGradle.contains("useLegacyPackaging = false"))
    assertTrue(previewCard.contains("android.graphics.pdf.PdfRenderer"))
    assertTrue(previewCard.contains("detectTransformGestures"))
    assertTrue(previewCard.contains("bmp.recycle()"))
    assertFalse(previewCard.contains("com.github.barteksc.pdfviewer"))
    assertTrue(workspace.contains("PdfDocumentPreviewCard("))
  }
}
