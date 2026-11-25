package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PdfToolIconType
import com.example.model.ToolPalette3D
import kotlin.math.min

/**
 * Short, high-contrast letter badge rendered inside each 3D icon so every icon clearly communicates
 * its action with crisp, centered, unclipped lettering.
 */
private fun PdfToolIconType.badgeLetters(): String = when (this) {
  PdfToolIconType.MERGE -> "MRG"
  PdfToolIconType.SPLIT -> "SPL"
  PdfToolIconType.COMPRESS -> "ZIP"
  PdfToolIconType.EDIT -> "EDT"
  PdfToolIconType.SIGN -> "SGN"
  PdfToolIconType.CONVERTER -> "CVT"
  PdfToolIconType.IMAGES_TO_PDF -> "IMG"
  PdfToolIconType.PDF_TO_IMAGES -> "PIC"
  PdfToolIconType.EXTRACT_IMAGES -> "EXT"
  PdfToolIconType.PROTECT -> "LCK"
  PdfToolIconType.UNLOCK -> "UNL"
  PdfToolIconType.ROTATE -> "ROT"
  PdfToolIconType.REMOVE_PAGES -> "DEL"
  PdfToolIconType.EXTRACT_PAGES -> "PG+"
  PdfToolIconType.REARRANGE -> "ORD"
  PdfToolIconType.WEBPAGE_TO_PDF -> "WEB"
  PdfToolIconType.OCR -> "OCR"
  PdfToolIconType.WATERMARK -> "WMK"
  PdfToolIconType.PAGE_NUMBERS -> "123"
  PdfToolIconType.OVERLAY -> "OVL"
  PdfToolIconType.COMPARE -> "CMP"
  PdfToolIconType.WEB_OPTIMIZE -> "OPT"
  PdfToolIconType.REDACT -> "RDT"
  PdfToolIconType.CREATE -> "NEW"
}

/**
 * Renders a tactile, high-depth 3D squircle icon badge using Jetpack Compose drawWithCache:
 * - Soft ambient multi-layered floor drop shadow
 * - Physical 3D extruded bottom bevel slab
 * - Studio-lit beveled top face with inner chamfered rim
 * - Glossy upper specular glass sheen
 * - Bespoke 3D extruded Canvas artwork + crisp centered micro-letter badge inside the icon
 */
