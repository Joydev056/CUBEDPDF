package com.example.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detected encryption / password-protection status of a user-selected PDF document.
 */
enum class PdfProtectionStatus(
  val title: String,
  val detail: String
) {
  UNPROTECTED(
    title = "Unprotected PDF",
    detail = "Ready to encrypt with a password using on-device AES-256-GCM."
  ),
  AES_VAULT_ENCRYPTED(
    title = "AES-256 Encrypted Vault",
    detail = "Encrypted by CubedPDF. Enter your password to decrypt and remove protection."
  ),
  NATIVE_PDF_PASSWORD_PROTECTED(
    title = "Password-Protected PDF",
    detail = "Standard encrypted PDF detected. Enter its password to unlock and save a decrypted copy."
  ),
  UNKNOWN(
    title = "Document Loaded",
    detail = "Choose Set Password to encrypt or Remove Password to decrypt."
  )
}

/**
 * Real-time password strength classification used by the Password Control UI.
 */
enum class PasswordStrength(
  val label: String,
  val score: Float
) {
  EMPTY("Enter password", 0f),
  WEAK("Weak", 0.33f),
  FAIR("Fair", 0.66f),
  STRONG("Strong", 1f)
}

/**
 * User-selectable operation inside the PDF Security workspace.
 */
enum class PdfPasswordActionMode(
  val title: String,
  val actionButtonLabel: String
) {
  SET_PASSWORD("Set Password", "Encrypt & Set Password"),
  REMOVE_PASSWORD("Remove Password", "Decrypt & Remove Password")
}

/**
 * Dedicated on-device PDF encryption and decryption service.
 *
 * Uses PBKDF2-HMAC-SHA256 (65,536 iterations, 16-byte random salt) to derive a 256-bit AES key,
 * and AES-256-GCM (12-byte random IV, 128-bit authentication tag) to authenticate and encrypt
 * PDF bytes into CubedPDF's `.pdfenc` vault container (`PDFTOOLS_AES_V1`).
 *
 * Also supports removing passwords from native password-protected PDFs on Android 15+ (API 35+)
 * via `PdfRenderer` `LoadParams`. All cryptographic key material and character buffers are wiped
 * immediately after use.
 */
object PdfCryptoService {

  const val ENC_MAGIC = "PDFTOOLS_AES_V1"
  const val MIN_PASSWORD_LENGTH = 4

  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val GCM_TAG_BITS = 128
  private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
  private const val PBKDF2_ITERATIONS = 65536
  private const val AES_KEY_BITS = 256

  private val magicBytes: ByteArray = ENC_MAGIC.toByteArray(Charsets.UTF_8)
  private val pdfHeaderBytes: ByteArray = "%PDF-".toByteArray(Charsets.US_ASCII)

  /**
   * Returns true if the payload starts with CubedPDF's AES-256-GCM vault header.
   */
  fun isAesVaultBytes(bytes: ByteArray): Boolean {
    if (bytes.size <= magicBytes.size + SALT_BYTES + IV_BYTES) return false
    for (i in magicBytes.indices) {
      if (bytes[i] != magicBytes[i]) return false
    }
    return true
  }

  /**
   * Heuristic check for standard PDF `/Encrypt` dictionary entry in raw PDF bytes.
   */
  fun containsPdfEncryptDictionary(bytes: ByteArray): Boolean {
    if (bytes.size < pdfHeaderBytes.size) return false
    for (i in pdfHeaderBytes.indices) {
      if (bytes[i] != pdfHeaderBytes[i]) return false
    }
    // Trailer / cross-reference dictionary lives near the end; scan last 64 KB + first 8 KB.
    val headSlice = bytes.copyOfRange(0, bytes.size.coerceAtMost(8192))
    if (String(headSlice, Charsets.ISO_8859_1).contains("/Encrypt")) return true
    val tailStart = (bytes.size - 65536).coerceAtLeast(0)
    val tailSlice = bytes.copyOfRange(tailStart, bytes.size)
    return String(tailSlice, Charsets.ISO_8859_1).contains("/Encrypt")
  }

  /**
   * Evaluates password strength for live UI feedback.
   */
  fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.EMPTY
    if (password.length < MIN_PASSWORD_LENGTH) return PasswordStrength.WEAK

