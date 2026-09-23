package io.github.xxparthparekhxx.composekeyboard.ui.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelState
import io.github.xxparthparekhxx.composekeyboard.theme.getKeyboardColors
import kotlin.math.roundToInt

/** Top level of the Settings tab: grouped toggles plus rows that open detail pages. */
@Composable
fun SettingsScreen(
    settings: KeyboardSettings,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    onOpenSize: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val voice = rememberVoiceStatus()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            SettingsGroup(title = stringResource(R.string.set_section_keys)) {
                SettingsSwitchItem(
                    icon = Icons.Default.Dialpad,
                    title = stringResource(R.string.set_number_row),
                    summary = stringResource(R.string.set_number_row_desc),
                    checked = settings.showNumberRow,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowNumberRow(checked) }
                    }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.set_popups),
                    summary = stringResource(R.string.set_popups_desc),
                    checked = settings.showKeyPopups,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowKeyPopups(checked) }
                    }
                )
                SettingsNavItem(
                    icon = Icons.Default.Height,
                    title = stringResource(R.string.set_size_title),
                    summary = stringResource(
                        R.string.set_size_summary,
                        settings.heightMultiplier.asPercent(),
                        settings.fontScale.asPercent(),
                        settings.emojiScale.asPercent()
                    ),
                    onClick = onOpenSize
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.set_section_typing)) {
                SettingsSwitchItem(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.set_glide),
                    summary = stringResource(R.string.set_glide_desc),
                    checked = settings.swipeTypingEnabled,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSwipeTypingEnabled(checked) }
                    }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.KeyboardCapslock,
                    title = stringResource(R.string.set_autocaps),
                    summary = stringResource(R.string.set_autocaps_desc),
                    checked = settings.autoCapitalization,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setAutoCapitalization(checked) }
                    }
                )
                SettingsNavItem(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.set_voice),
                    summary = voice.summary(),
                    onClick = onOpenVoice
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.set_section_feedback)) {
                SettingsSwitchItem(
                    icon = Icons.Default.Vibration,
                    title = stringResource(R.string.set_haptic),
                    summary = stringResource(R.string.set_haptic_desc),
                    checked = settings.hapticFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setHapticFeedback(checked) }
                    }
                )
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = stringResource(R.string.set_sounds),
                    summary = stringResource(R.string.set_sounds_desc),
                    checked = settings.soundFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSoundFeedback(checked) }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.set_about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.set_about_version, appVersionName),
                    summary = stringResource(R.string.set_about_body)
                )
            }
        }
    }
}

@Composable
private fun VoiceStatus.summary(): String = when {
    !supported -> stringResource(R.string.set_voice_summary_unsupported)
    model is WhisperModelState.Downloading ->
        stringResource(R.string.set_downloading, (model.fraction * 100).toInt())
    ready -> stringResource(R.string.set_voice_summary_ready)
    else -> stringResource(R.string.set_voice_summary_setup)
}

private fun Float.asPercent(): Int = (this * 100).roundToInt()