@Composable
fun Pdf3DIcon(
  iconType: PdfToolIconType,
  palette: ToolPalette3D,
  modifier: Modifier = Modifier,
  size: Dp = 66.dp
) {
  val textMeasurer = rememberTextMeasurer()
  val badgeLabel = remember(iconType) { iconType.badgeLetters() }
  val labelTextColor = remember(palette) { lerpColor(palette.depth, Color.Black, 0.35f) }

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          val s = min(this.size.width, this.size.height)
          val pad = s * 0.08f
          val boxSize = s - pad * 2f
          val radius = boxSize * 0.28f
          val extrudeDepth = s * 0.08f
          val topLeft = Offset(pad, pad - extrudeDepth * 0.35f)
          val cornerRadius = CornerRadius(radius, radius)

          val dropShadowBrush = Brush.radialGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.28f),
              palette.glow.copy(alpha = 0.42f),
              Color.Transparent
            ),
            center = Offset(s * 0.5f, s * 0.87f),
            radius = s * 0.48f
          )

          val extrudeBrush = Brush.linearGradient(
            colors = listOf(
              palette.depth,
              lerpColor(palette.depth, Color.Black, 0.48f),
              lerpColor(palette.depth, Color.Black, 0.62f)
            ),
            start = Offset(topLeft.x, topLeft.y + extrudeDepth),
            end = Offset(topLeft.x + boxSize, topLeft.y + boxSize + extrudeDepth)
          )

          val catchLightBrush = Brush.horizontalGradient(
            colors = listOf(
              Color.Transparent,
              palette.highlight.copy(alpha = 0.35f),
              Color.Transparent
            )
          )

          val topFaceBrush = Brush.linearGradient(
            colors = listOf(
              palette.highlight,
              palette.base,
              palette.depth
            ),
            start = topLeft,
            end = Offset(topLeft.x + boxSize, topLeft.y + boxSize)
          )

          val bevelInset = boxSize * 0.045f
          val bevelBrush = Brush.linearGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.24f),
              Color.Transparent,
              Color.Black.copy(alpha = 0.20f)
            ),
            start = topLeft,
            end = Offset(topLeft.x + boxSize, topLeft.y + boxSize)
          )

          val sheenPath = Path().apply {
            addRoundRect(
              RoundRect(
                rect = Rect(
                  left = topLeft.x + boxSize * 0.05f,
                  top = topLeft.y + boxSize * 0.04f,
                  right = topLeft.x + boxSize * 0.95f,
                  bottom = topLeft.y + boxSize * 0.46f
                ),
                topLeft = CornerRadius(radius * 0.88f, radius * 0.88f),
                topRight = CornerRadius(radius * 0.88f, radius * 0.88f),
                bottomLeft = CornerRadius(boxSize * 0.45f, boxSize * 0.20f),
                bottomRight = CornerRadius(boxSize * 0.45f, boxSize * 0.20f)
              )
            )
          }

          val sheenBrush = Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.40f),
              Color.White.copy(alpha = 0.02f)
            ),
            startY = topLeft.y,
            endY = topLeft.y + boxSize * 0.46f
          )

          val rimBrush = Brush.linearGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.80f),
              Color.White.copy(alpha = 0.16f),
              Color.Black.copy(alpha = 0.22f)
            ),
            start = topLeft,
            end = Offset(topLeft.x + boxSize, topLeft.y + boxSize)
          )

          // Crisp centered letter pill inside the bottom safe zone of the 3D icon
          val pillHeight = boxSize * 0.25f
          val pillWidth = boxSize * 0.74f
          val pillTopLeft = Offset(
            x = topLeft.x + (boxSize - pillWidth) * 0.5f,
            y = topLeft.y + boxSize * 0.69f
          )
          val pillRadius = CornerRadius(pillHeight * 0.48f, pillHeight * 0.48f)

          val targetFontSp = ((boxSize * 0.175f) / density).coerceIn(7.5f, 13f).sp
          val measuredLabel = textMeasurer.measure(
            text = badgeLabel,
            style = TextStyle(
              color = labelTextColor,
              fontSize = targetFontSp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.4.sp,
              textAlign = TextAlign.Center
            ),
            maxLines = 1,
            softWrap = false
          )

          val glyphCenter = Offset(topLeft.x + boxSize * 0.5f, topLeft.y + boxSize * 0.37f)
          val glyphSize = boxSize * 0.50f

          onDrawBehind {
            // 1. Multi-layered soft ambient drop shadow
            drawOval(
              brush = dropShadowBrush,
              topLeft = Offset(s * 0.06f, s * 0.66f),
              size = Size(s * 0.88f, s * 0.33f)
            )

            // 2. 3D Extruded Bottom Slab
            drawRoundRect(
              brush = extrudeBrush,
              topLeft = Offset(topLeft.x, topLeft.y + extrudeDepth),
              size = Size(boxSize, boxSize),
              cornerRadius = cornerRadius
            )

            // Catch-light along the bottom edge of the 3D extrusion
            drawRoundRect(
              brush = catchLightBrush,
              topLeft = Offset(topLeft.x, topLeft.y + extrudeDepth),
              size = Size(boxSize, boxSize),
              cornerRadius = cornerRadius,
              style = Stroke(width = s * 0.014f)
            )

            // 3. Main 3D Top Face
            drawRoundRect(
              brush = topFaceBrush,
              topLeft = topLeft,
              size = Size(boxSize, boxSize),
              cornerRadius = cornerRadius
            )

            // 4. Inner chamfered bevel ring
            drawRoundRect(
              brush = bevelBrush,
              topLeft = topLeft + Offset(bevelInset, bevelInset),
              size = Size(boxSize - bevelInset * 2f, boxSize - bevelInset * 2f),
              cornerRadius = CornerRadius(radius * 0.85f, radius * 0.85f),
              style = Stroke(width = s * 0.025f)
            )

            // 5. Glossy curved upper specular reflection
            drawPath(path = sheenPath, brush = sheenBrush)

            // 6. Crisp 3D Glass Rim Border
            drawRoundRect(
              brush = rimBrush,
              topLeft = topLeft,
              size = Size(boxSize, boxSize),
              cornerRadius = cornerRadius,
              style = Stroke(width = s * 0.022f)
            )

            // 7. Custom 3D Foreground Symbol (positioned cleanly in upper safe zone)
            drawToolGlyph3D(iconType = iconType, center = glyphCenter, g = glyphSize, shadowColor = palette.depth)

            // 8. High-contrast White Letter Pill inside the 3D Icon
            drawRoundRect(
              color = Color.Black.copy(alpha = 0.24f),
              topLeft = pillTopLeft + Offset(0f, s * 0.018f),
              size = Size(pillWidth, pillHeight),
              cornerRadius = pillRadius
            )
            drawRoundRect(
              color = Color.White.copy(alpha = 0.96f),
              topLeft = pillTopLeft,
              size = Size(pillWidth, pillHeight),
              cornerRadius = pillRadius
            )
            drawRoundRect(
              color = palette.depth.copy(alpha = 0.22f),
              topLeft = pillTopLeft,
              size = Size(pillWidth, pillHeight),
              cornerRadius = pillRadius,
              style = Stroke(width = s * 0.012f)
            )

            val textX = pillTopLeft.x + (pillWidth - measuredLabel.size.width) * 0.5f
            val textY = pillTopLeft.y + (pillHeight - measuredLabel.size.height) * 0.5f
            drawText(
              textLayoutResult = measuredLabel,
              topLeft = Offset(textX, textY)
            )
          }
        }
    )
  }
}

