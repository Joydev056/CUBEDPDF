package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.LocalGlassColors
import kotlin.math.hypot

/**
 * Pure, bounds-safe page list reordering helper used by both gesture drag-and-drop
 * and accessibility button fallbacks.
 */
fun reorderPageList(order: List<Int>, fromIndex: Int, toIndex: Int): List<Int> {
  if (order.isEmpty()) return order
  if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
    return order
  }
  val mutable = order.toMutableList()
  val moved = mutable.removeAt(fromIndex)
  mutable.add(toIndex, moved)
  return mutable
}

/**
 * Reverses the current page order.
 */
fun reversePageOrder(order: List<Int>): List<Int> = order.reversed()

/**
 * Resets the page order back to 0..pageCount-1.
 */
fun resetPageOrder(pageCount: Int): List<Int> = (0 until pageCount.coerceAtLeast(0)).toList()

/**
 * Determines which grid slot the dragged card's center is hovering over.
 * Uses direct containment first, falling back to nearest slot only when within generous proximity.
 */
internal fun findDropTargetIndex(
  draggedCenter: Offset,
  slotBounds: Map<Int, Rect>,
  currentIndex: Int
): Int {
  // Prefer direct hit inside any slot's bounding box
  slotBounds.entries.firstOrNull { (_, rect) -> rect.contains(draggedCenter) }?.let {
    return it.key
  }
  // Fallback: nearest slot within reasonable distance of its center
  var bestIndex = currentIndex
  var bestDistance = Float.MAX_VALUE
  slotBounds.forEach { (idx, rect) ->
    val dist = hypot(draggedCenter.x - rect.center.x, draggedCenter.y - rect.center.y)
    val threshold = maxOf(rect.width, rect.height) * 0.72f
    if (dist < threshold && dist < bestDistance) {
      bestDistance = dist
      bestIndex = idx
    }
  }
  return bestIndex
}

/**
 * Responsive, tactile Drag-and-Drop UI component for reordering pages in an uploaded PDF document.
 *
 * Features:
 * - Immediate drag from the grip handle OR long-press drag anywhere on a page card
 * - Real-time 2D grid reordering across rows & columns (2 columns on phones, 3–4 on tablets)
 * - Spring lift animation, elevation glow, haptic feedback, and live drop-zone feedback
 * - Quick Reverse & Reset controls plus accessible left/right shift buttons
 * - Live output sequence summary bar
 */
