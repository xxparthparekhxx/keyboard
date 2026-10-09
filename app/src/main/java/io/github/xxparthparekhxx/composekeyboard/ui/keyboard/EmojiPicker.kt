package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiFlags
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.EmojiTransportation
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.EmojiTransportation
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCatalog
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCategory
import io.github.xxparthparekhxx.composekeyboard.data.RecentEmojiManager
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * While searching, the picker shrinks to a search field plus a horizontally
 * scrolling result strip above the search keyboard; the first result is
 * highlighted because Enter inserts it (see `KeyboardScreen.dispatchKey`).
 */
@Composable
fun EmojiPicker(
    hapticEnabled: Boolean,
    onEmojiSelected: (String) -> Unit,
    onDelete: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    emojiScale: Float = 1.0f,
    isSearching: Boolean = false,
    searchQuery: String = "",
    onSearchingChange: (Boolean) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    hideBottomBar: Boolean = false
) {
    val context = LocalContext.current
    val colors = LocalKeyboardColors.current
    val view = LocalView.current

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

    // Nothing to show under Recent yet: open on the first real category.
    var selectedIndex by remember {
        mutableIntStateOf(if (recentManager.recentEmojis.value.isEmpty()) 1 else 0)
    }
    val safeIndex = selectedIndex.coerceIn(0, (allCategories.size - 1).coerceAtLeast(0))
    val activeCategory = allCategories[safeIndex]

    val gridState = rememberLazyGridState()

    // Tab switch: jump the small per-category grid back to the top. No
    // long-distance animation over thousands of items anymore.
    LaunchedEffect(selectedIndex) {
        gridState.scrollToItem(0)
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
    val emojiFontSize = (30 * emojiScale).sp
    val pickEmoji: (String) -> Unit = { emoji ->
        triggerHaptic()
        recentManager.recordEmoji(emoji)
        onEmojiSelected(emoji)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        if (isSearching) {
            EmojiSearchTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onClose = {
                    triggerHaptic()
                    onSearchingChange(false)
                    onSearchQueryChange("")
                }
            )
            if (searchQuery.isBlank()) {
                EmojiSearchSuggestions(
                    recents = liveRecents,
                    cellSize = cellSize,
                    emojiFontSize = emojiFontSize,
                    onTagSelected = {
                        triggerHaptic()
                        onSearchQueryChange(it)
                    },
                    onEmojiClick = pickEmoji
                )
            } else {
                EmojiSearchResults(
                    results = searchResults,
                    searchQuery = searchQuery,
                    cellSize = cellSize,
                    emojiFontSize = emojiFontSize,
                    onEmojiClick = pickEmoji
                )
            }
        } else {
            EmojiCategoryTabs(
                categories = allCategories,
                selectedIndex = safeIndex,
                onSelect = { index ->
                    triggerHaptic()
                    selectedIndex = index
                }
            )
            // Only the active category is composed — keyed so switching tabs
            // drops the previous page's composition instead of diffing 2k rows.
            key(safeIndex) {
                EmojiCategoryGrid(
                    category = activeCategory,
                    isRecent = safeIndex == 0,
                    gridState = gridState,
                    cellSize = cellSize,
                    emojiFontSize = emojiFontSize,
                    onEmojiClick = pickEmoji
                )
            }
        }

        if (!hideBottomBar) {
            EmojiBottomBar(
                onSearch = {
                    triggerHaptic()
                    onSearchingChange(true)
                },
                onEmojiSelected = onEmojiSelected,
                onDelete = onDelete,
                onSwitchToKeyboard = onSwitchToKeyboard,
                triggerHaptic = ::triggerHaptic
            )
        }
    }
}

