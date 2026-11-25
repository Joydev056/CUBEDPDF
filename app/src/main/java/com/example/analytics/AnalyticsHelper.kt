package com.example.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

/**
 * Privacy-first wrapper around Google Analytics for Firebase (`firebase-analytics`).
 *
 * Logs only aggregate, non-PII product events (screen views, tool launches, completion status,
 * theme toggles) and never records passwords, email addresses, or document contents.
 * All calls are safely guarded so local JVM/Robolectric tests without a live FirebaseApp
 * never throw.
 */
object AnalyticsHelper {

  @Volatile
  private var analyticsInstance: FirebaseAnalytics? = null

  fun initialize(context: Context) {
    runCatching {
      analyticsInstance = FirebaseAnalytics.getInstance(context.applicationContext)
    }
  }

  private fun getOrInit(): FirebaseAnalytics? {
    analyticsInstance?.let { return it }
    return runCatching {
      Firebase.analytics.also { analyticsInstance = it }
    }.getOrNull()
  }

  fun logScreenView(screenName: String, screenClass: String = "MainActivity") {
    val bundle = Bundle().apply {
      putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
      putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
    }
    runCatching {
      getOrInit()?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }
  }

  fun logToolOpened(toolId: String, toolName: String) {
    val bundle = Bundle().apply {
      putString("tool_id", toolId)
      putString("tool_name", toolName)
    }
    runCatching {
      getOrInit()?.logEvent("pdf_tool_opened", bundle)
    }
  }

  fun logToolCompleted(toolId: String, success: Boolean) {
    val bundle = Bundle().apply {
      putString("tool_id", toolId)
      putString("status", if (success) "success" else "error")
    }
    runCatching {
      getOrInit()?.logEvent("pdf_tool_completed", bundle)
    }
  }

  fun logAuthSession(method: String) {
    val bundle = Bundle().apply {
      putString(FirebaseAnalytics.Param.METHOD, method)
    }
    runCatching {
      getOrInit()?.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
    }
  }

  fun logThemeToggled(isDark: Boolean) {
    val bundle = Bundle().apply {
      putString("theme", if (isDark) "dark" else "light")
    }
    runCatching {
      getOrInit()?.logEvent("theme_toggled", bundle)
    }
  }
}