@Composable
fun DragDropPageReorderGrid(
  thumbnails: List<Bitmap>,
  pageOrder: List<Int>,
  totalPageCount: Int,
  onOrderChange: (List<Int>) -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val haptics = LocalHapticFeedback.current

  val effectiveOrder = remember(pageOrder, totalPageCount, thumbnails.size) {
    val expectedCount = maxOf(totalPageCount, thumbnails.size)
    if (pageOrder.isNotEmpty()) pageOrder else resetPageOrder(expectedCount)
  }
  val defaultOrder = remember(effectiveOrder.size) { resetPageOrder(effectiveOrder.size) }
  val isModified = effectiveOrder != defaultOrder

  // Latest state references so pointerInput closures always see up-to-date order and callbacks
  val latestOrder by rememberUpdatedState(effectiveOrder)
  val latestOnOrderChange by rememberUpdatedState(onOrderChange)

  // Drag state tracked by stable page index (original 0-based page number)
  var draggedPageId by remember { mutableStateOf<Int?>(null) }
  var dragOffset by remember { mutableStateOf(Offset.Zero) }
  var hoverSlotIndex by remember { mutableIntStateOf(-1) }

  // Container-relative bounds for each slot position (0..N-1)
  var gridOriginInRoot by remember { mutableStateOf(Offset.Zero) }
  val slotBounds = remember { mutableStateMapOf<Int, Rect>() }

  // Reset drag state if document changes drastically
  LaunchedEffect(effectiveOrder.size) {
    slotBounds.keys.filter { it >= effectiveOrder.size }.forEach { slotBounds.remove(it) }
    if (draggedPageId != null && !effectiveOrder.contains(draggedPageId)) {
      draggedPageId = null
      dragOffset = Offset.Zero
      hoverSlotIndex = -1
    }
  }

  fun startDragging(pageId: Int) {
    val currentPos = latestOrder.indexOf(pageId)
    if (currentPos < 0 || latestOrder.size < 2) return
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    draggedPageId = pageId
    dragOffset = Offset.Zero
    hoverSlotIndex = currentPos
  }

  fun updateDrag(delta: Offset) {
    val activePage = draggedPageId ?: return
    val currentOrder = latestOrder
    val currentIdx = currentOrder.indexOf(activePage)
    if (currentIdx < 0) return

    val updatedOffset = dragOffset + delta
    val currentSlotRect = slotBounds[currentIdx]
    if (currentSlotRect == null) {
      dragOffset = updatedOffset
      return
    }

    val draggedCenter = currentSlotRect.center + updatedOffset
    val targetIdx = findDropTargetIndex(draggedCenter, slotBounds, currentIdx)
    hoverSlotIndex = targetIdx

    if (targetIdx != currentIdx && targetIdx in currentOrder.indices) {
      val targetRect = slotBounds[targetIdx]
      val reordered = reorderPageList(currentOrder, currentIdx, targetIdx)
      latestOnOrderChange(reordered)
      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      // Compensate translation offset by slot delta so the card stays anchored under the finger
      dragOffset = if (targetRect != null) {
        updatedOffset - (targetRect.topLeft - currentSlotRect.topLeft)
      } else {
        Offset.Zero
      }
    } else {
      dragOffset = updatedOffset
    }
  }

  fun finishDragging() {
    draggedPageId = null
    dragOffset = Offset.Zero
    hoverSlotIndex = -1
  }

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("drag_drop_page_reorder_container"),
    shape = RoundedCornerShape(22.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header Row: Title, Drag hint, and Modified badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.SwapVert,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "Drag & Drop Pages",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(visible = isModified, enter = fadeIn(), exit = fadeOut()) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(50))
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                  .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(50))
                  .padding(horizontal = 8.dp, vertical = 2.dp)
                  .testTag("reorder_modified_badge")
              ) {
                Text(
                  text = "Modified",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
          Spacer(Modifier.height(2.dp))
          Text(
            text = if (effectiveOrder.size >= 2) {
              "Drag the grip handle or long-press any page to move it."
            } else {
              "Upload a multi-page PDF to drag and reorder its pages."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Quick Actions Row (Reverse + Reset)
      if (effectiveOrder.size >= 2) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onOrderChange(reversePageOrder(effectiveOrder))
            },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, glass.cardBorderStart.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = glass.pillFill,
              contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .testTag("reorder_reverse_button")
          ) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Reverse Order",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }

          OutlinedButton(
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onOrderChange(resetPageOrder(effectiveOrder.size))
            },
            enabled = isModified,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
              1.dp,
              if (isModified) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
              else glass.cardBorderStart.copy(alpha = 0.35f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = glass.pillFill,
              contentColor = MaterialTheme.colorScheme.primary,
              disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .testTag("reorder_reset_button")
          ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Reset Order",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // Body: Empty state OR Adaptive Multi-Column Drag-and-Drop Grid
      if (effectiveOrder.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glass.pillFill)
            .border(1.dp, glass.cardBorderStart.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 26.dp, horizontal = 16.dp)
            .testTag("reorder_empty_state"),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
              modifier = Modifier.size(34.dp)
            )
            Text(
              text = "No PDF Pages Loaded",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Pick a PDF file or tap Sample PDF above to inspect and drag-reorder its pages.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
              gridOriginInRoot = coords.positionInRoot()
            }
            .testTag("drag_drop_pages_grid")
        ) {
          val columns = when {
            maxWidth >= 680.dp -> 4
            maxWidth >= 460.dp -> 3
            else -> 2
          }
          val rows = effectiveOrder.chunked(columns)

          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            rows.forEachIndexed { rowIdx, rowPages ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                rowPages.forEachIndexed { colIdx, pageId ->
                  val slotIndex = rowIdx * columns + colIdx
                  val isDragging = draggedPageId == pageId
                  val isDropTarget = draggedPageId != null && !isDragging && hoverSlotIndex == slotIndex

                  DraggablePdfPageCard(
                    slotIndex = slotIndex,
                    pageId = pageId,
                    totalPages = effectiveOrder.size,
                    thumbnail = thumbnails.getOrNull(pageId),
                    isDragging = isDragging,
                    isDropTarget = isDropTarget,
                    dragOffset = if (isDragging) dragOffset else Offset.Zero,
                    onSlotMeasured = { rectInRoot ->
                      slotBounds[slotIndex] = Rect(
                        left = rectInRoot.left - gridOriginInRoot.x,
                        top = rectInRoot.top - gridOriginInRoot.y,
                        right = rectInRoot.right - gridOriginInRoot.x,
                        bottom = rectInRoot.bottom - gridOriginInRoot.y
                      )
                    },
                    onDragStart = { startDragging(pageId) },
                    onDragDelta = { delta -> updateDrag(delta) },
                    onDragEnd = { finishDragging() },
                    onMoveLeft = {
                      if (slotIndex > 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOrderChange(reorderPageList(effectiveOrder, slotIndex, slotIndex - 1))
                      }
                    },
                    onMoveRight = {
                      if (slotIndex < effectiveOrder.lastIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOrderChange(reorderPageList(effectiveOrder, slotIndex, slotIndex + 1))
                      }
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
                // Pad final row so cards keep uniform width
                repeat(columns - rowPages.size) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }

        // Live Output Sequence Preview Strip
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glass.pillFill)
            .border(1.dp, glass.cardBorderStart.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("reorder_sequence_summary"),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "Output Sequence (${effectiveOrder.size} ${if (effectiveOrder.size == 1) "page" else "pages"})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            itemsIndexed(effectiveOrder) { pos, pageIdx ->
              val moved = pos != pageIdx
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                      if (moved) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                      else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                    .border(
                      1.dp,
                      if (moved) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                      else glass.cardBorderStart.copy(alpha = 0.35f),
                      RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text(
                    text = "P${pageIdx + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (moved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                }
                if (pos < effectiveOrder.lastIndex) {
                  Text(
                    text = "→",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun DraggablePdfPageCard(
  slotIndex: Int,
  pageId: Int,
  totalPages: Int,
  thumbnail: Bitmap?,
  isDragging: Boolean,
  isDropTarget: Boolean,
  dragOffset: Offset,
  onSlotMeasured: (Rect) -> Unit,
  onDragStart: () -> Unit,
  onDragDelta: (Offset) -> Unit,
  onDragEnd: () -> Unit,
  onMoveLeft: () -> Unit,
  onMoveRight: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val movedFromOriginal = slotIndex != pageId

  val cardScale by animateFloatAsState(
    targetValue = if (isDragging) 1.06f else if (isDropTarget) 0.96f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "dragCardScale"
  )
  val cardElevation by animateDpAsState(
    targetValue = if (isDragging) 18.dp else 0.dp,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "dragCardElevation"
  )
  val borderColor by animateColorAsState(
    targetValue = when {
      isDragging -> MaterialTheme.colorScheme.primary
      isDropTarget -> MaterialTheme.colorScheme.tertiary
      movedFromOriginal -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
      else -> glass.cardBorderStart.copy(alpha = 0.45f)
    },
    label = "dragBorderColor"
  )

  // Outer Box measures stationary slot coordinates so hit-testing never drifts during drag
  Box(
    modifier = modifier
      .zIndex(if (isDragging) 10f else 0f)
      .onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        val size = coords.size
        onSlotMeasured(
          Rect(
            left = pos.x,
            top = pos.y,
            right = pos.x + size.width.toFloat(),
            bottom = pos.y + size.height.toFloat()
          )
        )
      }
      .testTag("reorder_page_slot_$slotIndex")
  ) {
    // Drop-zone ghost slot visible underneath while this card is lifted
    if (isDragging) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
          .border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Slot #${slotIndex + 1}",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }

    val cardShape = RoundedCornerShape(16.dp)
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          translationX = dragOffset.x
          translationY = dragOffset.y
          scaleX = cardScale
          scaleY = cardScale
          alpha = if (isDragging) 0.96f else 1f
        }
        .shadow(elevation = cardElevation, shape = cardShape, clip = false)
        .clip(cardShape)
        .background(
          if (isDragging) MaterialTheme.colorScheme.surface
          else if (isDropTarget) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
          else glass.pillFill
        )
        .border(
          width = if (isDragging || isDropTarget) 2.dp else 1.dp,
          color = borderColor,
          shape = cardShape
        )
        .pointerInput(pageId) {
          detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart() },
            onDrag = { change, dragAmount ->
              change.consume()
              onDragDelta(dragAmount)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() }
          )
        }
        .padding(8.dp)
        .semantics {
          contentDescription = "Position ${slotIndex + 1}, Original Page ${pageId + 1}. Long press and drag to reorder."
        }
        .testTag("reorder_page_card_$pageId")
    ) {
      // Top Card Bar: Position pill + Dedicated immediate Drag Handle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (movedFromOriginal) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
          Text(
            text = "#${slotIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (movedFromOriginal) Color.White else MaterialTheme.colorScheme.primary
          )
        }

        // Dedicated grip handle allows immediate drag with 0ms long-press wait
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
              if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
              else Color.Transparent
            )
            .pointerInput(pageId) {
              detectDragGestures(
                onDragStart = { onDragStart() },
                onDrag = { change, dragAmount ->
                  change.consume()
                  onDragDelta(dragAmount)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() }
              )
            }
            .testTag("reorder_drag_handle_$pageId"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = "Drag Page ${pageId + 1}",
            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(Modifier.height(6.dp))

      // Page Thumbnail Preview
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.74f)
          .clip(RoundedCornerShape(10.dp))
          .background(Color.White)
          .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        if (thumbnail != null) {
          val cachedThumb = remember(thumbnail) { thumbnail.asImageBitmap() }
          Image(
            bitmap = cachedThumb,
            contentDescription = "Thumbnail of Page ${pageId + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Description,
              contentDescription = null,
              tint = Color(0xFF64748B),
              modifier = Modifier.size(26.dp)
            )
            Text(
              text = "Page ${pageId + 1}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A)
            )
          }
        }
      }

      Spacer(Modifier.height(6.dp))

      // Page Identity + Accessible Shift Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onMoveLeft,
          enabled = slotIndex > 0,
          modifier = Modifier
            .size(34.dp)
            .testTag("reorder_move_left_$pageId")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Move Page ${pageId + 1} earlier",
            modifier = Modifier.size(16.dp)
          )
        }

        Text(
          text = if (movedFromOriginal) "Orig P${pageId + 1}" else "Page ${pageId + 1}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = if (movedFromOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f)
        )

        IconButton(
          onClick = onMoveRight,
          enabled = slotIndex < totalPages - 1,
          modifier = Modifier
            .size(34.dp)
            .testTag("reorder_move_right_$pageId")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "Move Page ${pageId + 1} later",
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}
