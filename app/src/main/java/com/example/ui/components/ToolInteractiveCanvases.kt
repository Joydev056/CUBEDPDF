package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf.EditStroke
import com.example.ui.theme.LocalGlassColors

/**
 * Interactive signature drawing pad returning normalized strokes (0f..1f).
 */
@Composable
fun SignaturePadCard(
  strokes: List<List<Pair<Float, Float>>>,
  inkColor: Color,
  onAddStroke: (List<Pair<Float, Float>>) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val currentStroke = remember { mutableStateListOf<Pair<Float, Float>>() }

  GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Draw Your Signature",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        TextButton(
          onClick = onClear,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 40.dp)
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear signature", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Clear", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White)
          .border(1.dp, glass.cardBorderStart, RoundedCornerShape(14.dp))
          .testTag("signature_pad")
          .pointerInput(inkColor) {
            detectDragGestures(
              onDragStart = { offset ->
                currentStroke.clear()
                currentStroke.add(
                  (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
                    (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                )
              },
              onDrag = { change, _ ->
                change.consume()
                val pt = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
                  (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                currentStroke.add(pt)
              },
              onDragEnd = {
                if (currentStroke.size >= 2) {
                  onAddStroke(currentStroke.toList())
                }
                currentStroke.clear()
              },
              onDragCancel = { currentStroke.clear() }
            )
          }
      ) {
        if (strokes.isEmpty() && currentStroke.isEmpty()) {
          Text(
            text = "Sign here with your finger or stylus",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            modifier = Modifier.align(Alignment.Center)
          )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
          // Baseline
          drawLine(
            color = Color(0xFFE2E8F0),
            start = Offset(size.width * 0.08f, size.height * 0.78f),
            end = Offset(size.width * 0.92f, size.height * 0.78f),
            strokeWidth = 2f
          )
          fun drawNormStroke(pts: List<Pair<Float, Float>>) {
            if (pts.size < 2) return
            val path = Path().apply {
              moveTo(pts.first().first * size.width, pts.first().second * size.height)
              pts.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) }
            }
            drawPath(
              path = path,
              color = inkColor,
              style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
          }
          strokes.forEach { drawNormStroke(it) }
          drawNormStroke(currentStroke)
        }
      }
    }
  }
}

/**
 * Interactive PDF Page Canvas for drawing freehand edits OR placing opaque redaction bars.
 */
