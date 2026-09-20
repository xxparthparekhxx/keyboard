package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCatalog
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCategory
import io.github.xxparthparekhxx.composekeyboard.data.RecentEmojiManager
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Emoji picker split into per-category pages.
 *
 * Previously this was one continuous [LazyVerticalGrid] holding every category
 * (~2k emojis + section headers). That meant one giant lazy scope, a
 * scroll-derived active tab recomposing on every scroll frame, and long-distance
 * `animateScrollToItem` jumps on every tab tap.
 *
 * Now only the selected category's grid is composed (~100-200 items), tab
 * selection is explicit state instead of scroll-derived, and switching tabs is
 * a cheap `scrollToItem(0)` on a small grid.
 */
@Composable
fun EmojiPicker(
    hapticEnabled: Boolean,
    emojiScale: Float = 1.0f,
    isSearching: Boolean = false,
    searchQuery: String = "",
    onSearchingChange: (Boolean) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    hideBottomBar: Boolean = false,
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
    val cachedCatalog = remember { EmojiCatalog.peek() }
    var catalog by remember { mutableStateOf(cachedCatalog ?: EmojiCatalog.empty) }
    LaunchedEffect(context) {
        if (cachedCatalog == null) {
            catalog = withContext(Dispatchers.IO) { EmojiCatalog.get(context) }
        }
    }
    // Live recents are safe now: only the Recent page recomposes on change,
    // other category grids are unaffected (previously any change shifted the
    // shared grid's section indices and jerked the scroll position).
    val liveRecents by recentManager.recentEmojis.collectAsState()

    val recentLabel = stringResource(R.string.desc_recent)
    val allCategories = remember(liveRecents, catalog, recentLabel) {
        listOf(
            EmojiCategory(
                name = recentLabel,
                icon = "🕒",
                emojis = liveRecents
            )
        ) + catalog.categories
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (allCategories.size - 1).coerceAtLeast(0))
    val activeCategory = allCategories.getOrNull(safeIndex) ?: EmojiCategory(
        name = recentLabel,
        icon = "🕒",
        emojis = liveRecents
    )

    val gridState = rememberLazyGridState()
    val tabRowState = rememberLazyListState()

    // Tab switch: jump the small per-category grid back to the top and keep
    // the tab row showing the selected tab. No long-distance animation over
    // thousands of items anymore.
    LaunchedEffect(selectedIndex) {
        gridState.scrollToItem(0)
        if (allCategories.isNotEmpty()) {
            tabRowState.animateScrollToItem(selectedIndex)
        }
    }

    val searchResults = remember(searchQuery, catalog) {
        if (searchQuery.isBlank()) emptyList()
        else EmojiCatalog.search(catalog, searchQuery)
    }

    fun triggerHaptic() {
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val cellSize = (50 * emojiScale).dp
    val emojiFontSize = (32 * emojiScale).sp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        // --- Top Bar: Category Tabs or Search Input ---
        if (isSearching) {
            EmojiSearchTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchingChange = onSearchingChange,
                onSwitchToKeyboard = onSwitchToKeyboard,
                triggerHaptic = ::triggerHaptic
            )
        } else {
            EmojiCategoryTabs(
                categories = allCategories,
                selectedIndex = selectedIndex,
                tabRowState = tabRowState,
                onSelect = { index ->
                    triggerHaptic()
                    selectedIndex = index
                }
            )
        }

        // --- Main Content: single-category grid or search results ---
        if (isSearching && searchQuery.isBlank()) {
            EmojiPopularTags(
                onTagSelected = {
                    triggerHaptic()
                    onSearchQueryChange(it)
                }
            )
        } else if (isSearching) {
            EmojiSearchResultsGrid(
                results = searchResults,
                searchQuery = searchQuery,
                cellSize = cellSize,
                emojiFontSize = emojiFontSize,
                onEmojiClick = { emoji ->
                    triggerHaptic()
                    recentManager.recordEmoji(emoji)
                    onEmojiSelected(emoji)
                }
            )
        } else {
            // Only the active category is composed — keyed so switching tabs
            // drops the previous page's composition instead of diffing 2k rows.
            key(selectedIndex) {
                EmojiCategoryGrid(
                    category = activeCategory,
                    gridState = gridState,
                    cellSize = cellSize,
                    emojiFontSize = emojiFontSize,
                    onEmojiClick = { emoji ->
                        triggerHaptic()
                        recentManager.recordEmoji(emoji)
                        onEmojiSelected(emoji)
                    }
                )
            }
        }

        if (!hideBottomBar) {
            EmojiBottomBar(
                activeCategoryName = activeCategory.name,
                isSearching = isSearching,
                onSearchingChange = onSearchingChange,
                onSearchQueryChange = onSearchQueryChange,
                onEmojiSelected = onEmojiSelected,
                onDelete = onDelete,
                onSwitchToKeyboard = onSwitchToKeyboard,
                triggerHaptic = ::triggerHaptic
            )
        }
    }
}