private fun DrawScope.drawToolGlyph3D(
  iconType: PdfToolIconType,
  center: Offset,
  g: Float,
  shadowColor: Color
) {
  val white3D = Brush.linearGradient(
    colors = listOf(Color.White, Color(0xFFE2E8F0)),
    start = Offset(center.x - g * 0.5f, center.y - g * 0.5f),
    end = Offset(center.x + g * 0.5f, center.y + g * 0.5f)
  )
  val frostSheet = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.88f), Color(0xFFCBD5E1).copy(alpha = 0.64f)),
    start = Offset(center.x - g * 0.5f, center.y - g * 0.5f),
    end = Offset(center.x + g * 0.5f, center.y + g * 0.5f)
  )
  val goldTop = Color(0xFFFEF08A)
  val goldBase = Color(0xFFF59E0B)
  val goldDepth = Color(0xFF92400E)
  val shadowShift = Offset(g * 0.05f, g * 0.09f)
  val dropShadow = lerpColor(shadowColor, Color.Black, 0.35f).copy(alpha = 0.58f)

  when (iconType) {
    PdfToolIconType.MERGE -> {
      // Back left 3D extruded document slab with folded corner
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.19f, -g * 0.08f),
        width = g * 0.46f,
        height = g * 0.58f,
        angleDeg = -11f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFE11D48).copy(alpha = 0.35f)
      )
      // Front right 3D extruded document slab
      draw3DDocumentSheet(
        center = center + Offset(g * 0.17f, -g * 0.02f),
        width = g * 0.48f,
        height = g * 0.60f,
        angleDeg = 9f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFE11D48).copy(alpha = 0.55f)
      )
      // Foreground 3D Extruded Beveled Merge Arrow uniting both sheets
      draw3DExtrudedBlockArrow(
        tip = center + Offset(g * 0.28f, g * 0.26f),
        tail = center + Offset(-g * 0.30f, g * 0.26f),
        shaftHalf = g * 0.065f,
        headHalf = g * 0.145f,
        extrudeOffset = Offset(0f, g * 0.055f),
        topBrush = Brush.verticalGradient(listOf(goldTop, goldBase)),
        sideColor = goldDepth,
        shadowColor = dropShadow
      )
    }

    PdfToolIconType.SPLIT -> {
      // Left & Right 3D extruded half-documents separating apart
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.25f, g * 0.03f),
        width = g * 0.34f,
        height = g * 0.60f,
        angleDeg = -8f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color(0xFFF43F5E).copy(alpha = 0.50f)
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.25f, -g * 0.03f),
        width = g * 0.34f,
        height = g * 0.60f,
        angleDeg = 8f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFF43F5E).copy(alpha = 0.40f)
      )
      // Central 3D glowing laser cut beam with shadow
      drawLine(
        color = dropShadow,
        start = Offset(center.x + g * 0.025f, center.y - g * 0.42f),
        end = Offset(center.x + g * 0.025f, center.y + g * 0.42f),
        strokeWidth = g * 0.075f,
        cap = StrokeCap.Round
      )
      drawLine(
        brush = Brush.verticalGradient(listOf(goldTop, Color.White, goldBase)),
        start = Offset(center.x, center.y - g * 0.42f),
        end = Offset(center.x, center.y + g * 0.42f),
        strokeWidth = g * 0.068f,
        cap = StrokeCap.Round
      )
      // Outward 3D beveled split arrowheads
      draw3DExtrudedBlockArrow(
        tip = center + Offset(-g * 0.38f, g * 0.26f),
        tail = center + Offset(-g * 0.12f, g * 0.26f),
        shaftHalf = g * 0.042f,
        headHalf = g * 0.105f,
        extrudeOffset = Offset(0f, g * 0.04f),
        topBrush = Brush.verticalGradient(listOf(goldTop, goldBase)),
        sideColor = goldDepth,
        shadowColor = dropShadow
      )
      draw3DExtrudedBlockArrow(
        tip = center + Offset(g * 0.38f, g * 0.26f),
        tail = center + Offset(g * 0.12f, g * 0.26f),
        shaftHalf = g * 0.042f,
        headHalf = g * 0.105f,
        extrudeOffset = Offset(0f, g * 0.04f),
        topBrush = Brush.verticalGradient(listOf(goldTop, goldBase)),
        sideColor = goldDepth,
        shadowColor = dropShadow
      )
    }

    PdfToolIconType.COMPRESS -> {
      // Compressed 3D Document Slab in center
      draw3DDocumentSheet(
        center = center,
        width = g * 0.56f,
        height = g * 0.34f,
        angleDeg = 0f,
        faceFill = white3D,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF059669).copy(alpha = 0.55f)
      )
      // Top 3D Extruded Downward Piston Arrow
      draw3DExtrudedBlockArrow(
        tip = center + Offset(0f, -g * 0.16f),
        tail = center + Offset(0f, -g * 0.46f),
        shaftHalf = g * 0.07f,
        headHalf = g * 0.165f,
        extrudeOffset = Offset(g * 0.035f, g * 0.045f),
        topBrush = Brush.linearGradient(listOf(goldTop, goldBase)),
        sideColor = goldDepth,
        shadowColor = dropShadow
      )
      // Bottom 3D Extruded Upward Piston Arrow
      draw3DExtrudedBlockArrow(
        tip = center + Offset(0f, g * 0.16f),
        tail = center + Offset(0f, g * 0.46f),
        shaftHalf = g * 0.07f,
        headHalf = g * 0.165f,
        extrudeOffset = Offset(g * 0.035f, g * 0.045f),
        topBrush = Brush.linearGradient(listOf(goldTop, goldBase)),
        sideColor = goldDepth,
        shadowColor = dropShadow
      )
    }

    PdfToolIconType.EDIT -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.08f, 0f),
        width = g * 0.56f,
        height = g * 0.72f,
        angleDeg = -2f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF4F46E5).copy(alpha = 0.45f)
      )
      // 3D beveled diagonal pencil
      rotate(-38f, pivot = center + Offset(g * 0.17f, g * 0.05f)) {
        val pCenter = center + Offset(g * 0.17f, g * 0.05f)
        val pw = g * 0.18f
        val ph = g * 0.52f
        val tl = pCenter - Offset(pw / 2f, ph / 2f)
        drawRoundRect(
          color = dropShadow,
          topLeft = tl + shadowShift,
          size = Size(pw, ph),
          cornerRadius = CornerRadius(g * 0.06f, g * 0.06f)
        )
        drawRoundRect(
          brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFE2E8F0), Color(0xFF94A3B8))),
          topLeft = tl,
          size = Size(pw, ph),
          cornerRadius = CornerRadius(g * 0.06f, g * 0.06f)
        )
        drawRoundRect(
          brush = Brush.verticalGradient(listOf(goldTop, goldBase)),
          topLeft = tl,
          size = Size(pw, g * 0.13f),
          cornerRadius = CornerRadius(g * 0.05f, g * 0.05f)
        )
        // Pencil cone tip
        val tipPath = Path().apply {
          moveTo(tl.x, tl.y + ph - g * 0.03f)
          lineTo(tl.x + pw, tl.y + ph - g * 0.03f)
          lineTo(pCenter.x, tl.y + ph + g * 0.12f)
          close()
        }
        drawPath(tipPath, color = Color(0xFF334155))
      }
    }

    PdfToolIconType.SIGN -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.72f,
        height = g * 0.56f,
        angleDeg = -3f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      val sigPath = Path().apply {
        moveTo(center.x - g * 0.24f, center.y + g * 0.12f)
        cubicTo(
          center.x - g * 0.12f, center.y - g * 0.18f,
          center.x - g * 0.02f, center.y + g * 0.20f,
          center.x + g * 0.10f, center.y - g * 0.05f
        )
        cubicTo(
          center.x + g * 0.18f, center.y - g * 0.16f,
          center.x + g * 0.24f, center.y + g * 0.08f,
          center.x + g * 0.30f, center.y + g * 0.02f
        )
      }
      drawPath(sigPath, color = dropShadow, style = Stroke(width = g * 0.085f, cap = StrokeCap.Round))
      drawPath(
        sigPath,
        brush = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))),
        style = Stroke(width = g * 0.072f, cap = StrokeCap.Round)
      )
    }

    PdfToolIconType.CONVERTER -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.20f, -g * 0.14f),
        width = g * 0.40f,
        height = g * 0.46f,
        angleDeg = -6f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0284C7).copy(alpha = 0.4f)
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.20f, g * 0.14f),
        width = g * 0.40f,
        height = g * 0.46f,
        angleDeg = 6f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0284C7).copy(alpha = 0.55f)
      )
      drawArc(
        color = dropShadow,
        startAngle = -140f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = center - Offset(g * 0.36f, g * 0.36f) + Offset(0f, g * 0.04f),
        size = Size(g * 0.72f, g * 0.72f),
        style = Stroke(width = g * 0.08f, cap = StrokeCap.Round)
      )
      drawArc(
        color = goldTop,
        startAngle = -140f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = center - Offset(g * 0.36f, g * 0.36f),
        size = Size(g * 0.72f, g * 0.72f),
        style = Stroke(width = g * 0.072f, cap = StrokeCap.Round)
      )
      drawArc(
        color = Color.White,
        startAngle = 40f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = center - Offset(g * 0.36f, g * 0.36f),
        size = Size(g * 0.72f, g * 0.72f),
        style = Stroke(width = g * 0.072f, cap = StrokeCap.Round)
      )
    }

    PdfToolIconType.IMAGES_TO_PDF -> {
      draw3DDocumentSheet(
        center = center + Offset(g * 0.14f, -g * 0.10f),
        width = g * 0.50f,
        height = g * 0.62f,
        angleDeg = 8f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      draw3DPhotoCard(center + Offset(-g * 0.10f, g * 0.08f), g * 0.56f, g * 0.48f, -6f, dropShadow)
    }

    PdfToolIconType.PDF_TO_IMAGES -> {
      draw3DPhotoCard(center + Offset(g * 0.14f, -g * 0.08f), g * 0.52f, g * 0.46f, 10f, dropShadow)
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.14f, g * 0.08f),
        width = g * 0.46f,
        height = g * 0.58f,
        angleDeg = -6f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0D9488).copy(alpha = 0.45f)
      )
    }

    PdfToolIconType.EXTRACT_IMAGES -> {
      draw3DDocumentSheet(
        center = center + Offset(0f, g * 0.12f),
        width = g * 0.62f,
        height = g * 0.54f,
        angleDeg = 0f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color.Transparent
      )
      draw3DPhotoCard(center + Offset(0f, -g * 0.14f), g * 0.50f, g * 0.40f, -4f, dropShadow)
    }

    PdfToolIconType.PROTECT -> {
      draw3DPadlock(center = center, g = g, isUnlocked = false, dropShadow = dropShadow, white3D = white3D)
    }

    PdfToolIconType.UNLOCK -> {
      draw3DPadlock(center = center, g = g, isUnlocked = true, dropShadow = dropShadow, white3D = white3D)
    }

    PdfToolIconType.ROTATE -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.44f,
        height = g * 0.56f,
        angleDeg = 12f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      drawArc(
        color = dropShadow,
        startAngle = -60f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = center - Offset(g * 0.36f, g * 0.36f) + shadowShift * 0.6f,
        size = Size(g * 0.72f, g * 0.72f),
        style = Stroke(width = g * 0.095f, cap = StrokeCap.Round)
      )
      drawArc(
        color = Color.White,
        startAngle = -60f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = center - Offset(g * 0.36f, g * 0.36f),
        size = Size(g * 0.72f, g * 0.72f),
        style = Stroke(width = g * 0.095f, cap = StrokeCap.Round)
      )
    }

    PdfToolIconType.REMOVE_PAGES -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.06f, -g * 0.04f),
        width = g * 0.52f,
        height = g * 0.66f,
        angleDeg = -5f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFE11D48).copy(alpha = 0.35f)
      )
      val orbCenter = center + Offset(g * 0.16f, g * 0.14f)
      drawCircle(color = dropShadow, radius = g * 0.23f, center = orbCenter + shadowShift)
      drawCircle(color = Color(0xFFCBD5E1), radius = g * 0.22f, center = orbCenter + Offset(0f, g * 0.03f))
      drawCircle(brush = white3D, radius = g * 0.22f, center = orbCenter)
      val xR = g * 0.09f
      drawLine(Color(0xFFE11D48), orbCenter - Offset(xR, xR), orbCenter + Offset(xR, xR), strokeWidth = g * 0.065f, cap = StrokeCap.Round)
      drawLine(Color(0xFFE11D48), orbCenter + Offset(xR, -xR), orbCenter - Offset(xR, -xR), strokeWidth = g * 0.065f, cap = StrokeCap.Round)
    }

    PdfToolIconType.EXTRACT_PAGES -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.12f, -g * 0.08f),
        width = g * 0.48f,
        height = g * 0.62f,
        angleDeg = -5f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color.Transparent
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.14f, g * 0.08f),
        width = g * 0.48f,
        height = g * 0.62f,
        angleDeg = 4f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF9333EA).copy(alpha = 0.45f)
      )
      draw3DExtrudedBlockArrow(
        tip = center + Offset(g * 0.34f, g * 0.12f),
        tail = center + Offset(-g * 0.02f, g * 0.12f),
        shaftHalf = g * 0.048f,
        headHalf = g * 0.115f,
        extrudeOffset = Offset(0f, g * 0.04f),
        topBrush = Brush.verticalGradient(listOf(Color(0xFFC084FC), Color(0xFF7E22CE))),
        sideColor = Color(0xFF4C1D95),
        shadowColor = dropShadow
      )
    }

    PdfToolIconType.REARRANGE -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.22f, -g * 0.16f),
        width = g * 0.36f,
        height = g * 0.44f,
        angleDeg = -8f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color.Transparent
      )
      draw3DDocumentSheet(
        center = center,
        width = g * 0.38f,
        height = g * 0.46f,
        angleDeg = 0f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color.Transparent
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.22f, g * 0.16f),
        width = g * 0.38f,
        height = g * 0.46f,
        angleDeg = 8f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFD97706).copy(alpha = 0.50f)
      )
    }

    PdfToolIconType.WEBPAGE_TO_PDF -> {
      val w = g * 0.78f
      val h = g * 0.64f
      val tl = center - Offset(w / 2f, h / 2f)
      drawRoundRect(dropShadow, tl + shadowShift, Size(w, h), CornerRadius(g * 0.10f, g * 0.10f))
      drawRoundRect(Color(0xFF94A3B8), tl + Offset(0f, g * 0.045f), Size(w, h), CornerRadius(g * 0.10f, g * 0.10f))
      drawRoundRect(frostSheet, tl, Size(w, h), CornerRadius(g * 0.10f, g * 0.10f))
      drawRoundRect(white3D, tl, Size(w, h * 0.26f), CornerRadius(g * 0.10f, g * 0.10f))
      drawCircle(
        color = Color(0xFF0284C7),
        radius = g * 0.15f,
        center = center + Offset(0f, g * 0.09f),
        style = Stroke(width = g * 0.045f)
      )
      drawLine(Color(0xFF0284C7), center + Offset(-g * 0.15f, g * 0.09f), center + Offset(g * 0.15f, g * 0.09f), strokeWidth = g * 0.035f)
    }

    PdfToolIconType.OCR -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.56f,
        height = g * 0.70f,
        angleDeg = 0f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0F766E).copy(alpha = 0.45f)
      )
      drawRoundRect(
        color = dropShadow,
        topLeft = Offset(center.x - g * 0.38f, center.y - g * 0.01f),
        size = Size(g * 0.76f, g * 0.08f),
        cornerRadius = CornerRadius(g * 0.04f, g * 0.04f)
      )
      drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFF5EEAD4), Color(0xFFA7F3D0), Color(0xFF5EEAD4))),
        topLeft = Offset(center.x - g * 0.38f, center.y - g * 0.035f),
        size = Size(g * 0.76f, g * 0.075f),
        cornerRadius = CornerRadius(g * 0.038f, g * 0.038f)
      )
    }

    PdfToolIconType.WATERMARK -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.60f,
        height = g * 0.74f,
        angleDeg = 0f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      rotate(-24f, pivot = center) {
        drawRoundRect(
          color = dropShadow,
          topLeft = center - Offset(g * 0.34f, g * 0.11f) + shadowShift * 0.6f,
          size = Size(g * 0.68f, g * 0.22f),
          cornerRadius = CornerRadius(g * 0.08f, g * 0.08f)
        )
        drawRoundRect(
          color = Color(0xFF6366F1),
          topLeft = center - Offset(g * 0.34f, g * 0.08f),
          size = Size(g * 0.68f, g * 0.22f),
          cornerRadius = CornerRadius(g * 0.08f, g * 0.08f)
        )
        drawRoundRect(
          brush = white3D,
          topLeft = center - Offset(g * 0.34f, g * 0.11f),
          size = Size(g * 0.68f, g * 0.22f),
          cornerRadius = CornerRadius(g * 0.08f, g * 0.08f)
        )
      }
    }

    PdfToolIconType.PAGE_NUMBERS -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.05f, 0f),
        width = g * 0.58f,
        height = g * 0.72f,
        angleDeg = 0f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFFD97706).copy(alpha = 0.45f)
      )
      val badgeCenter = center + Offset(g * 0.20f, g * 0.22f)
      drawCircle(dropShadow, g * 0.20f, badgeCenter + shadowShift * 0.7f)
      drawCircle(Color(0xFFCBD5E1), g * 0.19f, badgeCenter + Offset(0f, g * 0.03f))
      drawCircle(white3D, g * 0.19f, badgeCenter)
      val r = g * 0.075f
      drawLine(Color(0xFFD97706), badgeCenter + Offset(-r * 0.45f, -r), badgeCenter + Offset(-r * 0.65f, r), strokeWidth = g * 0.032f, cap = StrokeCap.Round)
      drawLine(Color(0xFFD97706), badgeCenter + Offset(r * 0.55f, -r), badgeCenter + Offset(r * 0.35f, r), strokeWidth = g * 0.032f, cap = StrokeCap.Round)
      drawLine(Color(0xFFD97706), badgeCenter + Offset(-r, -r * 0.35f), badgeCenter + Offset(r, -r * 0.35f), strokeWidth = g * 0.032f, cap = StrokeCap.Round)
      drawLine(Color(0xFFD97706), badgeCenter + Offset(-r, r * 0.35f), badgeCenter + Offset(r, r * 0.35f), strokeWidth = g * 0.032f, cap = StrokeCap.Round)
    }

    PdfToolIconType.OVERLAY -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.10f, g * 0.12f),
        width = g * 0.54f,
        height = g * 0.48f,
        angleDeg = -14f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color.Transparent
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.10f, -g * 0.10f),
        width = g * 0.54f,
        height = g * 0.48f,
        angleDeg = -14f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF9333EA).copy(alpha = 0.40f)
      )
    }

    PdfToolIconType.COMPARE -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.20f, 0f),
        width = g * 0.36f,
        height = g * 0.56f,
        angleDeg = -4f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = false,
        lineColor = Color(0xFF0284C7).copy(alpha = 0.40f)
      )
      draw3DDocumentSheet(
        center = center + Offset(g * 0.20f, 0f),
        width = g * 0.36f,
        height = g * 0.56f,
        angleDeg = 4f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0284C7).copy(alpha = 0.55f)
      )
      val lens = center + Offset(0f, g * 0.08f)
      drawCircle(dropShadow, g * 0.18f, lens + shadowShift * 0.7f)
      drawCircle(Color(0xFF38BDF8).copy(alpha = 0.45f), g * 0.15f, lens)
      drawCircle(Color.White, g * 0.15f, lens, style = Stroke(width = g * 0.058f))
      drawLine(Color.White, lens + Offset(g * 0.11f, g * 0.11f), lens + Offset(g * 0.25f, g * 0.25f), strokeWidth = g * 0.07f, cap = StrokeCap.Round)
    }

    PdfToolIconType.WEB_OPTIMIZE -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.56f,
        height = g * 0.68f,
        angleDeg = -4f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      val bolt = Path().apply {
        moveTo(center.x + g * 0.06f, center.y - g * 0.26f)
        lineTo(center.x - g * 0.16f, center.y + g * 0.03f)
        lineTo(center.x + g * 0.01f, center.y + g * 0.03f)
        lineTo(center.x - g * 0.07f, center.y + g * 0.28f)
        lineTo(center.x + g * 0.18f, center.y - g * 0.03f)
        lineTo(center.x + g * 0.01f, center.y - g * 0.03f)
        close()
      }
      val boltExtrude = Path().apply {
        addPath(bolt, Offset(g * 0.025f, g * 0.045f))
      }
      drawPath(boltExtrude, color = goldDepth)
      drawPath(bolt, brush = Brush.verticalGradient(listOf(goldTop, goldBase)))
    }

    PdfToolIconType.REDACT -> {
      draw3DDocumentSheet(
        center = center,
        width = g * 0.60f,
        height = g * 0.72f,
        angleDeg = 0f,
        faceFill = white3D,
        edgeColor = Color(0xFFCBD5E1),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color.Transparent
      )
      // Extruded raised redaction bars
      drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = center + Offset(-g * 0.19f, -g * 0.11f),
        size = Size(g * 0.40f, g * 0.105f),
        cornerRadius = CornerRadius(g * 0.03f, g * 0.03f)
      )
      drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
        topLeft = center + Offset(-g * 0.20f, -g * 0.14f),
        size = Size(g * 0.40f, g * 0.105f),
        cornerRadius = CornerRadius(g * 0.03f, g * 0.03f)
      )
      drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = center + Offset(-g * 0.19f, g * 0.07f),
        size = Size(g * 0.30f, g * 0.105f),
        cornerRadius = CornerRadius(g * 0.03f, g * 0.03f)
      )
      drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
        topLeft = center + Offset(-g * 0.20f, g * 0.04f),
        size = Size(g * 0.30f, g * 0.105f),
        cornerRadius = CornerRadius(g * 0.03f, g * 0.03f)
      )
    }

    PdfToolIconType.CREATE -> {
      draw3DDocumentSheet(
        center = center + Offset(-g * 0.06f, -g * 0.04f),
        width = g * 0.54f,
        height = g * 0.68f,
        angleDeg = -4f,
        faceFill = frostSheet,
        edgeColor = Color(0xFF94A3B8),
        shadowColor = dropShadow,
        hasFold = true,
        lineColor = Color(0xFF0D9488).copy(alpha = 0.35f)
      )
      val orbCenter = center + Offset(g * 0.16f, g * 0.16f)
      drawCircle(dropShadow, g * 0.23f, orbCenter + shadowShift * 0.7f)
      drawCircle(Color(0xFFCBD5E1), g * 0.22f, orbCenter + Offset(0f, g * 0.035f))
      drawCircle(white3D, g * 0.22f, orbCenter)
      val pR = g * 0.095f
      drawLine(Color(0xFF0D9488), orbCenter - Offset(pR, 0f), orbCenter + Offset(pR, 0f), strokeWidth = g * 0.065f, cap = StrokeCap.Round)
      drawLine(Color(0xFF0D9488), orbCenter - Offset(0f, pR), orbCenter + Offset(0f, pR), strokeWidth = g * 0.065f, cap = StrokeCap.Round)
    }
  }
}

