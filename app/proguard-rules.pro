# CubedPDF Release R8 / ProGuard Rules for Google Play App Bundle (AAB)

# Keep Cloud Firestore and Room data models
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.local.** { *; }

# Keep @Keep annotated classes and members
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Keep ML Kit Vision & Credential Manager entry points
-keep class com.google.mlkit.vision.text.** { *; }
-keep class androidx.credentials.** { *; }

# Preserve line numbers for Play Console crash symbolication
-keepattributes SourceFile,LineNumberTable