@Composable
private fun EmojiCategoryTabs(
    categories: List<EmojiCategory>,
    selectedIndex: Int,
    tabRowState: LazyListState,
    onSelect: (Int) -> Unit
) {
    val colors = LocalKeyboardColors.current
    LazyRow(
        state = tabRowState,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.headerBackground)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(categories, key = { index, cat -> "$index-${cat.name}" }) { index, cat ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.actionKeyBackground else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                if (index == 0) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.desc_recent),
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

@Composable
private fun ColumnScope.EmojiCategoryGrid(
    category: EmojiCategory,
    gridState: LazyGridState,
    cellSize: Dp,
    emojiFontSize: TextUnit,
    onEmojiClick: (String) -> Unit
) {
    val keyPrefix = remember(category.name) { category.name }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = cellSize),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(
            items = category.emojis,
            key = { "$keyPrefix-$it" },
            contentType = { "emoji" }
        ) { emoji ->
            EmojiCell(
                emoji = emoji,
                cellSize = cellSize,
                fontSize = emojiFontSize,
                onClick = { onEmojiClick(emoji) }
            )
        }
    }
}

@Composable
private fun ColumnScope.EmojiSearchResultsGrid(
    results: List<String>,
    searchQuery: String,
    cellSize: Dp,
    emojiFontSize: TextUnit,
    onEmojiClick: (String) -> Unit
) {
    val colors = LocalKeyboardColors.current
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.emoji_no_results, searchQuery),
                color = colors.accentKeyTextColor,
                fontSize = 15.sp
            )
        }
    } else {
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
            items(
                items = results,
                key = { "search-$it" },
                contentType = { "emoji" }
            ) { emoji ->
                EmojiCell(
                    emoji = emoji,
                    cellSize = cellSize,
                    fontSize = emojiFontSize,
                    onClick = { onEmojiClick(emoji) }
                )
            }
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    cellSize: Dp,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = fontSize
        )
    }
}

@Composable
private fun EmojiSearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchingChange: (Boolean) -> Unit,
    onSwitchToKeyboard: () -> Unit,
    triggerHaptic: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(colors.headerBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accentKeyBackground)
                .clickable {
                    triggerHaptic()
                    onSwitchToKeyboard()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.key_abc),
                color = colors.accentKeyTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = stringResource(R.string.emoji_search_hint),
                    color = colors.keyTextColor.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = searchQuery,
                    color = colors.keyTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        if (searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSearchQueryChange("") }
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
                    onSearchingChange(false)
                    onSearchQueryChange("")
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
}

@Composable
private fun ColumnScope.EmojiPopularTags(
    onTagSelected: (String) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.emoji_popular),
            color = colors.accentKeyTextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val popular = listOf("smile", "love", "fire", "cat", "dog", "laugh", "cry", "party", "food", "star", "heart", "cool")
            items(popular, key = { "tag-$it" }) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentKeyBackground)
                        .clickable { onTagSelected(tag) }
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
}

@Composable
private fun EmojiBottomBar(
    activeCategoryName: String,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onDelete: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    triggerHaptic: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val scope = rememberCoroutineScope()
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
                text = stringResource(R.string.key_abc),
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
                    onSearchingChange(!isSearching)
                    if (isSearching) onSearchQueryChange("")
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.desc_search_emojis),
                tint = if (isSearching) colors.actionKeyTextColor else colors.accentKeyTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Current visible category indicator (if not searching)
        if (!isSearching) {
            Text(
                text = activeCategoryName,
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
                text = stringResource(R.string.key_space),
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
                contentDescription = stringResource(R.string.desc_delete),
                tint = colors.accentKeyTextColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
