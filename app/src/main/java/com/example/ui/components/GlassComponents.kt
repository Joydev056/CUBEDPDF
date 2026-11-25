package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PdfTool
import com.example.ui.theme.LocalGlassColors

/**
 * Full-bleed atmospheric gradient mesh with soft glowing orbs and geometric refraction rings
 * cached via drawWithCache so zero Brush objects are allocated per frame.
 */
@Composable
fun AtmosphericGlassBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  val glass = LocalGlassColors.current
  val bgGradient = remember(glass.bgTop, glass.bgMid, glass.bgBottom) {
    Brush.verticalGradient(colors = listOf(glass.bgTop, glass.bgMid, glass.bgBottom))
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(brush = bgGradient)
      .drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val c1 = Offset(w * 0.14f, h * 0.12f)
        val r1 = w * 0.72f
        val b1 = Brush.radialGradient(listOf(glass.orbPrimary, Color.Transparent), center = c1, radius = r1)

        val c2 = Offset(w * 0.88f, h * 0.22f)
        val r2 = w * 0.68f
        val b2 = Brush.radialGradient(listOf(glass.orbSecondary, Color.Transparent), center = c2, radius = r2)

        val c3 = Offset(w * 0.18f, h * 0.58f)
        val r3 = w * 0.64f
        val b3 = Brush.radialGradient(listOf(glass.orbTertiary, Color.Transparent), center = c3, radius = r3)

        val c4 = Offset(w * 0.84f, h * 0.86f)
        val r4 = w * 0.72f
        val b4 = Brush.radialGradient(listOf(glass.orbAccent, Color.Transparent), center = c4, radius = r4)

        val ringColor = if (glass.isDark) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.35f)
        val ringStroke1 = Stroke(width = 1.5.dp.toPx())
        val ringStroke2 = Stroke(width = 1.2.dp.toPx())

        onDrawBehind {
          drawCircle(brush = b1, center = c1, radius = r1)
          drawCircle(brush = b2, center = c2, radius = r2)
          drawCircle(brush = b3, center = c3, radius = r3)
          drawCircle(brush = b4, center = c4, radius = r4)
          drawCircle(color = ringColor, radius = w * 0.58f, center = Offset(w * 0.85f, h * 0.18f), style = ringStroke1)
          drawCircle(color = ringColor, radius = w * 0.44f, center = Offset(w * 0.12f, h * 0.72f), style = ringStroke2)
        }
      },
    content = content
  )
}

/**
 * Reusable frosted glass container with cached specular border gradient and optional glow.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
  tintGlow: Color? = null,
  borderWidth: Dp = 1.dp,
  content: @Composable BoxScope.() -> Unit
) {
  val glass = LocalGlassColors.current
  val fillBrush = remember(glass.cardFillTop, glass.cardFillBottom) {
    Brush.linearGradient(
      colors = listOf(glass.cardFillTop, glass.cardFillBottom),
      start = Offset.Zero,
      end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
  }
  val borderBrush = remember(glass.cardBorderStart, glass.cardBorderEnd) {
    Brush.linearGradient(
      colors = listOf(glass.cardBorderStart, glass.cardBorderEnd),
      start = Offset.Zero,
      end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
  }

  Box(
    modifier = modifier
      .clip(shape)
      .background(brush = fillBrush)
      .then(
        if (tintGlow != null) {
          Modifier.drawWithCache {
            val center = Offset(size.width * 0.5f, size.height * 0.28f)
            val radius = (size.width * 0.65f).coerceAtLeast(1f)
            val glowBrush = Brush.radialGradient(
              colors = listOf(
                tintGlow.copy(alpha = if (glass.isDark) 0.16f else 0.10f),
                Color.Transparent
              ),
              center = center,
              radius = radius
            )
            onDrawBehind {
              drawCircle(brush = glowBrush, center = center, radius = radius)
            }
          }
        } else {
          Modifier
        }
      )
      .border(width = borderWidth, brush = borderBrush, shape = shape),
    content = content
  )
}

/**
 * Frosted glassmorphism tool card featuring a tactile 3D icon badge with visible inside lettering,
 * crisp readable tag pill, and unclipped tool name.
 */
@Composable
fun GlassToolCard(
  tool: PdfTool,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "cardScale"
  )
  val tiltX by animateFloatAsState(
    targetValue = if (isPressed) 3.5f else 0f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "cardTilt"
  )

  val cardShape = remember { RoundedCornerShape(24.dp) }

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 188.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationX = tiltX
        cameraDistance = 12f * density
      }
      .testTag("tool_card_${tool.id}"),
    shape = cardShape,
    tintGlow = tool.palette.glow
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 188.dp)
        .drawWithCache {
          val lineBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, glass.cardInnerHighlight, Color.Transparent)
          )
          val y = 1.5.dp.toPx()
          val stroke = 1.5.dp.toPx()
          onDrawBehind {
            drawLine(
              brush = lineBrush,
              start = Offset(size.width * 0.14f, y),
              end = Offset(size.width * 0.86f, y),
              strokeWidth = stroke
            )
          }
        }
        .minimumInteractiveComponentSize()
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(color = tool.palette.glow),
          onClick = onClick
        )
        .padding(horizontal = 12.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Crisp high-contrast glass tag pill
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(tool.palette.glow.copy(alpha = if (glass.isDark) 0.24f else 0.14f))
          .border(
            width = 0.8.dp,
            color = tool.palette.glow.copy(alpha = 0.50f),
            shape = CircleShape
          )
          .padding(horizontal = 10.dp, vertical = 3.dp)
      ) {
        Text(
          text = tool.shortTag,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (glass.isDark) Color.White else tool.palette.depth,
          maxLines = 1,
          softWrap = false
        )
      }

      // 3D Tactile Icon Badge with crisp internal letters
      Pdf3DIcon(
        iconType = tool.iconType,
        palette = tool.palette,
        size = 70.dp
      )

      // Tool Name (full visibility, generous line height)
      Text(
        text = tool.name,
        style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun GlassCategoryChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val shape = remember { RoundedCornerShape(50) }

  val bgColor = if (selected) glass.pillSelectedFill else glass.pillFill
  val borderColor = if (selected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
  } else {
    glass.cardBorderStart.copy(alpha = if (glass.isDark) 0.30f else 0.85f)
  }

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .heightIn(min = 42.dp)
      .clip(shape)
      .background(bgColor)
      .border(width = 1.dp, color = borderColor, shape = shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
      color = if (selected) {
        if (glass.isDark) Color.White else MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      maxLines = 1,
      softWrap = false
    )
  }
}

@Composable
fun GlassSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val shape = RoundedCornerShape(18.dp)

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .height(52.dp),
    shape = shape
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = "Search PDF tools",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart
      ) {
        if (query.isEmpty()) {
          Text(
            text = "Search 24 PDF tools (Merge, OCR, Sign...)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
          ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("search_tools_input")
        )
      }
      if (query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChange("") },
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Clear search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