/** Monochrome Material icons for the Unicode emoji groups, as (outlined, filled). */
private val CATEGORY_ICONS: Map<String, Pair<ImageVector, ImageVector>> = mapOf(
    "Smileys & Emotion" to (Icons.Outlined.EmojiEmotions to Icons.Filled.EmojiEmotions),
    "People & Body" to (Icons.Outlined.EmojiPeople to Icons.Filled.EmojiPeople),
    "Animals & Nature" to (Icons.Outlined.EmojiNature to Icons.Filled.EmojiNature),
    "Food & Drink" to (Icons.Outlined.EmojiFoodBeverage to Icons.Filled.EmojiFoodBeverage),
    "Travel & Places" to (Icons.Outlined.EmojiTransportation to Icons.Filled.EmojiTransportation),
    "Activities" to (Icons.Outlined.EmojiEvents to Icons.Filled.EmojiEvents),
    "Objects" to (Icons.Outlined.EmojiObjects to Icons.Filled.EmojiObjects),
    "Symbols" to (Icons.Outlined.EmojiSymbols to Icons.Filled.EmojiSymbols),
    "Flags" to (Icons.Outlined.EmojiFlags to Icons.Filled.EmojiFlags)
)

@Composable
private fun EmojiCategoryTabs(
    categories: List<EmojiCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalKeyboardColors.current
    // Ten tabs fit any phone width, so they share the row evenly instead of
    // scrolling — every category is always one tap away.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.headerBackground)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEachIndexed { index, cat ->
            val isSelected = index == selectedIndex
            val pillColor by animateColorAsState(
                if (isSelected) colors.actionKeyBackground.copy(alpha = 0.22f) else Color.Transparent,
                label = "tabPill"
            )
            val tint by animateColorAsState(
                if (isSelected) colors.actionKeyBackground else colors.accentKeyTextColor.copy(alpha = 0.6f),
                label = "tabTint"
            )
            val icons = if (index == 0) {
                Icons.Outlined.History to Icons.Filled.History
            } else {
                CATEGORY_ICONS[cat.name]
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .clip(CircleShape)
                    .background(pillColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                if (icons != null) {
                    Icon(
                        imageVector = if (isSelected) icons.second else icons.first,
                        contentDescription = cat.name,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    // Fallback catalog uses its own group names: show its emoji icon.
                    Text(
                        text = cat.icon,
                        fontSize = 17.sp,
                        modifier = Modifier.graphicsLayer { alpha = if (isSelected) 1f else 0.6f }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmojiCategoryGrid(
    category: EmojiCategory,
    isRecent: Boolean,
    gridState: LazyGridState,
    cellSize: Dp,
    emojiFontSize: TextUnit,
    onEmojiClick: (String) -> Unit
) {
    val colors = LocalKeyboardColors.current
    val keyPrefix = remember(category.name) { category.name }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = cellSize),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Text(
                text = category.name,
                color = colors.accentKeyTextColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
            )
        }
        if (isRecent && category.emojis.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.accentKeyTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.emoji_recent_empty),
                        color = colors.accentKeyTextColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        itemsIndexed(
            items = category.emojis,
            key = { _, emoji -> "$keyPrefix-$emoji" },
            contentType = { _, _ -> "emoji" }
        ) { _, emoji ->
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
private fun EmojiCell(
    emoji: String,
    cellSize: Dp,
    fontSize: TextUnit,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background = when {
        isPressed -> colors.keyTextColor.copy(alpha = 0.14f)
        highlighted -> colors.actionKeyBackground.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(cellSize)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = fontSize,
            modifier = Modifier.graphicsLayer {
                val s = if (isPressed) 1.15f else 1f
                scaleX = s
                scaleY = s
            }
        )
    }
}

@Composable
private fun EmojiSearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(colors.headerBackground)
            .padding(start = 4.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.desc_close_search),
                tint = colors.accentKeyTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.keyBackground)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.keyTextColor.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // The query is typed on our own keys, not a TextField, so draw the
            // caret ourselves and keep the tail of a long query in view.
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo(it) }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState, enabled = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = searchQuery,
                        color = colors.keyTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                SearchCaret()
                if (searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.emoji_search_hint),
                        color = colors.keyTextColor.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onSearchQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.desc_clear_search),
                        tint = colors.keyTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Blinking text caret; the blink runs in the draw phase only. */
@Composable
private fun SearchCaret() {
    val colors = LocalKeyboardColors.current
    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "caretAlpha"
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .width(2.dp)
            .height(18.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(colors.actionKeyBackground)
    )
}

/** Search terms offered before anything is typed, each with a preview emoji. */
private val POPULAR_SEARCHES = listOf(
    "laugh" to "😂", "love" to "❤️", "smile" to "😊", "fire" to "🔥",
    "party" to "🎉", "cry" to "😭", "cool" to "😎", "thanks" to "🙏",
    "star" to "⭐", "cat" to "🐱", "dog" to "🐶", "food" to "🍕"
)

@Composable
private fun ColumnScope.EmojiSearchSuggestions(
    recents: List<String>,
    cellSize: Dp,
    emojiFontSize: TextUnit,
    onTagSelected: (String) -> Unit,
    onEmojiClick: (String) -> Unit
) {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(POPULAR_SEARCHES, key = { "tag-${it.first}" }) { (tag, emoji) ->
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(colors.accentKeyBackground)
                        .clickable { onTagSelected(tag) }
                        .padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tag,
                        color = colors.accentKeyTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (recents.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "recent-icon", contentType = "icon") {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = stringResource(R.string.emoji_recently_used),
                        tint = colors.accentKeyTextColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(18.dp)
                    )
                }
                items(recents, key = { "recent-$it" }, contentType = { "emoji" }) { emoji ->
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
}

@Composable
private fun ColumnScope.EmojiSearchResults(
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
                .weight(1f)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.emoji_no_results, searchQuery.trim()),
                color = colors.accentKeyTextColor.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    // The strip is only ~2 cells tall above the search keyboard, so results
    // scroll sideways rather than one cramped row at a time.
    val gridState = rememberLazyGridState()
    LaunchedEffect(results) { gridState.scrollToItem(0) }
    LazyHorizontalGrid(
        state = gridState,
        rows = GridCells.Adaptive(minSize = cellSize),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        itemsIndexed(
            items = results,
            key = { _, emoji -> "search-$emoji" },
            contentType = { _, _ -> "emoji" }
        ) { index, emoji ->
            EmojiCell(
                emoji = emoji,
                cellSize = cellSize,
                fontSize = emojiFontSize,
                highlighted = index == 0,
                onClick = { onEmojiClick(emoji) }
            )
        }
    }
}

@Composable
private fun EmojiBottomBar(
    onSearch: () -> Unit,
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
            .height(50.dp)
            .background(colors.background)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarKey(
            weight = 1.3f,
            background = colors.accentKeyBackground,
            onClick = {
                triggerHaptic()
                onSwitchToKeyboard()
            }
        ) {
            Text(
                text = stringResource(R.string.key_abc),
                color = colors.accentKeyTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        BarKey(weight = 1.1f, background = colors.accentKeyBackground, onClick = onSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.desc_search_emojis),
                tint = colors.accentKeyTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        BarKey(
            weight = 4.2f,
            background = colors.keyBackground,
            onClick = {
                triggerHaptic()
                onEmojiSelected(" ")
            }
        ) {
            Text(
                text = stringResource(R.string.key_space),
                color = colors.spaceBarText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Backspace with repeating delete on hold
        var isPressed by remember { mutableStateOf(false) }
        BarKey(
            weight = 1.3f,
            background = colors.accentKeyBackground,
            pressedOverride = isPressed,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        triggerHaptic()
                        // onTap would also fire after a long hold, deleting
                        // one extra character once auto-repeat has run.
                        var repeated = false
                        val repeatJob: Job = scope.launch {
                            delay(400)
                            while (isPressed) {
                                repeated = true
                                triggerHaptic()
                                onDelete()
                                delay(50)
                            }
                        }
                        val released = tryAwaitRelease()
                        repeatJob.cancel()
                        isPressed = false
                        if (released && !repeated) onDelete()
                    }
                )
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = stringResource(R.string.desc_delete),
                tint = colors.accentKeyTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A key-shaped button for the emoji bottom bar, styled like [KeyboardKey] so
 * the bar reads as a keyboard row. Pass a gesture [modifier] instead of
 * [onClick] for custom gestures, with [pressedOverride] driving the pressed look.
 */
@Composable
private fun RowScope.BarKey(
    weight: Float,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    pressedOverride: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = LocalKeyboardColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressed = isPressed || pressedOverride
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .shadow(
                elevation = if (pressed) 1.dp else 2.dp,
                shape = shape,
                spotColor = colors.keyShadow,
                ambientColor = colors.keyShadow
            )
            .clip(shape)
            .background(if (pressed) lerp(background, colors.keyTextColor, 0.15f) else background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
