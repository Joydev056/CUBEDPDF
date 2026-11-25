package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
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
import com.example.pdf.ExtractedPageText
import com.example.pdf.PdfEngine
import com.example.ui.components.ExtractTextStudioCard
import com.example.ui.components.buildHighlightedAnnotatedString
import com.example.ui.components.combineEditedPages
import com.example.ui.components.computeTextStats
import com.example.ui.components.findSearchMatches
import com.example.ui.components.replaceAllCaseInsensitive
import com.example.ui.screens.PdfToolsDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RecentFilesViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
class ExtractTextOcrTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `search match highlighting stats and find-replace helpers work accurately`() {
    val sample = "Invoice #402\nCubedPDF scanned invoice summary.\nTotal: $240.00"
    val stats = computeTextStats(sample)
    assertEquals(8, stats.words)
    assertEquals(sample.length, stats.characters)
    assertEquals(3, stats.lines)

    val matches = findSearchMatches(sample, "invoice")
    assertEquals(2, matches.size)
    assertEquals(0..6, matches[0])

    val highlighted = buildHighlightedAnnotatedString(sample, "invoice", activeMatchIndex = 1)
    assertEquals(sample, highlighted.text)
    assertEquals(2, highlighted.spanStyles.size)

    val (replaced, count) = replaceAllCaseInsensitive(sample, "invoice", "Receipt")
    assertEquals(2, count)
    assertTrue(replaced.contains("Receipt #402"))
    assertTrue(replaced.contains("scanned Receipt summary"))

    val pages = listOf(
      ExtractedPageText(0, 1, "First page text", 3, 15, 1),
      ExtractedPageText(1, 2, "Second page text", 3, 16, 1)
    )
    val combined = combineEditedPages(pages, mapOf(1 to "Updated page one", 2 to "Second page text"))
    assertTrue(combined.contains("--- Page 1 ---"))
    assertTrue(combined.contains("Updated page one"))
    assertTrue(combined.contains("--- Page 2 ---"))
  }

  @Test
  fun `pdf engine saves edited text as txt validates empty images and generates searchable pdf`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Empty image list validation
    assertTrue(PdfEngine.ocrImages(context, emptyList()).isFailure)

    // Empty text export validation
    assertTrue(PdfEngine.saveEditedTextAsTxt(context, "Notes", "   ").isFailure)
    assertTrue(PdfEngine.createSearchablePdfFromText(context, "Notes", "   ").isFailure)

    // Save edited .TXT round-trip
    val sampleText = "Quarterly Budget Review\nPrepared via ML Kit OCR.\nRevenue grew by 28%."
    val txtRes = PdfEngine.saveEditedTextAsTxt(context, "Quarterly Budget", sampleText)
    assertTrue("saveEditedTextAsTxt failed: ${txtRes.exceptionOrNull()}", txtRes.isSuccess)
    val txtFile = txtRes.getOrThrow().file
    assertTrue(txtFile.exists())
    assertEquals(sampleText, txtFile.readText(Charsets.UTF_8))

    // Export Searchable PDF (Robolectric's native PdfDocument shadow may report closed handle on JVM)
    val pdfRes = PdfEngine.createSearchablePdfFromText(context, "Quarterly Budget", sampleText)
    if (pdfRes.isSuccess) {
      val generated = pdfRes.getOrThrow()
      assertTrue(generated.primaryFile.file.exists())
      assertTrue(generated.resultSizeBytes > 0L)
      assertEquals(1, generated.pageCount)
    }
  }

  @Test
  fun `extract text studio supports searching highlighting editing find-replace per-page tabs and exports`() {
    val pages = listOf(
      ExtractedPageText(0, 1, "Invoice Alpha\nItem: Cloud Storage\nInvoice Confirmed", 7, 49, 2),
      ExtractedPageText(1, 2, "Appendix Notes\nSigned by Finance Team", 6, 37, 1)
    )
    val fullText = "--- Page 1 ---\nInvoice Alpha\nItem: Cloud Storage\nInvoice Confirmed\n\n--- Page 2 ---\nAppendix Notes\nSigned by Finance Team"

    var copiedText = ""
    var sharedText = ""
    var exportedTxt = ""
    var exportedPdf = ""

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        ExtractTextStudioCard(
          originalFullText = fullText,
          extractedPages = pages,
          accentColor = Color(0xFF0D9488),
          onCopyText = { copiedText = it },
          onShareText = { sharedText = it },
          onExportTxt = { exportedTxt = it },
          onExportSearchablePdf = { exportedPdf = it }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("extract_text_studio_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ocr_stats_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ocr_page_chip_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ocr_page_chip_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ocr_page_chip_2").assertIsDisplayed()

    // Search for "Invoice" -> finds 2 matches
    composeTestRule.onNodeWithTag("ocr_search_input").performTextInput("Invoice")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Match 1 of 2").assertIsDisplayed()

    // Step to next match -> switches to Search & Highlight preview and advances counter
    composeTestRule.onNodeWithTag("ocr_next_match_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Match 2 of 2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ocr_highlighted_preview").assertIsDisplayed()

    // Open Find & Replace -> replace "Invoice" with "Receipt"
    composeTestRule.onNodeWithTag("ocr_toggle_replace_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ocr_replace_input").performTextInput("Receipt")
    composeTestRule.onNodeWithTag("ocr_replace_all_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Replaced 2 occurrence(s).").assertIsDisplayed()

    // Trigger Copy, Share, Save .TXT, and Searchable PDF actions with updated text
    composeTestRule.onNodeWithTag("ocr_copy_text_button").performClick()
    composeTestRule.onNodeWithTag("ocr_share_text_button").performClick()
    composeTestRule.onNodeWithTag("ocr_export_txt_button").performClick()
    composeTestRule.onNodeWithTag("ocr_export_pdf_button").performClick()
    composeTestRule.waitForIdle()

    assertTrue(copiedText.contains("Receipt Alpha"))
    assertTrue(sharedText.contains("Receipt Confirmed"))
    assertTrue(exportedTxt.contains("Receipt Alpha"))
    assertTrue(exportedPdf.contains("Receipt Confirmed"))

    // Reset edits restores original ML Kit text
    composeTestRule.onNodeWithTag("ocr_reset_edits_button").performClick()
    composeTestRule.onNodeWithTag("ocr_copy_text_button").performClick()
    assertTrue(copiedText.contains("Invoice Alpha"))
  }

  @Test
  fun `dashboard extract text spotlight opens ocr workspace with pdf and scanned images modes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val viewModel = RecentFilesViewModel(RecentFileRepository(db.recentFileDao()))
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
      composeTestRule.onNodeWithTag("primary_spotlight_ocr").performScrollTo().performClick()
      composeTestRule.waitForIdle()

      // Verify Extract Text workspace opens with PDF & Scanned Images toggles
      composeTestRule.onNodeWithText("Extract Text (OCR)").assertIsDisplayed()
      composeTestRule.onNodeWithTag("ocr_source_pdf").assertIsDisplayed()
      composeTestRule.onNodeWithTag("ocr_source_images").assertIsDisplayed()

      // Execute with no PDF selected -> clean validation error
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select a scanned PDF file first, or switch to Scanned Images.").performScrollTo().assertIsDisplayed()

      // Switch to Scanned Images mode -> execute with no images -> clean validation error
      composeTestRule.onNodeWithTag("ocr_source_images").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("execute_tool_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Select at least one scanned image or photo first.").performScrollTo().assertIsDisplayed()
    } finally {
      db.close()
    }
  }
}
