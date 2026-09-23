package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Build
import android.util.Size
import android.view.inputmethod.InlineSuggestion
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors

/**
 * Strip above the keys showing what a gesture resolved to.
 *
 * It takes the place of the toolbar rather than adding a row of its own, so the
 * keys never shift under the user's finger mid-gesture. While the finger is
 * still down it shows the running best guess; once it lifts, the alternates
 * appear and any of them can be tapped to swap the committed word.
 *
 * The live preview arrives as a lambda rather than a value: the state behind it
 * ticks roughly every 55 ms during a gesture, and reading it here — instead of
 * in the parent — confines that recomposition to this strip instead of the
 * whole keyboard.
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    selectedIndex: Int,
    previewWord: () -> String?,
    isSwiping: Boolean,
    hapticEnabled: Boolean,
    fontScale: Float = 1.0f,
    otpCode: String? = null,
    inlineSuggestions: List<InlineSuggestion> = emptyList(),
    onOtpSelected: (String) -> Unit = {},
    onSuggestionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = androidx.compose.ui.platform.LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.headerBackground)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSwiping) {
            val preview = previewWord()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preview ?: "",
                    color = colors.actionKeyBackground,
                    fontSize = (19.5 * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            return@Row
        }

        val showOtp = !otpCode.isNullOrEmpty()
        val showInline = inlineSuggestions.isNotEmpty() && Build.VERSION.SDK_INT >= 30
        if (showOtp || showInline) {
            if (showInline) {
                inlineSuggestions.forEach { suggestion ->
                    InlineSuggestionChip(
                        suggestion = suggestion,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (showOtp) {
                val code = otpCode
                val otpDesc = stringResource(R.string.desc_otp, code)
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.actionKeyBackground)
                        .semantics {
                            role = Role.Button
                            contentDescription = otpDesc
                        }
                        .clickable {
                            if (hapticEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            onOtpSelected(code)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.otp_paste, code),
                        color = colors.actionKeyTextColor,
                        fontSize = (16 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            if (suggestions.isEmpty()) return@Row
        }

        suggestions.forEachIndexed { index, word ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(colors.keyTextColor.copy(alpha = 0.15f))
                )
            }
            SuggestionCell(
                word = word,
                isSelected = index == selectedIndex,
                hapticEnabled = hapticEnabled,
                fontScale = fontScale,
                onClick = { onSuggestionSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SuggestionCell(
    word: String,
    isSelected: Boolean,
    hapticEnabled: Boolean,
    fontScale: Float = 1.0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = androidx.compose.ui.platform.LocalView.current
    val suggestionDesc = stringResource(R.string.desc_suggestion, word)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Button
                contentDescription = suggestionDesc
            }
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                if (hapticEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onClick()
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = word,
            color = if (isSelected) colors.actionKeyBackground else colors.keyTextColor,
            fontSize = (18 * fontScale).sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlineSuggestionChip(
    suggestion: InlineSuggestion,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT < 30) return
    AndroidView(
        modifier = modifier
            .height(40.dp)
            .width(160.dp),
        factory = { context ->
            FrameLayout(context)
        },
        update = { host ->
            if (host.tag === suggestion) return@AndroidView
            host.tag = suggestion
            host.removeAllViews()
            val height = host.height.coerceAtLeast(40)
            suggestion.inflate(
                host.context,
                Size(host.width.coerceAtLeast(160), height),
                ContextCompat.getMainExecutor(host.context)
            ) { content ->
                host.removeAllViews()
                if (content != null) {
                    host.addView(
                        content,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }
    )
}
