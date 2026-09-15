package com.example.composekeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.composekeyboard.data.KeyboardMode
import com.example.composekeyboard.theme.LocalKeyboardColors

@Composable
fun KeyboardHeader(
    currentMode: KeyboardMode,
    onEmojiClick: () -> Unit,
    onClipboardClick: () -> Unit,
    onNumpadClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.headerBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Quick Numpad button
        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Number Pad",
                    tint = if (currentMode == KeyboardMode.NUMPAD) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onNumpadClick
        )

        // Quick Emoji button
        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = "Emoji",
                    tint = if (currentMode == KeyboardMode.EMOJI) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onEmojiClick
        )

        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice typing",
                    tint = if (currentMode == KeyboardMode.VOICE) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onVoiceClick
        )

        // Clipboard history button
        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Clipboard History",
                    tint = if (currentMode == KeyboardMode.CLIPBOARD) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onClipboardClick
        )

        // Theme Switcher button (opens proper Theme Picker!)
        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Choose Theme",
                    tint = if (currentMode == KeyboardMode.THEMES) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onThemeClick
        )

        // Settings button
        HeaderIconButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (currentMode == KeyboardMode.SETTINGS) colors.actionKeyBackground else colors.headerIconColor,
                    modifier = Modifier.size(22.dp)
                )
            },
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .semantics { role = Role.Button }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
