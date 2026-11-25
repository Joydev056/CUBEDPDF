package com.example.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class CompressionLevel(val label: String, val maxDim: Int, val jpegQuality: Int) {
  LOW("Light (High Quality)", 1400, 85),
  BALANCED("Balanced (Recommended)", 1050, 65),
  MAXIMUM("Maximum (Smallest Size)", 760, 42)
}

enum class SignaturePosition(val label: String) {
  BOTTOM_RIGHT("Bottom Right"),
  BOTTOM_CENTER("Bottom Center"),
  BOTTOM_LEFT("Bottom Left"),
  CENTER("Center")
}

enum class PageSizeMode(val label: String, val widthPt: Int, val heightPt: Int) {
  A4("A4 (595×842)", 595, 842),
  LETTER("Letter (612×792)", 612, 792),
  FIT_IMAGE("Fit Content", 0, 0)
}

enum class ImageOutputFormat(val label: String, val ext: String, val mime: String) {
  PNG("PNG (Lossless)", "png", "image/png"),
  JPEG("JPEG (Compact)", "jpg", "image/jpeg")
}

enum class ConvertTarget(val label: String, val ext: String, val mime: String) {
  PDF_TO_PNG("PDF → PNG Image", "png", "image/png"),
  PDF_TO_JPEG("PDF → JPEG Image", "jpg", "image/jpeg"),
  PDF_TO_HTML("PDF → HTML Web View", "html", "text/html"),
  TEXT_OR_IMAGE_TO_PDF("Document / Image → PDF", "pdf", "application/pdf")
}

enum class PageNumberPosition(val label: String) {
  BOTTOM_CENTER("Bottom Center"),
  BOTTOM_RIGHT("Bottom Right"),
  TOP_RIGHT("Top Right")
}

enum class PageNumberFormat(val label: String) {
  NUMBER_ONLY("1, 2, 3"),
  PAGE_X("Page 1"),
  PAGE_X_OF_N("Page 1 of N")
}

data class EditStroke(
  val points: List<Pair<Float, Float>>, // normalized 0f..1f
  val colorInt: Int,
  val strokeWidthFraction: Float = 0.008f
)

data class PdfDocumentMeta(
  val uri: Uri,
  val displayName: String,
  val sizeBytes: Long,
  val pageCount: Int,
  val firstPageWidth: Int,
  val firstPageHeight: Int
)

data class ProcessedFile(
  val file: File,
  val mimeType: String,
  val displayName: String,
  val sizeBytes: Long
)

data class ExtractedPageText(
  val pageIndex: Int,
  val pageNumber: Int,
  val text: String,
  val wordCount: Int,
  val characterCount: Int,
  val blockCount: Int
)

data class PdfProcessResult(
  val primaryFile: ProcessedFile,
  val extraFiles: List<ProcessedFile> = emptyList(),
  val pageCount: Int,
  val originalSizeBytes: Long,
  val resultSizeBytes: Long,
  val summary: String,
  val extractedText: String? = null,
  val extractedPages: List<ExtractedPageText> = emptyList(),
  val previewBitmaps: List<Bitmap> = emptyList()
)

object PdfEngine {

  private const val ENC_MAGIC = "PDFTOOLS_AES_V1"

  private fun workDir(context: Context): File {
    return File(context.filesDir, "cubedpdf_documents").apply { mkdirs() }
  }

  internal fun newWorkFile(context: Context, prefix: String, ext: String): File {
    val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return File(workDir(context), "${cleanPrefix}_${System.currentTimeMillis()}.$ext")
  }

