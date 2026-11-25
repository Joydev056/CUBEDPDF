package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.analytics.AnalyticsHelper
import com.example.model.PdfTool
import com.example.pdf.CompressionLevel
import com.example.pdf.ConvertTarget
import com.example.pdf.EditStroke
import com.example.pdf.ImageOutputFormat
import com.example.pdf.PageNumberFormat
import com.example.pdf.PageNumberPosition
import com.example.pdf.PageSizeMode
import com.example.pdf.PdfCryptoService
import com.example.pdf.PdfDocumentMeta
import com.example.pdf.PdfEngine
import com.example.pdf.PdfOcrService
import com.example.pdf.PdfPasswordActionMode
import com.example.pdf.PdfProcessResult
import com.example.pdf.PdfProtectionStatus
import com.example.pdf.PdfRotationSupport
import com.example.pdf.ProcessedFile
import com.example.pdf.SignaturePosition
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.DragDropPageReorderGrid
import com.example.ui.components.ExtractTextStudioCard
import com.example.ui.components.GlassCategoryChip
import com.example.ui.components.GlassSurface
import com.example.ui.components.InteractivePageAnnotator
import com.example.ui.components.PageRotationStudioCard
import com.example.ui.components.PageThumbnailsStrip
import com.example.ui.components.Pdf3DIcon
import com.example.ui.components.PdfDocumentPreviewCard
import com.example.ui.components.PdfPasswordControlCard
import com.example.ui.components.SignaturePadCard
import com.example.ui.theme.BlackWorkspaceTheme
import com.example.ui.theme.LocalGlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolWorkspaceScreen(
  tool: PdfTool,
  onBack: () -> Unit,
  initialFileUri: Uri? = null,
  onRecordRecentFile: ((
    fileName: String,
    fileUriString: String,
    mimeType: String,
    sizeBytes: Long,
    pageCount: Int,
    summaryNote: String
  ) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val glass = LocalGlassColors.current

  // Shared processing & output state
  var isProcessing by remember(tool.id) { mutableStateOf(false) }
  var processingStatus by remember(tool.id) { mutableStateOf<String?>(null) }
  var errorMessage by remember(tool.id) { mutableStateOf<String?>(null) }
  var processResult by remember(tool.id) { mutableStateOf<PdfProcessResult?>(null) }
  var ocrSourceIsImages by remember(tool.id) { mutableStateOf(false) }
  var ocrPageRange by remember(tool.id) { mutableStateOf("") }

  // Single PDF state
  var primaryUri by remember(tool.id) { mutableStateOf<Uri?>(null) }
  var primaryMeta by remember(tool.id) { mutableStateOf<PdfDocumentMeta?>(null) }
  var thumbnails by remember(tool.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
  var activePageBitmap by remember(tool.id) { mutableStateOf<Bitmap?>(null) }
  var activePageIndex by remember(tool.id) { mutableIntStateOf(0) }

  // Secondary PDF (Overlay / Compare)
  var secondaryUri by remember(tool.id) { mutableStateOf<Uri?>(null) }
  var secondaryMeta by remember(tool.id) { mutableStateOf<PdfDocumentMeta?>(null) }

  // Multi PDF (Merge)
  val mergeUris = remember(tool.id) { mutableStateListOf<Uri>() }

  // Images (Images to PDF / Create PDF)
  val selectedImages = remember(tool.id) { mutableStateListOf<Uri>() }

  // Tool-specific options
  var splitRanges by remember(tool.id) { mutableStateOf("1-2, 3") }
  var compressionLevel by remember(tool.id) { mutableStateOf(CompressionLevel.BALANCED) }
  var editText by remember(tool.id) { mutableStateOf("") }
  var editColor by remember(tool.id) { mutableStateOf(Color(0xFFE11D48)) }
  val editStrokes = remember(tool.id) { mutableStateListOf<EditStroke>() }

  val signatureStrokes = remember(tool.id) { mutableStateListOf<List<Pair<Float, Float>>>() }
  var signatureColor by remember(tool.id) { mutableStateOf(Color(0xFF0F172A)) }
  var signaturePosition by remember(tool.id) { mutableStateOf(SignaturePosition.BOTTOM_RIGHT) }

  var convertTarget by remember(tool.id) { mutableStateOf(ConvertTarget.PDF_TO_PNG) }
  var imageOutputFormat by remember(tool.id) { mutableStateOf(ImageOutputFormat.PNG) }
  var pageSizeMode by remember(tool.id) { mutableStateOf(PageSizeMode.A4) }

  var passwordActionMode by remember(tool.id) {
    mutableStateOf(
      if (tool.id == "unlock") PdfPasswordActionMode.REMOVE_PASSWORD
      else PdfPasswordActionMode.SET_PASSWORD
    )
  }
  var passwordInput by remember(tool.id) { mutableStateOf("") }
  var confirmPasswordInput by remember(tool.id) { mutableStateOf("") }
  var detectedProtectionStatus by remember(tool.id) { mutableStateOf<PdfProtectionStatus?>(null) }
  var rotateDegrees by remember(tool.id) { mutableIntStateOf(90) }
  var perPageRotations by remember(tool.id) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
  var selectedPages by remember(tool.id) { mutableStateOf<Set<Int>>(emptySet()) }
  var pageOrder by remember(tool.id) { mutableStateOf<List<Int>>(emptyList()) }

  var webpageUrl by remember(tool.id) { mutableStateOf("https://example.com") }
  var watermarkText by remember(tool.id) { mutableStateOf("CONFIDENTIAL") }
  var watermarkOpacity by remember(tool.id) { mutableFloatStateOf(0.28f) }
  var watermarkAngle by remember(tool.id) { mutableFloatStateOf(-35f) }
  var watermarkColor by remember(tool.id) { mutableStateOf(Color(0xFFE11D48)) }

  var pageNumPos by remember(tool.id) { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
  var pageNumFormat by remember(tool.id) { mutableStateOf(PageNumberFormat.PAGE_X_OF_N) }
  var pageNumStart by remember(tool.id) { mutableStateOf("1") }
  var overlayAlpha by remember(tool.id) { mutableFloatStateOf(0.55f) }

  val redactionRects = remember(tool.id) { mutableStateListOf<RectF>() }

  var createTitle by remember(tool.id) { mutableStateOf("Project Overview") }
  var createSubtitle by remember(tool.id) { mutableStateOf("Prepared with CubedPDF") }
  var createBody by remember(tool.id) {
    mutableStateOf(
      "Summary:\nAll document workflows run cleanly on-device with zero broad storage permissions.\n\nHighlights:\n• Tactile 3D tools and Glassmorphism workspace\n• Edge-to-edge compatibility on Android 16, 17, and 18"
    )
  }
  var createAccent by remember(tool.id) { mutableStateOf(Color(0xFF0D9488)) }

  // Helper: Load metadata & thumbnails when primaryUri changes
  fun loadPrimaryPdf(uri: Uri) {
    primaryUri = uri
    processResult = null
    errorMessage = null
    detectedProtectionStatus = null
    selectedPages = emptySet()
    perPageRotations = emptyMap()
    ocrPageRange = ""
    activePageIndex = 0
    editStrokes.clear()
    redactionRects.clear()
    scope.launch {
      if (tool.id == "protect" || tool.id == "unlock") {
        detectedProtectionStatus = PdfCryptoService.inspectProtectionStatus(context, uri)
      }
      if (tool.id == "protect" || tool.id == "unlock" || tool.id == "converter") {
        // Might be .pdfenc, native password-protected PDF, or .txt/.html/image
        val name = PdfEngine.queryDisplayName(context, uri)
        val size = PdfEngine.querySizeBytes(context, uri)
        val metaRes = PdfEngine.inspectPdf(context, uri)
        if (metaRes.isSuccess) {
          val meta = metaRes.getOrThrow()
          primaryMeta = meta
          pageOrder = (0 until meta.pageCount).toList()
          thumbnails = PdfEngine.renderThumbnails(context, uri).getOrDefault(emptyList())
          val durableUri = PdfEngine.persistLocalSnapshotForRecents(context, uri, meta.displayName)
          onRecordRecentFile?.invoke(
            meta.displayName,
            durableUri.toString(),
            "application/pdf",
            meta.sizeBytes,
            meta.pageCount,
            "Opened in ${tool.name}"
          )
        } else {
          primaryMeta = PdfDocumentMeta(uri, name, size, 1, 595, 842)
          thumbnails = emptyList()
          val durableUri = PdfEngine.persistLocalSnapshotForRecents(context, uri, name)
          onRecordRecentFile?.invoke(
            name,
            durableUri.toString(),
            "application/octet-stream",
            size,
            1,
            "Opened in ${tool.name}"
          )
        }
      } else {
        val metaRes = PdfEngine.inspectPdf(context, uri)
        metaRes.onSuccess { meta ->
          primaryMeta = meta
          pageOrder = (0 until meta.pageCount).toList()
          thumbnails = PdfEngine.renderThumbnails(context, uri).getOrDefault(emptyList())
          if (tool.id == "edit" || tool.id == "redact") {
            activePageBitmap = PdfEngine.renderSinglePage(context, uri, 0).getOrNull()
          }
          val durableUri = PdfEngine.persistLocalSnapshotForRecents(context, uri, meta.displayName)
          onRecordRecentFile?.invoke(
            meta.displayName,
            durableUri.toString(),
            "application/pdf",
            meta.sizeBytes,
            meta.pageCount,
            "Working in ${tool.name}"
          )
        }.onFailure { err ->
          errorMessage = err.message ?: "Unable to read the selected PDF."
        }
      }
    }
  }

  LaunchedEffect(tool.id) {
    AnalyticsHelper.logToolOpened(tool.id, tool.name)
  }

  LaunchedEffect(tool.id, initialFileUri) {
    if (initialFileUri != null) {
      if (tool.id == "merge") {
        if (!mergeUris.contains(initialFileUri)) {
          mergeUris.add(initialFileUri)
        }
      } else {
        loadPrimaryPdf(initialFileUri)
      }
    }
  }

  LaunchedEffect(activePageIndex, primaryUri) {
    val u = primaryUri
    if (u != null && (tool.id == "edit" || tool.id == "redact")) {
      activePageBitmap = PdfEngine.renderSinglePage(context, u, activePageIndex).getOrNull()
    }
  }

  // Activity Result Launchers (SAF + Photo Picker)
  val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) loadPrimaryPdf(uri)
  }
  val anyDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) loadPrimaryPdf(uri)
  }
  val secondaryPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      secondaryUri = uri
      scope.launch {
        PdfEngine.inspectPdf(context, uri).onSuccess { secondaryMeta = it }
          .onFailure { errorMessage = it.message }
      }
    }
  }
  val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) {
      mergeUris.addAll(uris)
      errorMessage = null
    }
  }
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
    if (uris.isNotEmpty()) {
      selectedImages.addAll(uris)
      errorMessage = null
    }
  }

  var filePendingSave by remember { mutableStateOf<ProcessedFile?>(null) }
  val saveDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("*/*")
  ) { destUri ->
    val src = filePendingSave
    if (destUri != null && src != null) {
      scope.launch {
        PdfEngine.saveFileToUri(context, src.file, destUri)
          .onSuccess { snackbarHostState.showSnackbar("Saved ${src.displayName}") }
          .onFailure { snackbarHostState.showSnackbar(it.message ?: "Save failed") }
      }
    }
    filePendingSave = null
  }
  val saveTxtDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument(PdfOcrService.PLAIN_TEXT_MIME_TYPE)
  ) { destUri ->
    val src = filePendingSave
    if (destUri != null && src != null) {
      scope.launch {
        PdfEngine.saveFileToUri(context, src.file, destUri)
          .onSuccess { snackbarHostState.showSnackbar("Saved plain text file ${src.displayName}") }
          .onFailure { snackbarHostState.showSnackbar(it.message ?: "Failed to save .txt file") }
      }
    }
    filePendingSave = null
  }

  fun launchSaveForFile(file: ProcessedFile) {
    filePendingSave = file
    if (file.mimeType == PdfOcrService.PLAIN_TEXT_MIME_TYPE || file.displayName.endsWith(".txt", ignoreCase = true)) {
      saveTxtDocumentLauncher.launch(PdfOcrService.sanitizeTxtFileName(file.displayName))
    } else {
      saveDocumentLauncher.launch(file.displayName)
    }
  }

  fun runToolAction() {
    errorMessage = null
    isProcessing = true
    scope.launch {
      val result = when (tool.id) {
        "merge" -> PdfEngine.mergePdfs(context, mergeUris.toList())
        "split" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.splitPdf(context, u, splitRanges)
        }
        "compress" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.compressPdf(context, u, compressionLevel)
        }
        "edit" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.editPdf(context, u, activePageIndex, editText, editColor.toArgb(), editStrokes.toList())
        }
        "sign" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.signPdf(context, u, activePageIndex, signatureStrokes.toList(), signatureColor.toArgb(), signaturePosition)
        }
        "converter" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a file to convert first."))
          else PdfEngine.convertPdf(context, u, convertTarget)
        }
        "images_to_pdf" -> PdfEngine.imagesToPdf(context, selectedImages.toList(), pageSizeMode)
        "pdf_to_images" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.pdfToImages(context, u, imageOutputFormat)
        }
        "extract_images" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.extractImages(context, u)
        }
        "protect", "unlock" -> {
          val u = primaryUri
          when (passwordActionMode) {
            PdfPasswordActionMode.SET_PASSWORD -> {
              if (u == null) {
                Result.failure(IllegalStateException("Select a PDF file first."))
              } else {
                val validationError = PdfCryptoService.validateSetPassword(passwordInput, confirmPasswordInput)
                if (validationError != null) {
                  Result.failure(IllegalArgumentException(validationError))
                } else {
                  PdfCryptoService.encryptPdfFile(context, u, passwordInput)
                }
              }
            }
            PdfPasswordActionMode.REMOVE_PASSWORD -> {
              if (u == null) {
                Result.failure(IllegalStateException("Select a protected file first."))
              } else {
                val validationError = PdfCryptoService.validateRemovePassword(passwordInput)
                if (validationError != null) {
                  Result.failure(IllegalArgumentException(validationError))
                } else {
                  PdfCryptoService.decryptPdfFile(context, u, passwordInput)
                }
              }
            }
          }
        }
        "rotate" -> {
          val u = primaryUri
          if (u == null) {
            Result.failure(IllegalStateException("Select a PDF file first."))
          } else {
            val total = primaryMeta?.pageCount ?: thumbnails.size.coerceAtLeast(1)
            val rotations = PdfRotationSupport.buildPageRotationsMap(
              totalPages = total,
              perPageRotations = perPageRotations,
              fallbackPages = selectedPages,
              fallbackDegrees = rotateDegrees
            )
            PdfEngine.rotateSpecificPages(context, u, rotations)
          }
        }
        "remove" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else if (selectedPages.isEmpty()) Result.failure(IllegalStateException("Tap at least one page thumbnail to remove."))
          else PdfEngine.removePages(context, u, selectedPages)
        }
        "extract_pages" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.extractPages(context, u, selectedPages)
        }
        "rearrange" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.rearrangePages(context, u, pageOrder)
        }
        "webpage" -> PdfEngine.webpageToPdf(context, webpageUrl)
        "ocr" -> {
          if (ocrSourceIsImages) {
            if (selectedImages.isEmpty()) {
              Result.failure(IllegalStateException("Select at least one scanned image or photo first."))
            } else {
              PdfEngine.ocrImages(context, selectedImages.toList()) { cur, total ->
                scope.launch(Dispatchers.Main.immediate) {
                  processingStatus = "Recognizing image $cur of $total with ML Kit..."
                }
              }
            }
          } else {
            val u = primaryUri
            if (u == null) {
              Result.failure(IllegalStateException("Select a scanned PDF file first, or switch to Scanned Images."))
            } else {
              PdfEngine.ocrPdf(
                context = context,
                uri = u,
                selectedPages = selectedPages,
                pageRangeExpression = ocrPageRange
              ) { cur, total ->
                scope.launch(Dispatchers.Main.immediate) {
                  processingStatus = "Scanning page $cur of $total with ML Kit..."
                }
              }
            }
          }
        }
        "watermark" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.addWatermark(context, u, watermarkText, watermarkOpacity, watermarkAngle, watermarkColor.toArgb())
        }
        "page_numbers" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.addPageNumbers(context, u, pageNumPos, pageNumStart.toIntOrNull()?.coerceAtLeast(1) ?: 1, pageNumFormat)
        }
        "overlay" -> {
          val b = primaryUri
          val o = secondaryUri
          if (b == null || o == null) Result.failure(IllegalStateException("Select both a Base PDF and an Overlay PDF."))
          else PdfEngine.overlayPdf(context, b, o, overlayAlpha)
        }
        "compare" -> {
          val a = primaryUri
          val b = secondaryUri
          if (a == null || b == null) Result.failure(IllegalStateException("Select both Document A and Document B."))
          else PdfEngine.comparePdfs(context, a, b)
        }
        "web_optimize" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.webOptimizePdf(context, u)
        }
        "redact" -> {
          val u = primaryUri
          if (u == null) Result.failure(IllegalStateException("Select a PDF file first."))
          else PdfEngine.redactPdf(context, u, activePageIndex, redactionRects.toList())
        }
        "create" -> PdfEngine.createPdf(
          context = context,
          title = createTitle,
          subtitle = createSubtitle,
          bodyText = createBody,
          pageSizeMode = pageSizeMode,
          accentColorInt = createAccent.toArgb(),
          attachedImageUris = selectedImages.toList()
        )
        else -> Result.failure(IllegalStateException("Unsupported tool"))
      }

      isProcessing = false
      processingStatus = null
      result.onSuccess { res ->
        AnalyticsHelper.logToolCompleted(tool.id, success = true)
        processResult = res
        errorMessage = null
        if (tool.id == "protect" || tool.id == "unlock") {
          passwordInput = ""
          confirmPasswordInput = ""
        }
        val outUri = Uri.fromFile(res.primaryFile.file).toString()
        onRecordRecentFile?.invoke(
          res.primaryFile.displayName,
          outUri,
          res.primaryFile.mimeType,
          res.resultSizeBytes,
          res.pageCount.coerceAtLeast(1),
          res.summary
        )
      }.onFailure { err ->
        AnalyticsHelper.logToolCompleted(tool.id, success = false)
        errorMessage = err.message ?: "Something went wrong while processing."
      }
    }
  }

  BlackWorkspaceTheme {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
      ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .imePadding(),
        contentAlignment = Alignment.TopCenter
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Floating Glass Top Bar
          GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = onBack,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("workspace_back_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = "Back to dashboard",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
              Spacer(Modifier.width(6.dp))
              Pdf3DIcon(iconType = tool.iconType, palette = tool.palette, size = 44.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = tool.name,
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = tool.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          // Input Section (tailored per tool)
          when (tool.id) {
            "merge" -> {
              MultiPdfPickerCard(
                uris = mergeUris,
                onAddPdfs = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                onAddSample = {
                  scope.launch {
                    val idx = mergeUris.size + 1
                    PdfEngine.createSamplePdf(context, "Sample Part $idx", pages = 2)
                      .onSuccess { mergeUris.add(it) }
                      .onFailure { errorMessage = it.message }
                  }
                },
                onMoveUp = { i ->
                  if (i > 0) {
                    val item = mergeUris.removeAt(i)
                    mergeUris.add(i - 1, item)
                  }
                },
                onMoveDown = { i ->
                  if (i < mergeUris.lastIndex) {
                    val item = mergeUris.removeAt(i)
                    mergeUris.add(i + 1, item)
                  }
                },
                onRemove = { i -> mergeUris.removeAt(i) }
              )
            }

            "images_to_pdf" -> {
              ImagePickerCard(
                selectedImages = selectedImages,
                onPickImages = {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { i -> selectedImages.removeAt(i) }
              )
              PageSizeSelectorCard(selected = pageSizeMode, onSelect = { pageSizeMode = it })
            }

            "webpage" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("Website URL or HTML", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                  GlassTextField(
                    value = webpageUrl,
                    onValueChange = { webpageUrl = it },
                    label = "Enter URL (e.g. https://example.com) or <html>...",
                    singleLine = false
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCategoryChip(
                      label = "Sample HTML Page",
                      selected = webpageUrl.startsWith("<"),
                      onClick = {
                        webpageUrl = "<html><body style='font-family:sans-serif;padding:24px'><h1>CubedPDF Web Snapshot</h1><p>Rendered cleanly offline from HTML into a paginated PDF document.</p><ul><li>Edge-to-edge ready</li><li>Android 16/17/18 compatible</li></ul></body></html>"
                      }
                    )
                  }
                }
              }
            }

            "create" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("Compose Document", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                  GlassTextField(value = createTitle, onValueChange = { createTitle = it }, label = "Document Title")
                  GlassTextField(value = createSubtitle, onValueChange = { createSubtitle = it }, label = "Subtitle")
                  GlassTextField(value = createBody, onValueChange = { createBody = it }, label = "Body Content", singleLine = false, minLines = 4)
                  ColorDotsRow("Banner Accent", createAccent, onSelect = { createAccent = it })
                }
              }
              PageSizeSelectorCard(selected = pageSizeMode, onSelect = { pageSizeMode = it })
              ImagePickerCard(
                selectedImages = selectedImages,
                title = "Attach Photos (Optional)",
                onPickImages = {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { i -> selectedImages.removeAt(i) }
              )
            }

            "overlay", "compare" -> {
              SinglePdfPickerCard(
                label = if (tool.id == "compare") "Document A" else "Base PDF Document",
                meta = primaryMeta,
                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                onCreateSample = {
                  scope.launch {
                    PdfEngine.createSamplePdf(context, "Document A", 2)
                      .onSuccess { loadPrimaryPdf(it) }
                      .onFailure { errorMessage = it.message }
                  }
                }
              )
              SinglePdfPickerCard(
                label = if (tool.id == "compare") "Document B" else "Overlay PDF Layer",
                meta = secondaryMeta,
                onPick = { secondaryPdfPicker.launch(arrayOf("application/pdf")) },
                onCreateSample = {
                  scope.launch {
                    PdfEngine.createSamplePdf(context, if (tool.id == "compare") "Document B (Modified)" else "Watermark Overlay", 2)
                      .onSuccess { uri ->
                        secondaryUri = uri
                        PdfEngine.inspectPdf(context, uri).onSuccess { secondaryMeta = it }
                      }
                      .onFailure { errorMessage = it.message }
                  }
                }
              )
              if (tool.id == "overlay") {
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Text("Overlay Opacity: ${(overlayAlpha * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(value = overlayAlpha, onValueChange = { overlayAlpha = it }, valueRange = 0.15f..1.0f)
                  }
                }
              }
            }

            "ocr" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    text = "Scan Input Source (ML Kit On-Device)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCategoryChip(
                      label = "PDF Document",
                      selected = !ocrSourceIsImages,
                      onClick = {
                        ocrSourceIsImages = false
                        errorMessage = null
                      },
                      modifier = Modifier.testTag("ocr_source_pdf")
                    )
                    GlassCategoryChip(
                      label = "Scanned Images",
                      selected = ocrSourceIsImages,
                      onClick = {
                        ocrSourceIsImages = true
                        errorMessage = null
                      },
                      modifier = Modifier.testTag("ocr_source_images")
                    )
                  }
                }
              }

              if (ocrSourceIsImages) {
                ImagePickerCard(
                  selectedImages = selectedImages,
                  title = "Scanned Pages / Document Photos",
                  onPickImages = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  onRemove = { i -> selectedImages.removeAt(i) }
                )
              } else {
                SinglePdfPickerCard(
                  label = "Scanned PDF Document",
                  meta = primaryMeta,
                  onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                  onCreateSample = {
                    scope.launch {
                      PdfEngine.createSamplePdf(context, "Scanned Invoice & Notes", 2)
                        .onSuccess { loadPrimaryPdf(it) }
                        .onFailure { errorMessage = it.message }
                    }
                  }
                )
                val ocrDocMeta = primaryMeta
                if (ocrDocMeta != null) {
                  val targetPageList = PdfOcrService.resolveTargetPages(
                    totalPages = ocrDocMeta.pageCount,
                    selectedPages = selectedPages,
                    pageRangeExpression = ocrPageRange
                  )
                  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Text(
                          text = "Pages to Extract (Optional)",
                          style = MaterialTheme.typography.titleSmall,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = if (selectedPages.isEmpty() && ocrPageRange.isBlank()) {
                            "All ${ocrDocMeta.pageCount} page(s)"
                          } else {
                            "${targetPageList.size} of ${ocrDocMeta.pageCount} selected"
                          },
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.SemiBold,
                          color = Color.White
                        )
                      }
                      GlassTextField(
                        value = ocrPageRange,
                        onValueChange = { ocrPageRange = it },
                        label = "Page range (e.g. 1, 3-5) or leave blank for all",
                        modifier = Modifier.testTag("ocr_page_range_input")
                      )
                    }
                  }
                }
                if (thumbnails.isNotEmpty()) {
                  PageThumbnailsStrip(
                    thumbnails = thumbnails,
                    selectedIndices = selectedPages,
                    pageOrder = pageOrder,
                    reorderMode = false,
                    onTogglePage = { idx ->
                      selectedPages = if (selectedPages.contains(idx)) selectedPages - idx else selectedPages + idx
                    },
                    onMoveOrder = { _, _ -> }
                  )
                }
              }
            }

            else -> {
              // Single PDF / Document Picker
              val allowAny = tool.id == "protect" || tool.id == "unlock" || tool.id == "converter"
              val pickerLabel = when {
                tool.id == "protect" || tool.id == "unlock" -> {
                  if (passwordActionMode == PdfPasswordActionMode.REMOVE_PASSWORD) {
                    "Protected PDF or .pdfenc Vault"
                  } else {
                    "PDF Document to Protect"
                  }
                }
                else -> "Source Document"
              }
              SinglePdfPickerCard(
                label = pickerLabel,
                meta = primaryMeta,
                onPick = {
                  if (allowAny) anyDocPicker.launch(arrayOf("*/*"))
                  else singlePdfPicker.launch(arrayOf("application/pdf"))
                },
                onCreateSample = {
                  scope.launch {
                    PdfEngine.createSamplePdf(context, "CubedPDF Sample", 3)
                      .onSuccess { loadPrimaryPdf(it) }
                      .onFailure { errorMessage = it.message }
                  }
                }
              )
            }
          }

          // Live interactive AndroidPdfViewer preview of the selected document before running operations
          val previewUri = primaryUri
          val showDocumentPreview = previewUri != null &&
            tool.id !in setOf("merge", "images_to_pdf", "webpage", "create") &&
            !(tool.id == "ocr" && ocrSourceIsImages)
          if (showDocumentPreview) {
            PdfDocumentPreviewCard(
              uri = previewUri,
              displayName = primaryMeta?.displayName ?: PdfEngine.queryDisplayName(context, previewUri),
              accentColor = tool.palette.base,
              password = if (tool.id == "protect" || tool.id == "unlock") passwordInput else null,
              protectionStatus = if (tool.id == "protect" || tool.id == "unlock") detectedProtectionStatus else null,
              initiallyExpanded = tool.id !in setOf("edit", "redact")
            )
          }

          // Tool-specific options below source picker
          when (tool.id) {
            "split" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Page Ranges (leave blank to split every page)", style = MaterialTheme.typography.titleSmall)
                  GlassTextField(value = splitRanges, onValueChange = { splitRanges = it }, label = "e.g. 1-2, 3")
                }
              }
            }

            "compress" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Compression Preset", style = MaterialTheme.typography.titleSmall)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CompressionLevel.entries) { lvl ->
                      GlassCategoryChip(
                        label = lvl.label,
                        selected = compressionLevel == lvl,
                        onClick = { compressionLevel = lvl }
                      )
                    }
                  }
                }
              }
            }

            "edit" -> {
              PageNumberSwitcher(
                currentPage = activePageIndex,
                totalPages = primaryMeta?.pageCount ?: 1,
                onChangePage = { activePageIndex = it }
              )
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Add Text Note (Optional)", style = MaterialTheme.typography.titleSmall)
                  GlassTextField(value = editText, onValueChange = { editText = it }, label = "Type annotation text...")
                  ColorDotsRow("Ink / Text Color", editColor, onSelect = { editColor = it })
                }
              }
              InteractivePageAnnotator(
                pageBitmap = activePageBitmap,
                isRedactionMode = false,
                strokes = editStrokes,
                redactionRects = emptyList(),
                activeColor = editColor,
                activeColorInt = editColor.toArgb(),
                onAddStroke = { editStrokes.add(it) },
                onAddRedactionRect = {},
                onUndo = { if (editStrokes.isNotEmpty()) editStrokes.removeAt(editStrokes.lastIndex) }
              )
            }

            "sign" -> {
              PageNumberSwitcher(
                currentPage = activePageIndex,
                totalPages = primaryMeta?.pageCount ?: 1,
                onChangePage = { activePageIndex = it }
              )
              SignaturePadCard(
                strokes = signatureStrokes,
                inkColor = signatureColor,
                onAddStroke = { signatureStrokes.add(it) },
                onClear = { signatureStrokes.clear() }
              )
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  ColorDotsRow("Signature Ink", signatureColor, onSelect = { signatureColor = it })
                  Text("Placement on Page", style = MaterialTheme.typography.titleSmall)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SignaturePosition.entries) { pos ->
                      GlassCategoryChip(
                        label = pos.label,
                        selected = signaturePosition == pos,
                        onClick = { signaturePosition = pos }
                      )
                    }
                  }
                }
              }
            }

            "converter" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Conversion Format", style = MaterialTheme.typography.titleSmall)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ConvertTarget.entries) { tgt ->
                      GlassCategoryChip(
                        label = tgt.label,
                        selected = convertTarget == tgt,
                        onClick = { convertTarget = tgt }
                      )
                    }
                  }
                }
              }
            }

            "pdf_to_images" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Output Image Format", style = MaterialTheme.typography.titleSmall)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageOutputFormat.entries.forEach { fmt ->
                      GlassCategoryChip(
                        label = fmt.label,
                        selected = imageOutputFormat == fmt,
                        onClick = { imageOutputFormat = fmt }
                      )
                    }
                  }
                }
              }
            }

            "protect", "unlock" -> {
              PdfPasswordControlCard(
                mode = passwordActionMode,
                onModeChange = { nextMode ->
                  passwordActionMode = nextMode
                  confirmPasswordInput = ""
                  errorMessage = null
                },
                password = passwordInput,
                onPasswordChange = {
                  passwordInput = it
                  errorMessage = null
                },
                confirmPassword = confirmPasswordInput,
                onConfirmPasswordChange = {
                  confirmPasswordInput = it
                  errorMessage = null
                },
                protectionStatus = detectedProtectionStatus,
                accentColor = tool.palette.base
              )
            }

            "rotate" -> {
              PageRotationStudioCard(
                thumbnails = thumbnails,
                totalPageCount = primaryMeta?.pageCount ?: thumbnails.size,
                selectedAngle = rotateDegrees,
                onSelectAngle = {
                  rotateDegrees = it
                  errorMessage = null
                },
                perPageRotations = perPageRotations,
                onUpdatePerPageRotations = { updated ->
                  perPageRotations = updated
                  errorMessage = null
                },
                accentColor = tool.palette.base
              )
            }

            "remove", "extract_pages" -> {
              PageThumbnailsStrip(
                thumbnails = thumbnails,
                selectedIndices = selectedPages,
                pageOrder = pageOrder,
                reorderMode = false,
                onTogglePage = { idx ->
                  selectedPages = if (selectedPages.contains(idx)) selectedPages - idx else selectedPages + idx
                },
                onMoveOrder = { _, _ -> }
              )
            }

            "rearrange" -> {
              DragDropPageReorderGrid(
                thumbnails = thumbnails,
                pageOrder = pageOrder,
                totalPageCount = primaryMeta?.pageCount ?: thumbnails.size,
                onOrderChange = { updatedOrder -> pageOrder = updatedOrder }
              )
            }

            "watermark" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("Watermark Settings", style = MaterialTheme.typography.titleSmall)
                  GlassTextField(value = watermarkText, onValueChange = { watermarkText = it }, label = "Watermark Text")
                  Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                  Slider(value = watermarkOpacity, onValueChange = { watermarkOpacity = it }, valueRange = 0.1f..0.85f)
                  Text("Angle: ${watermarkAngle.toInt()}°", style = MaterialTheme.typography.bodySmall)
                  Slider(value = watermarkAngle, onValueChange = { watermarkAngle = it }, valueRange = -60f..60f)
                  ColorDotsRow("Watermark Color", watermarkColor, onSelect = { watermarkColor = it })
                }
              }
            }

            "page_numbers" -> {
              GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("Position", style = MaterialTheme.typography.titleSmall)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageNumberPosition.entries) { pos ->
                      GlassCategoryChip(label = pos.label, selected = pageNumPos == pos, onClick = { pageNumPos = pos })
                    }
                  }
                  Text("Format", style = MaterialTheme.typography.titleSmall)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageNumberFormat.entries) { fmt ->
                      GlassCategoryChip(label = fmt.label, selected = pageNumFormat == fmt, onClick = { pageNumFormat = fmt })
                    }
                  }
                  GlassTextField(value = pageNumStart, onValueChange = { pageNumStart = it.filter(Char::isDigit) }, label = "Starting Number")
                }
              }
            }

            "redact" -> {
              PageNumberSwitcher(
                currentPage = activePageIndex,
                totalPages = primaryMeta?.pageCount ?: 1,
                onChangePage = { activePageIndex = it }
              )
              InteractivePageAnnotator(
                pageBitmap = activePageBitmap,
                isRedactionMode = true,
                strokes = emptyList(),
                redactionRects = redactionRects,
                activeColor = Color.Black,
                activeColorInt = AndroidColor.BLACK,
                onAddStroke = {},
                onAddRedactionRect = { redactionRects.add(it) },
                onUndo = { if (redactionRects.isNotEmpty()) redactionRects.removeAt(redactionRects.lastIndex) }
              )
            }
          }

          // Error Banner (clean, informative, never crashes)
          val err = errorMessage
          if (err != null) {
            GlassSurface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              tintGlow = Color(0xFFE11D48)
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Rounded.ErrorOutline,
                  contentDescription = "Error",
                  tint = Color(0xFFE11D48),
                  modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                  text = err,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }

          // Primary Execute Action Button
          Button(
            onClick = { runToolAction() },
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
              containerColor = tool.palette.base,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 54.dp)
              .testTag("execute_tool_button")
          ) {
            if (isProcessing) {
              CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
              )
              Spacer(Modifier.width(10.dp))
              Text(
                text = processingStatus ?: "Processing...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("processing_status_text")
              )
            } else {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                text = when (tool.id) {
                  "ocr" -> "Extract Text with ML Kit"
                  "protect", "unlock" -> passwordActionMode.actionButtonLabel
                  "rotate" -> {
                    val queuedCount = perPageRotations.count { (_, deg) ->
                      PdfRotationSupport.normalizeDegrees(deg) != 0
                    }
                    if (queuedCount > 0) {
                      "Rotate $queuedCount Selected Page${if (queuedCount == 1) "" else "s"}"
                    } else {
                      "Rotate Pages (${rotateDegrees}°)"
                    }
                  }
                  else -> "Run ${tool.name}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
              )
            }
          }

          // Interactive Searchable & Editable Text Studio for ML Kit OCR results
          val res = processResult
          if (res != null && tool.id == "ocr" && res.extractedText != null) {
            ExtractTextStudioCard(
              originalFullText = res.extractedText,
              extractedPages = res.extractedPages,
              accentColor = tool.palette.base,
              onCopyText = { textToCopy ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Extracted Text", textToCopy))
                scope.launch { snackbarHostState.showSnackbar("Copied extracted text to clipboard.") }
              },
              onShareText = { textToShare ->
                runCatching {
                  val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, primaryMeta?.displayName ?: "Extracted Text")
                    putExtra(Intent.EXTRA_TEXT, textToShare)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                }.onFailure {
                  scope.launch { snackbarHostState.showSnackbar("Unable to share extracted text.") }
                }
              },
              suggestedTxtFileName = PdfOcrService.suggestTxtFileName(primaryMeta?.displayName),
              onExportTxt = { editedText ->
                scope.launch {
                  val suggested = PdfOcrService.suggestTxtFileName(primaryMeta?.displayName)
                  PdfOcrService.savePlainTextFile(context, suggested, editedText)
                    .onSuccess { processedTxt ->
                      onRecordRecentFile?.invoke(
                        processedTxt.displayName,
                        Uri.fromFile(processedTxt.file).toString(),
                        processedTxt.mimeType,
                        processedTxt.sizeBytes,
                        res.pageCount.coerceAtLeast(1),
                        "Saved extracted plain text (${processedTxt.displayName})"
                      )
                      launchSaveForFile(processedTxt)
                    }
                    .onFailure { err ->
                      snackbarHostState.showSnackbar(err.message ?: "Unable to export .txt file.")
                    }
                }
              },
              onExportTxtWithName = { desiredFileName, editedText ->
                scope.launch {
                  PdfOcrService.savePlainTextFile(context, desiredFileName, editedText)
                    .onSuccess { processedTxt ->
                      onRecordRecentFile?.invoke(
                        processedTxt.displayName,
                        Uri.fromFile(processedTxt.file).toString(),
                        processedTxt.mimeType,
                        processedTxt.sizeBytes,
                        res.pageCount.coerceAtLeast(1),
                        "Saved extracted plain text (${processedTxt.displayName})"
                      )
                      launchSaveForFile(processedTxt)
                    }
                    .onFailure { err ->
                      snackbarHostState.showSnackbar(err.message ?: "Unable to export .txt file.")
                    }
                }
              },
              onExportSearchablePdf = { editedText ->
                scope.launch {
                  val docTitle = primaryMeta?.displayName?.substringBeforeLast('.') ?: "Extracted Document"
                  PdfEngine.createSearchablePdfFromText(context, docTitle, editedText)
                    .onSuccess { pdfRes ->
                      processResult = pdfRes
                      val outUri = Uri.fromFile(pdfRes.primaryFile.file).toString()
                      onRecordRecentFile?.invoke(
                        pdfRes.primaryFile.displayName,
                        outUri,
                        pdfRes.primaryFile.mimeType,
                        pdfRes.resultSizeBytes,
                        pdfRes.pageCount.coerceAtLeast(1),
                        pdfRes.summary
                      )
                      launchSaveForFile(pdfRes.primaryFile)
                    }
                    .onFailure { err ->
                      snackbarHostState.showSnackbar(err.message ?: "Unable to create searchable PDF.")
                    }
                }
              }
            )
          }

          // Result Panel (Preview, Metrics, Save, Share, Open)
          if (res != null) {
            ResultOutputCard(
              result = res,
              accentColor = tool.palette.base,
              showRawExtractedText = tool.id != "ocr",
              onSaveFile = { file ->
                launchSaveForFile(file)
              },
              onShareFile = { file ->
                PdfEngine.shareFile(context, file.file, file.mimeType).onFailure {
                  scope.launch { snackbarHostState.showSnackbar(it.message ?: "Share failed") }
                }
              },
              onOpenFile = { file ->
                PdfEngine.openFileExternally(context, file.file, file.mimeType).onFailure {
                  scope.launch { snackbarHostState.showSnackbar(it.message ?: "No viewer installed") }
                }
              }
            )
          }

          Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
      }
    }
    }
  }
}

