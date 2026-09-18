package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardHistoryManager
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardItem
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClipboardView(
    clipboardManager: ClipboardHistoryManager,
    hapticEnabled: Boolean,
    onClipSelected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val history by clipboardManager.history.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Prioritize pinned clips first, then newest
    val sortedHistory = remember(history) {
        history.sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestamp })
    }

    val displayedItems = remember(sortedHistory, searchQuery) {
        if (searchQuery.isBlank()) sortedHistory
        else sortedHistory.filter { it.text.contains(searchQuery.trim(), ignoreCase = true) }
    }

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
        // Top Header / Search bar
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(colors.headerBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.desc_search),
                    tint = colors.actionKeyBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.clipboard_search_hint),
                            color = colors.keyTextColor.copy(alpha = 0.45f),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = colors.keyTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(colors.actionKeyBackground),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { searchQuery = "" }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.desc_clear_search),
                            tint = colors.accentKeyTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentKeyBackground)
                        .clickable {
                            triggerHaptic()
                            isSearching = false
                            searchQuery = ""
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = colors.accentKeyTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
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
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = colors.headerIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.clipboard_title),
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (history.isNotEmpty()) {
                        // Search icon button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentKeyBackground)
                                .clickable {
                                    triggerHaptic()
                                    isSearching = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.desc_search_clipboard),
                                tint = colors.accentKeyTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        // Clear unpinned
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentKeyBackground)
                                .clickable {
                                    triggerHaptic()
                                    clipboardManager.clearAllUnpinned()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_clear),
                                color = colors.accentKeyTextColor,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                            text = "ABC",
                            color = colors.actionKeyTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Search else Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = colors.accentKeyTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSearching) stringResource(R.string.clip_empty_search_title) else stringResource(R.string.clip_empty_title_ime),
                        color = colors.accentKeyTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isSearching) stringResource(R.string.clip_empty_search_body) else stringResource(R.string.clip_empty_body_ime),
                        color = colors.accentKeyTextColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedItems, key = { it.id }) { item ->
                    ClipboardItemCard(
                        item = item,
                        hapticEnabled = hapticEnabled,
                        onPaste = {
                            triggerHaptic()
                            onClipSelected(item.text)
                        },
                        onTogglePin = {
                            triggerHaptic()
                            clipboardManager.togglePin(item.id)
                        },
                        onDelete = {
                            triggerHaptic()
                            clipboardManager.deleteClip(item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardItemCard(
    item: ClipboardItem,
    hapticEnabled: Boolean,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val formattedTime = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val pasteDesc = stringResource(R.string.desc_paste_clip, item.text.take(40))
    val pinDesc = if (item.isPinned) stringResource(R.string.desc_unpin_clip)
        else stringResource(R.string.desc_pin_clip)
    val deleteDesc = stringResource(R.string.desc_delete_clip)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (item.isPinned) 1.5.dp else 1.dp,
                color = if (item.isPinned) colors.actionKeyBackground else colors.accentKeyBackground.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(colors.keyBackground)
            .semantics {
                role = Role.Button
                contentDescription = pasteDesc
            }
            .clickable { onPaste() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                color = colors.keyTextColor,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Text(
                        text = stringResource(R.string.clip_pinned_prefix),
                        color = colors.actionKeyBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = formattedTime,
                    color = colors.accentKeyTextColor.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Pin button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = pinDesc
                }
                .clickable { onTogglePin() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                tint = if (item.isPinned) colors.actionKeyBackground else colors.accentKeyTextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }

        // Delete button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = deleteDesc
                }
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = colors.accentKeyTextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
