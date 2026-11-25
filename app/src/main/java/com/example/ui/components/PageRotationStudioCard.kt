package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pdf.PdfRotationSupport
import com.example.pdf.RotationAngle
import com.example.ui.theme.LocalGlassColors

/**
 * Interactive PDF Page Rotation Studio allowing users to rotate specific pages (or batch subsets
 * like All / Odd / Even / custom ranges such as "1, 3-5") by 90°, 180°, or 270° with live
 * animated per-page thumbnail rotation previews.
 */
@Composable
fun PageRotationStudioCard(
  thumbnails: List<Bitmap>,
  totalPageCount: Int,
  selectedAngle: Int,
  onSelectAngle: (Int) -> Unit,
  perPageRotations: Map<Int, Int>,
  onUpdatePerPageRotations: (Map<Int, Int>) -> Unit,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val effectivePages = maxOf(totalPageCount, thumbnails.size)
  var pageRangeInput by remember(effectivePages) { mutableStateOf("") }
  var rangeFeedback by remember { mutableStateOf<String?>(null) }

  val activeRotations = remember(perPageRotations, effectivePages) {
    perPageRotations.filter { (idx, deg) ->
      idx in 0 until effectivePages && PdfRotationSupport.normalizeDegrees(deg) != 0
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("page_rotation_studio_card"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Angle Selector + Specific Page Range Targeting + Quick Presets
    GlassSurface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      tintGlow = accentColor
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = if (glass.isDark) 0.24f else 0.14f))
              .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.Rotate90DegreesCw,
              contentDescription = null,
              tint = if (glass.isDark) Color.White else accentColor,
              modifier = Modifier.size(20.dp)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Rotate Specific PDF Pages",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Choose 90°, 180°, or 270° and apply to individual pages or custom ranges",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Angle Selector Chips (90°, 180°, 270°)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          RotationAnglePill(
            angle = RotationAngle.CW_90,
            icon = Icons.Rounded.Rotate90DegreesCw,
            selected = selectedAngle == 90,
            accentColor = accentColor,
            onClick = { onSelectAngle(90) },
            modifier = Modifier
              .weight(1f)
              .testTag("rotate_angle_90")
          )
          RotationAnglePill(
            angle = RotationAngle.HALF_180,
            icon = Icons.Rounded.Sync,
            selected = selectedAngle == 180,
            accentColor = accentColor,
            onClick = { onSelectAngle(180) },
            modifier = Modifier
              .weight(1f)
              .testTag("rotate_angle_180")
          )
          RotationAnglePill(
            angle = RotationAngle.CCW_90,
            icon = Icons.Rounded.Rotate90DegreesCcw,
            selected = selectedAngle == 270,
            accentColor = accentColor,
            onClick = { onSelectAngle(270) },
            modifier = Modifier
              .weight(1f)
              .testTag("rotate_angle_270")
          )
        }

        // Specific Page Range Input (works even for multi-page documents)
        if (effectivePages > 0) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = pageRangeInput,
              onValueChange = {
                pageRangeInput = it
                rangeFeedback = null
              },
              label = { Text("Specific pages (e.g. 1, 3-$effectivePages)") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("rotate_page_range_input")
            )
            Button(
              onClick = {
                val parsed = PdfRotationSupport.parseSpecificPages(pageRangeInput, effectivePages)
                if (parsed.isEmpty()) {
                  rangeFeedback = "Enter page numbers between 1 and $effectivePages (e.g. 1, 2-3)."
                } else {
                  val updated = perPageRotations.toMutableMap()
                  parsed.forEach { idx -> updated[idx] = selectedAngle }
                  onUpdatePerPageRotations(updated)
                  rangeFeedback = "Set page(s) ${PdfRotationSupport.formatPageSelection(parsed)} to ${selectedAngle}°."
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier
                .heightIn(min = 54.dp)
                .testTag("rotate_apply_range_button")
            ) {
              Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Apply", fontWeight = FontWeight.Bold)
            }
          }

          if (rangeFeedback != null) {
            Text(
              text = rangeFeedback!!,
              style = MaterialTheme.typography.bodySmall,
              color = accentColor,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.testTag("rotate_range_feedback")
            )
          }

          // Batch Quick Presets
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
          ) {
            item {
              GlassCategoryChip(
                label = "All Pages (${selectedAngle}°)",
                selected = activeRotations.size == effectivePages && activeRotations.values.all { it == selectedAngle },
                onClick = {
                  val updated = (0 until effectivePages).associateWith { selectedAngle }
                  onUpdatePerPageRotations(updated)
                  pageRangeInput = PdfRotationSupport.formatPageSelection((0 until effectivePages).toSet())
                  rangeFeedback = null
                },
                modifier = Modifier.testTag("rotate_apply_all")
              )
            }
            if (effectivePages > 1) {
              item {
                GlassCategoryChip(
                  label = "Odd Pages",
                  selected = false,
                  onClick = {
                    val oddIndices = (0 until effectivePages).filter { it % 2 == 0 }
                    val updated = perPageRotations.toMutableMap()
                    oddIndices.forEach { updated[it] = selectedAngle }
                    onUpdatePerPageRotations(updated)
                    pageRangeInput = PdfRotationSupport.formatPageSelection(oddIndices.toSet())
                    rangeFeedback = null
                  },
                  modifier = Modifier.testTag("rotate_apply_odd")
                )
              }
              item {
                GlassCategoryChip(
                  label = "Even Pages",
                  selected = false,
                  onClick = {
                    val evenIndices = (0 until effectivePages).filter { it % 2 == 1 }
                    val updated = perPageRotations.toMutableMap()
                    evenIndices.forEach { updated[it] = selectedAngle }
                    onUpdatePerPageRotations(updated)
                    pageRangeInput = PdfRotationSupport.formatPageSelection(evenIndices.toSet())
                    rangeFeedback = null
                  },
                  modifier = Modifier.testTag("rotate_apply_even")
                )
              }
            }
            if (activeRotations.isNotEmpty()) {
              item {
                GlassCategoryChip(
                  label = "Reset All",
                  selected = false,
                  onClick = {
                    onUpdatePerPageRotations(emptyMap())
                    pageRangeInput = ""
                    rangeFeedback = null
                  },
                  modifier = Modifier.testTag("rotate_reset_all")
                )
              }
            }
          }
        }

        // Pending Rotation Summary Pill
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glass.pillFill)
            .padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = if (activeRotations.isEmpty()) {
              if (effectivePages > 0) {
                "No individual pages marked yet — tapping Rotate will rotate all $effectivePages page(s) by ${selectedAngle}°."
              } else {
                "Select a PDF file above to rotate specific pages by 90°, 180°, or 270°."
              }
            } else {
              PdfRotationSupport.summarizeRotations(activeRotations, effectivePages)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("rotate_summary_text")
          )
        }
      }
    }

    // 2. Live Per-Page Visual Rotation Cards (Animated Thumbnail Rotation + Individual Controls)
    if (effectivePages > 0) {
      GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "Per-Page Live Preview ($effectivePages page${if (effectivePages == 1) "" else "s"})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Tap any page card to apply ${selectedAngle}° (or tap arrows for +/- 90° precision)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
          ) {
            items((0 until effectivePages).toList(), key = { it }) { pageIdx ->
              val currentDeg = PdfRotationSupport.normalizeDegrees(perPageRotations[pageIdx] ?: 0)
              val thumb = thumbnails.getOrNull(pageIdx)
              PerPageRotationTile(
                pageIndex = pageIdx,
                thumbnail = thumb,
                rotationDegrees = currentDeg,
                selectedBatchAngle = selectedAngle,
                accentColor = accentColor,
                onTapCard = {
                  val nextDeg = if (currentDeg == 0) {
                    selectedAngle
                  } else if (currentDeg == selectedAngle) {
                    0
                  } else {
                    selectedAngle
                  }
                  val updated = perPageRotations.toMutableMap()
                  if (nextDeg == 0) updated.remove(pageIdx) else updated[pageIdx] = nextDeg
                  onUpdatePerPageRotations(updated)
                },
                onRotateCcw = {
                  val nextDeg = PdfRotationSupport.nextRotation(currentDeg, -90)
                  val updated = perPageRotations.toMutableMap()
                  if (nextDeg == 0) updated.remove(pageIdx) else updated[pageIdx] = nextDeg
                  onUpdatePerPageRotations(updated)
                },
                onRotateCw = {
                  val nextDeg = PdfRotationSupport.nextRotation(currentDeg, 90)
                  val updated = perPageRotations.toMutableMap()
                  if (nextDeg == 0) updated.remove(pageIdx) else updated[pageIdx] = nextDeg
                  onUpdatePerPageRotations(updated)
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RotationAnglePill(
  angle: RotationAngle,
  icon: ImageVector,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val shape = RoundedCornerShape(14.dp)
  val bg = if (selected) {
    accentColor.copy(alpha = if (glass.isDark) 0.28f else 0.16f)
  } else {
    glass.pillFill
  }
  val border = if (selected) {
    accentColor.copy(alpha = 0.8f)
  } else {
    glass.cardBorderStart.copy(alpha = 0.4f)
  }
  val fg = if (selected) {
    if (glass.isDark) Color.White else accentColor
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  Column(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .heightIn(min = 56.dp)
      .clip(shape)
      .background(bg)
      .border(1.dp, border, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
    Spacer(Modifier.height(2.dp))
    Text(
      text = angle.shortLabel,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = fg,
      maxLines = 1,
      softWrap = false
    )
  }
}

@Composable
private fun PerPageRotationTile(
  pageIndex: Int,
  thumbnail: Bitmap?,
  rotationDegrees: Int,
  selectedBatchAngle: Int,
  accentColor: Color,
  onTapCard: () -> Unit,
  onRotateCcw: () -> Unit,
  onRotateCw: () -> Unit
) {
  val glass = LocalGlassColors.current
  val isRotated = rotationDegrees != 0
  val animatedAngle by animateFloatAsState(
    targetValue = rotationDegrees.toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "pageRotation_$pageIndex"
  )

  val borderColor = if (isRotated) accentColor else glass.cardBorderStart.copy(alpha = 0.45f)
  val shape = RoundedCornerShape(16.dp)

  Column(
    modifier = Modifier
      .width(126.dp)
      .clip(shape)
      .background(if (isRotated) accentColor.copy(alpha = if (glass.isDark) 0.16f else 0.10f) else glass.pillFill)
      .border(if (isRotated) 2.dp else 1.dp, borderColor, shape)
      .clickable(onClick = onTapCard)
      .padding(8.dp)
      .testTag("rotate_page_card_$pageIndex"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Top row: Page number + current rotation badge
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "P${pageIndex + 1}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(if (isRotated) accentColor else MaterialTheme.colorScheme.surfaceVariant)
          .padding(horizontal = 7.dp, vertical = 2.dp)
      ) {
        Text(
          text = "${rotationDegrees}°",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = if (isRotated) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("rotate_page_badge_$pageIndex")
        )
      }
    }

    // Live Rotated Page Thumbnail Box
    Box(
      modifier = Modifier
        .size(96.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color.White.copy(alpha = 0.92f)),
      contentAlignment = Alignment.Center
    ) {
      if (thumbnail != null) {
        val imageBmp = remember(thumbnail) { thumbnail.asImageBitmap() }
        Image(
          bitmap = imageBmp,
          contentDescription = "Page ${pageIndex + 1} preview rotated ${rotationDegrees} degrees",
          contentScale = ContentScale.Fit,
          modifier = Modifier
            .size(84.dp)
            .graphicsLayer { rotationZ = animatedAngle }
        )
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.graphicsLayer { rotationZ = animatedAngle }
        ) {
          Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = "Page ${pageIndex + 1} rotated ${rotationDegrees} degrees",
            tint = accentColor,
            modifier = Modifier.size(30.dp)
          )
          Text(
            text = "Page ${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF334155),
            textAlign = TextAlign.Center
          )
        }
      }
    }

    // Individual -90° (CCW) and +90° (CW) step buttons (48dp touch targets)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onRotateCcw,
        modifier = Modifier
          .size(48.dp)
          .testTag("rotate_page_ccw_$pageIndex")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.RotateLeft,
          contentDescription = "Rotate page ${pageIndex + 1} counter-clockwise 90 degrees",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp)
        )
      }
      IconButton(
        onClick = onRotateCw,
        modifier = Modifier
          .size(48.dp)
          .testTag("rotate_page_cw_$pageIndex")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.RotateRight,
          contentDescription = "Rotate page ${pageIndex + 1} clockwise 90 degrees",
          tint = accentColor,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}
