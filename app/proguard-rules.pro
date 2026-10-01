# Compose Keyboard ProGuard Rules

# Keep Android component entry points declared in AndroidManifest.xml
-keep class io.github.xxparthparekhxx.composekeyboard.service.ComposeInputMethodService { *; }
-keep class io.github.xxparthparekhxx.composekeyboard.MainActivity { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Preserve enum values, valueOf, and enum constant fields
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final ** *;
}

# Keep project data models and enums
-keep enum io.github.xxparthparekhxx.composekeyboard.** { *; }
-keep class io.github.xxparthparekhxx.composekeyboard.data.** { *; }

# Keep whisper.cpp JNI bindings. The Maven coordinate is dev.ffmpegkit-maintained,
# but the Java package inside the AAR is dev.ffmpegkit.whisper (verified against
# whisper-android-1.0.0.aar). The library also ships consumer rules keeping
# WhisperJNI + native methods, so this is belt-and-braces.
-keep class dev.ffmpegkit.whisper.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn org.jetbrains.kotlinx.**

# Keep attributes for debugging & stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod