package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pdf.ExtractedPageText
import com.example.pdf.PdfOcrService
import com.example.ui.theme.LocalGlassColors

data class ExtractedTextStats(
  val words: Int,
  val characters: Int,
  val lines: Int
)

fun computeTextStats(text: String): ExtractedTextStats {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return ExtractedTextStats(words = 0, characters = 0, lines = 0)
  val words = trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
  val lines = text.lines().count { it.isNotBlank() }
  return ExtractedTextStats(words = words, characters = text.length, lines = lines)
}

fun findSearchMatches(text: String, query: String): List<IntRange> {
  val needle = query.trim()
  if (needle.isEmpty() || text.isEmpty()) return emptyList()
  val matches = mutableListOf<IntRange>()
  var startIndex = 0
  while (startIndex <= text.length - needle.length) {
    val found = text.indexOf(needle, startIndex = startIndex, ignoreCase = true)
    if (found < 0) break
    matches.add(found until (found + needle.length))
    startIndex = found + needle.length.coerceAtLeast(1)
  }
  return matches
}

fun buildHighlightedAnnotatedString(
  text: String,
  query: String,
  activeMatchIndex: Int,
  highlightBg: Color = Color(0x55F59E0B),
  activeHighlightBg: Color = Color(0xFFF59E0B),
  activeTextColor: Color = Color(0xFF0F172A)
): AnnotatedString {
  val matches = findSearchMatches(text, query)
  if (matches.isEmpty()) return AnnotatedString(text)
  val clampedActive = activeMatchIndex.coerceIn(0, matches.lastIndex)
  return buildAnnotatedString {
    append(text)
    matches.forEachIndexed { idx, range ->
      val isActive = idx == clampedActive
      addStyle(
        style = SpanStyle(
          background = if (isActive) activeHighlightBg else highlightBg,
          color = if (isActive) activeTextColor else Color.Unspecified,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
        ),
        start = range.first,
        end = range.last + 1
      )
    }
  }
}

fun replaceAllCaseInsensitive(text: String, findQuery: String, replacement: String): Pair<String, Int> {
  val needle = findQuery.trim()
  if (needle.isEmpty() || text.isEmpty()) return text to 0
  val matches = findSearchMatches(text, needle)
  if (matches.isEmpty()) return text to 0
  val sb = StringBuilder(text.length)
  var cursor = 0
  for (range in matches) {
    sb.append(text, cursor, range.first)
    sb.append(replacement)
    cursor = range.last + 1
  }
  sb.append(text, cursor, text.length)
  return sb.toString() to matches.size
}

fun combineEditedPages(
  pages: List<ExtractedPageText>,
  editedByPage: Map<Int, String>,
  includePageHeaders: Boolean = true
): String {
  if (pages.isEmpty()) return ""
  if (pages.size == 1) {
    val p = pages.first()
    return (editedByPage[p.pageNumber] ?: p.text).trim()
  }
  return pages.joinToString("\n\n") { page ->
    val body = (editedByPage[page.pageNumber] ?: page.text).trim()
      .ifBlank { "(No text detected on this page)" }
    if (includePageHeaders) "--- Page ${page.pageNumber} ---\n$body" else body
  }.trim()
}

private enum class OcrStudioMode {
  EDIT,
  SEARCH_HIGHLIGHT
}

/**
 * Searchable, editable text studio for ML Kit OCR results from scanned PDFs and images.
 */
