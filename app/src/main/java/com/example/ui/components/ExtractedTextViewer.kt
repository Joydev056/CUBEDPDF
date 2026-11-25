package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdf.ExtractedPageText
import com.example.ui.theme.LocalGlassColors
import kotlinx.coroutines.delay

@Composable
fun ExtractedTextViewerCard(
  extractedText: String,
  extractedPages: List<ExtractedPageText>,
  accentColor: Color,
  onCopiedMessage: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val clipboardManager = LocalClipboardManager.current

  // Build normalized pages list even when called with raw extractedText only
  val normalizedPages = remember(extractedText, extractedPages) {
    if (extractedPages.isNotEmpty()) {
      extractedPages
    } else {
      val clean = extractedText.trim()
      val words = if (clean.isBlank()) 0 else clean.split(Regex("\\s+")).size
      listOf(
        ExtractedPageText(
          pageIndex = 0,
          pageNumber = 1,
          text = clean,
          wordCount = words,
          characterCount = clean.length,
          blockCount = if (clean.isBlank()) 0 else 1
        )
      )
    }
  }

  var selectedPageNumber by remember(normalizedPages) { mutableStateOf<Int?>(null) } // null = All Pages
  var showSearchBar by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showFullScreenReader by remember { mutableStateOf(false) }
  var justCopiedAll by remember { mutableStateOf(false) }

  LaunchedEffect(justCopiedAll) {
    if (justCopiedAll) {
      delay(1800)
      justCopiedAll = false
    }
  }

  val totalWords = remember(normalizedPages) { normalizedPages.sumOf { it.wordCount } }
  val totalChars = remember(normalizedPages) { normalizedPages.sumOf { it.characterCount } }

  val visiblePages by remember(normalizedPages, selectedPageNumber, searchQuery) {
    derivedStateOf {
      normalizedPages.filter { page ->
        val matchesPage = selectedPageNumber == null || page.pageNumber == selectedPageNumber
        val matchesQuery = searchQuery.isBlank() || page.text.contains(searchQuery, ignoreCase = true)
        matchesPage && matchesQuery
      }
    }
  }

  val totalMatches by remember(visiblePages, searchQuery) {
    derivedStateOf {
      if (searchQuery.isBlank()) 0
      else visiblePages.sumOf { countOccurrencesIgnoreCase(it.text, searchQuery) }
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(glass.pillFill)
      .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
      .padding(12.dp)
      .testTag("extracted_text_viewer"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Header & ML Kit Badge + Actions
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Extracted Text",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        Text(
          text = "$totalWords words • $totalChars chars • ${normalizedPages.size} page(s)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            showSearchBar = !showSearchBar
            if (!showSearchBar) searchQuery = ""
          },
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (showSearchBar) accentColor.copy(alpha = 0.2f) else Color.Transparent)
            .testTag("extracted_text_search_toggle")
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search extracted text",
            tint = if (showSearchBar) accentColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp)
          )
        }

        IconButton(
          onClick = {
            clipboardManager.setText(AnnotatedString(extractedText))
            justCopiedAll = true
            onCopiedMessage("Copied extracted text to clipboard")
          },
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (justCopiedAll) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Transparent)
            .testTag("extracted_text_copy_all")
        ) {
          Icon(
            imageVector = if (justCopiedAll) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = "Copy all extracted text",
            tint = if (justCopiedAll) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
          )
        }

        IconButton(
          onClick = { showFullScreenReader = true },
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .testTag("extracted_text_expand_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.Fullscreen,
            contentDescription = "Expand full-screen text reader",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // Collapsible Search Input with live match counter
    AnimatedVisibility(visible = showSearchBar) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search extracted text...") },
          singleLine = true,
          leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
              }
            }
          },
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = glass.cardBorderStart
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("extracted_text_search_input")
        )
        if (searchQuery.isNotBlank()) {
          Text(
            text = "$totalMatches match(es) across ${visiblePages.size} page(s)",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Page Filter Pills (when multiple pages exist)
    if (normalizedPages.size > 1) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        item {
          GlassCategoryChip(
            label = "All Pages (${normalizedPages.size})",
            selected = selectedPageNumber == null,
            onClick = { selectedPageNumber = null },
            modifier = Modifier.testTag("extracted_page_chip_all")
          )
        }
        items(normalizedPages, key = { it.pageNumber }) { page ->
          GlassCategoryChip(
            label = "Page ${page.pageNumber}",
            selected = selectedPageNumber == page.pageNumber,
            onClick = { selectedPageNumber = page.pageNumber },
            modifier = Modifier.testTag("extracted_page_chip_${page.pageNumber}")
          )
        }
      }
    }

    // Dedicated Scrollable Text View (never truncated, supports text selection)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 160.dp, max = 380.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(
          if (glass.isDark) Color(0xFF0B1120).copy(alpha = 0.65f)
          else Color.White.copy(alpha = 0.85f)
        )
        .border(1.dp, glass.cardBorderStart.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
        .testTag("extracted_text_scroll_view")
    ) {
      if (visiblePages.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No matching text found for \"$searchQuery\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        SelectionContainer {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(scrollState)
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            visiblePages.forEach { page ->
              ExtractedPageBlock(
                page = page,
                searchQuery = searchQuery,
                accentColor = accentColor,
                showPageHeader = normalizedPages.size > 1,
                onCopyPage = {
                  clipboardManager.setText(AnnotatedString(page.text))
                  onCopiedMessage("Copied Page ${page.pageNumber} text")
                }
              )
            }
          }
        }
      }
    }

    // Subtle Scroll / Reader Hint Footer
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Long-press to select text • Scroll to read all",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = "Full Reader",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable { showFullScreenReader = true }
          .padding(horizontal = 6.dp, vertical = 4.dp)
      )
    }
  }

  if (showFullScreenReader) {
    FullScreenExtractedTextDialog(
      pages = normalizedPages,
      initialQuery = searchQuery,
      accentColor = accentColor,
      onCopyAll = {
        clipboardManager.setText(AnnotatedString(extractedText))
        onCopiedMessage("Copied all extracted text")
      },
      onCopyPage = { page ->
        clipboardManager.setText(AnnotatedString(page.text))
        onCopiedMessage("Copied Page ${page.pageNumber} text")
      },
      onDismiss = { showFullScreenReader = false }
    )
  }
}

