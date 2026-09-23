package io.github.xxparthparekhxx.composekeyboard.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.xxparthparekhxx.composekeyboard.R
import java.util.Locale

val PresetColorSwatches = listOf(
    Color(0xFF181824), Color(0xFF242436), Color(0xFF000000), Color(0xFF1E1E2E),
    Color(0xFF2E3440), Color(0xFF0F172A), Color(0xFF3B82F6), Color(0xFF6366F1),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E), Color(0xFFEF4444),
    Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF06B6D4),
    Color(0xFFFFFFFF), Color(0xFFECEFF4), Color(0xFF94A3B8), Color(0xFF475569)
)

private const val SWATCHES_PER_ROW = 5

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

private fun Color.toHsv(): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(toArgb(), it) }

private fun Color.toHex(): String = String.format(Locale.ROOT, "%06X", 0xFFFFFF and toArgb())

/** Parses 6 hex digits (no `#`) into an opaque color, or null. */
private fun parseHex(text: String): Color? {
    if (text.length != 6) return null
    val rgb = text.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember { initialColor.toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val currentColor = hsvColor(hue, saturation, value)

    // The hex field keeps its own text so a half-typed value isn't overwritten;
    // slider and swatch changes push their result into it.
    var hexText by remember { mutableStateOf(initialColor.toHex()) }
    fun applyColor(color: Color) {
        val hsv = color.toHsv()
        // Greys have no hue; keep the old one so the hue slider doesn't jump.
        if (hsv[1] > 0f) hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }
    fun syncHex() {
        hexText = hsvColor(hue, saturation, value).toHex()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    ColorComparison(before = initialColor, after = currentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val cleaned = input.removePrefix("#").filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                            hexText = cleaned
                            parseHex(cleaned)?.let(::applyColor)
                        },
                        label = { Text(stringResource(R.string.color_hex)) },
                        prefix = { Text("#") },
                        isError = parseHex(hexText) == null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    GradientSlider(
                        label = stringResource(R.string.color_hue),
                        value = hue,
                        valueRange = 0f..360f,
                        brush = Brush.horizontalGradient(
                            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hsvColor(it, 1f, 1f) }
                        ),
                        thumbColor = hsvColor(hue, 1f, 1f),
                        onValueChange = { hue = it; syncHex() }
                    )
                    GradientSlider(
                        label = stringResource(R.string.color_saturation),
                        value = saturation,
                        valueRange = 0f..1f,
                        brush = Brush.horizontalGradient(
                            listOf(hsvColor(hue, 0f, value), hsvColor(hue, 1f, value))
                        ),
                        thumbColor = currentColor,
                        onValueChange = { saturation = it; syncHex() }
                    )
                    GradientSlider(
                        label = stringResource(R.string.color_brightness),
                        value = value,
                        valueRange = 0f..1f,
                        brush = Brush.horizontalGradient(
                            listOf(Color.Black, hsvColor(hue, saturation, 1f))
                        ),
                        thumbColor = currentColor,
                        onValueChange = { value = it; syncHex() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.color_presets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SwatchGrid(
                        selected = currentColor,
                        onSelect = { swatch ->
                            applyColor(swatch)
                            hexText = swatch.toHex()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(onClick = { onColorSelected(currentColor) }) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
        }
    }
}

/** Old color on the left, new on the right, so the change is obvious before applying. */
@Composable
private fun ColorComparison(before: Color, after: Color) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, outline, RoundedCornerShape(16.dp))
    ) {
        ComparisonHalf(color = before, label = stringResource(R.string.color_current))
        ComparisonHalf(color = after, label = stringResource(R.string.color_new))
    }
}

@Composable
private fun RowScope.ComparisonHalf(color: Color, label: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (color.luminance() > 0.5f) Color.Black else Color.White
        )
    }
}

/**
 * A slider whose track is the gradient it controls, with the thumb filled in
 * the resulting color. Built on Material's Slider so drag, tap and
 * accessibility actions all behave as usual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    thumbColor: Color,
    onValueChange: (Float) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.semantics { contentDescription = label },
        thumb = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
        }
    )
}

@Composable
private fun SwatchGrid(
    selected: Color,
    onSelect: (Color) -> Unit
) {
    val selectedArgb = selected.toArgb()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PresetColorSwatches.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { swatch ->
                    val isSelected = swatch.toArgb() == selectedArgb
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                            .clickable { onSelect(swatch) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.theme_selected),
                                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White
                            )
                        }
                    }
                }
                // Keep a short last row on the same grid.
                repeat(SWATCHES_PER_ROW - row.size) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
