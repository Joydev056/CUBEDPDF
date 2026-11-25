package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.local.RecentFileEntity
import com.example.model.PdfTool
import com.example.model.PdfToolIconType
import com.example.model.ToolCategory
import com.example.model.ToolPalette3D
import com.example.model.pdfToolsList
import com.example.pdf.PdfEngine
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.GlassCategoryChip
import com.example.ui.components.GlassSearchBar
import com.example.ui.components.GlassSurface
import com.example.ui.components.GlassToolCard
import com.example.ui.components.Pdf3DIcon
import com.example.ui.theme.LocalGlassColors
import com.example.ui.viewmodel.RecentFilesViewModel

@Composable
fun PdfToolsDashboard(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
  recentFilesViewModel: RecentFilesViewModel = viewModel(
    factory = RecentFilesViewModel.Factory(LocalContext.current)
  ),
  accountDisplayName: String? = null,
  onOpenAccount: (() -> Unit)? = null,
  onMirrorRecentDoc: ((
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    pageCount: Int,
    lastToolId: String,
    lastToolName: String,
    summaryNote: String
  ) -> Unit)? = null
) {
  val context = LocalContext.current
  val recentFiles by recentFilesViewModel.recentFiles.collectAsStateWithLifecycle()

  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }
  var activeTool by remember { mutableStateOf<PdfTool?>(null) }
  var resumeFileUri by remember { mutableStateOf<Uri?>(null) }

  val filteredTools by remember {
    derivedStateOf {
      pdfToolsList.filter { tool ->
        val matchesCategory = selectedCategory == ToolCategory.ALL || tool.category == selectedCategory
        val matchesQuery = searchQuery.isBlank() ||
          tool.name.contains(searchQuery, ignoreCase = true) ||
          tool.description.contains(searchQuery, ignoreCase = true) ||
          tool.shortTag.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
      }
    }
  }

  AnimatedContent(
    targetState = activeTool,
    transitionSpec = {
      if (targetState != null) {
        (slideInHorizontally(tween(240)) { it / 6 } + fadeIn(tween(220))) togetherWith
          fadeOut(tween(160))
      } else {
        fadeIn(tween(220)) togetherWith
          (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(180)))
      }
    },
    label = "workspaceTransition",
    modifier = modifier.fillMaxSize()
  ) { currentTool ->
    if (currentTool != null) {
      ToolWorkspaceScreen(
        tool = currentTool,
        initialFileUri = resumeFileUri,
        onRecordRecentFile = { fileName, fileUriString, mimeType, sizeBytes, pageCount, summaryNote ->
          recentFilesViewModel.recordRecentDocument(
            fileName = fileName,
            fileUriString = fileUriString,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            tool = currentTool,
            summaryNote = summaryNote
          )
          onMirrorRecentDoc?.invoke(
            fileName,
            mimeType,
            sizeBytes,
            pageCount,
            currentTool.id,
            currentTool.name,
            summaryNote
          )
        },
        onBack = {
          activeTool = null
          resumeFileUri = null
        }
      )
    } else {
      AtmosphericGlassBackground {
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
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 152.dp),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxSize()
                .statusBarsPadding()
            ) {
              // Floating Frosted Glass Header
              item(span = { GridItemSpan(maxLineSpan) }) {
                GlassHeaderBar(
                  isDarkTheme = isDarkTheme,
                  onToggleTheme = onToggleTheme,
                  accountDisplayName = accountDisplayName,
                  onOpenAccount = onOpenAccount
                )
              }

              // Hero Glass Status Banner
              item(span = { GridItemSpan(maxLineSpan) }) {
                GlassHeroBanner(
                  totalTools = pdfToolsList.size,
                  onQuickSelect = { tool ->
                    resumeFileUri = null
                    activeTool = tool
                  }
                )
              }

              // Local Room Database 'Recent Files' Section
              item(span = { GridItemSpan(maxLineSpan) }) {
                GlassRecentFilesSection(
                  recentFiles = recentFiles,
                  onResumeFile = { item, overrideTool ->
                    val targetTool = overrideTool
                      ?: pdfToolsList.find { it.id == item.lastToolId }
                      ?: pdfToolsList.first { it.id == "edit" }
                    resumeFileUri = Uri.parse(item.fileUriString)
                    activeTool = targetTool
                  },
                  onDeleteRecentFile = { id ->
                    recentFilesViewModel.deleteRecentFile(id)
                  },
                  onClearAll = {
                    recentFilesViewModel.clearAllRecentFiles()
                  },
                  onCreateQuickSample = {
                    val editTool = pdfToolsList.find { it.id == "edit" } ?: pdfToolsList.first()
                    recentFilesViewModel.createQuickSampleInRecents(context, editTool)
                  }
                )
              }

              // Frosted Glass Search Input
              item(span = { GridItemSpan(maxLineSpan) }) {
                GlassSearchBar(
                  query = searchQuery,
                  onQueryChange = { searchQuery = it }
                )
              }

              // Category Filter Chips
              item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                  items(ToolCategory.entries) { category ->
                    GlassCategoryChip(
                      label = category.label,
                      selected = selectedCategory == category,
                      onClick = { selectedCategory = category },
                      modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                    )
                  }
                }
              }

              // Section Label
              item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = if (selectedCategory == ToolCategory.ALL) "All PDF Utilities" else "${selectedCategory.label} Tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                  Text(
                    text = "${filteredTools.size} tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              // Empty state or Tool Cards
              if (filteredTools.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  GlassEmptyState(
                    onReset = {
                      searchQuery = ""
                      selectedCategory = ToolCategory.ALL
                    }
                  )
                }
              } else {
                items(
                  items = filteredTools,
                  key = { it.id }
                ) { tool ->
                  GlassToolCard(
                    tool = tool,
                    onClick = {
                      resumeFileUri = null
                      activeTool = tool
                    }
                  )
                }
              }

              // Bottom navigation bar safe inset spacer
              item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GlassRecentFilesSection(
  recentFiles: List<RecentFileEntity>,
  onResumeFile: (RecentFileEntity, PdfTool?) -> Unit,
  onDeleteRecentFile: (Long) -> Unit,
  onClearAll: () -> Unit,
  onCreateQuickSample: () -> Unit
) {
  val glass = LocalGlassColors.current
  val quickSwitchTools = remember {
    listOf("edit", "sign", "compress").mapNotNull { id ->
      pdfToolsList.find { it.id == id }
    }
  }

  GlassSurface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("recent_files_section"),
    shape = RoundedCornerShape(24.dp),
    tintGlow = Color(0xFF0EA5E9)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF0EA5E9).copy(alpha = if (glass.isDark) 0.22f else 0.14f))
              .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Recent Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(glass.pillFill)
                  .border(0.8.dp, glass.cardBorderStart.copy(alpha = 0.4f), CircleShape)
                  .padding(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "${recentFiles.size}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Text(
              text = "Stored locally via Room • Tap any document to resume work",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .heightIn(min = 34.dp)
              .clip(CircleShape)
              .background(glass.pillFill)
              .border(1.dp, glass.cardBorderStart.copy(alpha = 0.45f), CircleShape)
              .clickable { onCreateQuickSample() }
              .testTag("recent_files_add_sample_button")
              .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add sample document to Recent Files",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Sample",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
              )
            }
          }

          if (recentFiles.isNotEmpty()) {
            Box(
              modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(CircleShape)
                .background(glass.pillFill)
                .border(1.dp, glass.cardBorderStart.copy(alpha = 0.45f), CircleShape)
                .clickable { onClearAll() }
                .testTag("recent_files_clear_all_button")
                .padding(horizontal = 10.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.DeleteSweep,
                  contentDescription = "Clear all recent files",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Clear",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }
      }

      if (recentFiles.isEmpty()) {
        // Empty State Card
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glass.pillFill)
            .border(1.dp, glass.cardBorderStart.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .testTag("recent_files_empty_state")
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "No recent documents yet",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Documents you create, edit, sign, or compress appear here for instant one-tap resume.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Button(
            onClick = onCreateQuickSample,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF0284C7),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
              .heightIn(min = 44.dp)
              .testTag("recent_files_create_sample_button")
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "New Draft",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      } else {
        // Horizontal Carousel of Recent Files from Room
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
          modifier = Modifier.testTag("recent_files_list")
        ) {
          items(
            items = recentFiles,
            key = { it.id }
          ) { item ->
            val associatedTool = remember(item.lastToolId) {
              pdfToolsList.find { it.id == item.lastToolId }
                ?: pdfToolsList.first { it.id == "edit" }
            }
            GlassRecentFileCard(
              item = item,
              tool = associatedTool,
              quickSwitchTools = quickSwitchTools,
              onResume = { onResumeFile(item, associatedTool) },
              onResumeWithTool = { targetTool -> onResumeFile(item, targetTool) },
              onDelete = { onDeleteRecentFile(item.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlassRecentFileCard(
  item: RecentFileEntity,
  tool: PdfTool,
  quickSwitchTools: List<PdfTool>,
  onResume: () -> Unit,
  onResumeWithTool: (PdfTool) -> Unit,
  onDelete: () -> Unit
) {
  val glass = LocalGlassColors.current

  Box(
    modifier = Modifier
      .width(276.dp)
      .clip(RoundedCornerShape(20.dp))
      .background(glass.pillFill)
      .border(
        width = 1.dp,
        brush = Brush.linearGradient(
          colors = listOf(
            glass.cardBorderStart,
            tool.palette.glow.copy(alpha = 0.38f)
          )
        ),
        shape = RoundedCornerShape(20.dp)
      )
      .clickable { onResume() }
      .testTag("recent_file_card_${item.id}")
      .padding(14.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Pdf3DIcon(
            iconType = tool.iconType,
            palette = tool.palette,
            size = 42.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.fileName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${item.pageCount} pg • ${PdfEngine.formatFileSize(item.sizeBytes)} • ${formatRelativeTime(item.updatedAt)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(32.dp)
            .testTag("delete_recent_file_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Remove ${item.fileName} from Recent Files",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Text(
        text = item.summaryNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      // Primary Full-Width Resume Button + Dedicated Quick Switch Row so no letters ever clip
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onResume,
          colors = ButtonDefaults.buttonColors(
            containerColor = tool.palette.base,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .testTag("resume_recent_file_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "Resume ${tool.name.removeSuffix(" PDF")}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          quickSwitchTools.filter { it.id != tool.id }.take(2).forEach { altTool ->
            Box(
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(altTool.palette.base.copy(alpha = if (glass.isDark) 0.24f else 0.14f))
                .border(0.8.dp, altTool.palette.glow.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                .clickable { onResumeWithTool(altTool) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = altTool.name.removeSuffix(" PDF"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (glass.isDark) Color.White else altTool.palette.depth,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }
  }
}

private fun formatRelativeTime(timestampMs: Long): String {
  val diffSeconds = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
  return when {
    diffSeconds < 60L -> "Just now"
    diffSeconds < 3600L -> "${diffSeconds / 60L}m ago"
    diffSeconds < 86400L -> "${diffSeconds / 3600L}h ago"
    else -> "${diffSeconds / 86400L}d ago"
  }
}

@Composable
private fun GlassHeaderBar(
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  accountDisplayName: String? = null,
  onOpenAccount: (() -> Unit)? = null
) {
  val glass = LocalGlassColors.current
  val initials = remember(accountDisplayName) {
    accountDisplayName.orEmpty()
      .trim()
      .split(" ")
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercase() }
      .ifBlank { "U" }
  }

  GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_cubed_pdf_logo),
          contentDescription = "CubedPDF Logo",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, glass.cardBorderStart.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .testTag("cubed_pdf_logo")
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "CubedPDF",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = if (!accountDisplayName.isNullOrBlank()) "Welcome, $accountDisplayName" else "3D Glass Document Suite",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (onOpenAccount != null) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF38BDF8), Color(0xFF6366F1))
                )
              )
              .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape)
              .clickable { onOpenAccount() }
              .testTag("dashboard_account_button"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = initials,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
          }
        }

        // Frosted Theme Mode Toggle
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(glass.pillFill)
            .border(1.dp, glass.cardBorderStart.copy(alpha = 0.45f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.testTag("theme_toggle_button")
          ) {
            Icon(
              imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
              contentDescription = if (isDarkTheme) "Switch to light glass theme" else "Switch to dark glass theme",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlassHeroBanner(
  totalTools: Int,
  onQuickSelect: (PdfTool) -> Unit
) {
  val glass = LocalGlassColors.current
  val primaryTools = remember {
    listOf("merge", "split", "compress").mapNotNull { id ->
      pdfToolsList.find { it.id == id }
    }
  }
  val extractTextTool = remember {
    pdfToolsList.find { it.id == "ocr" }
  }

  GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    tintGlow = Color(0xFF6366F1)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF10B981).copy(alpha = if (glass.isDark) 0.22f else 0.14f))
            .border(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint = Color(0xFF10B981),
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "Encrypted & Auto-Delete",
              style = MaterialTheme.typography.labelSmall,
              color = if (glass.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(glass.pillFill)
            .border(0.8.dp, glass.cardBorderStart.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(
            text = "$totalTools Free Tools",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Primary PDF Actions",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = "Instant Merge, Split, and Compress with hardware-accelerated local & cloud workflows.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Primary Tools 3D Showcase Row (Merge, Split, Compress)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        primaryTools.forEach { tool ->
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(18.dp))
              .background(glass.pillFill)
              .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                  colors = listOf(
                    glass.cardBorderStart,
                    tool.palette.glow.copy(alpha = 0.32f)
                  )
                ),
                shape = RoundedCornerShape(18.dp)
              )
              .clickable { onQuickSelect(tool) }
              .testTag("primary_spotlight_${tool.id}")
              .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Pdf3DIcon(
                iconType = tool.iconType,
                palette = tool.palette,
                size = 56.dp
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = tool.name.removeSuffix(" PDF"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      if (extractTextTool != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glass.pillFill)
            .border(
              width = 1.dp,
              brush = Brush.linearGradient(
                colors = listOf(
                  extractTextTool.palette.glow.copy(alpha = 0.55f),
                  glass.cardBorderStart
                )
              ),
              shape = RoundedCornerShape(18.dp)
            )
            .clickable { onQuickSelect(extractTextTool) }
            .testTag("primary_spotlight_ocr")
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Pdf3DIcon(
            iconType = extractTextTool.iconType,
            palette = extractTextTool.palette,
            size = 44.dp
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Extract Text (ML Kit OCR)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Convert scanned PDFs or images into searchable, editable text on-device.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlassEmptyState(onReset: () -> Unit) {
  val searchPalette = remember {
    ToolPalette3D(
      highlight = Color(0xFF38BDF8),
      base = Color(0xFF0284C7),
      depth = Color(0xFF0C4A6E),
      glow = Color(0xFF0EA5E9)
    )
  }
  GlassSurface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    shape = RoundedCornerShape(24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Pdf3DIcon(
        iconType = PdfToolIconType.COMPARE,
        palette = searchPalette,
        size = 72.dp
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "No matching PDF tools found",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Try a different keyword or reset the category filter.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(16.dp))
      GlassCategoryChip(
        label = "Show All 24 Tools",
        selected = true,
        onClick = onReset
      )
    }
  }
}
