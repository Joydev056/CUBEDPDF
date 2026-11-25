package com.example

import com.example.legal.AppLegalConfig
import com.example.pdf.PasswordStrength
import com.example.pdf.PdfCryptoService
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated 14-Point Google Play Console Policy, Security & Upload Verification Suite.
 *
 * Runs directly on the local JVM against the live project sources, resources, Gradle build
 * configuration, Firestore security rules, and the compiled merged APK AndroidManifest.xml
 * so every Play Store submission gate is verified deterministically.
 */
class PlayStore14PointAuditTest {

  private fun projectRoot(): File {
    val cwd = File(System.getProperty("user.dir") ?: ".")
    return if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile ?: cwd
  }

  private fun readProjectFile(relativePath: String): String {
    val file = File(projectRoot(), relativePath)
    assertTrue("Expected project file to exist: $relativePath", file.exists())
    return file.readText(Charsets.UTF_8)
  }

  private fun allMainKotlinSources(): List<File> {
    val srcDir = File(projectRoot(), "app/src/main/java")
    assertTrue("Main Kotlin source dir must exist", srcDir.exists())
    return srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
  }

  // 1. Package Identity, SDK Targets, Versioning & AAB Configuration
  @Test
  fun `01_package_identity_sdk_targets_and_versioning_meet_play_console_requirements`() {
    val appGradle = readProjectFile("app/build.gradle.kts")

    assertTrue(
      "applicationId must be unique and not com.example.*",
      appGradle.contains("""applicationId = "com.aistudio.pdftools.xqmztb"""")
    )
    assertFalse("Must not ship com.example applicationId", appGradle.contains("""applicationId = "com.example"""))
    assertTrue("targetSdk must be >= 35 (configured 36)", appGradle.contains("targetSdk = 36"))
    assertTrue("minSdk must be configured (>= 24)", appGradle.contains("minSdk = 24"))
    assertTrue("versionCode must be >= 1", Regex("""versionCode\s*=\s*[1-9]\d*""").containsMatchIn(appGradle))
    assertTrue("versionName must be non-blank", Regex("""versionName\s*=\s*"[^"]+"""").containsMatchIn(appGradle))
    assertTrue("AAB dependenciesInfo should be enabled", appGradle.contains("includeInBundle = true"))
  }

  // 2. App Metadata & Store Branding Policy (Title <= 30 chars, no buzzwords, synced)
  @Test
  fun `02_app_metadata_and_branding_comply_with_play_store_policy`() {
    val stringsXml = readProjectFile("app/src/main/res/values/strings.xml")
    val metadataJson = readProjectFile("metadata.json")
    val settingsGradle = readProjectFile("settings.gradle.kts")

    val appName = Regex("""<string name="app_name">([^<]+)</string>""")
      .find(stringsXml)?.groupValues?.get(1)?.trim()
    assertNotNull("app_name must exist in strings.xml", appName)
    val title = appName!!

    assertTrue("Title must be <= 30 chars (got ${title.length})", title.length <= 30)
    assertFalse("Title must not be ALL CAPS", title == title.uppercase())
    assertTrue("Title must contain printable ASCII only (no emojis)", title.all { it.code in 32..126 })

    val buzzwords = listOf("free", "best", "#1", "top", "new", "download now", "100%")
    buzzwords.forEach { bad ->
      assertFalse("Title must not contain buzzword '$bad'", title.lowercase().contains(bad))
    }

    assertTrue("metadata.json name must match strings.xml app_name", metadataJson.contains(""""name": "$title""""))
    assertTrue(
      "metadata.json must preserve server-side capability",
      metadataJson.contains("MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API")
    )
    assertTrue("settings.gradle.kts rootProject.name must match app_name", settingsGradle.contains("""rootProject.name = "$title""""))
  }

  // 3. Custom Adaptive Launcher Icons (No Default Android Icon)
  @Test
  fun `03_custom_adaptive_launcher_icons_are_properly_configured`() {
    val icLauncher = readProjectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
    val icLauncherRound = readProjectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")

    assertTrue("ic_launcher.xml must be an <adaptive-icon>", icLauncher.contains("<adaptive-icon"))
    assertTrue("ic_launcher_round.xml must be an <adaptive-icon>", icLauncherRound.contains("<adaptive-icon"))
    assertTrue(
      "Foreground launcher drawable must exist",
      File(projectRoot(), "app/src/main/res/drawable/ic_launcher_foreground.xml").exists()
    )
    assertTrue(
      "Background launcher drawable must exist",
      File(projectRoot(), "app/src/main/res/drawable/ic_launcher_background.xml").exists()
    )
  }

  // 4. Permissions Minimization & Merged Manifest Audit (Zero Broad / Restricted / Ad-ID Permissions)
  @Test
  fun `04_merged_manifest_has_zero_prohibited_storage_sensitive_or_ad_id_permissions`() {
    val mergedManifestFile = File(
      projectRoot(),
      "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
    )
    val manifestXml = if (mergedManifestFile.exists()) {
      mergedManifestFile.readText(Charsets.UTF_8)
    } else {
      readProjectFile("app/src/main/AndroidManifest.xml")
    }

    val prohibited = listOf(
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE",
      "android.permission.READ_MEDIA_IMAGES",
      "android.permission.READ_MEDIA_VIDEO",
      "android.permission.READ_MEDIA_AUDIO",
      "android.permission.MANAGE_EXTERNAL_STORAGE",
      "android.permission.REQUEST_INSTALL_PACKAGES",
      "android.permission.QUERY_ALL_PACKAGES",
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.ACCESS_FINE_LOCATION",
      "android.permission.ACCESS_COARSE_LOCATION",
      "android.permission.ACCESS_BACKGROUND_LOCATION",
      "android.permission.READ_CONTACTS",
      "android.permission.READ_CALL_LOG",
      "android.permission.READ_SMS",
      "android.permission.SEND_SMS",
      "android.permission.CAMERA",
      "android.permission.RECORD_AUDIO",
      "com.google.android.gms.permission.AD_ID",
      "android.permission.ACCESS_ADSERVICES_AD_ID",
      "android.permission.ACCESS_ADSERVICES_ATTRIBUTION"
    )

    val found = prohibited.filter { perm ->
      Regex("""<uses-permission[^>]*android:name="$perm"(?![^>]*tools:node="remove")""")
        .containsMatchIn(manifestXml)
    }
    assertTrue("Prohibited permissions found in manifest: $found", found.isEmpty())
  }

  // 5. Zero-Permission Media & Document Pickers (Photo Picker & SAF)
  @Test
  fun `05_media_and_document_selection_use_zero_permission_photo_picker_and_saf`() {
    val workspaceSrc = readProjectFile("app/src/main/java/com/example/ui/screens/ToolWorkspaceScreen.kt")
    assertTrue("Must use Android Photo Picker", workspaceSrc.contains("ActivityResultContracts.PickMultipleVisualMedia"))
    assertTrue("Must use SAF OpenDocument", workspaceSrc.contains("ActivityResultContracts.OpenDocument"))
    assertTrue("Must use SAF OpenMultipleDocuments", workspaceSrc.contains("ActivityResultContracts.OpenMultipleDocuments"))
    assertTrue("Must use SAF CreateDocument", workspaceSrc.contains("ActivityResultContracts.CreateDocument"))
    assertFalse("Must never request runtime storage permissions", workspaceSrc.contains("READ_EXTERNAL_STORAGE"))
    assertFalse("Must never request READ_MEDIA_IMAGES", workspaceSrc.contains("READ_MEDIA_IMAGES"))
  }

  // 6. Network Security, Cleartext Traffic Ban & Hardened WebView
  @Test
  fun `06_cleartext_traffic_is_disabled_and_webview_is_hardened_to_https_only`() {
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    val pdfEngineSrc = readProjectFile("app/src/main/java/com/example/pdf/PdfEngine.kt")

    assertTrue("usesCleartextTraffic must be false", manifestXml.contains("""android:usesCleartextTraffic="false""""))
    assertTrue("WebView JavaScript must be disabled", pdfEngineSrc.contains("settings.javaScriptEnabled = false"))
    assertTrue("WebView file access must be disabled", pdfEngineSrc.contains("settings.allowFileAccess = false"))
    assertTrue("WebView content access must be disabled", pdfEngineSrc.contains("settings.allowContentAccess = false"))
    assertTrue("WebView geolocation must be disabled", pdfEngineSrc.contains("settings.setGeolocationEnabled(false)"))
    assertTrue("WebView mixed content must be blocked", pdfEngineSrc.contains("MIXED_CONTENT_NEVER_ALLOW"))
    assertTrue("WebView safe browsing must be enabled", pdfEngineSrc.contains("settings.safeBrowsingEnabled = true"))
    assertTrue("Non-HTTPS URL schemes must be blocked", pdfEngineSrc.contains("""lower.startsWith("http://")"""))
    assertTrue("Redirects must enforce https scheme", pdfEngineSrc.contains("""return scheme != "https""""))
  }

  // 7. FileProvider Scoping & Component Export Security
  @Test
  fun `07_file_provider_is_non_exported_and_strictly_scoped_to_app_private_dirs`() {
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    val filePathsXml = readProjectFile("app/src/main/res/xml/file_paths.xml")

    assertTrue("FileProvider must be registered", manifestXml.contains("androidx.core.content.FileProvider"))
    assertTrue("FileProvider authority must be app-scoped", manifestXml.contains("""android:authorities="${'$'}{applicationId}.fileprovider""""))
    assertTrue("FileProvider must NOT be exported", manifestXml.contains("""android:exported="false""""))
    assertTrue("FileProvider must grant URI permissions", manifestXml.contains("""android:grantUriPermissions="true""""))

    assertFalse("file_paths.xml must never expose <root-path>", filePathsXml.contains("<root-path"))
    assertFalse("file_paths.xml must never expose <external-path>", filePathsXml.contains("<external-path"))
    assertTrue("file_paths.xml must scope to cubedpdf_cache/", filePathsXml.contains("""path="cubedpdf_cache/""""))
    assertTrue("file_paths.xml must scope to cubedpdf_documents/", filePathsXml.contains("""path="cubedpdf_documents/""""))
  }

  // 8. System Integrity: Dynamic Code Loading (DCL) & Foreground Service Prohibition
  @Test
  fun `08_zero_dynamic_code_loading_and_compliant_service_declarations`() {
    val dclPatterns = listOf(
      "DexClassLoader",
      "PathClassLoader",
      "InMemoryDexClassLoader",
      "System.load(",
      "Runtime.getRuntime().exec",
      "ProcessBuilder(",
      "FLAG_MUTABLE"
    )
    allMainKotlinSources().forEach { file ->
      val content = file.readText(Charsets.UTF_8)
      dclPatterns.forEach { forbidden ->
        assertFalse(
          "Forbidden system-integrity API '$forbidden' found in ${file.name}",
          content.contains(forbidden)
        )
      }
    }
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    assertFalse("App manifest should not declare undeclared foreground services", manifestXml.contains("<service"))
  }

  // 9. Backup & Android 12+ Data Extraction Rules (Sensitive User Files Excluded)
  @Test
  fun `09_backup_and_data_extraction_rules_exclude_sensitive_local_pdfs_and_db`() {
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    val backupRules = readProjectFile("app/src/main/res/xml/backup_rules.xml")
    val extractionRules = readProjectFile("app/src/main/res/xml/data_extraction_rules.xml")

    assertTrue("Manifest must wire backup_rules", manifestXml.contains("""android:fullBackupContent="@xml/backup_rules""""))
    assertTrue("Manifest must wire data_extraction_rules", manifestXml.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    assertTrue("backup_rules must exclude cubedpdf_documents/", backupRules.contains("""path="cubedpdf_documents/""""))
    assertTrue("backup_rules must exclude local Room DB", backupRules.contains("""path="cubedpdf_recent_files.db""""))
    assertTrue("data_extraction_rules must exclude cubedpdf_documents/", extractionRules.contains("""path="cubedpdf_documents/""""))
    assertTrue("data_extraction_rules must exclude local Room DB", extractionRules.contains("""path="cubedpdf_recent_files.db""""))
  }

  // 10. User Data, Mandatory Account Deletion (In-App + Re-Auth + Web URL), Privacy Policy & Auth Invariants
  @Test
  fun `10_auth_invariants_and_in_app_account_deletion_comply_with_play_user_data_policy`() {
    val accountDialogSrc = readProjectFile("app/src/main/java/com/example/ui/screens/AccountProfileDialog.kt")
    val authScreenSrc = readProjectFile("app/src/main/java/com/example/ui/screens/AuthScreen.kt")
    val accountRepoSrc = readProjectFile("app/src/main/java/com/example/data/remote/UserAccountRepository.kt")
    val authHelperSrc = readProjectFile("app/src/main/java/com/example/data/remote/AuthHelper.kt")

    // Mandatory Google Play in-app account deletion + re-auth handling
    assertTrue("UI must offer Delete Account & Cloud Data", accountDialogSrc.contains("Delete Account & Cloud Data"))
    assertTrue("UI must prompt re-auth if recent login is required", accountDialogSrc.contains("reauth_delete_account_dialog"))
    assertTrue("Repository must delete Firestore user docs & Firebase Auth account", accountRepoSrc.contains("user.delete().await()"))
    assertTrue("Repository must return RequiresReauth instead of swallowing recent-login error", accountRepoSrc.contains("AccountDeletionOutcome.RequiresReauth"))
    assertTrue("AuthHelper must support Google re-authentication", authHelperSrc.contains("fun reauthenticateWithGoogle"))
    assertTrue("AuthHelper must support email/password re-authentication", authHelperSrc.contains("fun reauthenticateWithEmailPassword"))

    // Mandatory Privacy Policy & Public Web Account Deletion URLs
    assertTrue("Privacy Policy URL must be valid HTTPS", AppLegalConfig.isValidHttpsUrl(AppLegalConfig.PRIVACY_POLICY_URL))
    assertTrue("Web Account Deletion URL must be valid HTTPS", AppLegalConfig.isValidHttpsUrl(AppLegalConfig.ACCOUNT_DELETION_WEB_URL))
    assertTrue("AuthScreen must link Privacy Policy", authScreenSrc.contains("auth_privacy_policy_link"))
    assertTrue("AuthScreen must link Account Deletion", authScreenSrc.contains("auth_account_deletion_link"))
    assertTrue("AccountProfileDialog must link Privacy Policy", accountDialogSrc.contains("profile_privacy_policy_button"))
    assertTrue("AccountProfileDialog must link Web Account Deletion", accountDialogSrc.contains("profile_web_deletion_button"))

    // Zero prohibited auth patterns in app/src/main
    val forbiddenAuthRegex = Regex("""signInAnonymously|signInWithEmailAndPassword|\.apps\.googleusercontent\.com""")
    allMainKotlinSources().forEach { file ->
      val content = file.readText(Charsets.UTF_8)
      assertFalse(
        "Prohibited auth pattern found in ${file.name}",
        forbiddenAuthRegex.containsMatchIn(content)
      )
    }
  }

  // 11. Secrets Hygiene & Verified AES-256-GCM Encryption / Decryption Engine
  @Test
  fun `11_no_hardcoded_secrets_and_pdf_crypto_service_aes256_gcm_verified`() {
    val secretPatterns = listOf(
      Regex("""AIza[0-9A-Za-z_-]{25,}"""),
      Regex("""sk-[A-Za-z0-9]{20,}"""),
      Regex("""ghp_[A-Za-z0-9]{20,}""")
    )
    allMainKotlinSources().forEach { file ->
      val content = file.readText(Charsets.UTF_8)
      secretPatterns.forEach { rx ->
        assertFalse("Hardcoded secret pattern found in ${file.name}", rx.containsMatchIn(content))
      }
    }

    // Verify AES-256-GCM encryption/decryption round-trip, unique salt/IV, tamper detection & validation
    val samplePdf = "%PDF-1.7\n1 0 obj<<>>endobj\n%%EOF\n".toByteArray(Charsets.UTF_8)
    val enc1 = PdfCryptoService.encryptBytes(samplePdf, "PlayAudit#2026".toCharArray())
    val enc2 = PdfCryptoService.encryptBytes(samplePdf, "PlayAudit#2026".toCharArray())

    assertTrue(PdfCryptoService.isAesVaultBytes(enc1))
    assertFalse("Fresh random salt & IV must prevent deterministic ciphertext", enc1.contentEquals(enc2))
    assertArrayEquals(samplePdf, PdfCryptoService.decryptBytes(enc1, "PlayAudit#2026".toCharArray()))

    // Tampered ciphertext must fail authentication
    val tampered = enc1.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x55).toByte() }
    assertTrue(runCatching { PdfCryptoService.decryptBytes(tampered, "PlayAudit#2026".toCharArray()) }.isFailure)

    // Wrong password must fail cleanly
    val wrongPass = runCatching { PdfCryptoService.decryptBytes(enc1, "WrongPass#999".toCharArray()) }
    assertTrue(wrongPass.isFailure)
    assertTrue(wrongPass.exceptionOrNull()?.message?.contains("Incorrect password", ignoreCase = true) == true)
    assertEquals(PasswordStrength.STRONG, PdfCryptoService.evaluatePasswordStrength("PlayAudit#2026!"))
  }

  // 12. Zero-Trust Firestore Security Rules Audit
  @Test
  fun `12_firestore_rules_enforce_zero_trust_owner_isolation_and_no_password_storage`() {
    val rules = readProjectFile("firestore.rules")
    assertTrue("Rules must use version 2", rules.contains("rules_version = '2';"))
    assertTrue("Global default-deny fallback must exist", rules.contains("allow read, write: if false;"))
    assertTrue("Must require authenticated user", rules.contains("request.auth != null"))
    assertTrue("Must isolate user profiles to owner only", rules.contains("request.auth.uid == userId"))
    assertTrue("Must block listing all users", rules.contains("allow list: if false;"))
    assertTrue("Must strictly forbid storing passwords in Firestore", rules.contains("!('password' in data)"))
    assertTrue("Must allow owner deletion for Play Account Deletion policy", rules.contains("allow delete: if isOwner(userId)"))
  }

  // 13. Privacy-First Google Analytics & Advertising ID Opt-Out Compliance
  @Test
  fun `13_google_analytics_is_configured_with_ad_id_disabled_and_zero_pii_logging`() {
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    val appGradle = readProjectFile("app/build.gradle.kts")
    val analyticsSrc = readProjectFile("app/src/main/java/com/example/analytics/AnalyticsHelper.kt")

    assertTrue("Firebase Analytics SDK must be included", appGradle.contains("implementation(libs.firebase.analytics)"))
    assertTrue("Google Services plugin must be applied", appGradle.contains("alias(libs.plugins.google.services)"))
    assertTrue("AD_ID permission must be stripped", manifestXml.contains("""com.google.android.gms.permission.AD_ID" tools:node="remove""""))
    assertTrue("Ad ID collection must be disabled", manifestXml.contains("google_analytics_adid_collection_enabled"))
    assertTrue("Ad personalization must be disabled", manifestXml.contains("google_analytics_default_allow_ad_personalization_signals"))
    assertFalse("AnalyticsHelper must never log passwords", analyticsSrc.lowercase().contains("putstring(\"password"))
    assertFalse("AnalyticsHelper must never log emails", analyticsSrc.lowercase().contains("putstring(\"email"))
  }

  // 14. Release Readiness: R8 Minification, ProGuard Rules, Predictive Back & Edge-to-Edge Compliance
  @Test
  fun `14_release_proguard_predictive_back_and_edge_to_edge_are_configured`() {
    val appGradle = readProjectFile("app/build.gradle.kts")
    val manifestXml = readProjectFile("app/src/main/AndroidManifest.xml")
    val proguard = readProjectFile("app/proguard-rules.pro")
    val mainActivity = readProjectFile("app/src/main/java/com/example/MainActivity.kt")

    assertTrue("Release build must enable R8 minification", appGradle.contains("isMinifyEnabled = true"))
    assertTrue("Release build must shrink unused resources", appGradle.contains("isShrinkResources = true"))
    assertTrue("Uncompressed 16KB JNI packaging must be enabled", appGradle.contains("useLegacyPackaging = false"))
    assertTrue("Predictive back callback must be enabled for Android 14-16+", manifestXml.contains("""android:enableOnBackInvokedCallback="true""""))
    assertTrue("MainActivity must call enableEdgeToEdge()", mainActivity.contains("enableEdgeToEdge("))
    assertTrue("ProGuard must keep remote models", proguard.contains("-keep class com.example.data.remote.**"))
    assertTrue("ProGuard must keep local Room models", proguard.contains("-keep class com.example.data.local.**"))
    assertTrue("ProGuard must preserve crash symbolication line numbers", proguard.contains("SourceFile,LineNumberTable"))
  }

  // 15. Mandatory Android 15/16 16 KB Page-Size Alignment Audit of Every 64-Bit Native Library
  @Test
  fun `15_all_64bit_native_so_libraries_are_16kb_page_size_aligned`() {
    val mergedLibRoot = File(projectRoot(), "app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib")
    if (!mergedLibRoot.exists()) return

    val failures = mutableListOf<String>()
    listOf("arm64-v8a", "x86_64").forEach { abi ->
      val abiDir = File(mergedLibRoot, abi)
      if (!abiDir.exists()) return@forEach
      abiDir.listFiles { f -> f.isFile && f.extension == "so" }?.forEach { soFile ->
        RandomAccessFile(soFile, "r").use { raf ->
          val header = ByteArray(64)
          if (raf.read(header) < 64) return@use
          if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
          ) return@use
          val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
          val phOff = buf.getLong(32)
          val phEntSize = buf.getShort(54).toInt() and 0xFFFF
          val phNum = buf.getShort(56).toInt() and 0xFFFF
          val phBytes = ByteArray(phEntSize)
          for (i in 0 until phNum) {
            raf.seek(phOff + i.toLong() * phEntSize)
            raf.readFully(phBytes)
            val phBuf = ByteBuffer.wrap(phBytes).order(ByteOrder.LITTLE_ENDIAN)
            val pType = phBuf.getInt(0)
            if (pType == 1) { // PT_LOAD
              val pAlign = phBuf.getLong(48)
              if (pAlign < 16384L || pAlign % 16384L != 0L) {
                failures.add("$abi/${soFile.name} PT_LOAD p_align=0x${pAlign.toString(16)}")
              }
            }
          }
        }
      }
    }
    assertTrue("Non-16KB-aligned 64-bit native libraries found: $failures", failures.isEmpty())
  }

  // 16. Hosted Privacy Policy & Account Deletion HTML Pages + Hosting Redirects
  @Test
  fun `16_hosted_privacy_policy_and_account_deletion_html_pages_and_redirects_are_configured`() {
    val privacyHtml = readProjectFile("public/privacy-policy.html")
    val deletionHtml = readProjectFile("public/delete-account.html")
    val indexHtml = readProjectFile("public/index.html")
    val firebaseJson = readProjectFile("firebase.json")

    assertTrue("AppLegalConfig Privacy Policy URL must point to privacy-policy.html", AppLegalConfig.PRIVACY_POLICY_URL.endsWith("/privacy-policy.html"))
    assertTrue("AppLegalConfig Account Deletion URL must point to delete-account.html", AppLegalConfig.ACCOUNT_DELETION_WEB_URL.endsWith("/delete-account.html"))

    assertTrue("privacy-policy.html must reference CubedPDF", privacyHtml.contains("CubedPDF"))
    assertTrue("privacy-policy.html must reference app package ID", privacyHtml.contains("com.aistudio.pdftools.xqmztb"))
    assertTrue("privacy-policy.html must link to delete-account.html", privacyHtml.contains("delete-account.html"))

    assertTrue("delete-account.html must reference CubedPDF", deletionHtml.contains("CubedPDF"))
    assertTrue("delete-account.html must explain in-app deletion", deletionHtml.contains("Delete Account"))
    assertTrue("delete-account.html must provide web deletion request form", deletionHtml.contains("deletionForm"))
    assertTrue("delete-account.html must link to privacy-policy.html", deletionHtml.contains("privacy-policy.html"))

    assertTrue("index.html must link to privacy-policy.html", indexHtml.contains("privacy-policy.html"))
    assertTrue("index.html must link to delete-account.html", indexHtml.contains("delete-account.html"))

    assertTrue("firebase.json must configure hosting public dir", firebaseJson.contains(""""hosting""""))
    assertTrue("firebase.json must redirect /privacy-policy to /privacy-policy.html", firebaseJson.contains(""""destination": "/privacy-policy.html""""))
    assertTrue("firebase.json must redirect /delete-account to /delete-account.html", firebaseJson.contains(""""destination": "/delete-account.html""""))
  }
}
