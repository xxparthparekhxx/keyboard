package com.example.composekeyboard.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.composekeyboard.data.CustomThemeColors
import com.example.composekeyboard.data.KeyboardThemeType

@Immutable
data class KeyboardColors(
    val background: Color,
    val keyBackground: Color,
    val keyTextColor: Color,
    val accentKeyBackground: Color,
    val accentKeyTextColor: Color,
    val actionKeyBackground: Color,
    val actionKeyTextColor: Color,
    val headerBackground: Color,
    val headerIconColor: Color,
    val keyShadow: Color,
    val popupBackground: Color,
    val popupTextColor: Color,
    val spaceBarText: Color
)

val LocalKeyboardColors = staticCompositionLocalOf {
    KeyboardColors(
        background = DarkBg,
        keyBackground = DarkKeyBg,
        keyTextColor = DarkKeyText,
        accentKeyBackground = DarkAccentKeyBg,
        accentKeyTextColor = DarkAccentKeyText,
        actionKeyBackground = DarkActionKeyBg,
        actionKeyTextColor = DarkActionKeyText,
        headerBackground = DarkBg,
        headerIconColor = DarkAccentKeyText,
        keyShadow = Color.Black.copy(alpha = 0.3f),
        popupBackground = DarkKeyBg,
        popupTextColor = DarkKeyText,
        spaceBarText = DarkAccentKeyText.copy(alpha = 0.6f)
    )
}

@Composable
fun getKeyboardColors(
    themeType: KeyboardThemeType,
    customColors: CustomThemeColors = CustomThemeColors()
): KeyboardColors {
    val context = LocalContext.current

    return when (themeType) {
        KeyboardThemeType.CUSTOM -> {
            val bg = Color(customColors.background)
            val keyBg = Color(customColors.keyBackground)
            val keyText = Color(customColors.keyTextColor)
            val accentBg = Color(customColors.accentKeyBackground)
            val accentText = Color(customColors.accentKeyTextColor)
            val actionBg = Color(customColors.actionKeyBackground)
            val actionText = Color(customColors.actionKeyTextColor)

            KeyboardColors(
                background = bg,
                keyBackground = keyBg,
                keyTextColor = keyText,
                accentKeyBackground = accentBg,
                accentKeyTextColor = accentText,
                actionKeyBackground = actionBg,
                actionKeyTextColor = actionText,
                headerBackground = bg,
                headerIconColor = accentText,
                keyShadow = Color.Black.copy(alpha = 0.25f),
                popupBackground = accentBg,
                popupTextColor = keyText,
                spaceBarText = accentText.copy(alpha = 0.6f)
            )
        }
        KeyboardThemeType.DYNAMIC_DARK, KeyboardThemeType.DYNAMIC_LIGHT -> {
            val isDark = themeType == KeyboardThemeType.DYNAMIC_DARK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamicScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                KeyboardColors(
                    background = dynamicScheme.surfaceContainer,
                    keyBackground = dynamicScheme.surfaceContainerHigh,
                    keyTextColor = dynamicScheme.onSurface,
                    accentKeyBackground = dynamicScheme.surfaceContainerHighest,
                    accentKeyTextColor = dynamicScheme.onSurfaceVariant,
                    actionKeyBackground = dynamicScheme.primary,
                    actionKeyTextColor = dynamicScheme.onPrimary,
                    headerBackground = dynamicScheme.surfaceContainer,
                    headerIconColor = dynamicScheme.onSurfaceVariant,
                    keyShadow = Color.Black.copy(alpha = if (isDark) 0.3f else 0.12f),
                    popupBackground = dynamicScheme.inverseSurface,
                    popupTextColor = dynamicScheme.inverseOnSurface,
                    spaceBarText = dynamicScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                if (isDark) getDarkColors() else getLightColors()
            }
        }
        KeyboardThemeType.MATERIAL_DARK -> getDarkColors()
        KeyboardThemeType.MATERIAL_LIGHT -> getLightColors()
        KeyboardThemeType.AMOLED -> KeyboardColors(
            background = AmoledBg,
            keyBackground = AmoledKeyBg,
            keyTextColor = AmoledKeyText,
            accentKeyBackground = AmoledAccentKeyBg,
            accentKeyTextColor = AmoledAccentKeyText,
            actionKeyBackground = AmoledActionKeyBg,
            actionKeyTextColor = AmoledActionKeyText,
            headerBackground = AmoledBg,
            headerIconColor = AmoledAccentKeyText,
            keyShadow = Color.Transparent,
            popupBackground = Color(0xFF222222),
            popupTextColor = AmoledKeyText,
            spaceBarText = AmoledAccentKeyText.copy(alpha = 0.5f)
        )
        KeyboardThemeType.NORD -> KeyboardColors(
            background = NordBg,
            keyBackground = NordKeyBg,
            keyTextColor = NordKeyText,
            accentKeyBackground = NordAccentKeyBg,
            accentKeyTextColor = NordAccentKeyText,
            actionKeyBackground = NordActionKeyBg,
            actionKeyTextColor = NordActionKeyText,
            headerBackground = NordBg,
            headerIconColor = NordAccentKeyText,
            keyShadow = Color.Black.copy(alpha = 0.25f),
            popupBackground = NordAccentKeyBg,
            popupTextColor = NordKeyText,
            spaceBarText = NordAccentKeyText.copy(alpha = 0.6f)
        )
        KeyboardThemeType.SUNSET -> KeyboardColors(
            background = SunsetBg,
            keyBackground = SunsetKeyBg,
            keyTextColor = SunsetKeyText,
            accentKeyBackground = SunsetAccentKeyBg,
            accentKeyTextColor = SunsetAccentKeyText,
            actionKeyBackground = SunsetActionKeyBg,
            actionKeyTextColor = SunsetActionKeyText,
            headerBackground = SunsetBg,
            headerIconColor = SunsetAccentKeyText,
            keyShadow = Color.Black.copy(alpha = 0.3f),
            popupBackground = SunsetAccentKeyBg,
            popupTextColor = SunsetKeyText,
            spaceBarText = SunsetAccentKeyText.copy(alpha = 0.6f)
        )
        KeyboardThemeType.CYBERPUNK -> KeyboardColors(
            background = CyberBg,
            keyBackground = CyberKeyBg,
            keyTextColor = CyberKeyText,
            accentKeyBackground = CyberAccentKeyBg,
            accentKeyTextColor = CyberAccentKeyText,
            actionKeyBackground = CyberActionKeyBg,
            actionKeyTextColor = CyberActionKeyText,
            headerBackground = CyberBg,
            headerIconColor = CyberKeyText,
            keyShadow = Color(0xFF00F0FF).copy(alpha = 0.15f),
            popupBackground = CyberAccentKeyBg,
            popupTextColor = CyberKeyText,
            spaceBarText = CyberAccentKeyText.copy(alpha = 0.7f)
        )
    }
}

