package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Dedicated on-device ML Kit OCR service for extracting text from scanned PDF pages
 * and document images, and exporting clean UTF-8 plain text (.txt) files.
 */
object PdfOcrService {

  const val PLAIN_TEXT_MIME_TYPE: String = "text/plain"
  private const val OCR_RENDER_MAX_DIM: Int = 1800
  private const val MAX_BASE_FILENAME_LENGTH: Int = 64

  /**
   * Produces a safe, sanitized `.txt` filename free of path separators, traversal tokens,
   * or unsupported characters.
   */
  fun sanitizeTxtFileName(rawName: String, fallback: String = "extracted_text"): String {
    val leaf = rawName
      .trim()
      .substringAfterLast('/')
      .substringAfterLast('\\')

    val withoutExt = leaf
      .replace(Regex("(?i)\\.(txt|pdf)$"), "")
      .replace("..", "_")
      .replace(Regex("[^A-Za-z0-9._ -]"), "_")
      .replace(Regex("\\s+"), "_")
      .replace(Regex("_+"), "_")
      .trim('_', '.', ' ', '-')
      .take(MAX_BASE_FILENAME_LENGTH)

    val safeBase = withoutExt.ifBlank {
      fallback
        .replace(Regex("(?i)\\.txt$"), "")
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .ifBlank { "extracted_text" }
    }
    return "$safeBase.txt"
  }

  /**
   * Suggests a descriptive `.txt` filename based on a source PDF display name.
   */
  fun suggestTxtFileName(sourceDisplayName: String?): String {
    val cleanSource = sourceDisplayName
      ?.trim()
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.replace(Regex("(?i)\\.(pdf|txt)$"), "")
      ?.trim()
      .orEmpty()

    val candidate = if (cleanSource.isBlank()) "extracted_text" else "${cleanSource}_extracted"
    return sanitizeTxtFileName(candidate)
  }

  /**
   * Resolves 0-based page indices to scan from a PDF document using either a 1-based range
   * expression (e.g., "1, 3-5"), thumbnail selections, or both. Defaults to all pages when empty.
   */
  fun resolveTargetPages(
    totalPages: Int,
    selectedPages: Set<Int> = emptySet(),
    pageRangeExpression: String = ""
  ): List<Int> {
    if (totalPages <= 0) return emptyList()
    val fromRange = if (pageRangeExpression.isNotBlank()) {
      PdfRotationSupport.parseSpecificPages(pageRangeExpression, totalPages)
    } else {
      emptySet()
    }
    val validSelected = selectedPages.filter { it in 0 until totalPages }
    val combined = (fromRange + validSelected).sorted()
    return if (combined.isEmpty()) {
      (0 until totalPages).toList()
    } else {
      combined
    }
  }

  /**
   * Formats recognized pages into clean UTF-8 plain text with normalized line endings
   * and optional per-page headers.
   */
  fun formatExtractedPlainText(
    pages: List<ExtractedPageText>,
    includePageHeaders: Boolean = true,
    emptyPagePlaceholder: String = "(No text detected on this page)"
  ): String {
    if (pages.isEmpty()) return ""
    if (pages.size == 1) {
      val page = pages.first()
      val normalized = normalizeLineEndings(page.text).trim()
      val body = normalized.ifBlank { emptyPagePlaceholder }
      return if (includePageHeaders) {
        "--- Page ${page.pageNumber} ---\n$body"
      } else {
        body
      }
    }

    return pages.joinToString("\n\n") { page ->
      val normalized = normalizeLineEndings(page.text).trim()
      val body = normalized.ifBlank { emptyPagePlaceholder }
      if (includePageHeaders) {
        "--- Page ${page.pageNumber} ---\n$body"
      } else {
        body
      }
    }.trim()
  }

  /**
   * Normalizes Windows/legacy carriage returns to standard LF (`\n`) for portable `.txt` files.
   */
  fun normalizeLineEndings(text: String): String {
    return text.replace("\r\n", "\n").replace("\r", "\n")
  }

  /**
   * Writes non-blank UTF-8 plain text content to [destFile] with normalized line endings.
   */
  fun writePlainTextFile(destFile: File, textContent: String): File {
    require(textContent.isNotBlank()) { "Extracted text is empty." }
    destFile.parentFile?.let { parent ->
      if (!parent.exists()) parent.mkdirs()
    }
    destFile.writeText(normalizeLineEndings(textContent), Charsets.UTF_8)
    return destFile
  }