@Composable
private fun ExtractedPageBlock(
  page: ExtractedPageText,
  searchQuery: String,
  accentColor: Color,
  showPageHeader: Boolean,
  onCopyPage: () -> Unit
) {
  val glass = LocalGlassColors.current
  val highlightBg = accentColor.copy(alpha = if (glass.isDark) 0.42f else 0.25f)
  val highlightTextColor = if (glass.isDark) Color.White else Color(0xFF0F172A)

  val displayText = remember(page.text, searchQuery, highlightBg, highlightTextColor) {
    val content = page.text.ifBlank { "(No text detected on this page)" }
    buildHighlightedAnnotatedString(content, searchQuery, highlightBg, highlightTextColor)
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (showPageHeader) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(accentColor.copy(alpha = if (glass.isDark) 0.18f else 0.1f))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Page ${page.pageNumber}  •  ${page.wordCount} words",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = accentColor
        )
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
            .clickable(onClick = onCopyPage)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("copy_page_${page.pageNumber}")
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.ContentCopy,
              contentDescription = "Copy Page ${page.pageNumber}",
              tint = Color(0xFF0F172A),
              modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Copy",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A),
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }
    }

    Text(
      text = displayText,
      style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun FullScreenExtractedTextDialog(
  pages: List<ExtractedPageText>,
  initialQuery: String,
  accentColor: Color,
  onCopyAll: () -> Unit,
  onCopyPage: (ExtractedPageText) -> Unit,
  onDismiss: () -> Unit
) {
  val glass = LocalGlassColors.current
  var query by remember { mutableStateOf(initialQuery) }
  var selectedPage by remember { mutableStateOf<Int?>(null) }

  val filteredPages by remember(pages, query, selectedPage) {
    derivedStateOf {
      pages.filter { p ->
        val matchesPage = selectedPage == null || p.pageNumber == selectedPage
        val matchesQuery = query.isBlank() || p.text.contains(query, ignoreCase = true)
        matchesPage && matchesQuery
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    AtmosphericGlassBackground(
      modifier = Modifier
        .fillMaxSize()
        .testTag("fullscreen_extracted_text_dialog")
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Top Reader Bar
          GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accentColor)
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(
                    text = "ML Kit Extracted Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "${pages.sumOf { it.wordCount }} words across ${pages.size} page(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  IconButton(
                    onClick = onCopyAll,
                    modifier = Modifier.size(44.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.ContentCopy,
                      contentDescription = "Copy all text",
                      tint = Color(0xFF0F172A)
                    )
                  }
                }
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                      .size(44.dp)
                      .testTag("close_fullscreen_reader_button")
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Close reader",
                      tint = Color(0xFF0F172A)
                    )
                  }
                }
              }
            }
          }

          // Search Box
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search across extracted pages...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
              }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = accentColor,
              unfocusedBorderColor = glass.cardBorderStart
            ),
            modifier = Modifier.fillMaxWidth()
          )

          // Page Chips (white popup pills for crisp contrast)
          if (pages.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                val isAll = selectedPage == null
                Box(
                  modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(
                      width = if (isAll) 2.dp else 1.dp,
                      color = if (isAll) accentColor else Color(0xFFCBD5E1),
                      shape = RoundedCornerShape(50)
                    )
                    .clickable { selectedPage = null }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "All Pages",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAll) accentColor else Color(0xFF0F172A),
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
              items(pages, key = { it.pageNumber }) { page ->
                val isSel = selectedPage == page.pageNumber
                Box(
                  modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(
                      width = if (isSel) 2.dp else 1.dp,
                      color = if (isSel) accentColor else Color(0xFFCBD5E1),
                      shape = RoundedCornerShape(50)
                    )
                    .clickable { selectedPage = page.pageNumber }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) accentColor else Color(0xFF0F172A),
                    maxLines = 1,
                    softWrap = false
                  )
                }
              }
            }
          }

          // Scrollable LazyColumn of Extracted Pages
          SelectionContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_extracted_text_list"),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              contentPadding = PaddingValues(bottom = 16.dp)
            ) {
              items(filteredPages, key = { it.pageNumber }) { page ->
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                  Box(modifier = Modifier.padding(14.dp)) {
                    ExtractedPageBlock(
                      page = page,
                      searchQuery = query,
                      accentColor = accentColor,
                      showPageHeader = true,
                      onCopyPage = { onCopyPage(page) }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun countOccurrencesIgnoreCase(text: String, query: String): Int {
  if (query.isBlank() || text.isBlank()) return 0
  var count = 0
  var index = 0
  while (true) {
    val found = text.indexOf(query, startIndex = index, ignoreCase = true)
    if (found < 0) break
    count++
    index = found + query.length
  }
  return count
}

private fun buildHighlightedAnnotatedString(
  text: String,
  query: String,
  highlightBg: Color,
  highlightTextColor: Color
): AnnotatedString {
  if (query.isBlank()) return AnnotatedString(text)
  return buildAnnotatedString {
    append(text)
    var index = 0
    while (true) {
      val found = text.indexOf(query, startIndex = index, ignoreCase = true)
      if (found < 0) break
      addStyle(
        style = SpanStyle(
          background = highlightBg,
          color = highlightTextColor,
          fontWeight = FontWeight.Bold
        ),
        start = found,
        end = found + query.length
      )
      index = found + query.length
    }
  }
}