@Composable
fun InteractivePageAnnotator(
  pageBitmap: Bitmap?,
  isRedactionMode: Boolean,
  strokes: List<EditStroke>,
  redactionRects: List<RectF>,
  activeColor: Color,
  activeColorInt: Int,
  onAddStroke: (EditStroke) -> Unit,
  onAddRedactionRect: (RectF) -> Unit,
  onUndo: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
  var dragStart by remember { mutableStateOf<Offset?>(null) }
  var dragEnd by remember { mutableStateOf<Offset?>(null) }

  GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isRedactionMode) "Drag or Tap to Blackout Regions" else "Draw Directly on Page",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        TextButton(
          onClick = onUndo,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 40.dp)
        ) {
          Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Undo", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
        }
      }

      if (pageBitmap == null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Pick or generate a sample PDF to preview & annotate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        val aspect = (pageBitmap.width.toFloat() / pageBitmap.height.coerceAtLeast(1)).coerceIn(0.55f, 1.6f)
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .pointerInput(isRedactionMode, activeColorInt) {
              if (isRedactionMode) {
                detectTapGestures { tap ->
                  val cx = (tap.x / size.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                  val cy = (tap.y / size.height.coerceAtLeast(1)).coerceIn(0.04f, 0.96f)
                  onAddRedactionRect(
                    RectF(
                      (cx - 0.16f).coerceAtLeast(0f),
                      (cy - 0.03f).coerceAtLeast(0f),
                      (cx + 0.16f).coerceAtMost(1f),
                      (cy + 0.03f).coerceAtMost(1f)
                    )
                  )
                }
              }
            }
            .pointerInput(isRedactionMode, activeColorInt) {
              detectDragGestures(
                onDragStart = { offset ->
                  if (isRedactionMode) {
                    dragStart = offset
                    dragEnd = offset
                  } else {
                    currentPoints.clear()
                    currentPoints.add(
                      (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
                        (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    )
                  }
                },
                onDrag = { change, _ ->
                  change.consume()
                  if (isRedactionMode) {
                    dragEnd = change.position
                  } else {
                    currentPoints.add(
                      (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) to
                        (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    )
                  }
                },
                onDragEnd = {
                  if (isRedactionMode) {
                    val s = dragStart
                    val e = dragEnd
                    if (s != null && e != null) {
                      val l = (minOf(s.x, e.x) / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                      val t = (minOf(s.y, e.y) / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                      val r = (maxOf(s.x, e.x) / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                      val b = (maxOf(s.y, e.y) / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                      if (r - l > 0.02f && b - t > 0.015f) {
                        onAddRedactionRect(RectF(l, t, r, b))
                      }
                    }
                    dragStart = null
                    dragEnd = null
                  } else {
                    if (currentPoints.size >= 2) {
                      onAddStroke(EditStroke(currentPoints.toList(), activeColorInt))
                    }
                    currentPoints.clear()
                  }
                },
                onDragCancel = {
                  dragStart = null
                  dragEnd = null
                  currentPoints.clear()
                }
              )
            }
        ) {
          val cachedPageImage = remember(pageBitmap) { pageBitmap.asImageBitmap() }
          Image(
            bitmap = cachedPageImage,
            contentDescription = "PDF Page Preview",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
          )
          Canvas(modifier = Modifier.fillMaxSize()) {
            if (isRedactionMode) {
              redactionRects.forEach { norm ->
                drawRect(
                  color = Color.Black,
                  topLeft = Offset(norm.left * size.width, norm.top * size.height),
                  size = Size(norm.width() * size.width, norm.height() * size.height)
                )
              }
              val s = dragStart
              val e = dragEnd
              if (s != null && e != null) {
                drawRect(
                  color = Color.Black.copy(alpha = 0.75f),
                  topLeft = Offset(minOf(s.x, e.x), minOf(s.y, e.y)),
                  size = Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y))
                )
              }
            } else {
              strokes.forEach { st ->
                if (st.points.size >= 2) {
                  val path = Path().apply {
                    moveTo(st.points.first().first * size.width, st.points.first().second * size.height)
                    st.points.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) }
                  }
                  drawPath(path, color = Color(st.colorInt), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
              }
              if (currentPoints.size >= 2) {
                val path = Path().apply {
                  moveTo(currentPoints.first().first * size.width, currentPoints.first().second * size.height)
                  currentPoints.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) }
                }
                drawPath(path, color = activeColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Page thumbnail selector & reorder strip for Rotate, Remove, Extract, and Rearrange Pages.
 */
@Composable
fun PageThumbnailsStrip(
  thumbnails: List<Bitmap>,
  selectedIndices: Set<Int>,
  pageOrder: List<Int>,
  reorderMode: Boolean,
  onTogglePage: (Int) -> Unit,
  onMoveOrder: (fromPos: Int, toPos: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = if (reorderMode) "Reorder Pages (Tap arrows to shift)" else "Select Pages (Tap thumbnails)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(Modifier.height(10.dp))

      if (thumbnails.isEmpty()) {
        Text(
          text = "Select a PDF to inspect its page thumbnails.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 4.dp)
        ) {
          val displayOrder = if (reorderMode && pageOrder.isNotEmpty()) pageOrder else thumbnails.indices.toList()
          itemsIndexed(displayOrder) { pos, pageIdx ->
            val bmp = thumbnails.getOrNull(pageIdx)
            val isSelected = selectedIndices.contains(pageIdx)
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else glass.pillFill)
                .border(
                  width = if (isSelected) 2.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else glass.cardBorderStart.copy(alpha = 0.4f),
                  shape = RoundedCornerShape(14.dp)
                )
                .clickable(enabled = !reorderMode) { onTogglePage(pageIdx) }
                .padding(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(112.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.White)
              ) {
                if (bmp != null) {
                  val cachedThumb = remember(bmp) { bmp.asImageBitmap() }
                  Image(
                    bitmap = cachedThumb,
                    contentDescription = "Page ${pageIdx + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                  )
                }
                if (!reorderMode && isSelected) {
                  Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(4.dp)
                      .size(20.dp)
                      .background(Color.White, CircleShape)
                  )
                }
              }
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Page ${pageIdx + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (reorderMode) {
                Row(
                  horizontalArrangement = Arrangement.SpaceEvenly,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  IconButton(
                    onClick = { if (pos > 0) onMoveOrder(pos, pos - 1) },
                    enabled = pos > 0,
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(16.dp))
                  }
                  IconButton(
                    onClick = { if (pos < displayOrder.lastIndex) onMoveOrder(pos, pos + 1) },
                    enabled = pos < displayOrder.lastIndex,
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(16.dp))
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