/**
 * Draws a thick 3D extruded document slab with cast drop shadow, side bevel thickness,
 * optional dog-ear folded corner, and subtle page lines.
 */
private fun DrawScope.draw3DDocumentSheet(
  center: Offset,
  width: Float,
  height: Float,
  angleDeg: Float,
  faceFill: Brush,
  edgeColor: Color,
  shadowColor: Color,
  hasFold: Boolean,
  lineColor: Color
) {
  rotate(angleDeg, pivot = center) {
    val tl = Offset(center.x - width / 2f, center.y - height / 2f)
    val r = min(width, height) * 0.16f
    val cr = CornerRadius(r, r)
    val thickness = min(width, height) * 0.095f
    val fold = if (hasFold) min(width, height) * 0.26f else 0f

    // 1. Soft cast shadow
    drawRoundRect(
      color = shadowColor,
      topLeft = tl + Offset(thickness * 1.1f, thickness * 1.6f),
      size = Size(width, height),
      cornerRadius = cr
    )

    // 2. 3D Extruded Bottom/Right Thickness Lip
    drawRoundRect(
      color = edgeColor,
      topLeft = tl + Offset(thickness * 0.35f, thickness),
      size = Size(width, height),
      cornerRadius = cr
    )

    // 3. Main Page Face (clipped corner if dog-ear fold enabled)
    if (hasFold) {
      val pagePath = Path().apply {
        moveTo(tl.x + r, tl.y)
        lineTo(tl.x + width - fold, tl.y)
        lineTo(tl.x + width, tl.y + fold)
        lineTo(tl.x + width, tl.y + height - r)
        quadraticTo(tl.x + width, tl.y + height, tl.x + width - r, tl.y + height)
        lineTo(tl.x + r, tl.y + height)
        quadraticTo(tl.x, tl.y + height, tl.x, tl.y + height - r)
        lineTo(tl.x, tl.y + r)
        quadraticTo(tl.x, tl.y, tl.x + r, tl.y)
        close()
      }
      drawPath(path = pagePath, brush = faceFill)
      drawPath(
        path = pagePath,
        color = Color.White.copy(alpha = 0.78f),
        style = Stroke(width = min(width, height) * 0.04f)
      )

      // Dog-ear fold cast shadow & turned flap
      val foldShadow = Path().apply {
        moveTo(tl.x + width - fold, tl.y)
        lineTo(tl.x + width - fold, tl.y + fold + thickness * 0.45f)
        lineTo(tl.x + width, tl.y + fold + thickness * 0.45f)
        close()
      }
      drawPath(foldShadow, color = shadowColor.copy(alpha = 0.24f))

      val foldFlap = Path().apply {
        moveTo(tl.x + width - fold, tl.y)
        lineTo(tl.x + width - fold, tl.y + fold)
        lineTo(tl.x + width, tl.y + fold)
        close()
      }
      drawPath(
        path = foldFlap,
        brush = Brush.linearGradient(
          colors = listOf(Color.White, Color(0xFFCBD5E1)),
          start = Offset(tl.x + width - fold, tl.y),
          end = Offset(tl.x + width, tl.y + fold)
        )
      )
    } else {
      drawRoundRect(brush = faceFill, topLeft = tl, size = Size(width, height), cornerRadius = cr)
      drawRoundRect(
        color = Color.White.copy(alpha = 0.75f),
        topLeft = tl,
        size = Size(width, height),
        cornerRadius = cr,
        style = Stroke(width = min(width, height) * 0.04f)
      )
    }

    // 4. Optional crisp micro text bars
    if (lineColor != Color.Transparent) {
      val barX = tl.x + width * 0.20f
      val fullW = width * 0.56f
      val barH = min(width, height) * 0.085f
      val y1 = tl.y + height * 0.42f
      val y2 = tl.y + height * 0.60f
      drawRoundRect(lineColor, Offset(barX, y1), Size(fullW, barH), CornerRadius(barH, barH))
      drawRoundRect(lineColor, Offset(barX, y2), Size(fullW * 0.72f, barH), CornerRadius(barH, barH))
    }
  }
}

