# Compose Keyboard ProGuard Rules

# Keep Android component entry points declared in AndroidManifest.xml
-keep class com.example.composekeyboard.service.ComposeInputMethodService { *; }
-keep class com.example.composekeyboard.MainActivity { *; }

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
-keep enum com.example.composekeyboard.** { *; }
-keep class com.example.composekeyboard.data.** { *; }

# Suppress warnings for missing optional dependencies
-dontwarn com.google.android.gms.**
-dontwarn org.jetbrains.kotlinx.**

# Keep attributes for debugging & stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod