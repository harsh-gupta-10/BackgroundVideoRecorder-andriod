# Keep app classes
-keep class com.bgrecorder.app.** { *; }

# Keep widget provider
-keep class com.bgrecorder.app.widget.RecorderWidget { *; }

# Keep service
-keep class com.bgrecorder.app.service.RecordingService { *; }

# AndroidX & Material
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

# Suppress warnings
-dontwarn android.hardware.camera2.**