  suspend fun persistLocalSnapshotForRecents(
    context: Context,
    uri: Uri,
    preferredName: String
  ): Uri = withContext(Dispatchers.IO) {
    if (uri.scheme == "file") return@withContext uri
    try {
      val safeName = preferredName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document.pdf" }
      val destFile = File(workDir(context), "recent_$safeName")
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      }
      if (destFile.exists() && destFile.length() > 0L) Uri.fromFile(destFile) else uri
    } catch (_: Exception) {
      uri
    }
  }

  fun queryDisplayName(context: Context, uri: Uri): String {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) cursor.getString(idx) else null
        } else null
      } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    } catch (_: Exception) {
      "document.pdf"
    }
  }

  fun querySizeBytes(context: Context, uri: Uri): Long {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (idx >= 0) cursor.getLong(idx) else 0L
        } else 0L
      } ?: run {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
      }
    } catch (_: Exception) {
      0L
    }
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
  }

  private fun openRenderer(pfd: ParcelFileDescriptor, password: String? = null): PdfRenderer {
    return if (!password.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      openRendererApi35(pfd, password)
    } else {
      PdfRenderer(pfd)
    }
  }

  @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
  internal fun openRendererApi35(pfd: ParcelFileDescriptor, password: String): PdfRenderer {
    val params = LoadParams.Builder().setPassword(password).build()
    return PdfRenderer(pfd, params)
  }

  internal fun renderPageToBitmap(page: PdfRenderer.Page, maxDim: Int, whiteBg: Boolean = true): Bitmap {
    val srcW = max(1, page.width)
    val srcH = max(1, page.height)
    val scale = min(maxDim.toFloat() / max(srcW, srcH), 2.5f).coerceAtLeast(0.25f)
    val targetW = max(1, (srcW * scale).roundToInt())
    val targetH = max(1, (srcH * scale).roundToInt())
    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    if (whiteBg) bmp.eraseColor(Color.WHITE)
    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bmp
  }

  internal fun buildPreviewsFromPdf(file: File, maxPages: Int = 4, maxDim: Int = 520): List<Bitmap> {
    val previews = mutableListOf<Bitmap>()
    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val count = min(renderer.pageCount, maxPages)
          for (i in 0 until count) {
            renderer.openPage(i).use { page ->
              previews.add(renderPageToBitmap(page, maxDim))
            }
          }
        }
      }
    } catch (_: Throwable) {
    }
    return previews
  }

  suspend fun createSamplePdf(context: Context, title: String = "CubedPDF Sample Document", pages: Int = 3): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val outFile = newWorkFile(context, "sample", "pdf")
        val doc = PdfDocument()
        try {
          val accents = listOf(Color.rgb(225, 29, 72), Color.rgb(79, 70, 229), Color.rgb(13, 148, 136))
          for (i in 0 until pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            c.drawColor(Color.WHITE)

            val accent = accents[i % accents.size]
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            c.drawRoundRect(RectF(40f, 40f, 555f, 130f), 16f, 16f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.WHITE
              textSize = 22f
              typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText("$title", 60f, 84f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.argb(220, 255, 255, 255)
              textSize = 13f
            }
            c.drawText("Page ${i + 1} of $pages  •  Generated locally by CubedPDF", 60f, 110f, subPaint)

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.rgb(30, 41, 59)
              textSize = 14f
            }
            val sampleBody = """
              This is page ${i + 1} of your sample PDF document. You can merge, split, compress, rotate, watermark, number, redact, sign, OCR, or convert this document right inside CubedPDF.

              Key Capabilities Verified:
              • High-resolution multi-page vector and bitmap PDF rendering
              • Safe Storage Access Framework (SAF) import and export
              • Full compatibility across Android 16, 17, and 18 edge-to-edge UI
              • Zero broad storage permissions required
            """.trimIndent()
            c.save()
            c.translate(48f, 170f)
            val layout = StaticLayout.Builder.obtain(sampleBody, 0, sampleBody.length, bodyPaint, 499)
              .setLineSpacing(4f, 1.25f)
              .build()
            layout.draw(c)
            c.restore()

            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            c.drawRoundRect(RectF(48f, 420f, 547f, 640f), 16f, 16f, cardPaint)
            val chartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            for (b in 0..4) {
              val left = 84f + b * 86f
              val barH = 60f + ((b + i) % 4) * 32f
              c.drawRoundRect(RectF(left, 600f - barH, left + 48f, 600f), 8f, 8f, chartPaint)
            }
            doc.finishPage(page)
          }
          FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
          runCatching { doc.close() }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
      }
    }

  suspend fun inspectPdf(context: Context, uri: Uri, password: String? = null): Result<PdfDocumentMeta> =
    withContext(Dispatchers.IO) {
      runCatching {
        val name = queryDisplayName(context, uri)
        val size = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open selected file.")
        pfd.use { fd ->
          openRenderer(fd, password).use { renderer ->
            if (renderer.pageCount <= 0) throw IllegalStateException("The PDF has no pages.")
            var w = 595
            var h = 842
            renderer.openPage(0).use { first ->
              w = first.width
              h = first.height
            }
            PdfDocumentMeta(
              uri = uri,
              displayName = name,
              sizeBytes = size,
              pageCount = renderer.pageCount,
              firstPageWidth = w,
              firstPageHeight = h
            )
          }
        }
      }
    }

  suspend fun renderThumbnails(
    context: Context,
    uri: Uri,
    maxDim: Int = 340,
    maxPages: Int = 24,
    password: String? = null
  ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
    runCatching {
      val list = mutableListOf<Bitmap>()
      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        openRenderer(fd, password).use { renderer ->
          val count = min(renderer.pageCount, maxPages)
          for (i in 0 until count) {
            renderer.openPage(i).use { page ->
              list.add(renderPageToBitmap(page, maxDim))
            }
          }
        }
      }
      list
    }
  }

  suspend fun renderSinglePage(
    context: Context,
    uri: Uri,
    pageIndex: Int,
    maxDim: Int = 1100,
    password: String? = null
  ): Result<Bitmap> = withContext(Dispatchers.IO) {
    runCatching {
      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        openRenderer(fd, password).use { renderer ->
          val safeIdx = pageIndex.coerceIn(0, max(0, renderer.pageCount - 1))
          renderer.openPage(safeIdx).use { page ->
            renderPageToBitmap(page, maxDim)
          }
        }
      }
    }
  }

  suspend fun saveFileToUri(context: Context, sourceFile: File, destUri: Uri): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val out = context.contentResolver.openOutputStream(destUri)
          ?: throw IllegalStateException("Could not open destination for writing.")
        out.use { output ->
          sourceFile.inputStream().use { input -> input.copyTo(output) }
        }
        Unit
      }
    }

  fun shareFile(context: Context, file: File, mimeType: String): Result<Unit> = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
  }

  fun openFileExternally(context: Context, file: File, mimeType: String): Result<Unit> = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, mimeType)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      throw IllegalStateException("No viewer app installed for $mimeType. Use Save or Share instead.")
    }
  }

  // 1. MERGE PDF
  suspend fun mergePdfs(context: Context, uris: List<Uri>): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(uris.size >= 2) { "Please select at least 2 PDF files to merge." }
      val outFile = newWorkFile(context, "merged", "pdf")
      val outDoc = PdfDocument()
      var totalOriginalSize = 0L
      var outPageNum = 0

      for (uri in uris) {
        totalOriginalSize += querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Could not read one of the selected PDFs.")
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
              renderer.openPage(i).use { page ->
                outPageNum++
                val pageInfo = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), outPageNum).create()
                val outPage = outDoc.startPage(pageInfo)
                val bmp = renderPageToBitmap(page, 1400)
                outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, pageInfo.pageWidth, pageInfo.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
                outDoc.finishPage(outPage)
              }
            }
          }
        }
      }
      if (outPageNum == 0) {
        outDoc.close()
        throw IllegalStateException("No pages found in the selected PDFs.")
      }
      FileOutputStream(outFile).use { outDoc.writeTo(it) }
      outDoc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = outPageNum,
        originalSizeBytes = totalOriginalSize,
        resultSizeBytes = outFile.length(),
        summary = "Merged ${uris.size} documents into $outPageNum pages.",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 2. SPLIT PDF
  suspend fun splitPdf(context: Context, uri: Uri, rangeSpec: String): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalSize = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val outputFiles = mutableListOf<ProcessedFile>()
        var totalPages = 0

        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            totalPages = renderer.pageCount
            val groups = parseRangeGroups(rangeSpec, totalPages)
            if (groups.isEmpty()) throw IllegalArgumentException("Enter valid page numbers between 1 and $totalPages (e.g. 1-2, 3).")

            groups.forEachIndexed { groupIdx, pageIndices ->
              val partFile = newWorkFile(context, "split_part${groupIdx + 1}", "pdf")
              val doc = PdfDocument()
              pageIndices.forEachIndexed { idx, pIdx ->
                renderer.openPage(pIdx).use { srcPage ->
                  val info = PdfDocument.PageInfo.Builder(max(1, srcPage.width), max(1, srcPage.height), idx + 1).create()
                  val outPage = doc.startPage(info)
                  val bmp = renderPageToBitmap(srcPage, 1400)
                  outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                  bmp.recycle()
                  doc.finishPage(outPage)
                }
              }
              FileOutputStream(partFile).use { doc.writeTo(it) }
              doc.close()
              outputFiles.add(ProcessedFile(partFile, "application/pdf", partFile.name, partFile.length()))
            }
          }
        }

        val primary = outputFiles.first()
        val totalResultSize = outputFiles.sumOf { it.sizeBytes }
        PdfProcessResult(
          primaryFile = primary,
          extraFiles = outputFiles.drop(1),
          pageCount = outputFiles.size,
          originalSizeBytes = originalSize,
          resultSizeBytes = totalResultSize,
          summary = "Split $totalPages-page PDF into ${outputFiles.size} separate PDF document(s).",
          previewBitmaps = buildPreviewsFromPdf(primary.file)
        )
      }
    }

  internal fun parseRangeGroups(spec: String, totalPages: Int): List<List<Int>> {
    if (spec.isBlank()) {
      return (0 until totalPages).map { listOf(it) }
    }
    val groups = mutableListOf<List<Int>>()
    val tokens = spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (token in tokens) {
      if (token.contains("-")) {
        val parts = token.split("-").map { it.trim().toIntOrNull() }
        val startRaw = parts.getOrNull(0)
        val endRaw = parts.getOrNull(1)
        if (parts.size == 2 && startRaw != null && endRaw != null) {
          val start = startRaw.coerceIn(1, totalPages)
          val end = endRaw.coerceIn(1, totalPages)
          val range = if (start <= end) (start..end) else (end..start)
          groups.add(range.map { it - 1 })
        }
      } else {
        val single = token.toIntOrNull()
        if (single != null && single in 1..totalPages) {
          groups.add(listOf(single - 1))
        }
      }
    }
    return groups
  }

  // 3. COMPRESS PDF
  suspend fun compressPdf(context: Context, uri: Uri, level: CompressionLevel): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalSize = querySizeBytes(context, uri)
        val outFile = newWorkFile(context, "compressed", "pdf")
        val doc = PdfDocument()
        var pageCount = 0

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
              renderer.openPage(i).use { page ->
                val bmp = renderPageToBitmap(page, level.maxDim)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                bmp.recycle()
                val jpegBytes = baos.toByteArray()
                val compressedBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
                val outPage = doc.startPage(info)
                outPage.canvas.drawBitmap(compressedBmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                compressedBmp.recycle()
                doc.finishPage(outPage)
              }
            }
          }
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val resultSize = outFile.length()
        val savedPct = if (originalSize > 0) (((originalSize - resultSize) * 100.0) / originalSize).roundToInt() else 0
        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, resultSize)
        PdfProcessResult(
          primaryFile = processed,
          pageCount = pageCount,
          originalSizeBytes = originalSize,
          resultSizeBytes = resultSize,
          summary = "Compressed $pageCount pages (${level.label}). ${if (savedPct > 0) "$savedPct% smaller" else "Optimized"}.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 4. EDIT PDF
  suspend fun editPdf(
    context: Context,
    uri: Uri,
    targetPageIndex: Int,
    annotationText: String,
    textColorInt: Int,
    strokes: List<EditStroke>
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "edited", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      var appliedPage = 0
      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      try {
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            pageCount = renderer.pageCount
            val safeTarget = targetPageIndex.coerceIn(0, max(0, pageCount - 1))
            appliedPage = safeTarget
            for (i in 0 until pageCount) {
              renderer.openPage(i).use { page ->
                val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
                val outPage = doc.startPage(info)
                val canvas = outPage.canvas
                val bmp = renderPageToBitmap(page, 1400)
                canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()

                if (i == safeTarget) {
                  val w = info.pageWidth.toFloat()
                  val h = info.pageHeight.toFloat()
                  // Render freehand strokes
                  strokes.forEach { stroke ->
                    if (stroke.points.size >= 2) {
                      val path = Path().apply {
                        moveTo(stroke.points.first().first * w, stroke.points.first().second * h)
                        stroke.points.drop(1).forEach { pt ->
                          lineTo(pt.first * w, pt.second * h)
                        }
                      }
                      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stroke.colorInt
                        style = Paint.Style.STROKE
                        strokeWidth = max(2.5f, w * stroke.strokeWidthFraction)
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                      }
                      canvas.drawPath(path, paint)
                    }
                  }
                  // Render text annotation box
                  if (annotationText.isNotBlank()) {
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                      color = Color.argb(225, 255, 251, 235)
                    }
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                      color = textColorInt
                      style = Paint.Style.STROKE
                      strokeWidth = 2f
                    }
                    val boxRect = RectF(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.22f)
                    canvas.drawRoundRect(boxRect, 10f, 10f, bgPaint)
                    canvas.drawRoundRect(boxRect, 10f, 10f, borderPaint)

                    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                      color = textColorInt
                      textSize = max(13f, w * 0.028f)
                      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.save()
                    canvas.translate(boxRect.left + 14f, boxRect.top + 12f)
                    val layout = StaticLayout.Builder.obtain(
                      annotationText,
                      0,
                      annotationText.length,
                      tp,
                      max(40, (boxRect.width() - 28f).toInt())
                    ).build()
                    layout.draw(canvas)
                    canvas.restore()
                  }
                }
                doc.finishPage(outPage)
              }
            }
          }
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
      } finally {
        doc.close()
      }

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Applied edits to page ${appliedPage + 1} of $pageCount.",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 5. SIGN PDF
  suspend fun signPdf(
    context: Context,
    uri: Uri,
    targetPageIndex: Int,
    signatureStrokes: List<List<Pair<Float, Float>>>, // normalized 0f..1f inside pad
    sigColorInt: Int,
    position: SignaturePosition
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(signatureStrokes.any { it.size >= 2 }) { "Please draw your signature first." }
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "signed", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          pageCount = renderer.pageCount
          val safeTarget = targetPageIndex.coerceIn(0, max(0, pageCount - 1))
          for (i in 0 until pageCount) {
            renderer.openPage(i).use { page ->
              val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
              val outPage = doc.startPage(info)
              val canvas = outPage.canvas
              val bmp = renderPageToBitmap(page, 1400)
              canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
              bmp.recycle()

              if (i == safeTarget) {
                val pw = info.pageWidth.toFloat()
                val ph = info.pageHeight.toFloat()
                val boxW = pw * 0.34f
                val boxH = ph * 0.11f
                val left = when (position) {
                  SignaturePosition.BOTTOM_LEFT -> pw * 0.08f
                  SignaturePosition.BOTTOM_CENTER, SignaturePosition.CENTER -> (pw - boxW) / 2f
                  SignaturePosition.BOTTOM_RIGHT -> pw - boxW - pw * 0.08f
                }
                val top = when (position) {
                  SignaturePosition.CENTER -> (ph - boxH) / 2f
                  else -> ph - boxH - ph * 0.08f
                }

                // Subtle signature backing line
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                  color = Color.argb(120, 100, 116, 139)
                  strokeWidth = 1.5f
                }
                canvas.drawLine(left, top + boxH - 8f, left + boxW, top + boxH - 8f, linePaint)

                val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                  color = sigColorInt
                  style = Paint.Style.STROKE
                  strokeWidth = max(2.2f, boxW * 0.022f)
                  strokeCap = Paint.Cap.ROUND
                  strokeJoin = Paint.Join.ROUND
                }
                signatureStrokes.forEach { pts ->
                  if (pts.size >= 2) {
                    val path = Path().apply {
                      moveTo(left + pts.first().first * boxW, top + pts.first().second * (boxH - 12f))
                      pts.drop(1).forEach { pt ->
                        lineTo(left + pt.first * boxW, top + pt.second * (boxH - 12f))
                      }
                    }
                    canvas.drawPath(path, sigPaint)
                  }
                }
              }
              doc.finishPage(outPage)
            }
          }
        }
      }
      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Signed page ${targetPageIndex + 1} (${position.label}).",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 6. PDF CONVERTER
  suspend fun convertPdf(context: Context, uri: Uri, target: ConvertTarget): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      when (target) {
        ConvertTarget.PDF_TO_PNG -> pdfToImages(context, uri, ImageOutputFormat.PNG)
        ConvertTarget.PDF_TO_JPEG -> pdfToImages(context, uri, ImageOutputFormat.JPEG)
        ConvertTarget.PDF_TO_HTML -> runCatching {
          val originalSize = querySizeBytes(context, uri)
          val name = queryDisplayName(context, uri)
          val outFile = newWorkFile(context, "converted", "html")
          val ocrRes = ocrPdf(context, uri).getOrNull()
          val bodyText = ocrRes?.extractedText ?: "Rendered PDF export for $name"
          val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>$name</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 32px auto; max-width: 780px; padding: 0 20px; color: #0f172a; line-height: 1.65; }
                header { border-bottom: 2px solid #e11d48; padding-bottom: 12px; margin-bottom: 24px; }
                pre { white-space: pre-wrap; font-family: inherit; background: #f8fafc; padding: 20px; border-radius: 12px; border: 1px solid #e2e8f0; }
              </style>
            </head>
            <body>
              <header><h1>$name</h1><p>Converted to HTML by CubedPDF</p></header>
              <pre>${bodyText.replace("<", "&lt;").replace(">", "&gt;")}</pre>
            </body>
            </html>
          """.trimIndent()
          outFile.writeText(html)
          val processed = ProcessedFile(outFile, "text/html", outFile.name, outFile.length())
          PdfProcessResult(
            primaryFile = processed,
            pageCount = ocrRes?.pageCount ?: 1,
            originalSizeBytes = originalSize,
            resultSizeBytes = outFile.length(),
            summary = "Converted $name to HTML webpage document.",
            extractedText = bodyText
          )
        }
        ConvertTarget.TEXT_OR_IMAGE_TO_PDF -> runCatching {
          val mime = context.contentResolver.getType(uri) ?: ""
          if (mime.startsWith("image/")) {
            imagesToPdf(context, listOf(uri), PageSizeMode.A4).getOrThrow()
          } else {
            val textContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
              ?: throw IllegalStateException("Unable to read source document.")
            val title = queryDisplayName(context, uri)
            createPdf(context, title, "Converted Document", textContent, PageSizeMode.A4, Color.rgb(2, 132, 199), emptyList()).getOrThrow()
          }
        }
      }
    }

  // 7. IMAGES TO PDF
  suspend fun imagesToPdf(
    context: Context,
    imageUris: List<Uri>,
    pageSizeMode: PageSizeMode
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(imageUris.isNotEmpty()) { "Please select at least one image." }
      val outFile = newWorkFile(context, "images_to_pdf", "pdf")
      val doc = PdfDocument()
      var totalOriginal = 0L

      imageUris.forEachIndexed { index, uri ->
        totalOriginal += querySizeBytes(context, uri)
        val bmp = decodeSampledBitmap(context, uri, 1600)
          ?: throw IllegalStateException("Could not decode image #${index + 1}.")
        val pageW = if (pageSizeMode == PageSizeMode.FIT_IMAGE) bmp.width else pageSizeMode.widthPt
        val pageH = if (pageSizeMode == PageSizeMode.FIT_IMAGE) bmp.height else pageSizeMode.heightPt
        val info = PdfDocument.PageInfo.Builder(max(1, pageW), max(1, pageH), index + 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        if (pageSizeMode == PageSizeMode.FIT_IMAGE) {
          canvas.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
          val margin = 28f
          val availW = pageW - margin * 2f
          val availH = pageH - margin * 2f
          val scale = min(availW / bmp.width, availH / bmp.height)
          val drawW = bmp.width * scale
          val drawH = bmp.height * scale
          val left = (pageW - drawW) / 2f
          val top = (pageH - drawH) / 2f
          canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bmp.recycle()
        doc.finishPage(page)
      }

      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = imageUris.size,
        originalSizeBytes = totalOriginal,
        resultSizeBytes = outFile.length(),
        summary = "Converted ${imageUris.size} image(s) into a ${imageUris.size}-page PDF.",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  internal fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    val w = opts.outWidth
    val h = opts.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (max(w / sample, h / sample) > maxDim) {
      sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
  }

  // 8. PDF TO IMAGES
  suspend fun pdfToImages(
    context: Context,
    uri: Uri,
    format: ImageOutputFormat
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = querySizeBytes(context, uri)
      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      val files = mutableListOf<ProcessedFile>()
      val previews = mutableListOf<Bitmap>()

      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
              val bmp = renderPageToBitmap(page, 1400)
              val imgFile = newWorkFile(context, "page_${i + 1}", format.ext)
              FileOutputStream(imgFile).use { out ->
                val cf = if (format == ImageOutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                bmp.compress(cf, 92, out)
              }
              if (previews.size < 4) {
                previews.add(Bitmap.createScaledBitmap(bmp, min(480, bmp.width), max(1, (bmp.height * min(480f / bmp.width, 1f)).toInt()), true))
              }
              bmp.recycle()
              files.add(ProcessedFile(imgFile, format.mime, imgFile.name, imgFile.length()))
            }
          }
        }
      }
      if (files.isEmpty()) throw IllegalStateException("No pages exported.")
      val totalOut = files.sumOf { it.sizeBytes }
      PdfProcessResult(
        primaryFile = files.first(),
        extraFiles = files.drop(1),
        pageCount = files.size,
        originalSizeBytes = originalSize,
        resultSizeBytes = totalOut,
        summary = "Exported ${files.size} ${format.name} image(s) from PDF.",
        previewBitmaps = previews
      )
    }
  }

  // 9. EXTRACT IMAGES FROM PDF
  suspend fun extractImages(context: Context, uri: Uri): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalSize = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val files = mutableListOf<ProcessedFile>()
        val previews = mutableListOf<Bitmap>()

        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
              renderer.openPage(i).use { page ->
                val bmp = renderPageToBitmap(page, 1200)
                val cropped = cropNonWhiteBounds(bmp) ?: bmp
                val imgFile = newWorkFile(context, "extracted_img_${i + 1}", "png")
                FileOutputStream(imgFile).use { out ->
                  cropped.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                if (previews.size < 4) {
                  val previewScale = min(480f / max(1, cropped.width), 1f)
                  previews.add(
                    Bitmap.createScaledBitmap(
                      cropped,
                      max(1, (cropped.width * previewScale).toInt()),
                      max(1, (cropped.height * previewScale).toInt()),
                      true
                    )
                  )
                }
                if (cropped !== bmp) cropped.recycle()
                bmp.recycle()
                files.add(ProcessedFile(imgFile, "image/png", imgFile.name, imgFile.length()))
              }
            }
          }
        }
        if (files.isEmpty()) throw IllegalStateException("No visual regions found to extract.")
        PdfProcessResult(
          primaryFile = files.first(),
          extraFiles = files.drop(1),
          pageCount = files.size,
          originalSizeBytes = originalSize,
          resultSizeBytes = files.sumOf { it.sizeBytes },
          summary = "Extracted ${files.size} trimmed PNG asset(s) from document pages.",
          previewBitmaps = previews
        )
      }
    }

  private fun cropNonWhiteBounds(bmp: Bitmap): Bitmap? {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    val step = 2
    for (y in 0 until h step step) {
      val rowOffset = y * w
      for (x in 0 until w step step) {
        val c = pixels[rowOffset + x]
        if (Color.red(c) < 245 || Color.green(c) < 245 || Color.blue(c) < 245) {
          if (x < minX) minX = x
          if (y < minY) minY = y
          if (x > maxX) maxX = x
          if (y > maxY) maxY = y
        }
      }
    }
    if (maxX <= minX || maxY <= minY) return null
    val pad = 12
    val l = (minX - pad).coerceAtLeast(0)
    val t = (minY - pad).coerceAtLeast(0)
    val r = (maxX + pad).coerceAtMost(w)
    val b = (maxY + pad).coerceAtMost(h)
    return Bitmap.createBitmap(bmp, l, t, max(1, r - l), max(1, b - t))
  }

  // 10. PROTECT PDF (Delegates to dedicated PdfCryptoService AES-256-GCM engine)
  suspend fun protectPdf(context: Context, uri: Uri, password: String): Result<PdfProcessResult> =
    PdfCryptoService.encryptPdfFile(context, uri, password)

  // 11. UNLOCK PDF (Delegates to dedicated PdfCryptoService for AES-256 Vaults + Android 15+ native PDFs)
  suspend fun unlockPdf(context: Context, uri: Uri, password: String): Result<PdfProcessResult> =
    PdfCryptoService.decryptPdfFile(context, uri, password)

  // 12. ROTATE PAGES (Supports independent 90°/180°/270° per-page rotations + batch rotation)
  suspend fun rotatePages(
    context: Context,
    uri: Uri,
    degrees: Int,
    selectedPages: Set<Int> // empty = all
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val meta = inspectPdf(context, uri).getOrThrow()
      val rotations = PdfRotationSupport.buildPageRotationsMap(
        totalPages = meta.pageCount,
        perPageRotations = emptyMap(),
        fallbackPages = selectedPages,
        fallbackDegrees = degrees
      )
      rotateSpecificPages(context, uri, rotations).getOrThrow()
    }
  }

  suspend fun rotateSpecificPages(
    context: Context,
    uri: Uri,
    pageRotations: Map<Int, Int>
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "rotated", "pdf")
      val doc = PdfDocument()
      var pageCount = 0
      var effectiveRotations: Map<Int, Int> = emptyMap()

      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      try {
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            pageCount = renderer.pageCount
            require(pageCount > 0) { "The selected PDF has no readable pages." }
            effectiveRotations = pageRotations.entries
              .mapNotNull { (idx, deg) ->
                val norm = PdfRotationSupport.normalizeDegrees(deg)
                if (idx in 0 until pageCount && norm != 0) idx to norm else null
              }
              .toMap()
            require(effectiveRotations.isNotEmpty()) {
              "Select at least one page and a 90°, 180°, or 270° rotation angle."
            }

            for (i in 0 until pageCount) {
              renderer.openPage(i).use { page ->
                val deg = effectiveRotations[i] ?: 0
                val swap = deg == 90 || deg == 270
                val (outW, outH) = PdfRotationSupport.computeRotatedDimensions(page.width, page.height, deg)
                val info = PdfDocument.PageInfo.Builder(outW, outH, i + 1).create()
                val outPage = doc.startPage(info)
                val bmp = renderPageToBitmap(page, 1400)

                val matrix = Matrix()
                val sx = page.width.toFloat() / bmp.width
                val sy = page.height.toFloat() / bmp.height
                matrix.postScale(sx, sy)
                if (deg != 0) {
                  matrix.postRotate(deg.toFloat(), page.width / 2f, page.height / 2f)
                  if (swap) {
                    matrix.postTranslate((outW - page.width) / 2f, (outH - page.height) / 2f)
                  }
                }
                outPage.canvas.drawBitmap(bmp, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
                doc.finishPage(outPage)
              }
            }
          }
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
      } finally {
        runCatching { doc.close() }
      }

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = PdfRotationSupport.summarizeRotations(effectiveRotations, pageCount),
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 13. REMOVE PAGES
  suspend fun removePages(context: Context, uri: Uri, pagesToRemove: Set<Int>): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalSize = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val outFile = newWorkFile(context, "pages_removed", "pdf")
        val doc = PdfDocument()
        var kept = 0
        var total = 0

        try {
          pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
              total = renderer.pageCount
              val remaining = (0 until total).filter { !pagesToRemove.contains(it) }
              require(remaining.isNotEmpty()) { "You cannot delete every page. Keep at least 1 page." }
              remaining.forEachIndexed { idx, srcIdx ->
                renderer.openPage(srcIdx).use { page ->
                  val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), idx + 1).create()
                  val outPage = doc.startPage(info)
                  val bmp = renderPageToBitmap(page, 1400)
                  outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                  bmp.recycle()
                  doc.finishPage(outPage)
                  kept++
                }
              }
            }
          }
          FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
          doc.close()
        }

        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
        PdfProcessResult(
          primaryFile = processed,
          pageCount = kept,
          originalSizeBytes = originalSize,
          resultSizeBytes = outFile.length(),
          summary = "Removed ${total - kept} page(s). $kept page(s) remaining.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 14. EXTRACT PAGES
  suspend fun extractPages(context: Context, uri: Uri, pagesToKeep: Set<Int>): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(pagesToKeep.isNotEmpty()) { "Select at least 1 page to extract." }
        val originalSize = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val outFile = newWorkFile(context, "extracted_pages", "pdf")
        val doc = PdfDocument()
        val sorted = pagesToKeep.sorted()
        var extractedCount = 0

        try {
          pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
              val validIndices = sorted.filter { it in 0 until renderer.pageCount }
              require(validIndices.isNotEmpty()) { "Selected page numbers are out of range for this PDF." }
              validIndices.forEachIndexed { idx, srcIdx ->
                renderer.openPage(srcIdx).use { page ->
                  val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), idx + 1).create()
                  val outPage = doc.startPage(info)
                  val bmp = renderPageToBitmap(page, 1400)
                  outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                  bmp.recycle()
                  doc.finishPage(outPage)
                  extractedCount++
                }
              }
            }
          }
          FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
          doc.close()
        }

        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
        PdfProcessResult(
          primaryFile = processed,
          pageCount = extractedCount,
          originalSizeBytes = originalSize,
          resultSizeBytes = outFile.length(),
          summary = "Extracted $extractedCount selected page(s) into a new PDF.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 15. REARRANGE PAGES
  suspend fun rearrangePages(context: Context, uri: Uri, newOrder: List<Int>): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(newOrder.isNotEmpty()) { "Page order list cannot be empty." }
        val originalSize = querySizeBytes(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val outFile = newWorkFile(context, "rearranged", "pdf")
        val doc = PdfDocument()
        var writtenCount = 0

        try {
          pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
              val validIndices = newOrder.filter { it in 0 until renderer.pageCount }
              require(validIndices.isNotEmpty()) { "Selected page order contains no valid pages for this PDF." }
              validIndices.forEachIndexed { idx, srcIdx ->
                renderer.openPage(srcIdx).use { page ->
                  val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), idx + 1).create()
                  val outPage = doc.startPage(info)
                  val bmp = renderPageToBitmap(page, 1400)
                  outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                  bmp.recycle()
                  doc.finishPage(outPage)
                  writtenCount++
                }
              }
            }
          }
          FileOutputStream(outFile).use { doc.writeTo(it) }
        } finally {
          doc.close()
        }

        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
        PdfProcessResult(
          primaryFile = processed,
          pageCount = writtenCount,
          originalSizeBytes = originalSize,
          resultSizeBytes = outFile.length(),
          summary = "Reordered $writtenCount page(s) into your custom sequence.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 16. WEBPAGE TO PDF
  suspend fun webpageToPdf(context: Context, rawInput: String): Result<PdfProcessResult> = runCatching {
    val trimmed = rawInput.trim()
    require(trimmed.isNotEmpty()) { "Enter a website URL or HTML snippet." }
    val isHtml = trimmed.startsWith("<")
    val url = if (isHtml) {
      trimmed
    } else {
      val lower = trimmed.lowercase()
      when {
        lower.startsWith("https://") -> trimmed
        lower.startsWith("http://") ||
          lower.startsWith("file://") ||
          lower.startsWith("content://") ||
          lower.startsWith("javascript:") ||
          lower.startsWith("data:") ||
          trimmed.contains("://") ->
          throw IllegalArgumentException("Only secure https:// website URLs or HTML snippets are supported.")
        else -> "https://$trimmed"
      }
    }

    val capturedBitmap = withTimeoutOrNull(18_000L) {
      withContext(Dispatchers.Main) {
        WebView.enableSlowWholeDocumentDraw()
        suspendCancellableCoroutine<Bitmap?> { cont ->
          val webView = WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              settings.safeBrowsingEnabled = true
            }
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            layout(0, 0, 1080, 1920)
          }
          var completed = false
          webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
              val scheme = request?.url?.scheme?.lowercase()
              return scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String?) {
              if (completed) return
              view.postDelayed({
                if (!completed) {
                  completed = true
                  try {
                    val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
                    val contentH = max(1200, (view.contentHeight * density).toInt()).coerceAtMost(7200)
                    val w = 1080
                    view.measure(
                      android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                      android.view.View.MeasureSpec.makeMeasureSpec(contentH, android.view.View.MeasureSpec.EXACTLY)
                    )
                    view.layout(0, 0, w, contentH)
                    val bmp = Bitmap.createBitmap(w, contentH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val c = Canvas(bmp)
                    view.draw(c)
                    view.destroy()
                    if (cont.isActive) cont.resume(bmp)
                  } catch (_: Exception) {
                    view.destroy()
                    if (cont.isActive) cont.resume(null)
                  }
                }
              }, 450L)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
              if (request?.isForMainFrame == true && !completed) {
                completed = true
                view?.destroy()
                if (cont.isActive) cont.resume(null)
              }
            }
          }
          cont.invokeOnCancellation { webView.destroy() }
          if (isHtml) {
            webView.loadDataWithBaseURL(null, trimmed, "text/html", "utf-8", null)
          } else {
            webView.loadUrl(url)
          }
        }
      }
    } ?: throw IllegalStateException("Could not load webpage. Check internet connection or URL.")

    withContext(Dispatchers.IO) {
      val outFile = newWorkFile(context, "webpage", "pdf")
      val doc = PdfDocument()
      var pageNum = 0
      try {
        val pageW = 595
        val pageH = 842
        val sliceHeightPx = max(1, (capturedBitmap.width * (pageH.toFloat() / pageW)).roundToInt())
        var y = 0
        while (y < capturedBitmap.height) {
          pageNum++
          val h = min(sliceHeightPx, capturedBitmap.height - y)
          val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
          val page = doc.startPage(info)
          page.canvas.drawColor(Color.WHITE)
          val destH = (h.toFloat() / sliceHeightPx) * pageH
          page.canvas.drawBitmap(
            capturedBitmap,
            Rect(0, y, capturedBitmap.width, y + h),
            RectF(0f, 0f, pageW.toFloat(), destH),
            Paint(Paint.FILTER_BITMAP_FLAG)
          )
          doc.finishPage(page)
          y += h
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
      } finally {
        capturedBitmap.recycle()
        runCatching { doc.close() }
      }

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageNum,
        originalSizeBytes = 0L,
        resultSizeBytes = outFile.length(),
        summary = "Captured webpage into $pageNum PDF page(s).",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 17. EXTRACT TEXT / PDF OCR (On-Device ML Kit Text Recognition for PDFs & Scanned Images)
  suspend fun ocrPdf(
    context: Context,
    uri: Uri,
    selectedPages: Set<Int> = emptySet(),
    pageRangeExpression: String = "",
    includePageHeaders: Boolean = true,
    onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
  ): Result<PdfProcessResult> = PdfOcrService.extractTextFromScannedPdf(
    context = context,
    uri = uri,
    selectedPages = selectedPages,
    pageRangeExpression = pageRangeExpression,
    includePageHeaders = includePageHeaders,
    onProgress = onProgress
  )

  /**
   * Runs on-device ML Kit Text Recognition across one or more scanned document images/photos.
   * Delegates to [PdfOcrService.extractTextFromImages].
   */
  suspend fun ocrImages(
    context: Context,
    imageUris: List<Uri>,
    includePageHeaders: Boolean = true,
    onProgress: ((currentImage: Int, totalImages: Int) -> Unit)? = null
  ): Result<PdfProcessResult> = PdfOcrService.extractTextFromImages(
    context = context,
    imageUris = imageUris,
    includePageHeaders = includePageHeaders,
    onProgress = onProgress
  )

  /**
   * Saves user-edited extracted text into a clean UTF-8 .txt file ready for SAF export or sharing.
   */
  suspend fun saveEditedTextAsTxt(
    context: Context,
    baseName: String,
    editedText: String
  ): Result<ProcessedFile> = PdfOcrService.savePlainTextFile(
    context = context,
    desiredFileName = baseName,
    textContent = editedText
  )

  /**
   * Generates a clean, paginated searchable PDF document from ML Kit extracted & edited text.
   */
  suspend fun createSearchablePdfFromText(
    context: Context,
    documentTitle: String,
    editedText: String
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(editedText.isNotBlank()) { "Extracted text is empty. Nothing to export." }
      val pageW = 595
      val pageH = 842
      val marginX = 44f
      val contentW = (pageW - marginX * 2).toInt()
      val headerBottom = 92f
      val footerTop = pageH - 46f

      val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D9488")
        textSize = 10.5f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        textSize = 11.5f
        typeface = Typeface.SANS_SERIF
      }
      val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 9.5f
        textAlign = Paint.Align.CENTER
      }
      val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        strokeWidth = 1f
      }

      val fullLayout = StaticLayout.Builder
        .obtain(editedText, 0, editedText.length, bodyPaint, contentW)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(4f, 1.15f)
        .build()

      val outFile = newWorkFile(context, "searchable_ocr", "pdf")
      val doc = PdfDocument()
      var pageNumber = 0

      try {
        var lineIndex = 0
        val totalLines = max(1, fullLayout.lineCount)
        while (lineIndex < totalLines) {
          pageNumber++
          val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
          val page = doc.startPage(pageInfo)
          val canvas = page.canvas
          canvas.drawColor(Color.WHITE)

          // Header on page 1, subtle running header on subsequent pages
          val startY = if (pageNumber == 1) {
            canvas.drawText("ML KIT EXTRACTED TEXT • SEARCHABLE PDF", marginX, 42f, subPaint)
            canvas.drawText(documentTitle.ifBlank { "Extracted Document" }.take(56), marginX, 66f, titlePaint)
            canvas.drawLine(marginX, 78f, pageW - marginX, 78f, dividerPaint)
            headerBottom
          } else {
            canvas.drawText(documentTitle.ifBlank { "Extracted Document" }.take(64), marginX, 40f, subPaint)
            canvas.drawLine(marginX, 50f, pageW - marginX, 50f, dividerPaint)
            66f
          }

          var cursorY = startY
          while (lineIndex < totalLines) {
            val lineHeight = (fullLayout.getLineBottom(lineIndex) - fullLayout.getLineTop(lineIndex)).toFloat().coerceAtLeast(14f)
            if (cursorY + lineHeight > footerTop && cursorY > startY) {
              break
            }
            val lineStart = fullLayout.getLineStart(lineIndex)
            val lineEnd = fullLayout.getLineEnd(lineIndex)
            val lineText = editedText.substring(lineStart, lineEnd).trimEnd('\n', '\r')
            val baselineOffset = (fullLayout.getLineBaseline(lineIndex) - fullLayout.getLineTop(lineIndex)).toFloat()
            canvas.drawText(lineText, marginX, cursorY + baselineOffset, bodyPaint)
            cursorY += lineHeight
            lineIndex++
          }

          canvas.drawText("Page $pageNumber • CubedPDF Searchable Export", pageW / 2f, pageH - 22f, footerPaint)
          doc.finishPage(page)
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
      } finally {
        runCatching { doc.close() }
      }

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageNumber,
        originalSizeBytes = editedText.toByteArray(Charsets.UTF_8).size.toLong(),
        resultSizeBytes = outFile.length(),
        summary = "Saved editable text into a clean $pageNumber-page searchable PDF.",
        previewBitmaps = buildPreviewsFromPdf(outFile),
        extractedText = editedText
      )
    }
  }

  // 18. ADD WATERMARK
  suspend fun addWatermark(
    context: Context,
    uri: Uri,
    watermarkText: String,
    opacity: Float,
    angleDeg: Float,
    colorInt: Int
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(watermarkText.isNotBlank()) { "Enter watermark text." }
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "watermarked", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          pageCount = renderer.pageCount
          for (i in 0 until pageCount) {
            renderer.openPage(i).use { page ->
              val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
              val outPage = doc.startPage(info)
              val canvas = outPage.canvas
              val bmp = renderPageToBitmap(page, 1400)
              canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
              bmp.recycle()

              val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorInt
                alpha = (opacity.coerceIn(0.08f, 0.9f) * 255).roundToInt()
                textSize = max(24f, info.pageWidth * 0.085f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
              }
              canvas.save()
              canvas.rotate(angleDeg, info.pageWidth / 2f, info.pageHeight / 2f)
              canvas.drawText(watermarkText, info.pageWidth / 2f, info.pageHeight / 2f, paint)
              canvas.restore()

              doc.finishPage(outPage)
            }
          }
        }
      }
      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Stamped \"$watermarkText\" watermark onto $pageCount page(s).",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 19. ADD PAGE NUMBERS
  suspend fun addPageNumbers(
    context: Context,
    uri: Uri,
    position: PageNumberPosition,
    startAt: Int,
    format: PageNumberFormat
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "numbered", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          pageCount = renderer.pageCount
          for (i in 0 until pageCount) {
            renderer.openPage(i).use { page ->
              val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
              val outPage = doc.startPage(info)
              val canvas = outPage.canvas
              val bmp = renderPageToBitmap(page, 1400)
              canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
              bmp.recycle()

              val num = startAt + i
              val totalLabel = startAt + pageCount - 1
              val text = when (format) {
                PageNumberFormat.NUMBER_ONLY -> "$num"
                PageNumberFormat.PAGE_X -> "Page $num"
                PageNumberFormat.PAGE_X_OF_N -> "Page $num of $totalLabel"
              }
              val pw = info.pageWidth.toFloat()
              val ph = info.pageHeight.toFloat()
              val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = max(11f, pw * 0.022f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = when (position) {
                  PageNumberPosition.BOTTOM_CENTER -> Paint.Align.CENTER
                  PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> Paint.Align.RIGHT
                }
              }
              val x = when (position) {
                PageNumberPosition.BOTTOM_CENTER -> pw / 2f
                PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> pw - pw * 0.06f
              }
              val y = when (position) {
                PageNumberPosition.TOP_RIGHT -> ph * 0.055f
                else -> ph - ph * 0.04f
              }
              canvas.drawText(text, x, y, paint)
              doc.finishPage(outPage)
            }
          }
        }
      }
      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Added page numbers (${position.label}) across $pageCount page(s).",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 20. PDF OVERLAY
  suspend fun overlayPdf(
    context: Context,
    baseUri: Uri,
    overlayUri: Uri,
    overlayAlpha: Float
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = querySizeBytes(context, baseUri)
      val outFile = newWorkFile(context, "overlaid", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      val basePfd = context.contentResolver.openFileDescriptor(baseUri, "r")
        ?: throw IllegalStateException("Unable to open base PDF.")
      val overlayPfd = context.contentResolver.openFileDescriptor(overlayUri, "r")
        ?: throw IllegalStateException("Unable to open overlay PDF.")

      basePfd.use { bFd ->
        overlayPfd.use { oFd ->
          PdfRenderer(bFd).use { baseRenderer ->
            PdfRenderer(oFd).use { overlayRenderer ->
              pageCount = baseRenderer.pageCount
              val overlayPages = overlayRenderer.pageCount
              if (overlayPages <= 0) throw IllegalStateException("Overlay PDF has no pages.")

              for (i in 0 until pageCount) {
                baseRenderer.openPage(i).use { basePage ->
                  val info = PdfDocument.PageInfo.Builder(max(1, basePage.width), max(1, basePage.height), i + 1).create()
                  val outPage = doc.startPage(info)
                  val baseBmp = renderPageToBitmap(basePage, 1400)
                  outPage.canvas.drawBitmap(baseBmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                  baseBmp.recycle()

                  val overlayIdx = i % overlayPages
                  overlayRenderer.openPage(overlayIdx).use { ovPage ->
                    val ovBmp = renderPageToBitmap(ovPage, 1400, whiteBg = false)
                    val ovPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                      alpha = (overlayAlpha.coerceIn(0.1f, 1.0f) * 255).roundToInt()
                    }
                    outPage.canvas.drawBitmap(ovBmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), ovPaint)
                    ovBmp.recycle()
                  }
                  doc.finishPage(outPage)
                }
              }
            }
          }
        }
      }
      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Overlaid graphics onto $pageCount page(s).",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 21. COMPARE PDFs
  suspend fun comparePdfs(context: Context, uriA: Uri, uriB: Uri): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sizeA = querySizeBytes(context, uriA)
        val sizeB = querySizeBytes(context, uriB)
        val pfdA = context.contentResolver.openFileDescriptor(uriA, "r")
          ?: throw IllegalStateException("Unable to open first PDF.")
        val pfdB = context.contentResolver.openFileDescriptor(uriB, "r")
          ?: throw IllegalStateException("Unable to open second PDF.")
        val outFile = newWorkFile(context, "diff_report", "pdf")
        val doc = PdfDocument()
        var comparedPages = 0
        var totalDiffPercent = 0.0

        pfdA.use { fdA ->
          pfdB.use { fdB ->
            PdfRenderer(fdA).use { renA ->
              PdfRenderer(fdB).use { renB ->
                val maxPages = max(renA.pageCount, renB.pageCount)
                comparedPages = maxPages
                for (i in 0 until maxPages) {
                  val bmpA = if (i < renA.pageCount) renA.openPage(i).use { renderPageToBitmap(it, 600) } else null
                  val bmpB = if (i < renB.pageCount) renB.openPage(i).use { renderPageToBitmap(it, 600) } else null

                  val w = 842
                  val h = 595
                  val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                  val outPage = doc.startPage(info)
                  val canvas = outPage.canvas
                  canvas.drawColor(Color.WHITE)

                  val diffBmp = Bitmap.createBitmap(400, 480, Bitmap.Config.ARGB_8888)
                  diffBmp.eraseColor(Color.WHITE)
                  var diffCount = 0L
                  val totalPx = 400L * 480L

                  val scaledA = bmpA?.let { Bitmap.createScaledBitmap(it, 400, 480, true) }
                  val scaledB = bmpB?.let { Bitmap.createScaledBitmap(it, 400, 480, true) }

                  if (scaledA != null && scaledB != null) {
                    val pA = IntArray(400 * 480)
                    val pB = IntArray(400 * 480)
                    val pD = IntArray(400 * 480)
                    scaledA.getPixels(pA, 0, 400, 0, 0, 400, 480)
                    scaledB.getPixels(pB, 0, 400, 0, 0, 400, 480)
                    for (idx in pA.indices) {
                      val dr = abs(Color.red(pA[idx]) - Color.red(pB[idx]))
                      val dg = abs(Color.green(pA[idx]) - Color.green(pB[idx]))
                      val db = abs(Color.blue(pA[idx]) - Color.blue(pB[idx]))
                      if (dr + dg + db > 45) {
                        diffCount++
                        pD[idx] = Color.rgb(225, 29, 72) // bright crimson highlight
                      } else {
                        val lum = (Color.red(pA[idx]) + Color.green(pA[idx]) + Color.blue(pA[idx])) / 3
                        val faded = 200 + (lum / 5)
                        pD[idx] = Color.rgb(faded.coerceAtMost(255), faded.coerceAtMost(255), faded.coerceAtMost(255))
                      }
                    }
                    diffBmp.setPixels(pD, 0, 400, 0, 0, 400, 480)
                  } else {
                    diffCount = totalPx
                    diffBmp.eraseColor(Color.rgb(254, 205, 211))
                  }

                  val pagePct = (diffCount * 100.0) / totalPx
                  totalDiffPercent += pagePct

                  val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(15, 23, 42)
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                  }
                  canvas.drawText("Page ${i + 1} Comparison  •  Visual Difference: ${String.format("%.1f", pagePct)}%", 24f, 34f, labelPaint)
                  scaledA?.let { canvas.drawBitmap(it, null, Rect(24, 54, 274, 410), Paint()) }
                  scaledB?.let { canvas.drawBitmap(it, null, Rect(296, 54, 546, 410), Paint()) }
                  canvas.drawBitmap(diffBmp, null, Rect(568, 54, 818, 410), Paint())

                  val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(71, 85, 105)
                    textSize = 11f
                  }
                  canvas.drawText("Document A", 24f, 430f, capPaint)
                  canvas.drawText("Document B", 296f, 430f, capPaint)
                  canvas.drawText("Highlighted Diff", 568f, 430f, capPaint)

                  bmpA?.recycle()
                  bmpB?.recycle()
                  scaledA?.recycle()
                  scaledB?.recycle()
                  diffBmp.recycle()
                  doc.finishPage(outPage)
                }
              }
            }
          }
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val avgDiff = if (comparedPages > 0) totalDiffPercent / comparedPages else 0.0
        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
        PdfProcessResult(
          primaryFile = processed,
          pageCount = comparedPages,
          originalSizeBytes = sizeA + sizeB,
          resultSizeBytes = outFile.length(),
          summary = "Compared $comparedPages page(s). Average visual difference: ${String.format("%.1f", avgDiff)}%.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 22. WEB OPTIMIZE PDF
  suspend fun webOptimizePdf(context: Context, uri: Uri): Result<PdfProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalSize = querySizeBytes(context, uri)
        val outFile = newWorkFile(context, "web_optimized", "pdf")
        val doc = PdfDocument()
        var pageCount = 0

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
              renderer.openPage(i).use { page ->
                val bmp = renderPageToBitmap(page, 960)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                bmp.recycle()
                val optBytes = baos.toByteArray()
                val optBmp = BitmapFactory.decodeByteArray(optBytes, 0, optBytes.size)

                // Standardize to crisp 72dpi web dimensions (max width 595pt)
                val ratio = page.height.toFloat() / max(1, page.width)
                val targetW = min(595, max(320, page.width))
                val targetH = max(1, (targetW * ratio).roundToInt())
                val info = PdfDocument.PageInfo.Builder(targetW, targetH, i + 1).create()
                val outPage = doc.startPage(info)
                outPage.canvas.drawBitmap(optBmp, null, Rect(0, 0, targetW, targetH), Paint(Paint.FILTER_BITMAP_FLAG))
                optBmp.recycle()
                doc.finishPage(outPage)
              }
            }
          }
        }
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
        PdfProcessResult(
          primaryFile = processed,
          pageCount = pageCount,
          originalSizeBytes = originalSize,
          resultSizeBytes = outFile.length(),
          summary = "Web-optimized $pageCount page(s) to fast-loading 72dpi stream format.",
          previewBitmaps = buildPreviewsFromPdf(outFile)
        )
      }
    }

  // 23. REDACT PDF (Permanent rasterized blackout)
  suspend fun redactPdf(
    context: Context,
    uri: Uri,
    targetPageIndex: Int,
    redactionRects: List<RectF> // normalized 0f..1f
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(redactionRects.isNotEmpty()) { "Add at least one redaction box on the page preview." }
      val originalSize = querySizeBytes(context, uri)
      val outFile = newWorkFile(context, "redacted", "pdf")
      val doc = PdfDocument()
      var pageCount = 0

      val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("Unable to open PDF.")
      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          pageCount = renderer.pageCount
          val safeTarget = targetPageIndex.coerceIn(0, max(0, pageCount - 1))
          for (i in 0 until pageCount) {
            renderer.openPage(i).use { page ->
              val info = PdfDocument.PageInfo.Builder(max(1, page.width), max(1, page.height), i + 1).create()
              val outPage = doc.startPage(info)
              val bmp = renderPageToBitmap(page, 1400)
              if (i == safeTarget) {
                val bmpCanvas = Canvas(bmp)
                val blackPaint = Paint().apply {
                  color = Color.BLACK
                  style = Paint.Style.FILL
                }
                redactionRects.forEach { norm ->
                  bmpCanvas.drawRect(
                    norm.left * bmp.width,
                    norm.top * bmp.height,
                    norm.right * bmp.width,
                    norm.bottom * bmp.height,
                    blackPaint
                  )
                }
              }
              outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
              bmp.recycle()
              doc.finishPage(outPage)
            }
          }
        }
      }
      FileOutputStream(outFile).use { doc.writeTo(it) }
      doc.close()

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageCount,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Permanently redacted ${redactionRects.size} region(s) on page ${targetPageIndex + 1}.",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }

  // 24. CREATE PDF
  suspend fun createPdf(
    context: Context,
    title: String,
    subtitle: String,
    bodyText: String,
    pageSizeMode: PageSizeMode,
    accentColorInt: Int,
    attachedImageUris: List<Uri>
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(title.isNotBlank() || bodyText.isNotBlank()) { "Enter a title or body text for your PDF." }
      val outFile = newWorkFile(context, "created", "pdf")
      val doc = PdfDocument()
      var pageNum = 1
      try {
        val pw = if (pageSizeMode == PageSizeMode.LETTER) 612 else 595
        val ph = if (pageSizeMode == PageSizeMode.LETTER) 792 else 842
        val margin = 44f
        val contentW = (pw - margin * 2).toInt()

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.rgb(15, 23, 42)
          textSize = 13.5f
        }
        val fullBody = bodyText.ifBlank { "Created with CubedPDF." }
        val layout = StaticLayout.Builder.obtain(fullBody, 0, fullBody.length, textPaint, contentW)
          .setLineSpacing(4f, 1.25f)
          .build()

        var lineIdx = 0
        while (lineIdx < layout.lineCount || pageNum == 1) {
          val info = PdfDocument.PageInfo.Builder(pw, ph, pageNum).create()
          val page = doc.startPage(info)
          val c = page.canvas
          c.drawColor(Color.WHITE)

          var cursorY = margin
          if (pageNum == 1) {
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColorInt }
            c.drawRoundRect(RectF(margin, margin, pw - margin, margin + 78f), 14f, 14f, bannerPaint)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.WHITE
              textSize = 20f
              typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText(title.ifBlank { "Untitled Document" }, margin + 18f, margin + 38f, titlePaint)
            if (subtitle.isNotBlank()) {
              val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 255, 255, 255)
                textSize = 12f
              }
              c.drawText(subtitle, margin + 18f, margin + 60f, subPaint)
            }
            cursorY += 102f
          }

          val maxContentBottom = ph - margin - 24f
          while (lineIdx < layout.lineCount) {
            val lineTop = layout.getLineTop(lineIdx)
            val lineBottom = layout.getLineBottom(lineIdx)
            val lineH = lineBottom - lineTop
            if (cursorY + lineH > maxContentBottom) break
            val lineStart = layout.getLineStart(lineIdx)
            val lineEnd = layout.getLineEnd(lineIdx)
            val lineStr = fullBody.substring(lineStart, lineEnd).trimEnd('\n')
            c.drawText(lineStr, margin, cursorY - layout.getLineAscent(lineIdx), textPaint)
            cursorY += lineH + 2f
            lineIdx++
          }

          val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.RIGHT
          }
          c.drawText("Page $pageNum", pw - margin, ph - 20f, footPaint)
          doc.finishPage(page)
          if (lineIdx >= layout.lineCount) break
          pageNum++
        }

        // Append attached images as trailing pages
        attachedImageUris.forEach { imgUri ->
          val bmp = decodeSampledBitmap(context, imgUri, 1400)
          if (bmp != null) {
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pw, ph, pageNum).create()
            val page = doc.startPage(info)
            page.canvas.drawColor(Color.WHITE)
            val availW = pw - margin * 2f
            val availH = ph - margin * 2f
            val scale = min(availW / bmp.width, availH / bmp.height)
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            page.canvas.drawBitmap(
              bmp,
              null,
              RectF((pw - drawW) / 2f, (ph - drawH) / 2f, (pw + drawW) / 2f, (ph + drawH) / 2f),
              Paint(Paint.FILTER_BITMAP_FLAG)
            )
            bmp.recycle()
            doc.finishPage(page)
          }
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
      } finally {
        runCatching { doc.close() }
      }

      val processed = ProcessedFile(outFile, "application/pdf", outFile.name, outFile.length())
      PdfProcessResult(
        primaryFile = processed,
        pageCount = pageNum,
        originalSizeBytes = 0L,
        resultSizeBytes = outFile.length(),
        summary = "Created a $pageNum-page PDF document.",
        previewBitmaps = buildPreviewsFromPdf(outFile)
      )
    }
  }
}
