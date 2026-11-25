package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdf.PdfEngine
import com.example.pdf.PdfProtectionStatus
import com.example.ui.theme.LocalGlassColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure helper functions for deciding renderability, clamping pages/zoom, and mapping
 * native Android PdfRenderer errors into clear user-facing guidance.
 */
object PdfPreviewSupport {

  private val nonPdfExtensions = setOf("pdfenc", "txt", "html", "htm", "png", "jpg", "jpeg", "webp")
  const val MIN_ZOOM: Float = 1f
  const val MAX_ZOOM: Float = 4f

  fun canRenderInPdfView(
    displayName: String?,
    protectionStatus: PdfProtectionStatus?
  ): Boolean {
    if (protectionStatus == PdfProtectionStatus.AES_VAULT_ENCRYPTED) return false
    val ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (ext in nonPdfExtensions) return false
    return true
  }

  fun clampPageIndex(requested: Int, totalPages: Int): Int {
    if (totalPages <= 0) return 0
    return requested.coerceIn(0, totalPages - 1)
  }

  fun clampZoom(requested: Float): Float {
    return requested.coerceIn(MIN_ZOOM, MAX_ZOOM)
  }

  fun mapRenderError(throwable: Throwable?): String {
    val msg = throwable?.message.orEmpty()
    val className = throwable?.javaClass?.simpleName.orEmpty()
    return when {
      className.contains("Password", ignoreCase = true) ||
        msg.contains("password", ignoreCase = true) ||
        throwable is SecurityException -> {
        "This PDF requires a password. Enter the password below to unlock its preview."
      }
      msg.contains("format", ignoreCase = true) || msg.contains("corrupt", ignoreCase = true) -> {
        "Unable to render preview — the selected file is not a standard PDF."
      }
      else -> "Preview unavailable for this document (${msg.ifBlank { "unreadable format" }})."
    }
  }
}

private data class RenderedPageResult(
  val bitmap: Bitmap,
  val pageIndex: Int,
  val totalPages: Int
)

private suspend fun renderNativePdfPage(
  context: Context,
  uri: Uri,
  requestedPageIndex: Int,
  password: String?,
  maxDim: Int
): Result<RenderedPageResult> = withContext(Dispatchers.IO) {
  runCatching {
    val localPath = uri.path
    val pfd = if (uri.scheme == "file" && !localPath.isNullOrBlank()) {
      ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
    } else {
      context.contentResolver.openFileDescriptor(uri, "r")
    } ?: throw IllegalStateException("Unable to open PDF document.")

    pfd.use { fd ->
      val renderer = if (!password.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        PdfEngine.openRendererApi35(fd, password)
      } else {
        PdfRenderer(fd)
      }
      renderer.use { r ->
        val count = r.pageCount
        if (count <= 0) throw IllegalStateException("The selected PDF has no pages.")
        val clampedIndex = PdfPreviewSupport.clampPageIndex(requestedPageIndex, count)
        val bmp = r.openPage(clampedIndex).use { page ->
          PdfEngine.renderPageToBitmap(page, maxDim = maxDim, whiteBg = true)
        }
        RenderedPageResult(bitmap = bmp, pageIndex = clampedIndex, totalPages = count)
      }
    }
  }
}

private val NightModeInvertMatrix = ColorMatrix(
  floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
  )
)

/**
 * 16 KB page-size compliant interactive PDF Document Preview powered by Android's native
 * `android.graphics.pdf.PdfRenderer` and Jetpack Compose pinch-zoom / pan / double-tap gestures.
 * Zero third-party native `.so` dependencies.
 */
