package com.example.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreferencesTest {

    @Test
    fun defaultSettings_haveSensibleValues() {
        val settings = KeyboardSettings()

        assertEquals(KeyboardThemeType.MATERIAL_DARK, settings.theme)
        assertTrue(settings.hapticFeedback)
        assertTrue(settings.showNumberRow)
        assertTrue(settings.showKeyPopups)
        assertTrue(settings.autoCapitalization)
        assertTrue(settings.swipeTypingEnabled)
        assertEquals(1.0f, settings.heightMultiplier, 0.001f)
        assertEquals(1.0f, settings.fontScale, 0.001f)
        assertEquals(1.0f, settings.emojiScale, 0.001f)
    }

    @Test
    fun customColors_defaultValuesAreValidArgb() {
        val colors = CustomThemeColors()

        assertNotNull(colors.background)
        assertNotNull(colors.keyBackground)
        assertNotNull(colors.keyTextColor)
        assertNotNull(colors.accentKeyBackground)
        assertNotNull(colors.accentKeyTextColor)
        assertNotNull(colors.actionKeyBackground)
        assertNotNull(colors.actionKeyTextColor)
    }

    @Test
    fun keyboardThemeType_displayNamesAreNonEmpty() {
        for (theme in KeyboardThemeType.entries) {
            assertTrue(theme.displayName.isNotBlank())
        }
    }
}
