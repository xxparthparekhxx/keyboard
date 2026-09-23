package io.github.xxparthparekhxx.composekeyboard.ui.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardItem
import io.github.xxparthparekhxx.composekeyboard.data.CustomThemeColors
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CompanionDestination(
    val labelRes: Int,
    val titleRes: Int,
    val icon: ImageVector
) {
    HOME(R.string.nav_home, R.string.title_home, Icons.Default.Home),
    THEMES(R.string.nav_themes, R.string.title_themes, Icons.Default.Palette),
    CLIPBOARD(R.string.nav_clipboard, R.string.title_clipboard, Icons.Default.ContentPaste),
    SETTINGS(R.string.nav_settings, R.string.title_settings, Icons.Default.Settings)
}

/** Detail pages opened from a tab. They replace the tab content and hide the bottom bar. */
private enum class CompanionPage(
    val titleRes: Int,
    val parent: CompanionDestination
) {
    KEYBOARD_SIZE(R.string.set_size_title, CompanionDestination.SETTINGS),
    VOICE(R.string.set_voice, CompanionDestination.SETTINGS),
    CUSTOM_THEME(R.string.theme_custom, CompanionDestination.THEMES)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(
    settings: KeyboardSettings,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    clipboardItems: List<ClipboardItem>,
    onRefreshStatus: () -> Unit,
    onTogglePinClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onClearAllClips: () -> Unit,
    onCopyClip: (String) -> Unit,
    onSaveCustomTheme: (CustomThemeColors) -> Unit,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    openVoiceSetup: Boolean = false,
    onVoiceSetupConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(CompanionDestination.HOME.name) }
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val current = CompanionDestination.entries.firstOrNull { it.name == destination }
        ?: CompanionDestination.HOME
    val page = CompanionPage.entries.firstOrNull { it.name == pageName }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun openPage(target: CompanionPage) {
        destination = target.parent.name
        pageName = target.name
    }

    BackHandler(enabled = page != null) { pageName = null }

    LaunchedEffect(openVoiceSetup) {
        if (openVoiceSetup) {
            openPage(CompanionPage.VOICE)
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            onVoiceSetupConsumed()
        }
    }

    fun refreshSoon() {
        scope.launch {
            delay(500)
            onRefreshStatus()
        }
    }

    // One collapsing-title state per screen, so switching tabs never lands on
    // a half-collapsed bar over a list that is scrolled to the top.
    val appBarState = key(current, page) { rememberTopAppBarState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page == null && current == CompanionDestination.HOME) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = stringResource(page?.titleRes ?: current.titleRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { pageName = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            if (page == null) {
                NavigationBar {
                    CompanionDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = { destination = dest.name },
                            icon = {
                                if (dest == CompanionDestination.CLIPBOARD && clipboardItems.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    text = if (clipboardItems.size > 99) "99+" else clipboardItems.size.toString()
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                    }
                                } else {
                                    Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                                }
                            },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = current to page,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            transitionSpec = {
                val (fromTab, fromPage) = initialState
                val (toTab, toPage) = targetState
                when {
                    // Opening a detail page: slide it in over its tab.
                    fromTab == toTab && fromPage == null && toPage != null ->
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    // Going back to the tab.
                    fromTab == toTab && fromPage != null && toPage == null ->
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    else -> fadeIn() togetherWith fadeOut()
                }
            },
            label = "companion-screen"
        ) { (dest, openPage) ->
            when (openPage) {
                CompanionPage.KEYBOARD_SIZE -> KeyboardSizeScreen(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
                CompanionPage.VOICE -> VoiceSetupScreen()
                CompanionPage.CUSTOM_THEME -> CustomThemeScreen(
                    settings = settings,
                    onSaveCustomTheme = { colors ->
                        onSaveCustomTheme(colors)
                        pageName = null
                    }
                )
                null -> when (dest) {
                    CompanionDestination.HOME -> HomeScreen(
                        isImeEnabled = isImeEnabled,
                        isImeSelected = isImeSelected,
                        onEnableClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            refreshSoon()
                        },
                        onSelectClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                            scope.launch {
                                repeat(10) {
                                    delay(300)
                                    onRefreshStatus()
                                }
                            }
                        }
                    )
                    CompanionDestination.THEMES -> AppearanceScreen(
                        settings = settings,
                        onUpdateSettings = onUpdateSettings,
                        onOpenCustomTheme = { openPage(CompanionPage.CUSTOM_THEME) }
                    )
                    CompanionDestination.CLIPBOARD -> ClipboardScreen(
                        clipboardItems = clipboardItems,
                        onTogglePinClip = onTogglePinClip,
                        onDeleteClip = onDeleteClip,
                        onClearAllClips = onClearAllClips,
                        onCopyClip = { text ->
                            onCopyClip(text)
                            scope.launch {
                                snackbarHostState.showSnackbar(copiedMessage)
                            }
                        }
                    )
                    CompanionDestination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onUpdateSettings = onUpdateSettings,
                        onOpenSize = { openPage(CompanionPage.KEYBOARD_SIZE) },
                        onOpenVoice = { openPage(CompanionPage.VOICE) }
                    )
                }
            }
        }
    }
}