/** Detail page for the three size multipliers, with a live preview pinned above the sliders. */
@Composable
fun KeyboardSizeScreen(
    settings: KeyboardSettings,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDefault = settings.heightMultiplier == 1f &&
        settings.fontScale == 1f &&
        settings.emojiScale == 1f

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            SectionHeader(stringResource(R.string.set_size_preview))
            KeyboardSizePreview(settings)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroup {
                    ScaleSettingItem(
                        title = stringResource(R.string.set_height),
                        subtitle = stringResource(R.string.set_height_desc),
                        value = settings.heightMultiplier,
                        valueRange = 0.70f..1.40f,
                        steps = 13,
                        presets = listOf(
                            stringResource(R.string.size_short) to 0.75f,
                            stringResource(R.string.size_compact) to 0.85f,
                            stringResource(R.string.size_standard) to 1.0f,
                            stringResource(R.string.size_tall) to 1.15f,
                            stringResource(R.string.size_extra_tall) to 1.30f
                        ),
                        selectionTolerance = 0.04f,
                        onValueChange = { value ->
                            onUpdateSettings { it.setHeightMultiplier(value) }
                        }
                    )
                    ScaleSettingItem(
                        title = stringResource(R.string.set_key_text),
                        subtitle = stringResource(R.string.set_key_text_desc),
                        value = settings.fontScale,
                        valueRange = 0.75f..1.40f,
                        steps = 12,
                        presets = listOf(
                            stringResource(R.string.size_small) to 0.85f,
                            stringResource(R.string.size_normal) to 1.00f,
                            stringResource(R.string.size_large) to 1.15f,
                            stringResource(R.string.size_extra_large) to 1.30f,
                            stringResource(R.string.size_huge) to 1.40f
                        ),
                        selectionTolerance = 0.035f,
                        onValueChange = { value ->
                            onUpdateSettings { it.setFontScale(value) }
                        }
                    )
                    ScaleSettingItem(
                        title = stringResource(R.string.set_emoji_size),
                        subtitle = stringResource(R.string.set_emoji_size_desc),
                        value = settings.emojiScale,
                        valueRange = 0.75f..1.40f,
                        steps = 12,
                        presets = listOf(
                            stringResource(R.string.size_small) to 0.85f,
                            stringResource(R.string.size_normal) to 1.00f,
                            stringResource(R.string.size_large) to 1.15f,
                            stringResource(R.string.size_extra_large) to 1.30f,
                            stringResource(R.string.size_huge) to 1.40f
                        ),
                        selectionTolerance = 0.035f,
                        onValueChange = { value ->
                            onUpdateSettings { it.setEmojiScale(value) }
                        }
                    )
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = {
                            onUpdateSettings {
                                it.setHeightMultiplier(1f)
                                it.setFontScale(1f)
                                it.setEmojiScale(1f)
                            }
                        },
                        enabled = !isDefault
                    ) {
                        Text(stringResource(R.string.set_size_reset))
                    }
                }
            }
        }
    }
}

/**
 * A scaled-down keyboard in the user's theme. Key height follows the height
 * multiplier, labels follow the text scale, and the strip shows emoji scale.
 */
@Composable
private fun KeyboardSizePreview(settings: KeyboardSettings) {
    val colors = getKeyboardColors(settings.theme, settings.customColors)
    val keyHeight by animateDpAsState(30.dp * settings.heightMultiplier, label = "preview-key-height")
    val labelSize = (13 * settings.fontScale).sp
    val emojiSize = (18 * settings.emojiScale).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("😀", "👍", "❤️", "🎉", "🔥").forEach { emoji ->
                Text(text = emoji, fontSize = emojiSize)
            }
        }
        PreviewRow {
            "qwertyuiop".forEach { PreviewKey(it.toString(), colors.keyBackground, colors.keyTextColor, keyHeight, labelSize) }
        }
        PreviewRow {
            "asdfghjkl".forEach { PreviewKey(it.toString(), colors.keyBackground, colors.keyTextColor, keyHeight, labelSize) }
        }
        PreviewRow {
            PreviewKey("⇧", colors.accentKeyBackground, colors.accentKeyTextColor, keyHeight, labelSize, weight = 1.5f)
            "zxcvbnm".forEach { PreviewKey(it.toString(), colors.keyBackground, colors.keyTextColor, keyHeight, labelSize) }
            PreviewKey("⌫", colors.accentKeyBackground, colors.accentKeyTextColor, keyHeight, labelSize, weight = 1.5f)
        }
        PreviewRow {
            PreviewKey("?123", colors.accentKeyBackground, colors.accentKeyTextColor, keyHeight, labelSize * 0.8f, weight = 1.5f)
            PreviewKey("", colors.keyBackground, colors.spaceBarText, keyHeight, labelSize, weight = 5f)
            PreviewKey("↵", colors.actionKeyBackground, colors.actionKeyTextColor, keyHeight, labelSize, weight = 1.5f)
        }
    }
}

@Composable
private fun PreviewRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun RowScope.PreviewKey(
    label: String,
    background: Color,
    textColor: Color,
    height: Dp,
    fontSize: TextUnit,
    weight: Float = 1f
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