@Composable
private fun SinglePdfPickerCard(
  label: String,
  meta: PdfDocumentMeta?,
  onPick: () -> Unit,
  onCreateSample: () -> Unit
) {
  val glass = LocalGlassColors.current
  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)

      if (meta != null) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glass.pillFill)
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(meta.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              "${meta.pageCount} page(s) • ${PdfEngine.formatFileSize(meta.sizeBytes)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = onPick,
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("pick_file_button"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(5.dp))
          Text("Choose File", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        OutlinedButton(
          onClick = onCreateSample,
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("sample_pdf_button"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(5.dp))
          Text("Sample PDF", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun MultiPdfPickerCard(
  uris: List<Uri>,
  onAddPdfs: () -> Unit,
  onAddSample: () -> Unit,
  onMoveUp: (Int) -> Unit,
  onMoveDown: (Int) -> Unit,
  onRemove: (Int) -> Unit
) {
  val context = LocalContext.current
  val glass = LocalGlassColors.current
  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Documents to Merge (${uris.size} selected)", style = MaterialTheme.typography.titleSmall)

      uris.forEachIndexed { idx, uri ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glass.pillFill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("${idx + 1}.", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          Spacer(Modifier.width(8.dp))
          Text(
            text = PdfEngine.queryDisplayName(context, uri),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          IconButton(onClick = { onMoveUp(idx) }, enabled = idx > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
          }
          IconButton(onClick = { onMoveDown(idx) }, enabled = idx < uris.lastIndex, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
          }
          IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = onAddPdfs,
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(5.dp))
          Text("Add PDFs", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        OutlinedButton(
          onClick = onAddSample,
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(5.dp))
          Text("Add Sample", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun ImagePickerCard(
  selectedImages: List<Uri>,
  onPickImages: () -> Unit,
  onRemove: (Int) -> Unit,
  title: String = "Selected Images"
) {
  val context = LocalContext.current
  val glass = LocalGlassColors.current
  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("$title (${selectedImages.size})", style = MaterialTheme.typography.titleSmall)
      selectedImages.forEachIndexed { idx, uri ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glass.pillFill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = PdfEngine.queryDisplayName(context, uri),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          IconButton(onClick = { onRemove(idx) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove image", modifier = Modifier.size(16.dp))
          }
        }
      }
      OutlinedButton(
        onClick = onPickImages,
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = Color(0xFF121216),
          contentColor = Color.White
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp)
      ) {
        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Pick Images from Gallery (Zero Permission)",
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun PageSizeSelectorCard(selected: PageSizeMode, onSelect: (PageSizeMode) -> Unit) {
  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Page Size", style = MaterialTheme.typography.titleSmall)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PageSizeMode.entries) { mode ->
          GlassCategoryChip(label = mode.label, selected = selected == mode, onClick = { onSelect(mode) })
        }
      }
    }
  }
}

@Composable
private fun PageNumberSwitcher(
  currentPage: Int,
  totalPages: Int,
  onChangePage: (Int) -> Unit
) {
  if (totalPages <= 1) return
  GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedButton(
        onClick = { onChangePage((currentPage - 1).coerceAtLeast(0)) },
        enabled = currentPage > 0,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 42.dp)
      ) {
        Text("Prev", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
      }
      Text(
        text = "Target Page ${currentPage + 1} of $totalPages",
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
      )
      OutlinedButton(
        onClick = { onChangePage((currentPage + 1).coerceAtMost(totalPages - 1)) },
        enabled = currentPage < totalPages - 1,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 42.dp)
      ) {
        Text("Next", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
      }
    }
  }
}

@Composable
private fun ColorDotsRow(
  label: String,
  selected: Color,
  onSelect: (Color) -> Unit
) {
  val palette = listOf(
    Color(0xFF0F172A),
    Color(0xFFE11D48),
    Color(0xFF4F46E5),
    Color(0xFF0284C7),
    Color(0xFF059669),
    Color(0xFFD97706)
  )
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      palette.forEach { color ->
        val isSel = selected == color
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (isSel) 3.dp else 1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable { onSelect(color) }
        )
      }
    }
  }
}

@Composable
private fun GlassTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  singleLine: Boolean = true,
  minLines: Int = 1,
  isPassword: Boolean = false,
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = singleLine,
    minLines = minLines,
    visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    shape = RoundedCornerShape(14.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = Color.White,
      unfocusedTextColor = Color.White,
      focusedLabelColor = Color.White,
      unfocusedLabelColor = Color.White.copy(alpha = 0.85f),
      focusedPlaceholderColor = Color.White.copy(alpha = 0.65f),
      unfocusedPlaceholderColor = Color.White.copy(alpha = 0.65f),
      cursorColor = Color.White,
      focusedContainerColor = Color(0xFF121216),
      unfocusedContainerColor = Color(0xFF0D0D10),
      focusedBorderColor = Color.White,
      unfocusedBorderColor = Color.White.copy(alpha = 0.45f)
    ),
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun ResultOutputCard(
  result: PdfProcessResult,
  accentColor: Color,
  showRawExtractedText: Boolean = true,
  onSaveFile: (ProcessedFile) -> Unit,
  onShareFile: (ProcessedFile) -> Unit,
  onOpenFile: (ProcessedFile) -> Unit
) {
  val glass = LocalGlassColors.current
  GlassSurface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("result_output_card"),
    shape = RoundedCornerShape(22.dp),
    tintGlow = Color(0xFF10B981)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = "Success", tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text("Completed Successfully", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      }

      Text(result.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

      // File size metrics
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(glass.pillFill)
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("OUTPUT FILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(result.primaryFile.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
          Text("RESULT SIZE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f))
          Text(PdfEngine.formatFileSize(result.resultSizeBytes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }

      // Preview thumbnails of generated PDF/images
      if (result.previewBitmaps.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 2.dp)
        ) {
          items(result.previewBitmaps) { bmp ->
            val cachedPreview = remember(bmp) { bmp.asImageBitmap() }
            Image(
              bitmap = cachedPreview,
              contentDescription = "Output preview",
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, glass.cardBorderStart, RoundedCornerShape(10.dp))
            )
          }
        }
      }

      // Extracted OCR text box (hidden when ExtractTextStudioCard is active)
      if (showRawExtractedText && !result.extractedText.isNullOrBlank()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glass.pillFill)
            .padding(12.dp)
        ) {
          Text(
            text = result.extractedText.take(1200),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      // Primary actions: Save As, Share, Open
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = { onSaveFile(result.primaryFile) },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
          Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Save", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        OutlinedButton(
          onClick = { onShareFile(result.primaryFile) },
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Share", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        OutlinedButton(
          onClick = { onOpenFile(result.primaryFile) },
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF121216),
            contentColor = Color.White
          ),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
          Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Open", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
      }

      // Additional split / image outputs if multiple files produced
      if (result.extraFiles.isNotEmpty()) {
        Text(
          text = "Additional Outputs (${result.extraFiles.size} more):",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        result.extraFiles.take(6).forEach { extra ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(glass.pillFill)
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(extra.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onSaveFile(extra) }, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Rounded.SaveAlt, contentDescription = "Save", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onShareFile(extra) }, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}
