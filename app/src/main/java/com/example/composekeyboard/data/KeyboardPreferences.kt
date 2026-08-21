package com.example.composekeyboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KeyboardThemeType(val displayName: String) {
    MATERIAL_DARK("Dark Slate"),
    MATERIAL_LIGHT("Clean Light"),
    AMOLED("Pitch Black AMOLED"),
    DYNAMIC_DARK("Material You (Dark)"),
    DYNAMIC_LIGHT("Material You (Light)"),
    NORD("Nordic Frost"),
    SUNSET("Sunset Glow"),
    CYBERPUNK("Cyber Neon"),
    CUSTOM("Custom Theme")
}

data class CustomThemeColors(
    val background: Long = 0xFF181824,
    val keyBackground: Long = 0xFF242436,
    val keyTextColor: Long = 0xFFFFFFFF,
    val accentKeyBackground: Long = 0xFF32324A,
    val accentKeyTextColor: Long = 0xFFB0B8C8,
    val actionKeyBackground: Long = 0xFF6366F1,
    val actionKeyTextColor: Long = 0xFFFFFFFF
)

data class KeyboardSettings(
    val theme: KeyboardThemeType = KeyboardThemeType.MATERIAL_DARK,
    val customColors: CustomThemeColors = CustomThemeColors(),
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val showNumberRow: Boolean = true,
    val showKeyPopups: Boolean = true,
    val autoCapitalization: Boolean = true,
    val swipeTypingEnabled: Boolean = true,
    val heightMultiplier: Float = 1.0f
)

class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("compose_keyboard_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<KeyboardSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = loadSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun loadSettings(): KeyboardSettings {
        val themeStr = prefs.getString(KEY_THEME, KeyboardThemeType.MATERIAL_DARK.name)
        val theme = try {
            KeyboardThemeType.valueOf(themeStr ?: KeyboardThemeType.MATERIAL_DARK.name)
        } catch (e: Exception) {
            KeyboardThemeType.MATERIAL_DARK
        }

        val customColors = CustomThemeColors(
            background = prefs.getLong(KEY_CUSTOM_BG, 0xFF181824),
            keyBackground = prefs.getLong(KEY_CUSTOM_KEY_BG, 0xFF242436),
            keyTextColor = prefs.getLong(KEY_CUSTOM_KEY_TEXT, 0xFFFFFFFF),
            accentKeyBackground = prefs.getLong(KEY_CUSTOM_ACCENT_BG, 0xFF32324A),
            accentKeyTextColor = prefs.getLong(KEY_CUSTOM_ACCENT_TEXT, 0xFFB0B8C8),
            actionKeyBackground = prefs.getLong(KEY_CUSTOM_ACTION_BG, 0xFF6366F1),
            actionKeyTextColor = prefs.getLong(KEY_CUSTOM_ACTION_TEXT, 0xFFFFFFFF)
        )

        return KeyboardSettings(
            theme = theme,
            customColors = customColors,
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
            soundFeedback = prefs.getBoolean(KEY_SOUND, false),
            showNumberRow = prefs.getBoolean(KEY_NUMBER_ROW, true),
            showKeyPopups = prefs.getBoolean(KEY_KEY_POPUPS, true),
            autoCapitalization = prefs.getBoolean(KEY_AUTO_CAPS, true),
            swipeTypingEnabled = prefs.getBoolean(KEY_SWIPE_TYPING, true),
            heightMultiplier = prefs.getFloat(KEY_HEIGHT, 1.0f)
        )
    }

    fun setTheme(theme: KeyboardThemeType) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setCustomColors(colors: CustomThemeColors) {
        prefs.edit()
            .putLong(KEY_CUSTOM_BG, colors.background)
            .putLong(KEY_CUSTOM_KEY_BG, colors.keyBackground)
            .putLong(KEY_CUSTOM_KEY_TEXT, colors.keyTextColor)
            .putLong(KEY_CUSTOM_ACCENT_BG, colors.accentKeyBackground)
            .putLong(KEY_CUSTOM_ACCENT_TEXT, colors.accentKeyTextColor)
            .putLong(KEY_CUSTOM_ACTION_BG, colors.actionKeyBackground)
            .putLong(KEY_CUSTOM_ACTION_TEXT, colors.actionKeyTextColor)
            .putString(KEY_THEME, KeyboardThemeType.CUSTOM.name)
            .apply()
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    fun setSoundFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setShowNumberRow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    fun setShowKeyPopups(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEY_POPUPS, enabled).apply()
    }

    fun setAutoCapitalization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CAPS, enabled).apply()
    }

    fun setSwipeTypingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_TYPING, enabled).apply()
    }

    fun setHeightMultiplier(multiplier: Float) {
        prefs.edit().putFloat(KEY_HEIGHT, multiplier).apply()
    }

    companion object {
        private const val KEY_THEME = "keyboard_theme"
        private const val KEY_CUSTOM_BG = "custom_bg"
        private const val KEY_CUSTOM_KEY_BG = "custom_key_bg"
        private const val KEY_CUSTOM_KEY_TEXT = "custom_key_text"
        private const val KEY_CUSTOM_ACCENT_BG = "custom_accent_bg"
        private const val KEY_CUSTOM_ACCENT_TEXT = "custom_accent_text"
        private const val KEY_CUSTOM_ACTION_BG = "custom_action_bg"
        private const val KEY_CUSTOM_ACTION_TEXT = "custom_action_text"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_SOUND = "sound_feedback"
        private const val KEY_NUMBER_ROW = "show_number_row"
        private const val KEY_KEY_POPUPS = "show_key_popups"
        private const val KEY_AUTO_CAPS = "auto_capitalization"
        private const val KEY_SWIPE_TYPING = "swipe_typing_enabled"
        private const val KEY_HEIGHT = "height_multiplier"

        @Volatile
        private var INSTANCE: KeyboardPreferences? = null

        fun getInstance(context: Context): KeyboardPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KeyboardPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