@Composable
fun PdfDocumentPreviewCard(
  uri: Uri,
  displayName: String,
  accentColor: Color,
  password: String? = null,
  protectionStatus: PdfProtectionStatus? = null,
  initiallyExpanded: Boolean = true,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  var isExpanded by remember(uri) { mutableStateOf(initiallyExpanded) }
  var isFullScreen by remember(uri) { mutableStateOf(false) }
  var nightMode by remember { mutableStateOf(false) }
  var currentPage by remember(uri) { mutableIntStateOf(0) }
  var totalPages by remember(uri) { mutableIntStateOf(0) }
  var isLoading by remember(uri, password) { mutableStateOf(true) }
  var renderError by remember(uri, password) { mutableStateOf<String?>(null) }

  val canRender = remember(displayName, protectionStatus) {
    PdfPreviewSupport.canRenderInPdfView(displayName, protectionStatus)
  }

  LaunchedEffect(uri, password, canRender) {
    if (!canRender) {
      isLoading = false
      renderError = null
    }
  }

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("pdf_document_preview_card"),
    shape = RoundedCornerShape(22.dp),
    tintGlow = accentColor
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header bar with live page badge + Night Mode + Fullscreen + Collapse toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = if (glass.isDark) 0.24f else 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.42f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = null,
            tint = if (glass.isDark) Color.White else accentColor,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Document Preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = if (totalPages > 0 && canRender) {
              "Page ${currentPage + 1} of $totalPages • Pinch or double-tap to zoom"
            } else {
              displayName
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("pdf_preview_page_indicator")
          )
        }

        if (canRender) {
          IconButton(
            onClick = { nightMode = !nightMode },
            modifier = Modifier
              .size(48.dp)
              .testTag("pdf_preview_night_toggle")
          ) {
            Icon(
              imageVector = if (nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
              contentDescription = if (nightMode) "Disable dark preview" else "Enable dark preview",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(
            onClick = { isFullScreen = true },
            modifier = Modifier
              .size(48.dp)
              .testTag("pdf_preview_fullscreen_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Fullscreen,
              contentDescription = "Open full-screen PDF preview",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        IconButton(
          onClick = { isExpanded = !isExpanded },
          modifier = Modifier
            .size(48.dp)
            .testTag("pdf_preview_collapse_toggle")
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (isExpanded) "Collapse preview" else "Expand preview",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (!canRender) {
            val isVault = protectionStatus == PdfProtectionStatus.AES_VAULT_ENCRYPTED ||
              displayName.endsWith(".pdfenc", ignoreCase = true)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(glass.pillFill)
                .border(1.dp, glass.cardBorderStart.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp)
                .testTag("pdf_preview_encrypted_placeholder"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = if (isVault) Icons.Rounded.Lock else Icons.Rounded.Description,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isVault) "Encrypted AES-256 Vault" else "Non-PDF Source File",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = if (isVault) {
                    "Enter your password and tap Decrypt & Remove Password to unlock and preview this document."
                  } else {
                    "Live PDF rendering is available for .pdf files. Tap the action button below to convert this file."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            // 16 KB-Compliant Native Android PdfRenderer Interactive Viewport
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (nightMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                .border(1.dp, glass.cardBorderStart.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .testTag("pdf_preview_viewport"),
              contentAlignment = Alignment.Center
            ) {
              if (!isFullScreen) {
                NativePdfRendererViewport(
                  uri = uri,
                  pageIndex = currentPage,
                  password = password,
                  nightMode = nightMode,
                  maxRenderDim = 1600,
                  accentColor = accentColor,
                  onLoadingStarted = {
                    isLoading = true
                    renderError = null
                  },
                  onRendered = { clampedPage, count ->
                    currentPage = clampedPage
                    totalPages = count
                    isLoading = false
                    renderError = null
                  },
                  onError = { err ->
                    isLoading = false
                    renderError = PdfPreviewSupport.mapRenderError(err)
                  },
                  modifier = Modifier.fillMaxSize()
                )
              }

              if (isLoading && renderError == null) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                  CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                  )
                  Text(
                    text = "Rendering PDF preview...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }

              val errText = renderError
              if (errText != null) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(14.dp)
                    .testTag("pdf_preview_error_banner")
                ) {
                  Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Preview notice",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                  )
                  Text(
                    text = errText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }

            // Quick page navigation bar when document has multiple pages
            if (totalPages > 1 && renderError == null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedButton(
                  onClick = {
                    currentPage = PdfPreviewSupport.clampPageIndex(currentPage - 1, totalPages)
                  },
                  enabled = currentPage > 0,
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pdf_preview_prev_page")
                ) {
                  Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(2.dp))
                  Text("Prev", fontWeight = FontWeight.SemiBold)
                }

                Text(
                  text = "${currentPage + 1} / $totalPages",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                  onClick = {
                    currentPage = PdfPreviewSupport.clampPageIndex(currentPage + 1, totalPages)
                  },
                  enabled = currentPage < totalPages - 1,
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pdf_preview_next_page")
                ) {
                  Text("Next", fontWeight = FontWeight.SemiBold)
                  Spacer(Modifier.width(2.dp))
                  Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }
      }
    }
  }

  // Full-Screen Interactive Native PDF Viewer Modal
  if (isFullScreen && canRender) {
    Dialog(
      onDismissRequest = { isFullScreen = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { isFullScreen = false },
              modifier = Modifier
                .size(48.dp)
                .testTag("pdf_preview_close_fullscreen")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close full-screen preview"
              )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (totalPages > 0) {
                Text(
                  text = "Page ${currentPage + 1} of $totalPages",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            IconButton(
              onClick = { nightMode = !nightMode },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = if (nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = if (nightMode) "Disable dark preview" else "Enable dark preview"
              )
            }
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(if (nightMode) Color(0xFF0F172A) else Color(0xFFE2E8F0))
          ) {
            NativePdfRendererViewport(
              uri = uri,
              pageIndex = currentPage,
              password = password,
              nightMode = nightMode,
              maxRenderDim = 2200,
              accentColor = accentColor,
              onLoadingStarted = {
                isLoading = true
                renderError = null
              },
              onRendered = { clampedPage, count ->
                currentPage = clampedPage
                totalPages = count
                isLoading = false
                renderError = null
              },
              onError = { err ->
                isLoading = false
                renderError = PdfPreviewSupport.mapRenderError(err)
              },
              modifier = Modifier.fillMaxSize()
            )
          }

          if (totalPages > 1) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedButton(
                onClick = { currentPage = PdfPreviewSupport.clampPageIndex(currentPage - 1, totalPages) },
                enabled = currentPage > 0,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
              ) {
                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Prev", fontWeight = FontWeight.SemiBold)
              }
              Text(
                text = "Page ${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              OutlinedButton(
                onClick = { currentPage = PdfPreviewSupport.clampPageIndex(currentPage + 1, totalPages) },
                enabled = currentPage < totalPages - 1,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
              ) {
                Text("Next", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null, modifier = Modifier.size(18.dp))
              }
            }
          }

          Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
      }
    }
  }
}

@Composable
private fun NativePdfRendererViewport(
  uri: Uri,
  pageIndex: Int,
  password: String?,
  nightMode: Boolean,
  maxRenderDim: Int,
  accentColor: Color,
  onLoadingStarted: () -> Unit,
  onRendered: (clampedPage: Int, totalPages: Int) -> Unit,
  onError: (Throwable) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var renderedBitmap by remember(uri, password) { mutableStateOf<Bitmap?>(null) }
  var scale by remember(uri, pageIndex) { mutableFloatStateOf(1f) }
  var offset by remember(uri, pageIndex) { mutableStateOf(Offset.Zero) }

  DisposableEffect(uri, password) {
    onDispose {
      renderedBitmap?.let { bmp ->
        if (!bmp.isRecycled) bmp.recycle()
      }
      renderedBitmap = null
    }
  }

  LaunchedEffect(uri, pageIndex, password, maxRenderDim) {
    onLoadingStarted()
    val result = renderNativePdfPage(
      context = context,
      uri = uri,
      requestedPageIndex = pageIndex,
      password = password,
      maxDim = maxRenderDim
    )
    result.onSuccess { rendered ->
      val previous = renderedBitmap
      renderedBitmap = rendered.bitmap
      if (previous != null && previous !== rendered.bitmap && !previous.isRecycled) {
        previous.recycle()
      }
      onRendered(rendered.pageIndex, rendered.totalPages)
    }.onFailure { err ->
      onError(err)
    }
  }

  Box(
    modifier = modifier
      .clipToBounds()
      .pointerInput(uri, pageIndex) {
        detectTapGestures(
          onDoubleTap = {
            if (scale > 1.05f) {
              scale = 1f
              offset = Offset.Zero
            } else {
              scale = 2.25f
            }
          }
        )
      }
      .pointerInput(uri, pageIndex) {
        detectTransformGestures { _, pan, zoom, _ ->
          val newScale = PdfPreviewSupport.clampZoom(scale * zoom)
          scale = newScale
          offset = if (newScale <= 1.01f) {
            Offset.Zero
          } else {
            val maxX = size.width * (newScale - 1f) * 0.5f
            val maxY = size.height * (newScale - 1f) * 0.5f
            Offset(
              x = (offset.x + pan.x).coerceIn(-maxX, maxX),
              y = (offset.y + pan.y).coerceIn(-maxY, maxY)
            )
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    val bmp = renderedBitmap
    if (bmp != null && !bmp.isRecycled) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "PDF Page ${pageIndex + 1} preview",
        contentScale = ContentScale.Fit,
        colorFilter = if (nightMode) ColorFilter.colorMatrix(NightModeInvertMatrix) else null,
        modifier = Modifier
          .fillMaxSize()
          .padding(8.dp)
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
          )
      )

      // Compact floating zoom controls
      Row(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(8.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
          .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = {
            val next = PdfPreviewSupport.clampZoom(scale - 0.5f)
            scale = next
            if (next <= 1.01f) offset = Offset.Zero
          },
          enabled = scale > PdfPreviewSupport.MIN_ZOOM,
          modifier = Modifier
            .size(48.dp)
            .testTag("pdf_preview_zoom_out")
        ) {
          Icon(
            imageVector = Icons.Rounded.ZoomOut,
            contentDescription = "Zoom out",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Text(
          text = "${(scale * 100).toInt()}%",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(
          onClick = {
            scale = PdfPreviewSupport.clampZoom(scale + 0.5f)
          },
          enabled = scale < PdfPreviewSupport.MAX_ZOOM,
          modifier = Modifier
            .size(48.dp)
            .testTag("pdf_preview_zoom_in")
        ) {
          Icon(
            imageVector = Icons.Rounded.ZoomIn,
            contentDescription = "Zoom in",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}
