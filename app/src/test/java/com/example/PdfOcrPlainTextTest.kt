package com.example

import com.example.pdf.ExtractedPageText
import com.example.pdf.PdfOcrService
import com.example.ui.components.combineEditedPages
import com.example.ui.components.computeTextStats
import com.example.ui.components.findSearchMatches
import com.example.ui.components.replaceAllCaseInsensitive
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure-JVM unit tests verifying ML Kit scanned PDF text extraction helpers,
 * page-range selection, plain-text (.txt) formatting & UTF-8 disk output,
 * filename sanitization, and UI/SAF wiring.
 */
class PdfOcrPlainTextTest {

  private fun projectRoot(): File {
    var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
    if (File(dir, "src/main").exists() && dir.name == "app") {
      return dir.parentFile ?: dir
    }
    return dir
  }

  @Test
  fun `sanitizeTxtFileName prevents path traversal normalizes extensions and handles blanks`() {
    assertEquals("Invoice_2026.txt", PdfOcrService.sanitizeTxtFileName("Invoice 2026"))
    assertEquals("Invoice_2026.txt", PdfOcrService.sanitizeTxtFileName("Invoice 2026.txt"))
    assertEquals("Quarterly_Report.txt", PdfOcrService.sanitizeTxtFileName("Quarterly Report.PDF"))
    assertEquals("secret.txt", PdfOcrService.sanitizeTxtFileName("../../etc/secret.txt"))
    assertEquals("notes.txt", PdfOcrService.sanitizeTxtFileName("C:\\Users\\Docs\\notes.pdf"))
    assertEquals("extracted_text.txt", PdfOcrService.sanitizeTxtFileName("   ...   "))
    assertEquals("custom_fallback.txt", PdfOcrService.sanitizeTxtFileName("", fallback = "custom_fallback"))
  }

  @Test
  fun `suggestTxtFileName derives clean txt name from source pdf display name`() {
    assertEquals(
      "Scanned_Receipt_extracted.txt",
      PdfOcrService.suggestTxtFileName("Scanned Receipt.pdf")
    )
    assertEquals(
      "extracted_text.txt",
      PdfOcrService.suggestTxtFileName(null)
    )
    assertEquals(
      "extracted_text.txt",
      PdfOcrService.suggestTxtFileName("   ")
    )
  }

  @Test
  fun `resolveTargetPages combines thumbnail selection and custom ranges with clamping`() {
    // Default: all pages when no range or thumbnails selected
    assertEquals(listOf(0, 1, 2, 3), PdfOcrService.resolveTargetPages(totalPages = 4))

    // Custom 1-based range expression
    assertEquals(
      listOf(0, 2, 3, 4),
      PdfOcrService.resolveTargetPages(totalPages = 6, pageRangeExpression = "1, 3-5")
    )

    // Combined thumbnail selection + range expression (deduplicated & sorted)
    assertEquals(
      listOf(0, 1, 3, 4),
      PdfOcrService.resolveTargetPages(
        totalPages = 5,
        selectedPages = setOf(4, 1, 99),
        pageRangeExpression = "1, 4-5"
      )
    )

    // Zero pages safety
    assertEquals(emptyList<Int>(), PdfOcrService.resolveTargetPages(totalPages = 0))
  }

  @Test
  fun `formatExtractedPlainText handles headers line endings and blank scanned pages`() {
    val pages = listOf(
      ExtractedPageText(0, 1, "Invoice #101\r\nTotal: $99.00", 4, 27, 2),
      ExtractedPageText(1, 2, "   ", 0, 0, 0),
      ExtractedPageText(2, 3, "Thank you for your business.", 5, 28, 1)
    )

    val withHeaders = PdfOcrService.formatExtractedPlainText(pages, includePageHeaders = true)
    assertTrue(withHeaders.contains("--- Page 1 ---\nInvoice #101\nTotal: $99.00"))
    assertTrue(withHeaders.contains("--- Page 2 ---\n(No text detected on this page)"))
    assertTrue(withHeaders.contains("--- Page 3 ---\nThank you for your business."))
    assertFalse("Line endings must be normalized to LF", withHeaders.contains("\r"))

    val withoutHeaders = PdfOcrService.formatExtractedPlainText(pages, includePageHeaders = false)
    assertFalse(withoutHeaders.contains("--- Page 1 ---"))
    assertTrue(withoutHeaders.contains("Invoice #101\nTotal: $99.00"))
    assertTrue(withoutHeaders.contains("Thank you for your business."))
  }

