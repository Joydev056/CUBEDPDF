package com.example.legal

/**
 * Centralized Google Play Policy legal URLs and in-app disclosure summaries
 * for CubedPDF Privacy Policy and Public Web Account Deletion.
 *
 * Replace the default HTTPS URLs with your hosted domain pages prior to
 * submitting the Play Console Data Safety and Privacy Policy forms.
 */
object AppLegalConfig {
  const val HOSTED_BASE_URL: String = "https://gen-lang-client-0042941445.web.app"
  const val PRIVACY_POLICY_URL: String = "$HOSTED_BASE_URL/privacy-policy.html"
  const val ACCOUNT_DELETION_WEB_URL: String = "$HOSTED_BASE_URL/delete-account.html"
  const val SUPPORT_EMAIL: String = "joydigitalworld2023@gmail.com"

  fun isValidHttpsUrl(url: String): Boolean {
    val trimmed = url.trim()
    return trimmed.startsWith("https://") && trimmed.length > "https://a.b".length && !trimmed.contains(" ")
  }

  const val PRIVACY_SUMMARY_BULLETS: String =
    "• On-Device Document Processing: All 24 PDF tools, AES-256-GCM encryption/decryption, page rotation, and ML Kit OCR text extraction run strictly on your device. Your PDF files are never uploaded to external servers.\n\n" +
      "• Optional Account & Cloud Sync: You may use CubedPDF completely offline via Guest Mode. If you sign in with Google or Email/Password, CubedPDF stores your display name, email address, workspace role, and optional recent-file metadata in your private Cloud Firestore record.\n\n" +
      "• Privacy-First Analytics: CubedPDF uses Google Analytics for Firebase solely for aggregate feature usage without Advertising IDs (AD_ID disabled) and without ad personalization.\n\n" +
      "• Encryption & Data Deletion: All network traffic uses HTTPS/TLS. You can permanently delete your account and cloud data anytime inside the app or via our web deletion page."

  const val WEB_DELETION_SUMMARY: String =
    "You can permanently delete your CubedPDF account and all associated Cloud Firestore data in two ways:\n\n" +
      "1. Inside the App (Instant): Open Account Profile → tap 'Delete Account & Cloud Data' → confirm (and re-verify sign-in if prompted). Your Firestore profile, synced recent-document history, and Firebase Authentication account are permanently deleted.\n\n" +
      "2. Online via Web Request: Visit our public account deletion page ($ACCOUNT_DELETION_WEB_URL) or email $SUPPORT_EMAIL from your registered email address to request deletion without reinstalling the app.\n\n" +
      "Note: Local PDF files saved on your device are never deleted by cloud account removal."
}