    var points = 0
    if (password.length >= 8) points++
    if (password.length >= 12) points++
    if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) points++
    if (password.any { it.isDigit() }) points++
    if (password.any { !it.isLetterOrDigit() }) points++

    return when {
      points >= 4 -> PasswordStrength.STRONG
      points >= 2 -> PasswordStrength.FAIR
      else -> PasswordStrength.WEAK
    }
  }

  /**
   * Validates password and confirmation fields before encrypting a PDF.
   * Returns an actionable error string, or null if valid.
   */
  fun validateSetPassword(password: String, confirmPassword: String): String? {
    if (password.isEmpty()) return "Please enter a password to protect this PDF."
    if (password.length < MIN_PASSWORD_LENGTH) {
      return "Password must be at least $MIN_PASSWORD_LENGTH characters."
    }
    if (confirmPassword.isEmpty()) return "Please confirm your password."
    if (password != confirmPassword) return "Passwords do not match."
    return null
  }

  /**
   * Validates the password field before decrypting / removing protection from a PDF.
   * Returns an actionable error string, or null if valid.
   */
  fun validateRemovePassword(password: String): String? {
    if (password.isEmpty()) return "Please enter the document password."
    return null
  }

  /**
   * Encrypts raw PDF bytes into the authenticated AES-256-GCM vault container format:
   * `[ENC_MAGIC][16-byte salt][12-byte IV][ciphertext + 16-byte GCM auth tag]`.
   */
  fun encryptBytes(plainBytes: ByteArray, password: CharArray): ByteArray {
    require(plainBytes.isNotEmpty()) { "Source PDF file is empty." }
    require(password.size >= MIN_PASSWORD_LENGTH) {
      "Password must be at least $MIN_PASSWORD_LENGTH characters."
    }

    val rng = SecureRandom()
    val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
    val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }

    val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
    var rawKey: ByteArray? = null
    return try {
      val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      rawKey = factory.generateSecret(spec).encoded
      val key = SecretKeySpec(rawKey, "AES")

      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      val encrypted = cipher.doFinal(plainBytes)

      val output = ByteArray(magicBytes.size + SALT_BYTES + IV_BYTES + encrypted.size)
      System.arraycopy(magicBytes, 0, output, 0, magicBytes.size)
      System.arraycopy(salt, 0, output, magicBytes.size, SALT_BYTES)
      System.arraycopy(iv, 0, output, magicBytes.size + SALT_BYTES, IV_BYTES)
      System.arraycopy(encrypted, 0, output, magicBytes.size + SALT_BYTES + IV_BYTES, encrypted.size)
      output
    } finally {
      spec.clearPassword()
      rawKey?.fill(0)
    }
  }

  /**
   * Decrypts an authenticated AES-256-GCM vault payload created by [encryptBytes].
   * Throws [IllegalArgumentException] with a clear message if the password is incorrect or the
   * vault file is corrupted/tampered.
   */
  fun decryptBytes(encryptedBytes: ByteArray, password: CharArray): ByteArray {
    require(password.isNotEmpty()) { "Please enter the document password." }
    val headerLen = magicBytes.size + SALT_BYTES + IV_BYTES
    require(
      encryptedBytes.size >= headerLen + GCM_TAG_BYTES && isAesVaultBytes(encryptedBytes)
    ) {
      "This file is not a valid CubedPDF AES-256 encrypted vault."
    }

    val offset = magicBytes.size
    val salt = encryptedBytes.copyOfRange(offset, offset + SALT_BYTES)
    val iv = encryptedBytes.copyOfRange(offset + SALT_BYTES, offset + SALT_BYTES + IV_BYTES)
    val cipherText = encryptedBytes.copyOfRange(headerLen, encryptedBytes.size)

    val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
    var rawKey: ByteArray? = null
    return try {
      val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      rawKey = factory.generateSecret(spec).encoded
      val key = SecretKeySpec(rawKey, "AES")

      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(cipherText)
    } catch (_: AEADBadTagException) {
      throw IllegalArgumentException("Incorrect password for encrypted PDF vault.")
    } catch (_: GeneralSecurityException) {
      throw IllegalArgumentException("Incorrect password for encrypted PDF vault.")
    } finally {
      spec.clearPassword()
      rawKey?.fill(0)
    }
  }

  /**
   * Inspects a user-selected document URI to determine whether it is an unprotected PDF,
   * a CubedPDF AES-256 encrypted vault, or a native password-protected PDF.
   */
  suspend fun inspectProtectionStatus(context: Context, uri: Uri): PdfProtectionStatus =
    withContext(Dispatchers.IO) {
      try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: return@withContext PdfProtectionStatus.UNKNOWN

        if (isAesVaultBytes(bytes)) {
          return@withContext PdfProtectionStatus.AES_VAULT_ENCRYPTED
        }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
          try {
            pfd.use { fd ->
              PdfRenderer(fd).use {
                return@withContext PdfProtectionStatus.UNPROTECTED
              }
            }
          } catch (_: SecurityException) {
            return@withContext PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED
          } catch (_: Throwable) {
            // Fall back to byte inspection (covers JVM Robolectric tests & malformed headers)
          }
        }

        if (containsPdfEncryptDictionary(bytes)) {
          PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED
        } else if (bytes.size >= pdfHeaderBytes.size &&
          bytes.copyOfRange(0, pdfHeaderBytes.size).contentEquals(pdfHeaderBytes)
        ) {
          PdfProtectionStatus.UNPROTECTED
        } else {
          PdfProtectionStatus.UNKNOWN
        }
      } catch (_: Throwable) {
        PdfProtectionStatus.UNKNOWN
      }
    }

  /**
   * Encrypts a PDF document from [uri] with [password] using AES-256-GCM and writes a `.pdfenc`
   * vault file in CubedPDF's workspace directory.
   */
  suspend fun encryptPdfFile(
    context: Context,
    uri: Uri,
    password: String
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(password.length >= MIN_PASSWORD_LENGTH) {
        "Password must be at least $MIN_PASSWORD_LENGTH characters."
      }
      val plainBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Unable to read source PDF.")
      require(plainBytes.isNotEmpty()) { "Selected file is empty." }
      require(!isAesVaultBytes(plainBytes)) {
        "This file is already encrypted with AES-256. Switch to Remove Password to decrypt it."
      }

      val meta = PdfEngine.inspectPdf(context, uri).getOrNull()
      val passwordChars = password.toCharArray()
      val encryptedVaultBytes = try {
        encryptBytes(plainBytes, passwordChars)
      } finally {
        passwordChars.fill('\u0000')
      }

      val outFile = PdfEngine.newWorkFile(context, "protected", "pdfenc")
      FileOutputStream(outFile).use { out ->
        out.write(encryptedVaultBytes)
      }

      val processed = ProcessedFile(
        file = outFile,
        mimeType = "application/octet-stream",
        displayName = outFile.name,
        sizeBytes = outFile.length()
      )
      PdfProcessResult(
        primaryFile = processed,
        pageCount = meta?.pageCount ?: 1,
        originalSizeBytes = plainBytes.size.toLong(),
        resultSizeBytes = outFile.length(),
        summary = "Encrypted with AES-256-GCM. Use Remove Password / Unlock PDF inside CubedPDF with your password to restore."
      )
    }
  }

  /**
   * Decrypts a CubedPDF AES-256 vault (`.pdfenc`) on all Android versions, or removes password
   * protection from a native password-protected PDF on Android 15+ (API 35+).
   */
  suspend fun decryptPdfFile(
    context: Context,
    uri: Uri,
    password: String
  ): Result<PdfProcessResult> = withContext(Dispatchers.IO) {
    runCatching {
      require(password.isNotEmpty()) { "Please enter the document password." }
      val allBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Unable to read file.")
      require(allBytes.isNotEmpty()) { "Selected file is empty." }

      if (isAesVaultBytes(allBytes)) {
        val passwordChars = password.toCharArray()
        val plain = try {
          decryptBytes(allBytes, passwordChars)
        } finally {
          passwordChars.fill('\u0000')
        }
        val outFile = PdfEngine.newWorkFile(context, "unlocked", "pdf")
        outFile.writeBytes(plain)
        val previews = PdfEngine.buildPreviewsFromPdf(outFile)
        val pageCount = previews.size.coerceAtLeast(1)
        val processed = ProcessedFile(
          file = outFile,
          mimeType = "application/pdf",
          displayName = outFile.name,
          sizeBytes = outFile.length()
        )
        PdfProcessResult(
          primaryFile = processed,
          pageCount = pageCount,
          originalSizeBytes = allBytes.size.toLong(),
          resultSizeBytes = outFile.length(),
          summary = "Decrypted AES-256 vault into an unlocked PDF.",
          previewBitmaps = previews
        )
      } else {
        // Standard password-protected PDF: Android 15+ (API 35+) natively supports LoadParams password
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
          throw UnsupportedOperationException(
            "Unlocking native password-protected PDFs requires Android 15 or higher (API 35+). AES vaults (.pdfenc) are supported on all versions."
          )
        }
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("Unable to open PDF.")
        val outFile = PdfEngine.newWorkFile(context, "unlocked", "pdf")
        val doc = PdfDocument()
        var pageCount = 0
        try {
          pfd.use { fd ->
            val renderer = try {
              PdfEngine.openRendererApi35(fd, password)
            } catch (_: SecurityException) {
              throw IllegalArgumentException("Incorrect password for this PDF document.")
            }
            renderer.use { pdfRenderer ->
              pageCount = pdfRenderer.pageCount
              for (i in 0 until pageCount) {
                pdfRenderer.openPage(i).use { page ->
                  val info = PdfDocument.PageInfo.Builder(
                    max(1, page.width),
                    max(1, page.height),
                    i + 1
                  ).create()
                  val outPage = doc.startPage(info)
                  val bmp = PdfEngine.renderPageToBitmap(page, 1400)
                  outPage.canvas.drawBitmap(
                    bmp,
                    null,
                    Rect(0, 0, info.pageWidth, info.pageHeight),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                  )
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
        val processed = ProcessedFile(
          file = outFile,
          mimeType = "application/pdf",
          displayName = outFile.name,
          sizeBytes = outFile.length()
        )
        PdfProcessResult(
          primaryFile = processed,
          pageCount = pageCount.coerceAtLeast(1),
          originalSizeBytes = allBytes.size.toLong(),
          resultSizeBytes = outFile.length(),
          summary = "Removed password protection across $pageCount page(s).",
          previewBitmaps = PdfEngine.buildPreviewsFromPdf(outFile)
        )
      }
    }
  }
}