  @Test
  fun `writePlainTextFile writes clean UTF-8 txt file and rejects blank text`() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "cubedpdf_ocr_test_${System.currentTimeMillis()}")
    val targetFile = File(tempDir, "nested/scanned_output.txt")
    try {
      val content = "--- Page 1 ---\r\nCafé Receipt • €42.50\r\nVerified via ML Kit"
      val written = PdfOcrService.writePlainTextFile(targetFile, content)
      assertTrue(written.exists())
      assertTrue(written.name.endsWith(".txt"))
      assertEquals(
        "--- Page 1 ---\nCafé Receipt • €42.50\nVerified via ML Kit",
        written.readText(Charsets.UTF_8)
      )

      try {
        PdfOcrService.writePlainTextFile(File(tempDir, "empty.txt"), "   \n  ")
        fail("Expected IllegalArgumentException for blank text")
      } catch (_: IllegalArgumentException) {
        // Expected
      }
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `studio text helpers support stats search find-replace and optional page headers`() {
    val pages = listOf(
      ExtractedPageText(0, 1, "Scanned Contract Alpha", 3, 22, 1),
      ExtractedPageText(1, 2, "Scanned Contract Beta", 3, 21, 1)
    )
    val withHeaders = combineEditedPages(pages, mapOf(1 to "Edited Alpha"), includePageHeaders = true)
    assertTrue(withHeaders.contains("--- Page 1 ---\nEdited Alpha"))
    assertTrue(withHeaders.contains("--- Page 2 ---\nScanned Contract Beta"))

    val withoutHeaders = combineEditedPages(pages, mapOf(1 to "Edited Alpha"), includePageHeaders = false)
    assertFalse(withoutHeaders.contains("--- Page 1 ---"))
    assertEquals("Edited Alpha\n\nScanned Contract Beta", withoutHeaders)

    val stats = computeTextStats(withoutHeaders)
    assertEquals(5, stats.words)
    assertEquals(2, stats.lines)

    val matches = findSearchMatches(withoutHeaders, "alpha")
    assertEquals(1, matches.size)

    val (replaced, count) = replaceAllCaseInsensitive(withoutHeaders, "alpha", "Final")
    assertEquals(1, count)
    assertTrue(replaced.contains("Edited Final"))
  }

  @Test
  fun `mlkit dependency service and text-plain saf launcher are properly wired`() {
    val root = projectRoot()
    val toml = File(root, "gradle/libs.versions.toml").readText()
    val appGradle = File(root, "app/build.gradle.kts").readText()
    val ocrServiceSrc = File(root, "app/src/main/java/com/example/pdf/PdfOcrService.kt").readText()
    val workspaceSrc = File(root, "app/src/main/java/com/example/ui/screens/ToolWorkspaceScreen.kt").readText()
    val studioSrc = File(root, "app/src/main/java/com/example/ui/components/ExtractTextStudio.kt").readText()

    assertTrue("ML Kit text-recognition dependency must be declared", toml.contains("com.google.mlkit") && toml.contains("text-recognition"))
    assertTrue("App Gradle must implement ML Kit text-recognition", appGradle.contains("libs.mlkit.text.recognition"))
    assertTrue("PdfOcrService must invoke ML Kit TextRecognition", ocrServiceSrc.contains("TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)"))
    assertTrue("PdfOcrService must close ML Kit recognizer in finally", ocrServiceSrc.contains("recognizer.close()"))
    assertTrue("ToolWorkspaceScreen must use CreateDocument(text/plain) for .txt export", workspaceSrc.contains("ActivityResultContracts.CreateDocument(PdfOcrService.PLAIN_TEXT_MIME_TYPE)"))
    assertTrue("ToolWorkspaceScreen must include PDF page range input", workspaceSrc.contains("ocr_page_range_input"))
    assertTrue("ExtractTextStudio must include .txt filename input and header toggle", studioSrc.contains("ocr_txt_filename_input") && studioSrc.contains("ocr_include_headers_toggle"))
  }
}