private fun getDarkColors() = KeyboardColors(
    background = DarkBg,
    keyBackground = DarkKeyBg,
    keyTextColor = DarkKeyText,
    accentKeyBackground = DarkAccentKeyBg,
    accentKeyTextColor = DarkAccentKeyText,
    actionKeyBackground = DarkActionKeyBg,
    actionKeyTextColor = DarkActionKeyText,
    headerBackground = DarkBg,
    headerIconColor = DarkAccentKeyText,
    keyShadow = Color.Black.copy(alpha = 0.35f),
    popupBackground = Color(0xFF38384A),
    popupTextColor = DarkKeyText,
    spaceBarText = DarkAccentKeyText.copy(alpha = 0.6f)
)

private fun getLightColors() = KeyboardColors(
    background = LightBg,
    keyBackground = LightKeyBg,
    keyTextColor = LightKeyText,
    accentKeyBackground = LightAccentKeyBg,
    accentKeyTextColor = LightAccentKeyText,
    actionKeyBackground = LightActionKeyBg,
    actionKeyTextColor = LightActionKeyText,
    headerBackground = LightBg,
    headerIconColor = LightAccentKeyText,
    keyShadow = Color.Black.copy(alpha = 0.1f),
    popupBackground = Color(0xFFFFFFFF),
    popupTextColor = LightKeyText,
    spaceBarText = LightAccentKeyText.copy(alpha = 0.6f)
)

@Composable
fun ComposeKeyboardTheme(
    themeType: KeyboardThemeType = KeyboardThemeType.MATERIAL_DARK,
    customColors: CustomThemeColors = CustomThemeColors(),
    content: @Composable () -> Unit
) {
    val keyboardColors = getKeyboardColors(themeType, customColors)
    val isLight = themeType == KeyboardThemeType.MATERIAL_LIGHT ||
            themeType == KeyboardThemeType.DYNAMIC_LIGHT

    val colorScheme = if (isLight) {
        lightColorScheme(
            primary = keyboardColors.actionKeyBackground,
            onPrimary = keyboardColors.actionKeyTextColor,
            surface = keyboardColors.background,
            onSurface = keyboardColors.keyTextColor
        )
    } else {
        darkColorScheme(
            primary = keyboardColors.actionKeyBackground,
            onPrimary = keyboardColors.actionKeyTextColor,
            surface = keyboardColors.background,
            onSurface = keyboardColors.keyTextColor
        )
    }

    CompositionLocalProvider(LocalKeyboardColors provides keyboardColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
