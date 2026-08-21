package com.example.composekeyboard.ui.keyboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.theme.LocalKeyboardColors

/**
 * Strip above the keys showing what a gesture resolved to.
 *
 * It takes the place of the toolbar rather than adding a row of its own, so the
 * keys never shift under the user's finger mid-gesture. While the finger is
 * still down it shows the running best guess; once it lifts, the alternates
 * appear and any of them can be tapped to swap the committed word.
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    selectedIndex: Int,
    previewWord: String?,
    isSwiping: Boolean,
    hapticEnabled: Boolean,
    onSuggestionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.headerBackground)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSwiping) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = previewWord ?: "",
                    color = colors.actionKeyBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            return@Row
        }

        suggestions.forEachIndexed { index, word ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(colors.keyTextColor.copy(alpha = 0.15f))
                )
            }
            SuggestionCell(
                word = word,
                isSelected = index == selectedIndex,
                hapticEnabled = hapticEnabled,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = androidx.compose.ui.platform.LocalView.current

    Box(
        modifier = modifier
            .fillMaxHeight()
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
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
