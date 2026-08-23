package com.example.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.data.KeyboardSettings
import com.example.composekeyboard.theme.LocalKeyboardColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QuickSettingsView(
    settings: KeyboardSettings,
    hapticEnabled: Boolean,
    onHapticToggled: (Boolean) -> Unit,
    onSoundToggled: (Boolean) -> Unit,
    onNumberRowToggled: (Boolean) -> Unit,
    onAutoCapsToggled: (Boolean) -> Unit,
    onSwipeTypingToggled: (Boolean) -> Unit,
    onHeightMultiplierChanged: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit = {},
    onEmojiScaleChanged: (Float) -> Unit = {},
    onOpenFullSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current

    fun triggerHaptic() {
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.headerBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colors.headerIconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Quick Settings",
                    color = colors.keyTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Return to ABC
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.actionKeyBackground)
                    .clickable {
                        triggerHaptic()
                        onClose()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = colors.actionKeyTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Keyboard Height adjustment
            QuickSettingScaleRow(
                icon = Icons.Default.Height,
                title = "Keyboard Height",
                currentValue = settings.heightMultiplier,
                valueRange = 0.75f..1.35f,
                presets = listOf(
                    "75%" to 0.75f,
                    "85%" to 0.85f,
                    "100%" to 1.00f,
                    "115%" to 1.15f,
                    "130%" to 1.30f
                ),
                hapticEnabled = hapticEnabled,
                onValueChanged = onHeightMultiplierChanged
            )

            // Key Font Size adjustment
            QuickSettingScaleRow(
                icon = Icons.Default.TextFields,
                title = "Key Font Size",
                currentValue = settings.fontScale,
                valueRange = 0.80f..1.40f,
                presets = listOf(
                    "85%" to 0.85f,
                    "100%" to 1.00f,
                    "115%" to 1.15f,
                    "130%" to 1.30f,
                    "140%" to 1.40f
                ),
                hapticEnabled = hapticEnabled,
                onValueChanged = onFontScaleChanged
            )

            // Emoji Display Size adjustment
            QuickSettingScaleRow(
                icon = Icons.Default.Check,
                title = "Emoji Display Size",
                currentValue = settings.emojiScale,
                valueRange = 0.80f..1.40f,
                presets = listOf(
                    "85%" to 0.85f,
                    "100%" to 1.00f,
                    "115%" to 1.15f,
                    "130%" to 1.30f,
                    "140%" to 1.40f
                ),
                hapticEnabled = hapticEnabled,
                onValueChanged = onEmojiScaleChanged
            )

            // Glide (swipe) typing toggle
            QuickSettingToggleRow(
                icon = Icons.Default.Gesture,
                title = "Glide Typing",
                description = "Swipe across letters with neural decoder",
                checked = settings.swipeTypingEnabled,
                onCheckedChange = {
                    triggerHaptic()
                    onSwipeTypingToggled(it)
                }
            )

            // Number Row Toggle
            QuickSettingToggleRow(
                icon = Icons.Default.Keyboard,
                title = "Number Row",
                description = "Show dedicated 1-0 numbers above letters",
                checked = settings.showNumberRow,
                onCheckedChange = {
                    triggerHaptic()
                    onNumberRowToggled(it)
                }
            )

            // Haptic Feedback Toggle
            QuickSettingToggleRow(
                icon = Icons.Default.Vibration,
                title = "Haptic Vibration",
                description = "Vibrate lightly on keypress & swipe completion",
                checked = settings.hapticFeedback,
                onCheckedChange = {
                    triggerHaptic()
                    onHapticToggled(it)
                }
            )

            // Sound Toggle
            QuickSettingToggleRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Key Sounds",
                description = "Play audio click when pressing keys",
                checked = settings.soundFeedback,
                onCheckedChange = {
                    triggerHaptic()
                    onSoundToggled(it)
                }
            )

            // Auto-Caps Toggle
            QuickSettingToggleRow(
                icon = Icons.Default.TextFields,
                title = "Auto-Capitalize",
                description = "Capitalize first letter of new sentences",
                checked = settings.autoCapitalization,
                onCheckedChange = {
                    triggerHaptic()
                    onAutoCapsToggled(it)
                }
            )

            // Open Full App Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.keyBackground)
                    .clickable {
                        triggerHaptic()
                        onOpenFullSettings()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colors.actionKeyBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Open Full Settings App",
                        color = colors.actionKeyBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSettingScaleRow(
    icon: ImageVector,
    title: String,
    currentValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    presets: List<Pair<String, Float>>,
    hapticEnabled: Boolean,
    onValueChanged: (Float) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current

    fun triggerHaptic() {
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val percent = (currentValue * 100).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.keyBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentKeyTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = colors.keyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "$percent%",
                color = colors.actionKeyBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { (label, value) ->
                val isSelected = abs(currentValue - value) < 0.035f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) colors.actionKeyBackground else colors.accentKeyBackground
                        )
                        .clickable {
                            triggerHaptic()
                            onValueChanged(value)
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) colors.actionKeyTextColor else colors.accentKeyTextColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSettingToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalKeyboardColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.keyBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentKeyTextColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = colors.keyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = colors.accentKeyTextColor,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.actionKeyTextColor,
                checkedTrackColor = colors.actionKeyBackground,
                uncheckedThumbColor = colors.accentKeyTextColor,
                uncheckedTrackColor = colors.accentKeyBackground
            ),
            modifier = Modifier.size(width = 44.dp, height = 24.dp)
        )
    }
}
