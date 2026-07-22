# Aesthetic Camera — ProGuard & R8 Optimization Rules

# Preserve Jetpack Compose runtime classes & annotations
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# Preserve Kotlin Coroutines
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.ContinuationImpl { *; }
-dontwarn kotlinx.coroutines.**

# Preserve AndroidX MediaStore & FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Preserve CameraX & Android Camera2
-keep class androidx.camera.** { *; }

# Preserve Data Models
-keep class com.example.aestheticcamera.ui.editor.model.** { *; }
-keep class com.example.aestheticcamera.ui.editor.EditState { *; }
