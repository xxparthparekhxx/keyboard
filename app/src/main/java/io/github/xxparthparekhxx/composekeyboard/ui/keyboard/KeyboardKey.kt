package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Language
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.KeyModel
import io.github.xxparthparekhxx.composekeyboard.data.KeyType
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardMode
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KeyboardKey(
    key: KeyModel,
    mode: KeyboardMode,
    imeAction: Int,
    hapticEnabled: Boolean,
    fontScale: Float = 1.0f,
    showKeyPopups: Boolean = true,
    showSecondaryHints: Boolean = true,
    onKeyPress: (KeyType) -> Unit,
    onKeyLongPress: (KeyType) -> Unit = {},
    onPopupSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }
    var pickerVisible by remember { mutableStateOf(false) }
    val currentOnKeyPress by rememberUpdatedState(onKeyPress)
    val currentOnKeyLongPress by rememberUpdatedState(onKeyLongPress)
    val currentOnPopupSelected by rememberUpdatedState(onPopupSelected)
    val currentHapticEnabled by rememberUpdatedState(hapticEnabled)
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 50),
        label = "key_scale"
    )

    fun triggerHaptic() {
        if (currentHapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val (bg, fg) = when (key.type) {
        is KeyType.Enter -> colors.actionKeyBackground to colors.actionKeyTextColor
        is KeyType.Shift -> {
            val isShiftActive = mode == KeyboardMode.UPPERCASE || mode == KeyboardMode.CAPS_LOCKED
            if (isShiftActive) colors.actionKeyBackground to colors.actionKeyTextColor
            else colors.accentKeyBackground to colors.accentKeyTextColor
        }
        is KeyType.Backspace,
        is KeyType.SymbolToggle,
        is KeyType.SymbolMoreToggle,
        is KeyType.AlphabetToggle,
        is KeyType.NumpadToggle,
        is KeyType.EmojiToggle,
        is KeyType.LanguageSwitch -> colors.accentKeyBackground to colors.accentKeyTextColor
        else -> if (key.isAccent) {
            colors.accentKeyBackground to colors.accentKeyTextColor
        } else {
            colors.keyBackground to colors.keyTextColor
        }
    }

    val pressedBg = if (key.type is KeyType.Enter) {
        bg.copy(alpha = 0.8f)
    } else {
        fg.copy(alpha = 0.18f)
    }

    // TalkBack announcement: what the key will type or do in the current mode.
    val keyDescription = when (val type = key.type) {
        is KeyType.Character -> when (mode) {
            KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> type.primary.uppercase()
            else -> type.primary
        }
        is KeyType.Shift ->
            if (mode == KeyboardMode.CAPS_LOCKED) stringResource(R.string.desc_caps_lock)
            else stringResource(R.string.desc_shift)
        is KeyType.Backspace -> stringResource(R.string.desc_backspace)
        is KeyType.Space -> stringResource(R.string.key_space)
        is KeyType.Enter -> stringResource(R.string.desc_enter_key)
        is KeyType.SymbolToggle -> stringResource(R.string.desc_symbols)
        is KeyType.SymbolMoreToggle -> stringResource(R.string.desc_more_symbols)
        is KeyType.AlphabetToggle -> stringResource(R.string.desc_letters)
        is KeyType.NumpadToggle -> stringResource(R.string.desc_number_pad)
        is KeyType.EmojiToggle -> stringResource(R.string.desc_emoji)
        is KeyType.LanguageSwitch -> stringResource(R.string.desc_language_switch)
    }

    val iconSize = (24 * fontScale.coerceIn(0.85f, 1.25f)).dp

    Box(
        modifier = modifier
            .fillMaxHeight()
            .zIndex(if (isPressed) 99f else 0f)
            .semantics {
                contentDescription = keyDescription
                role = Role.Button
            }
            .padding(
                horizontal = if (mode == KeyboardMode.NUMPAD) 6.dp else 2.dp,
                vertical = if (mode == KeyboardMode.NUMPAD) 5.dp else 3.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val popupChars = (key.type as? KeyType.Character)?.popup.orEmpty()
        val previewText = if (key.type is KeyType.Character) {
            if (isLongPressed && popupChars.isNotEmpty()) popupChars.first()
            else when (mode) {
                KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> key.type.primary.uppercase()
                else -> key.type.primary
            }
        } else ""

        if (showKeyPopups && isPressed && key.type is KeyType.Character && !pickerVisible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(density) { -52.dp.toPx().toInt() }),
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(10.dp),
                            spotColor = colors.keyShadow,
                            ambientColor = colors.keyShadow
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.keyBackground)
                        .border(
                            width = 1.5.dp,
                            color = if (isLongPressed) colors.actionKeyBackground else colors.accentKeyBackground.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = previewText,
                        color = if (isLongPressed && popupChars.isNotEmpty()) colors.actionKeyBackground else colors.keyTextColor,
                        fontSize = (26 * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (pickerVisible && popupChars.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(density) { -56.dp.toPx().toInt() }),
                onDismissRequest = { pickerVisible = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = colors.keyShadow, ambientColor = colors.keyShadow)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.keyBackground)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    popupChars.forEach { alt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentKeyBackground)
                                .clickable {
                                    triggerHaptic()
                                    currentOnPopupSelected(alt)
                                    pickerVisible = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = alt,
                                color = colors.keyTextColor,
                                fontSize = (20 * fontScale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        val keyCornerRadius = if (mode == KeyboardMode.NUMPAD) 14.dp else 8.dp

        // Main key surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .shadow(
                    elevation = if (isPressed) 1.dp else 2.dp,
                    shape = RoundedCornerShape(keyCornerRadius),
                    spotColor = colors.keyShadow,
                    ambientColor = colors.keyShadow
                )
                .clip(RoundedCornerShape(keyCornerRadius))
                .background(if (isPressed) pressedBg else bg)
                // Keyed only on key.type: `mode` affects rendering, not gestures.
                // Restarting the detector when a tap changes the mode (e.g. Shift
                // into CAPS_LOCK) would cancel onPress mid-gesture and leave the
                // key stuck in its pressed visual state forever.
                .pointerInput(key.type) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            isLongPressed = false
                            triggerHaptic()
                            var repeatJob: Job? = null
                            if (key.type is KeyType.Backspace) {
                                repeatJob = scope.launch {
                                    delay(350L)
                                    while (isPressed) {
                                        triggerHaptic()
                                        currentOnKeyPress(key.type)
                                        delay(50L)
                                    }
                                }
                            }
                            val longPressJob = scope.launch {
                                delay(260L)
                                if (isPressed && key.type !is KeyType.Backspace) {
                                    isLongPressed = true
                                    triggerHaptic()
                                    val type = key.type
                                    if (type is KeyType.Character && type.popup.size > 1) {
                                        pickerVisible = true
                                    } else if (type is KeyType.Character && type.popup.isNotEmpty()) {
                                        currentOnPopupSelected(type.popup.first())
                                    } else {
                                        currentOnKeyLongPress(type)
                                    }
                                }
                            }
                            val released = tryAwaitRelease()
                            longPressJob.cancel()
                            repeatJob?.cancel()
                            if (released && !isLongPressed) {
                                currentOnKeyPress(key.type)
                            }
                            isPressed = false
                            isLongPressed = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            when (val type = key.type) {
            is KeyType.Character -> {
                val displayText = when (mode) {
                    KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> type.primary.uppercase()
                    else -> type.primary
                }
                val charFontSize = if (mode == KeyboardMode.NUMPAD) {
                    if (type.primary.length == 1 && type.primary[0].isDigit()) (30 * fontScale).sp
                    else (22 * fontScale).sp
                } else {
                    (23 * fontScale).sp
                }
                val charFontWeight = if (mode == KeyboardMode.NUMPAD) {
                    if (type.primary.length == 1 && type.primary[0].isDigit()) FontWeight.Medium
                    else FontWeight.SemiBold
                } else {
                    FontWeight.SemiBold
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayText,
                        color = fg,
                        fontSize = charFontSize,
                        fontWeight = charFontWeight
                    )
                    if (type.popup.isNotEmpty() && showSecondaryHints) {
                        Text(
                            text = type.popup.first(),
                            color = fg.copy(alpha = 0.45f),
                            fontSize = (9.5 * fontScale).sp,
                            lineHeight = (9.5 * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.5.dp, end = 3.5.dp)
                        )
                    }
                }
            }
            is KeyType.Shift -> {
                val icon = when (mode) {
                    KeyboardMode.CAPS_LOCKED -> Icons.Default.KeyboardCapslock
                    else -> Icons.Default.KeyboardArrowUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.desc_shift),
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.Backspace -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.desc_backspace),
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.Enter -> {
                val icon = when (imeAction) {
                    EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
                    EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send
                    EditorInfo.IME_ACTION_DONE -> Icons.Default.Check
                    EditorInfo.IME_ACTION_GO -> Icons.AutoMirrored.Filled.ArrowForward
                    EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
                    EditorInfo.IME_ACTION_PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
                    else -> Icons.AutoMirrored.Filled.ArrowForward
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.desc_enter_key),
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.Space -> {
                Text(
                    text = stringResource(R.string.key_space),
                    color = colors.spaceBarText,
                    fontSize = (14.5 * fontScale).sp,
                    fontWeight = FontWeight.Medium
                )
            }
            is KeyType.SymbolToggle -> {
                Text(
                    text = if (mode == KeyboardMode.NUMPAD) "!?#" else "?123",
                    color = fg,
                    fontSize = (15.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            is KeyType.SymbolMoreToggle -> {
                Text(
                    text = "=\\<",
                    color = fg,
                    fontSize = (15.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            is KeyType.AlphabetToggle -> {
                Text(
                    text = stringResource(R.string.key_abc),
                    color = fg,
                    fontSize = (15.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            is KeyType.NumpadToggle -> {
                Text(
                    text = "1234",
                    color = fg,
                    fontSize = (15.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            is KeyType.EmojiToggle -> {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = stringResource(R.string.desc_emoji),
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.LanguageSwitch -> {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = stringResource(R.string.desc_language_switch),
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
}
