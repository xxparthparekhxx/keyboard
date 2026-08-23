package com.example.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.data.EmojiCategory
import com.example.composekeyboard.data.EmojiData
import com.example.composekeyboard.data.EmojiSuggestions
import com.example.composekeyboard.data.RecentEmojiManager
import com.example.composekeyboard.theme.LocalKeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EmojiPicker(
    hapticEnabled: Boolean,
    emojiScale: Float = 1.0f,
    onEmojiSelected: (String) -> Unit,
    onDelete: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val recentManager = remember(context) { RecentEmojiManager.getInstance(context) }
    // Initialize session recents stably so tapping emojis to insert them doesn't jerk the active scroll grid
    val sessionRecents = remember { recentManager.recentEmojis.value }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allCategories = remember(sessionRecents) {
        listOf(
            EmojiCategory(
                name = "Recent",
                icon = "🕒",
                emojis = sessionRecents
            )
        ) + EmojiData.categories
    }

    val gridState = rememberLazyGridState()
    val tabRowState = rememberLazyListState()

    // Precalculate header item indices for smooth vertical scroll navigation
    val categoryStartIndices = remember(allCategories) {
        var currentIndex = 0
        allCategories.map { cat ->
            val idx = currentIndex
            currentIndex += 1 + cat.emojis.size
            idx
        }
    }

    // Synchronize active category tab based on current vertical scroll position
    val activeCategoryIndex by remember(categoryStartIndices) {
        derivedStateOf {
            val firstVisible = gridState.firstVisibleItemIndex
            val idx = categoryStartIndices.indexOfLast { it <= firstVisible }
            if (idx >= 0) idx else 0
        }
    }

    // Keep horizontal category tab row scrolled to active category
    LaunchedEffect(activeCategoryIndex, isSearching) {
        if (!isSearching && allCategories.isNotEmpty()) {
            tabRowState.animateScrollToItem(activeCategoryIndex)
        }
    }

    val activeCategory = allCategories.getOrElse(activeCategoryIndex) { allCategories.first() }

    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else EmojiSuggestions.searchAll(searchQuery)
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
        // --- Top Bar: Category Tabs or Search Input ---
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
                    contentDescription = "Search",
                    tint = colors.actionKeyBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search emojis (e.g. smile, fire, love)...",
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
                            contentDescription = "Clear",
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
                        text = "Cancel",
                        color = colors.accentKeyTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // Category Tabs with smooth active selection & jump on click
            LazyRow(
                state = tabRowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.headerBackground)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(allCategories) { index, cat ->
                    val isSelected = index == activeCategoryIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.actionKeyBackground else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerHaptic()
                                scope.launch {
                                    gridState.animateScrollToItem(categoryStartIndices[index])
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == 0) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Recent",
                                tint = if (isSelected) colors.actionKeyTextColor else colors.accentKeyTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = cat.icon,
                                fontSize = 19.sp
                            )
                        }
                    }
                }
            }
        }

        // --- Main Content: Continuous Vertical Scroll Grid or Search Results ---
        if (isSearching && searchQuery.isBlank()) {
            // Popular quick search tags
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Popular Searches",
                    color = colors.accentKeyTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val popular = listOf("smile", "love", "fire", "cat", "dog", "laugh", "cry", "party", "food", "star", "heart", "cool")
                    items(popular) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentKeyBackground)
                                .clickable {
                                    triggerHaptic()
                                    searchQuery = tag
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = colors.keyTextColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else if (isSearching) {
            // Search Results Grid
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No emojis found for \"$searchQuery\"",
                        color = colors.accentKeyTextColor,
                        fontSize = 15.sp
                    )
                }
            } else {
                val cellSize = (50 * emojiScale).dp
                val emojiFontSize = (32 * emojiScale).sp

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cellSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(searchResults) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerHaptic()
                                    recentManager.recordEmoji(emoji)
                                    onEmojiSelected(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = emojiFontSize
                            )
                        }
                    }
                }
            }
        } else {
            val cellSize = (50 * emojiScale).dp
            val emojiFontSize = (32 * emojiScale).sp

            // Continuous Vertical Scroll Grid across all categories with section headers
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = cellSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                allCategories.forEach { category ->
                    // Section Header (spans all columns)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (category.name == "Recent") {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = colors.accentKeyTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Recently Used",
                                    color = colors.accentKeyTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "${category.icon}  ${category.name}",
                                    color = colors.accentKeyTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Emojis under this category
                    items(category.emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerHaptic()
                                    recentManager.recordEmoji(emoji)
                                    onEmojiSelected(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emoji,
                                fontSize = emojiFontSize
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Navigation Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.headerBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Return to ABC keyboard
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentKeyBackground)
                    .clickable {
                        triggerHaptic()
                        onSwitchToKeyboard()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    color = colors.accentKeyTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Search Toggle Icon
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSearching) colors.actionKeyBackground else colors.accentKeyBackground)
                    .clickable {
                        triggerHaptic()
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Emojis",
                    tint = if (isSearching) colors.actionKeyTextColor else colors.accentKeyTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Current visible category indicator (if not searching)
            if (!isSearching) {
                Text(
                    text = activeCategory.name,
                    color = colors.accentKeyTextColor.copy(alpha = 0.85f),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Space key in emoji mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.keyBackground)
                    .clickable {
                        triggerHaptic()
                        onEmojiSelected(" ")
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Space",
                    color = colors.keyTextColor.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Backspace button with repeating delete on hold
            var isPressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPressed) colors.actionKeyBackground.copy(alpha = 0.5f) else colors.accentKeyBackground)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                triggerHaptic()
                                var repeatJob: Job? = null
                                repeatJob = scope.launch {
                                    delay(400)
                                    while (isPressed) {
                                        triggerHaptic()
                                        onDelete()
                                        delay(50)
                                    }
                                }
                                tryAwaitRelease()
                                repeatJob.cancel()
                                isPressed = false
                            },
                            onTap = {
                                onDelete()
                            }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = colors.accentKeyTextColor,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

