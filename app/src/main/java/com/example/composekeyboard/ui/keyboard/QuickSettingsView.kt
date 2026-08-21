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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
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

@Composable
fun QuickSettingsView(
    settings: KeyboardSettings,
    hapticEnabled: Boolean,
    onHapticToggled: (Boolean) -> Unit,
    onSoundToggled: (Boolean) -> Unit,
    onNumberRowToggled: (Boolean) -> Unit,
    onAutoCapsToggled: (Boolean) -> Unit,
    onSwipeTypingToggled: (Boolean) -> Unit,
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
