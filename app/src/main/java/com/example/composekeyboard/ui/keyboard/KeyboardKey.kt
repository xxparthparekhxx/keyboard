package com.example.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onKeyPress: (KeyType) -> Unit,
    modifier: Modifier = Modifier,
    onKeyLongPress: (KeyType) -> Unit = {}
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 50),
        label = "keyScale"
    )

    fun triggerHaptic() {
        if (hapticEnabled) {
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
        is KeyType.EmojiToggle,
        is KeyType.LanguageSwitch -> colors.accentKeyBackground to colors.accentKeyTextColor
        else -> colors.keyBackground to colors.keyTextColor
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
        is KeyType.EmojiToggle -> "Emoji"
        is KeyType.LanguageSwitch -> "Switch language"
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = keyDescription
                role = Role.Button
            }
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 1.dp else 2.dp,
                shape = RoundedCornerShape(8.dp),
                spotColor = colors.keyShadow,
                ambientColor = colors.keyShadow
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) pressedBg else bg)
            // Keyed only on key.type: `mode` affects rendering, not gestures.
            // Restarting the detector when a tap changes the mode (e.g. Shift
            // into CAPS_LOCK) would cancel onPress mid-gesture and leave the
            // key stuck in its pressed visual state forever.
            .pointerInput(key.type) {
                val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        triggerHaptic()
                        var repeatJob: Job? = null
                        if (key.type is KeyType.Backspace) {
                            // Start repeating just after the long-press timeout so a
                            // held backspace can never fire both an auto-repeat and
                            // the detector's own tap/long-press handling for one press.
                            repeatJob = scope.launch {
                                delay(longPressTimeout + 50L)
                                while (isPressed) {
                                    triggerHaptic()
                                    onKeyPress(key.type)
                                    delay(50)
                                }
                            }
                        }
                        tryAwaitRelease()
                        repeatJob?.cancel()
                        isPressed = false
                    },
                    onTap = {
                        onKeyPress(key.type)
                    },
                    onLongPress = {
                        if (key.type !is KeyType.Backspace) {
                            triggerHaptic()
                            onKeyLongPress(key.type)
                        }
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
                Text(
                    text = displayText,
                    color = fg,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            is KeyType.Shift -> {
                val icon = when (mode) {
                    KeyboardMode.CAPS_LOCKED -> Icons.Default.KeyboardCapslock
                    else -> Icons.Default.KeyboardArrowUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Shift",
                    tint = fg
                )
            }
            is KeyType.Backspace -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = fg
                )
            }
            is KeyType.Enter -> {
                val icon = when (imeAction) {
                    EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
                    EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send
                    EditorInfo.IME_ACTION_GO -> Icons.AutoMirrored.Filled.ArrowForward
                    EditorInfo.IME_ACTION_DONE -> Icons.Default.Check
                    EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
                    else -> Icons.AutoMirrored.Filled.ArrowBack
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Action",
                    tint = fg
                )
            }
            is KeyType.Space -> {
                Text(
                    text = "Space",
                    color = colors.spaceBarText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            is KeyType.SymbolToggle -> {
                Text(
                    text = "?123",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            is KeyType.SymbolMoreToggle -> {
                Text(
                    text = "=\\<",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            is KeyType.AlphabetToggle -> {
                Text(
                    text = "ABC",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            is KeyType.EmojiToggle -> {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = "Emoji",
                    tint = fg
                )
            }
            is KeyType.LanguageSwitch -> {
                Text(
                    text = "EN",
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