@Composable
fun ExtractTextStudioCard(
  originalFullText: String,
  extractedPages: List<ExtractedPageText>,
  accentColor: Color,
  onCopyText: (String) -> Unit,
  onShareText: (String) -> Unit,
  onExportTxt: (String) -> Unit,
  onExportSearchablePdf: (String) -> Unit,
  modifier: Modifier = Modifier,
  suggestedTxtFileName: String = "extracted_text.txt",
  onExportTxtWithName: ((fileName: String, text: String) -> Unit)? = null
) {
  val glass = LocalGlassColors.current

  // Per-page editable text map (pageNumber -> text), plus combined text for "All Pages"
  val editedPageMap = remember(originalFullText, extractedPages) {
    mutableStateMapOf<Int, String>().apply {
      extractedPages.forEach { put(it.pageNumber, it.text) }
    }
  }
  var includePageHeaders by remember(originalFullText, extractedPages) { mutableStateOf(true) }
  var txtFileName by remember(suggestedTxtFileName) {
    mutableStateOf(PdfOcrService.sanitizeTxtFileName(suggestedTxtFileName))
  }
  var allPagesEditedText by remember(originalFullText, extractedPages) {
    mutableStateOf(
      if (extractedPages.isNotEmpty()) combineEditedPages(extractedPages, editedPageMap, includePageHeaders)
      else originalFullText
    )
  }
  var selectedPageNumber by remember(originalFullText, extractedPages) { mutableStateOf<Int?>(null) }
  var isModified by remember(originalFullText, extractedPages) { mutableStateOf(false) }

  var studioMode by remember { mutableStateOf(OcrStudioMode.EDIT) }
  var searchQuery by remember { mutableStateOf("") }
  var replaceQuery by remember { mutableStateOf("") }
  var showReplaceBar by remember { mutableStateOf(false) }
  var activeMatchIndex by remember { mutableIntStateOf(0) }
  var replaceNotice by remember { mutableStateOf<String?>(null) }

  val currentDisplayedText = if (selectedPageNumber == null) {
    allPagesEditedText
  } else {
    editedPageMap[selectedPageNumber] ?: ""
  }

  val matches = remember(currentDisplayedText, searchQuery) {
    findSearchMatches(currentDisplayedText, searchQuery)
  }
  val stats = remember(currentDisplayedText) { computeTextStats(currentDisplayedText) }

  LaunchedEffect(matches.size) {
    if (matches.isEmpty()) {
      activeMatchIndex = 0
    } else if (activeMatchIndex >= matches.size) {
      activeMatchIndex = 0
    }
  }

  fun updateCurrentText(updated: String) {
    isModified = true
    replaceNotice = null
    val pageNum = selectedPageNumber
    if (pageNum == null) {
      allPagesEditedText = updated
    } else {
      editedPageMap[pageNum] = updated
      allPagesEditedText = combineEditedPages(extractedPages, editedPageMap, includePageHeaders)
    }
  }

  fun resetAllEdits() {
    extractedPages.forEach { editedPageMap[it.pageNumber] = it.text }
    allPagesEditedText = if (extractedPages.isNotEmpty()) {
      combineEditedPages(extractedPages, editedPageMap, includePageHeaders)
    } else {
      originalFullText
    }
    isModified = false
    replaceNotice = null
  }

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("extract_text_studio_card"),
    shape = RoundedCornerShape(22.dp),
    tintGlow = accentColor
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header + Live Word/Char Metrics + Reset Edits
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Searchable & Editable Text",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (isModified) {
              Spacer(Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(accentColor.copy(alpha = 0.2f))
                  .border(0.8.dp, accentColor.copy(alpha = 0.55f), CircleShape)
                  .padding(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "Edited",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = accentColor
                )
              }
            }
          }
          Text(
            text = "${stats.words} words • ${stats.characters} chars • ${stats.lines} lines",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("ocr_stats_badge")
          )
        }

        if (isModified) {
          IconButton(
            onClick = { resetAllEdits() },
            modifier = Modifier
              .size(44.dp)
              .testTag("ocr_reset_edits_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = "Reset text edits",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Page Scope Chips (when 2+ pages/images were scanned)
      if (extractedPages.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            GlassCategoryChip(
              label = "All Pages (${extractedPages.size})",
              selected = selectedPageNumber == null,
              onClick = { selectedPageNumber = null },
              modifier = Modifier.testTag("ocr_page_chip_all")
            )
          }
          items(extractedPages, key = { it.pageNumber }) { page ->
            GlassCategoryChip(
              label = "Page ${page.pageNumber} (${page.wordCount}w)",
              selected = selectedPageNumber == page.pageNumber,
              onClick = { selectedPageNumber = page.pageNumber },
              modifier = Modifier.testTag("ocr_page_chip_${page.pageNumber}")
            )
          }
        }
      }

      // Mode Toggle Row (Edit Mode vs Highlighted Search Preview + Find/Replace toggle)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        GlassCategoryChip(
          label = "Edit Text",
          selected = studioMode == OcrStudioMode.EDIT,
          onClick = { studioMode = OcrStudioMode.EDIT },
          modifier = Modifier.testTag("ocr_mode_edit")
        )
        GlassCategoryChip(
          label = "Search & Highlight",
          selected = studioMode == OcrStudioMode.SEARCH_HIGHLIGHT,
          onClick = { studioMode = OcrStudioMode.SEARCH_HIGHLIGHT },
          modifier = Modifier.testTag("ocr_mode_search")
        )
        Spacer(Modifier.weight(1f))
        IconButton(
          onClick = { showReplaceBar = !showReplaceBar },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (showReplaceBar) accentColor.copy(alpha = 0.22f) else glass.pillFill)
            .testTag("ocr_toggle_replace_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.FindReplace,
            contentDescription = "Toggle Find and Replace",
            tint = if (showReplaceBar) accentColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      // In-Document Search Bar with Live Match Count + Prev/Next Navigation
      OutlinedTextField(
        value = searchQuery,
        onValueChange = {
          searchQuery = it
          activeMatchIndex = 0
          replaceNotice = null
          if (it.isNotBlank() && studioMode == OcrStudioMode.EDIT && !showReplaceBar) {
            // Keep current mode but surface match navigation immediately
          }
        },
        placeholder = { Text("Search extracted text...") },
        singleLine = true,
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search text",
            tint = accentColor
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = {
                searchQuery = ""
                activeMatchIndex = 0
                replaceNotice = null
              },
              modifier = Modifier.testTag("ocr_clear_search_button")
            ) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = accentColor,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ocr_search_input")
      )

      // Search Match Navigation Bar (visible when query is non-blank)
      if (searchQuery.isNotBlank()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glass.pillFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = if (matches.isEmpty()) {
              "0 matches found"
            } else {
              "Match ${activeMatchIndex + 1} of ${matches.size}"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (matches.isEmpty()) Color(0xFFE11D48) else accentColor,
            modifier = Modifier.testTag("ocr_match_count_label")
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (studioMode == OcrStudioMode.EDIT && matches.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(accentColor.copy(alpha = 0.16f))
                  .clickable { studioMode = OcrStudioMode.SEARCH_HIGHLIGHT }
                  .padding(horizontal = 8.dp, vertical = 5.dp)
                  .testTag("ocr_jump_highlight_button")
              ) {
                Text(
                  text = "Highlight",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = accentColor
                )
              }
            }
            IconButton(
              onClick = {
                if (matches.isNotEmpty()) {
                  activeMatchIndex = if (activeMatchIndex <= 0) matches.lastIndex else activeMatchIndex - 1
                  studioMode = OcrStudioMode.SEARCH_HIGHLIGHT
                }
              },
              enabled = matches.isNotEmpty(),
              modifier = Modifier
                .size(36.dp)
                .testTag("ocr_prev_match_button")
            ) {
              Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(20.dp))
            }
            IconButton(
              onClick = {
                if (matches.isNotEmpty()) {
                  activeMatchIndex = (activeMatchIndex + 1) % matches.size
                  studioMode = OcrStudioMode.SEARCH_HIGHLIGHT
                }
              },
              enabled = matches.isNotEmpty(),
              modifier = Modifier
                .size(36.dp)
                .testTag("ocr_next_match_button")
            ) {
              Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(20.dp))
            }
          }
        }
      }

      // Optional Find & Replace Row
      if (showReplaceBar) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = replaceQuery,
            onValueChange = { replaceQuery = it },
            placeholder = { Text("Replace with...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = accentColor,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("ocr_replace_input")
          )
          Button(
            onClick = {
              val (replacedText, count) = replaceAllCaseInsensitive(currentDisplayedText, searchQuery, replaceQuery)
              if (count > 0) {
                updateCurrentText(replacedText)
                replaceNotice = "Replaced $count occurrence(s)."
              } else {
                replaceNotice = "No matches to replace."
              }
            },
            enabled = searchQuery.isNotBlank() && matches.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier
              .heightIn(min = 50.dp)
              .testTag("ocr_replace_all_button")
          ) {
            Text("Replace All", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        }
      }

      replaceNotice?.let { notice ->
        Text(
          text = notice,
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.testTag("ocr_replace_notice")
        )
      }

      // Main Text Surface: Editable Multi-line Editor OR Highlighted Selectable Search Preview
      if (studioMode == OcrStudioMode.EDIT) {
        OutlinedTextField(
          value = currentDisplayedText,
          onValueChange = { updateCurrentText(it) },
          label = {
            Text(
              if (selectedPageNumber == null) "Editable Extracted Text" else "Editable Text — Page $selectedPageNumber"
            )
          },
          minLines = 6,
          maxLines = 14,
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ocr_editable_text_field")
        )
      } else {
        val highlighted = remember(currentDisplayedText, searchQuery, activeMatchIndex) {
          buildHighlightedAnnotatedString(
            text = currentDisplayedText.ifBlank { "(No text detected)" },
            query = searchQuery,
            activeMatchIndex = activeMatchIndex
          )
        }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glass.pillFill)
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("ocr_highlighted_preview")
        ) {
          SelectionContainer {
            Text(
              text = highlighted,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      // Quick Actions Row 1: Copy & Share Text
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = { onCopyText(allPagesEditedText) },
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("ocr_copy_text_button")
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy Text", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
          onClick = { onShareText(allPagesEditedText) },
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("ocr_share_text_button")
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Share Text", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
      }

      // Plain Text (.txt) Output Name & Page Header Options
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(glass.pillFill)
          .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Plain Text (.txt) File Settings",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (extractedPages.size > 1) {
            GlassCategoryChip(
              label = if (includePageHeaders) "Page Headers: On" else "Page Headers: Off",
              selected = includePageHeaders,
              onClick = {
                includePageHeaders = !includePageHeaders
                if (extractedPages.isNotEmpty()) {
                  allPagesEditedText = combineEditedPages(extractedPages, editedPageMap, includePageHeaders)
                }
              },
              modifier = Modifier.testTag("ocr_include_headers_toggle")
            )
          }
        }
        OutlinedTextField(
          value = txtFileName,
          onValueChange = { txtFileName = it },
          label = { Text("Output .txt filename") },
          placeholder = { Text("extracted_text.txt") },
          singleLine = true,
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Description,
              contentDescription = null,
              tint = accentColor
            )
          },
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ocr_txt_filename_input")
        )
      }

      // Quick Actions Row 2: Export Edited .TXT & Create Searchable PDF
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            val safeName = PdfOcrService.sanitizeTxtFileName(txtFileName, suggestedTxtFileName)
            txtFileName = safeName
            if (onExportTxtWithName != null) {
              onExportTxtWithName(safeName, allPagesEditedText)
            } else {
              onExportTxt(allPagesEditedText)
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("ocr_export_txt_button")
        ) {
          Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Save .TXT", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }

        Button(
          onClick = { onExportSearchablePdf(allPagesEditedText) },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4F46E5),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1.15f)
            .heightIn(min = 48.dp)
            .testTag("ocr_export_pdf_button")
        ) {
          Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Searchable PDF",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
