package com.example.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.example.composekeyboard.data.KeyModel
import com.example.composekeyboard.data.KeyType
import com.example.composekeyboard.data.KeyboardMode
import com.example.composekeyboard.theme.LocalKeyboardColors
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
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }

    // The gesture detector below is keyed on `key.type`, which never changes for
    // a given key, so its node is never restarted and it keeps invoking whatever
    // it captured on the composition that created it. KeyboardScreen rebuilds
    // `suggestions`, `typedPrefix` and `mode` on every input session
    // (`remember(inputSession)`), so a captured handler would go on writing to
    // the previous session's orphaned MutableStates and taps would stop having
    // any visible effect. Routing through rememberUpdatedState keeps the
    // detector pointed at the current handlers, the same way
    // [swipeTypingGestures] does for its own long-lived pointerInput.
    val currentOnKeyPress by rememberUpdatedState(onKeyPress)
    val currentOnKeyLongPress by rememberUpdatedState(onKeyLongPress)
    val currentHapticEnabled by rememberUpdatedState(hapticEnabled)

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
            if (mode == KeyboardMode.CAPS_LOCKED) "Caps lock on" else "Shift"
        is KeyType.Backspace -> "Backspace"
        is KeyType.Space -> "Space"
        is KeyType.Enter -> "Action"
        is KeyType.SymbolToggle -> "Symbols"
        is KeyType.SymbolMoreToggle -> "More symbols"
        is KeyType.AlphabetToggle -> "Letters"
        is KeyType.NumpadToggle -> "Number pad"
        is KeyType.EmojiToggle -> "Emoji"
        is KeyType.LanguageSwitch -> "Switch language"
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
        // Floating Magnifier Bubble above the key (Gboard style) - 100% aligned with key
        if (showKeyPopups && isPressed && key.type is KeyType.Character) {
            val popupText = if (isLongPressed && key.type.popup.isNotEmpty()) {
                key.type.popup.first()
            } else {
                when (mode) {
                    KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> key.type.primary.uppercase()
                    else -> key.type.primary
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .offset(y = (-48).dp)
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
                    text = popupText,
                    color = if (isLongPressed && key.type.popup.isNotEmpty()) colors.actionKeyBackground else colors.keyTextColor,
                    fontSize = (26 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
                if (key.type.popup.isNotEmpty() && !isLongPressed) {
                    Text(
                        text = key.type.popup.first(),
                        color = colors.actionKeyBackground,
                        fontSize = (10 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 3.dp)
                    )
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
                                    currentOnKeyLongPress(key.type)
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
                    contentDescription = "Shift",
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.Backspace -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
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
                    contentDescription = "Action",
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.Space -> {
                Text(
                    text = "Space",
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
                    text = "ABC",
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
                    contentDescription = "Emoji",
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            is KeyType.LanguageSwitch -> {
                Text(
                    text = "EN",
                    color = fg,
                    fontSize = (14.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
}
