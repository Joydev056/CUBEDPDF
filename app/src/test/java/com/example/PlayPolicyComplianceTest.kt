package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.pdf.PdfEngine
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Automated Google Play Console Policy & Upload Compliance Test Suite.
 * Verifies metadata, package identity, SDK targets, permissions minimization,
 * network security, FileProvider scoping, adaptive launcher icons, and WebView hardening.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PlayPolicyComplianceTest {

  @Test
  fun `app metadata title complies with Google Play Store branding policy`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val title = context.getString(R.string.app_name)

    assertTrue("App title must be non-blank", title.isNotBlank())
    assertTrue("App title must be <= 30 characters (got ${title.length})", title.length <= 30)
    assertFalse("App title must not be ALL CAPS", title == title.uppercase())
    assertTrue(
      "App title must not contain emojis or non-ASCII symbols",
      title.all { it.code in 32..126 }
    )

    val prohibitedBuzzwords = listOf("free", "best", "#1", "top", "download now", "100%")
    val lowerTitle = title.lowercase()
    prohibitedBuzzwords.forEach { word ->
      assertFalse("App title must not contain promotional buzzword '$word'", lowerTitle.contains(word))
    }
  }

  @Test
  fun `applicationId targetSdk versionCode and versionName meet Google Play Console upload requirements`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageName = context.packageName
    assertFalse(
      "Google Play rejects com.example.* applicationIds",
      packageName.startsWith("com.example")
    )
    assertEquals("com.aistudio.pdftools.xqmztb", packageName)

    val appInfo = context.applicationInfo
    assertTrue(
      "Google Play requires targetSdk >= 35 (got ${appInfo.targetSdkVersion})",
      appInfo.targetSdkVersion >= 35
    )
    assertTrue("minSdk must be >= 24", appInfo.minSdkVersion >= 24)

    val pkgInfo = context.packageManager.getPackageInfo(packageName, 0)
    assertTrue("versionCode must be >= 1", pkgInfo.longVersionCode >= 1L)
    assertTrue("versionName must be non-blank", !pkgInfo.versionName.isNullOrBlank())
  }

  @Test
  fun `merged manifest contains zero prohibited or broad Google Play permissions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PERMISSIONS
    )
    val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()

    val prohibitedPermissions = setOf(
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
      "com.google.android.gms.permission.AD_ID"
    )

    val violations = requested.intersect(prohibitedPermissions)
    assertTrue("Prohibited permissions found in merged manifest: $violations", violations.isEmpty())
  }

  @Test
  fun `network security cleartext traffic is disabled and adaptive launcher icons exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appInfo = context.applicationInfo

    // Verify android:usesCleartextTraffic="false"
    val allowsCleartext = (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
    assertFalse("usesCleartextTraffic must be false for Google Play security compliance", allowsCleartext)

    // Verify adaptive launcher icons resolve cleanly
    assertNotNull("ic_launcher drawable must exist", ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
    assertNotNull("ic_launcher_round drawable must exist", ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
  }

  @Test
  fun `fileProvider is non-exported grants uri permissions and uses app-specific authority`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PROVIDERS
    )
    val fileProvider = pkgInfo.providers?.firstOrNull {
      it.name == "androidx.core.content.FileProvider"
    }
    assertNotNull("FileProvider must be registered in manifest", fileProvider)
    assertFalse("FileProvider MUST NOT be exported", fileProvider!!.exported)
    assertTrue("FileProvider must grant URI permissions", fileProvider.grantUriPermissions)
    assertEquals("${context.packageName}.fileprovider", fileProvider.authority)
  }

  @Test
  fun `webpageToPdf enforces HTTPS-only URLs and rejects unsafe schemes`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val blockedInputs = listOf(
      "http://example.com",
      "file:///etc/hosts",
      "content://com.example/secret",
      "javascript:alert(1)",
      "data:text/html,<h1>x</h1>",
      "ftp://files.example.com/doc.pdf"
    )
    blockedInputs.forEach { unsafeInput ->
      val res = PdfEngine.webpageToPdf(context, unsafeInput)
      assertTrue("Expected unsafe scheme '$unsafeInput' to be rejected", res.isFailure)
      assertTrue(
        "Expected clear HTTPS error for '$unsafeInput', got: ${res.exceptionOrNull()?.message}",
        res.exceptionOrNull()?.message?.contains("https://", ignoreCase = true) == true
      )
    }
  }
}
