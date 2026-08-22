# Compose Keyboard ProGuard Rules

# Keep neural decoder classes and native serialization
-keep class com.example.composekeyboard.input.swipe.nn.** { *; }

# Keep dictionary and learning logic
-keep class com.example.composekeyboard.data.SwipeDictionary { *; }
-keep class com.example.composekeyboard.data.SwipeDictionary$Entry { *; }

# Keep clipboard history manager
-keep class com.example.composekeyboard.data.ClipboardHistoryManager { *; }
-keep class com.example.composekeyboard.data.ClipboardItem { *; }

# Keep preferences
-keep class com.example.composekeyboard.data.KeyboardPreferences { *; }
-keep class com.example.composekeyboard.data.KeyboardSettings { *; }
-keep class com.example.composekeyboard.data.CustomThemeColors { *; }

# Keep key model and layout data
-keep class com.example.composekeyboard.data.KeyModel { *; }
-keep class com.example.composekeyboard.data.KeyType { *; }
-keep class com.example.composekeyboard.data.KeyboardLayouts { *; }
-keep class com.example.composekeyboard.data.KeyboardMode { *; }

# Keep swipe geometry and trace
-keep class com.example.composekeyboard.input.swipe.SwipeKeyGeometry { *; }
-keep class com.example.composekeyboard.input.swipe.SwipeKeyMap { *; }
-keep class com.example.composekeyboard.input.swipe.SwipeTrace { *; }
-keep class com.example.composekeyboard.input.swipe.SampledSwipe { *; }
-keep class com.example.composekeyboard.input.swipe.SwipeController { *; }
-keep class com.example.composekeyboard.input.swipe.SwipeGestureHandler { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Keep Kotlinx Serialization / JSON (if used)
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }

# Keep Compose runtime internals
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# Keep Lifecycle/ViewModel
-keep class androidx.lifecycle.** { *; }

# Keep SavedState
-keep class androidx.savedstate.** { *; }

# Keep Datastore
-keep class androidx.datastore.** { *; }

# Keep Hilt/Dagger if used (not currently but safe)
-keep class dagger.** { *; }
-keep class hilt.** { *; }

# Keep AndroidX annotations
-keep @interface androidx.annotation.** { *; }

# Prevent obfuscation of enum values used in serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Service entry point
-keep class com.example.composekeyboard.service.ComposeInputMethodService { *; }

# Keep MainActivity for companion app
-keep class com.example.composekeyboard.MainActivity { *; }

# Keep theme classes
-keep class com.example.composekeyboard.theme.** { *; }

# Suppress warnings for missing classes in optional dependencies
-dontwarn com.google.android.gms.**
-dontwarn org.jetbrains.kotlinx.**

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod