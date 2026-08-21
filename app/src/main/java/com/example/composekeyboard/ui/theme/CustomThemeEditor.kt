package com.example.composekeyboard.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.data.CustomThemeColors

enum class ColorSlot(val title: String, val description: String) {
    BACKGROUND("Keyboard Background", "Main surface backdrop behind all keys"),
    KEY_BG("Key Background", "Standard character key button color"),
    KEY_TEXT("Letter / Text Color", "Character and symbol font color"),
    ACCENT_BG("Accent Keys Background", "Shift, Backspace, ?123 button color"),
    ACTION_BG("Action Key (Enter / Send)", "Primary action button color")
}

@Composable
fun CustomThemeEditorCard(
    initialColors: CustomThemeColors,
    onSaveAndApply: (CustomThemeColors) -> Unit,
    modifier: Modifier = Modifier
) {
    var bg by remember { mutableStateOf(Color(initialColors.background)) }
    var keyBg by remember { mutableStateOf(Color(initialColors.keyBackground)) }
    var keyText by remember { mutableStateOf(Color(initialColors.keyTextColor)) }
    var accentBg by remember { mutableStateOf(Color(initialColors.accentKeyBackground)) }
    var actionBg by remember { mutableStateOf(Color(initialColors.actionKeyBackground)) }

    var activeColorSlot by remember { mutableStateOf<ColorSlot?>(null) }

    val currentCustomColors = CustomThemeColors(
        background = bg.toArgb().toLong(),
        keyBackground = keyBg.toArgb().toLong(),
        keyTextColor = keyText.toArgb().toLong(),
        accentKeyBackground = accentBg.toArgb().toLong(),
        accentKeyTextColor = keyText.toArgb().toLong(),
        actionKeyBackground = actionBg.toArgb().toLong(),
        actionKeyTextColor = Color.White.toArgb().toLong()
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Theme Creator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Reset button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            bg = Color(0xFF181824)
                            keyBg = Color(0xFF242436)
                            keyText = Color(0xFFFFFFFF)
                            accentBg = Color(0xFF32324A)
                            actionBg = Color(0xFF6366F1)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Realtime Interactive Mini-Keyboard Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1 preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach { char ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    color = keyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Row 2 preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⇧", color = keyText, fontSize = 11.sp)
                        }
                        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").forEach { char ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    color = keyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⌫", color = keyText, fontSize = 11.sp)
                        }
                    }

                    // Bottom Row preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "?123", color = keyText, fontSize = 9.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(4f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(keyBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Space", color = keyText.copy(alpha = 0.5f), fontSize = 9.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(actionBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "↵", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color Slots Configuration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSlotItem(
                    slot = ColorSlot.BACKGROUND,
                    color = bg,
                    onClick = { activeColorSlot = ColorSlot.BACKGROUND }
                )
                ColorSlotItem(
                    slot = ColorSlot.KEY_BG,
                    color = keyBg,
                    onClick = { activeColorSlot = ColorSlot.KEY_BG }
                )
                ColorSlotItem(
                    slot = ColorSlot.KEY_TEXT,
                    color = keyText,
                    onClick = { activeColorSlot = ColorSlot.KEY_TEXT }
                )
                ColorSlotItem(
                    slot = ColorSlot.ACCENT_BG,
                    color = accentBg,
                    onClick = { activeColorSlot = ColorSlot.ACCENT_BG }
                )
                ColorSlotItem(
                    slot = ColorSlot.ACTION_BG,
                    color = actionBg,
                    onClick = { activeColorSlot = ColorSlot.ACTION_BG }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save & Apply Button
            Button(
                onClick = { onSaveAndApply(currentCustomColors) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save & Apply Custom Theme", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Color Picker Dialog when active
    activeColorSlot?.let { slot ->
        val currentSlotColor = when (slot) {
            ColorSlot.BACKGROUND -> bg
            ColorSlot.KEY_BG -> keyBg
            ColorSlot.KEY_TEXT -> keyText
            ColorSlot.ACCENT_BG -> accentBg
            ColorSlot.ACTION_BG -> actionBg
        }

        ColorPickerDialog(
            title = "Choose ${slot.title}",
            initialColor = currentSlotColor,
            onColorSelected = { selected ->
                when (slot) {
                    ColorSlot.BACKGROUND -> bg = selected
                    ColorSlot.KEY_BG -> keyBg = selected
                    ColorSlot.KEY_TEXT -> keyText = selected
                    ColorSlot.ACCENT_BG -> accentBg = selected
                    ColorSlot.ACTION_BG -> actionBg = selected
                }
                activeColorSlot = null
            },
            onDismiss = { activeColorSlot = null }
        )
    }
}

@Composable
private fun ColorSlotItem(
    slot: ColorSlot,
    color: Color,
    onClick: () -> Unit
) {
    val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = slot.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hex,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}