  private suspend fun recognizeInputImageText(
    recognizer: TextRecognizer,
    image: InputImage
  ): Pair<String, Int> = suspendCancellableCoroutine { cont ->
    recognizer.process(image)
      .addOnSuccessListener { result ->
        if (cont.isActive) {
          val formatted = if (result.textBlocks.isNotEmpty()) {
            // Sort text blocks top-to-bottom, then left-to-right for natural scanned page reading order
            val orderedBlocks = result.textBlocks.sortedWith(
              compareBy<com.google.mlkit.vision.text.Text.TextBlock>(
                { it.boundingBox?.top ?: Int.MAX_VALUE },
                { it.boundingBox?.left ?: Int.MAX_VALUE }
              )
            )
            orderedBlocks.joinToString("\n\n") { it.text.trim() }
          } else {
            result.text.trim()
          }
          cont.resume(formatted to result.textBlocks.size)
        }
      }
      .addOnFailureListener {
        if (cont.isActive) cont.resume("" to 0)
      }
  }

  /**
   * Renders scanned PDF pages to high-DPI bitmaps, extracts text via on-device ML Kit
   * Text Recognition, and writes the output to a UTF-8 `.txt` plain text file.
   */
  suspend fun extractTextFromScannedPdf(
    context: Context,
    uri: Uri,
    selectedPages: Set<Int> = emptySet(),
    pageRangeExpression: String = "",
    includePageHeaders: Boolean = true,
    onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalSize = PdfEngine.querySizeBytes(context, uri)
      val sourceDisplayName = PdfEngine.queryDisplayName(context, uri)
      val localPath = uri.path
      val pfd = if (uri.scheme == "file" && !localPath.isNullOrBlank()) {
        ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
      } else {
        context.contentResolver.openFileDescriptor(uri, "r")
      } ?: throw IllegalStateException("Unable to open PDF.")

      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val extractedPages = mutableListOf<ExtractedPageText>()
      var totalDocPages = 0

      try {
        pfd.use { fd ->
          PdfRenderer(fd).use { renderer ->
            totalDocPages = renderer.pageCount
            if (totalDocPages <= 0) {
              throw IllegalStateException("The selected PDF has no pages.")
            }

            val hasCustomSelection = selectedPages.isNotEmpty() || pageRangeExpression.isNotBlank()
            val targetIndices = if (hasCustomSelection) {
              val parsedRange = if (pageRangeExpression.isNotBlank()) {
                PdfRotationSupport.parseSpecificPages(pageRangeExpression, totalDocPages)
              } else {
                emptySet()
              }
              val validSelected = selectedPages.filter { it in 0 until totalDocPages }
              (parsedRange + validSelected).sorted()
            } else {
              (0 until totalDocPages).toList()
            }

            if (targetIndices.isEmpty()) {
              throw IllegalStateException(
                "No valid pages selected for text extraction. Enter pages between 1 and $totalDocPages."
              )
            }

            targetIndices.forEachIndexed { stepIndex, pageIdx ->
              onProgress?.invoke(stepIndex + 1, targetIndices.size)
              val bmp = renderer.openPage(pageIdx).use { page ->
                PdfEngine.renderPageToBitmap(page, OCR_RENDER_MAX_DIM, whiteBg = true)
              }
              val (recognizedText, blockCount) = try {
                recognizeInputImageText(recognizer, InputImage.fromBitmap(bmp, 0))
              } finally {
                bmp.recycle()
              }

              val cleanText = normalizeLineEndings(recognizedText).trim()
              val wordCount = if (cleanText.isBlank()) 0 else cleanText.split(Regex("\\s+")).size
              extractedPages.add(
                ExtractedPageText(
                  pageIndex = pageIdx,
                  pageNumber = pageIdx + 1,
                  text = cleanText,
                  wordCount = wordCount,
                  characterCount = cleanText.length,
                  blockCount = blockCount
                )
              )
            }
          }
        }
      } finally {
        recognizer.close()
      }

      val extracted = formatExtractedPlainText(
        pages = extractedPages,
        includePageHeaders = includePageHeaders
      )
      val totalWords = extractedPages.sumOf { it.wordCount }
      val suggestedName = suggestTxtFileName(sourceDisplayName)
      val outFile = PdfEngine.newWorkFile(context, suggestedName.removeSuffix(".txt"), "txt")
      writePlainTextFile(outFile, extracted)

      val processed = ProcessedFile(
        file = outFile,
        mimeType = PLAIN_TEXT_MIME_TYPE,
        displayName = suggestedName,
        sizeBytes = outFile.length()
      )
      PdfProcessResult(
        primaryFile = processed,
        pageCount = extractedPages.size,
        originalSizeBytes = originalSize,
        resultSizeBytes = outFile.length(),
        summary = "Extracted $totalWords word(s) from ${extractedPages.size} of $totalDocPages PDF page(s) using on-device ML Kit and saved as $suggestedName.",
        extractedText = extracted,
        extractedPages = extractedPages
      )
    }
  }

  /**
   * Runs on-device ML Kit Text Recognition across one or more scanned document images/photos
   * and writes the output to a UTF-8 `.txt` plain text file.
   */
  suspend fun extractTextFromImages(
    context: Context,
    imageUris: List<Uri>,
    includePageHeaders: Boolean = true,
    onProgress: ((currentImage: Int, totalImages: Int) -> Unit)? = null
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(imageUris.isNotEmpty()) { "Select at least one scanned image or photo." }
      var totalOriginalSize = 0L
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val extractedPages = mutableListOf<ExtractedPageText>()
      val previews = mutableListOf<Bitmap>()

      try {
        imageUris.forEachIndexed { idx, uri ->
          onProgress?.invoke(idx + 1, imageUris.size)
          totalOriginalSize += PdfEngine.querySizeBytes(context, uri)

          val (recognizedText, blockCount) = runCatching {
            val inputImage = InputImage.fromFilePath(context, uri)
            recognizeInputImageText(recognizer, inputImage)
          }.getOrElse {
            val fallbackBmp = PdfEngine.decodeSampledBitmap(context, uri, OCR_RENDER_MAX_DIM)
              ?: throw IllegalStateException("Unable to decode image ${idx + 1}.")
            try {
              recognizeInputImageText(recognizer, InputImage.fromBitmap(fallbackBmp, 0))
            } finally {
              fallbackBmp.recycle()
            }
          }

          if (previews.size < 4) {
            PdfEngine.decodeSampledBitmap(context, uri, 480)?.let { previews.add(it) }
          }

          val cleanText = normalizeLineEndings(recognizedText).trim()
          val wordCount = if (cleanText.isBlank()) 0 else cleanText.split(Regex("\\s+")).size
          extractedPages.add(
            ExtractedPageText(
              pageIndex = idx,
              pageNumber = idx + 1,
              text = cleanText,
              wordCount = wordCount,
              characterCount = cleanText.length,
              blockCount = blockCount
            )
          )
        }
      } finally {
        recognizer.close()
      }

      val extracted = formatExtractedPlainText(
        pages = extractedPages,
        includePageHeaders = includePageHeaders && imageUris.size > 1,
        emptyPagePlaceholder = "(No text detected in this image)"
      )
      val totalWords = extractedPages.sumOf { it.wordCount }
      val suggestedName = sanitizeTxtFileName("scanned_images_extracted")
      val outFile = PdfEngine.newWorkFile(context, suggestedName.removeSuffix(".txt"), "txt")
      writePlainTextFile(outFile, extracted)

      val processed = ProcessedFile(
        file = outFile,
        mimeType = PLAIN_TEXT_MIME_TYPE,
        displayName = suggestedName,
        sizeBytes = outFile.length()
      )
      PdfProcessResult(
        primaryFile = processed,
        pageCount = extractedPages.size,
        originalSizeBytes = totalOriginalSize,
        resultSizeBytes = outFile.length(),
        summary = "Extracted $totalWords word(s) from ${extractedPages.size} scanned image(s) using on-device ML Kit and saved as $suggestedName.",
        previewBitmaps = previews,
        extractedText = extracted,
        extractedPages = extractedPages
      )
    }
  }

  /**
   * Saves user-edited or freshly extracted text into a sanitized UTF-8 `.txt` file
   * ready for SAF export (`CreateDocument("text/plain")`) or sharing.
   */
  suspend fun savePlainTextFile(
    context: Context,
    desiredFileName: String,
    textContent: String
  ): Result<ProcessedFile> = withContext(Dispatchers.IO) {
    runCatching {
      require(textContent.isNotBlank()) { "Extracted text is empty." }
      val safeFileName = sanitizeTxtFileName(desiredFileName)
      val prefix = safeFileName.removeSuffix(".txt").take(32).ifBlank { "extracted_text" }
      val outFile = PdfEngine.newWorkFile(context, prefix, "txt")
      writePlainTextFile(outFile, textContent)
      ProcessedFile(
        file = outFile,
        mimeType = PLAIN_TEXT_MIME_TYPE,
        displayName = safeFileName,
        sizeBytes = outFile.length()
      )
    }
  }
}