/**
 * Draws a solid 3D extruded polygonal block arrow (horizontal or vertical) with cast shadow,
 * physical 3D underside depth, and studio-lit beveled top face.
 */
private fun DrawScope.draw3DExtrudedBlockArrow(
  tip: Offset,
  tail: Offset,
  shaftHalf: Float,
  headHalf: Float,
  extrudeOffset: Offset,
  topBrush: Brush,
  sideColor: Color,
  shadowColor: Color
) {
  val dx = tip.x - tail.x
  val dy = tip.y - tail.y
  val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
  val ux = dx / len
  val uy = dy / len
  val px = -uy
  val py = ux
  val headLen = headHalf * 1.35f
  val neck = Offset(tip.x - ux * headLen, tip.y - uy * headLen)

  fun buildArrowPath(offset: Offset): Path {
    return Path().apply {
      moveTo(tail.x + px * shaftHalf + offset.x, tail.y + py * shaftHalf + offset.y)
      lineTo(neck.x + px * shaftHalf + offset.x, neck.y + py * shaftHalf + offset.y)
      lineTo(neck.x + px * headHalf + offset.x, neck.y + py * headHalf + offset.y)
      lineTo(tip.x + offset.x, tip.y + offset.y)
      lineTo(neck.x - px * headHalf + offset.x, neck.y - py * headHalf + offset.y)
      lineTo(neck.x - px * shaftHalf + offset.x, neck.y - py * shaftHalf + offset.y)
      lineTo(tail.x - px * shaftHalf + offset.x, tail.y - py * shaftHalf + offset.y)
      close()
    }
  }

  // 1. Cast shadow
  drawPath(buildArrowPath(extrudeOffset * 1.9f), color = shadowColor)
  // 2. 3D Extruded side/bottom bevel
  drawPath(buildArrowPath(extrudeOffset), color = sideColor)
  // 3. Top beveled face + specular rim
  val topPath = buildArrowPath(Offset.Zero)
  drawPath(topPath, brush = topBrush)
  drawPath(
    topPath,
    color = Color.White.copy(alpha = 0.72f),
    style = Stroke(width = shaftHalf * 0.25f, join = StrokeJoin.Round)
  )
}

