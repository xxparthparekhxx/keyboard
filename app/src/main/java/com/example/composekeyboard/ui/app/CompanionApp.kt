package com.example.composekeyboard.ui.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.composekeyboard.data.ClipboardItem
import com.example.composekeyboard.data.CustomThemeColors
import com.example.composekeyboard.data.KeyboardPreferences
import com.example.composekeyboard.data.KeyboardSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CompanionDestination(
    val label: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("Home", "Compose Keyboard", Icons.Default.Home),
    THEMES("Themes", "Themes", Icons.Default.Palette),
    CLIPBOARD("Clipboard", "Clipboard", Icons.Default.ContentPaste),
    SETTINGS("Settings", "Settings", Icons.Default.Settings)
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(CompanionDestination.HOME.name) }
    val current = CompanionDestination.entries.firstOrNull { it.name == destination }
        ?: CompanionDestination.HOME
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(openVoiceSetup) {
        if (openVoiceSetup) {
            destination = CompanionDestination.SETTINGS.name
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current == CompanionDestination.HOME) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = current.title,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
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
                                    Icon(dest.icon, contentDescription = dest.label)
                                }
                            } else {
                                Icon(dest.icon, contentDescription = dest.label)
                            }
                        },
                        label = { Text(dest.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = current,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "companion-tab"
        ) { dest ->
            when (dest) {
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
                    onSaveCustomTheme = onSaveCustomTheme,
                    onUpdateSettings = onUpdateSettings
                )
                CompanionDestination.CLIPBOARD -> ClipboardScreen(
                    clipboardItems = clipboardItems,
                    onTogglePinClip = onTogglePinClip,
                    onDeleteClip = onDeleteClip,
                    onClearAllClips = onClearAllClips,
                    onCopyClip = { text ->
                        onCopyClip(text)
                        scope.launch {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        }
                    }
                )
                CompanionDestination.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
            }
        }
    }
}