private fun DrawScope.draw3DPhotoCard(
  center: Offset,
  width: Float,
  height: Float,
  angleDeg: Float,
  shadow: Color
) {
  rotate(angleDeg, pivot = center) {
    val tl = Offset(center.x - width / 2f, center.y - height / 2f)
    val cr = CornerRadius(min(width, height) * 0.18f, min(width, height) * 0.18f)
    val depth = min(width, height) * 0.085f
    drawRoundRect(
      color = shadow,
      topLeft = tl + Offset(depth * 1.1f, depth * 1.6f),
      size = Size(width, height),
      cornerRadius = cr
    )
    drawRoundRect(
      color = Color(0xFF94A3B8),
      topLeft = tl + Offset(depth * 0.35f, depth),
      size = Size(width, height),
      cornerRadius = cr
    )
    drawRoundRect(
      brush = Brush.linearGradient(
        colors = listOf(Color.White, Color(0xFFE0F2FE)),
        start = tl,
        end = tl + Offset(width, height)
      ),
      topLeft = tl,
      size = Size(width, height),
      cornerRadius = cr
    )
    val mountain = Path().apply {
      moveTo(tl.x + width * 0.14f, tl.y + height * 0.78f)
      lineTo(tl.x + width * 0.42f, tl.y + height * 0.42f)
      lineTo(tl.x + width * 0.62f, tl.y + height * 0.64f)
      lineTo(tl.x + width * 0.76f, tl.y + height * 0.50f)
      lineTo(tl.x + width * 0.88f, tl.y + height * 0.78f)
      close()
    }
    drawPath(mountain, color = Color(0xFF0284C7).copy(alpha = 0.82f))
    drawCircle(
      color = Color(0xFFF59E0B),
      radius = min(width, height) * 0.10f,
      center = Offset(tl.x + width * 0.72f, tl.y + height * 0.30f)
    )
  }
}

private fun DrawScope.draw3DPadlock(
  center: Offset,
  g: Float,
  isUnlocked: Boolean,
  dropShadow: Color,
  white3D: Brush
) {
  val shackleCenter = center + Offset(if (isUnlocked) g * 0.10f else 0f, -g * 0.16f)
  val shackleW = g * 0.36f
  val shackleH = g * 0.34f
  drawArc(
    color = dropShadow,
    startAngle = 180f,
    sweepAngle = if (isUnlocked) 140f else 180f,
    useCenter = false,
    topLeft = Offset(shackleCenter.x - shackleW / 2f + g * 0.02f, shackleCenter.y - shackleH / 2f + g * 0.04f),
    size = Size(shackleW, shackleH),
    style = Stroke(width = g * 0.10f, cap = StrokeCap.Round)
  )
  drawArc(
    color = Color.White,
    startAngle = 180f,
    sweepAngle = if (isUnlocked) 140f else 180f,
    useCenter = false,
    topLeft = Offset(shackleCenter.x - shackleW / 2f, shackleCenter.y - shackleH / 2f),
    size = Size(shackleW, shackleH),
    style = Stroke(width = g * 0.095f, cap = StrokeCap.Round)
  )
  val bodyW = g * 0.58f
  val bodyH = g * 0.44f
  val bodyTl = Offset(center.x - bodyW / 2f, center.y - g * 0.04f)
  drawRoundRect(
    color = dropShadow,
    topLeft = bodyTl + Offset(g * 0.04f, g * 0.09f),
    size = Size(bodyW, bodyH),
    cornerRadius = CornerRadius(g * 0.12f, g * 0.12f)
  )
  drawRoundRect(
    color = Color(0xFF94A3B8),
    topLeft = bodyTl + Offset(0f, g * 0.04f),
    size = Size(bodyW, bodyH),
    cornerRadius = CornerRadius(g * 0.12f, g * 0.12f)
  )
  drawRoundRect(
    brush = white3D,
    topLeft = bodyTl,
    size = Size(bodyW, bodyH),
    cornerRadius = CornerRadius(g * 0.12f, g * 0.12f)
  )
  drawCircle(
    color = if (isUnlocked) Color(0xFF059669) else Color(0xFFE11D48),
    radius = g * 0.068f,
    center = Offset(center.x, bodyTl.y + bodyH * 0.46f)
  )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
  return Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
  )
}
