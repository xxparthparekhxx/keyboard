package com.example.composekeyboard.ui.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.composekeyboard.data.KeyboardPreferences
import com.example.composekeyboard.data.KeyboardSettings
import com.example.composekeyboard.input.voice.WhisperAbi
import com.example.composekeyboard.input.voice.WhisperModelState
import com.example.composekeyboard.input.voice.WhisperModelStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: KeyboardSettings,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader("Typing")
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                SettingSwitchRow(
                    icon = Icons.Default.Keyboard,
                    title = "Number row",
                    subtitle = "Keep 1–0 visible above the letters. When off, numbers stay on long-press.",
                    checked = settings.showNumberRow,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowNumberRow(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.Gesture,
                    title = "Glide typing",
                    subtitle = "Swipe across letters to type words with on-device decoding.",
                    checked = settings.swipeTypingEnabled,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSwipeTypingEnabled(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.Settings,
                    title = "Auto-capitalization",
                    subtitle = "Capitalize the first letter of sentences and after punctuation.",
                    checked = settings.autoCapitalization,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setAutoCapitalization(checked) }
                    }
                )
            }
        }

        item {
            VoiceSetupCard()
        }

        item {
            SectionHeader("Feedback")
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                SettingSwitchRow(
                    icon = Icons.Default.Vibration,
                    title = "Haptic vibration",
                    subtitle = "Light taps on keys, swipe completions, and clipboard actions.",
                    checked = settings.hapticFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setHapticFeedback(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Key sounds",
                    subtitle = "Play a click when you press keys and space.",
                    checked = settings.soundFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSoundFeedback(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.TouchApp,
                    title = "Key popups",
                    subtitle = "Show an enlarged preview above each key as you press it.",
                    checked = settings.showKeyPopups,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowKeyPopups(checked) }
                    }
                )
            }
        }

        item {
            SectionHeader("Size")
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                ScaleSetting(
                    title = "Keyboard height",
                    subtitle = "Vertical scale of keys and panels.",
                    value = settings.heightMultiplier,
                    valueRange = 0.70f..1.40f,
                    steps = 13,
                    presets = listOf(
                        "Short" to 0.75f,
                        "Compact" to 0.85f,
                        "Standard" to 1.0f,
                        "Tall" to 1.15f,
                        "Extra tall" to 1.30f
                    ),
                    selectionTolerance = 0.04f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setHeightMultiplier(value) }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                ScaleSetting(
                    title = "Key text",
                    subtitle = "Letters, numbers, and symbols on the keys.",
                    value = settings.fontScale,
                    valueRange = 0.75f..1.40f,
                    steps = 12,
                    presets = listOf(
                        "Small" to 0.85f,
                        "Normal" to 1.00f,
                        "Large" to 1.15f,
                        "Extra large" to 1.30f,
                        "Huge" to 1.40f
                    ),
                    selectionTolerance = 0.035f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setFontScale(value) }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                ScaleSetting(
                    title = "Emoji size",
                    subtitle = "Emoji in the categorized picker grid.",
                    value = settings.emojiScale,
                    valueRange = 0.75f..1.40f,
                    steps = 12,
                    presets = listOf(
                        "Small" to 0.85f,
                        "Normal" to 1.00f,
                        "Large" to 1.15f,
                        "Extra large" to 1.30f,
                        "Huge" to 1.40f
                    ),
                    selectionTolerance = 0.035f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setEmojiScale(value) }
                    }
                )
            }
        }

        item {
            SectionHeader("About")
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "Compose Keyboard 1.2.0",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A Compose IME with neural swipe typing, clipboard history, and custom themes. Changes here apply the next time the keyboard opens.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun VoiceSetupCard() {
    val context = LocalContext.current
    val store = remember { WhisperModelStore.getInstance(context) }
    val modelState by store.state.collectAsState()
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMic = granted
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                store.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    val supported = WhisperAbi.isSupported()
    val modelReady = modelState is WhisperModelState.Ready
    val downloading = modelState as? WhisperModelState.Downloading
    val failed = modelState as? WhisperModelState.Failed

    SectionHeader("Voice typing")
    Spacer(modifier = Modifier.height(8.dp))
    CompanionCard {
        Text(
            text = if (!supported) {
                "Whisper Tiny needs a 64-bit ARM phone (arm64-v8a)."
            } else if (hasMic && modelReady) {
                "Ready. Tap the mic on the keyboard to dictate in English, on-device."
            } else {
                "On-device speech recognition with Whisper Tiny (~75 MB). Nothing is sent to a server."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            icon = Icons.Default.Mic,
            title = "Microphone",
            subtitle = if (hasMic) "Allowed" else "Required before the keyboard can listen",
            checked = hasMic,
            onCheckedChange = { checked ->
                if (checked) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            showDivider = true
        )
        if (downloading != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Downloading model ${(downloading.fraction * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = downloading.fraction,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            SettingSwitchRow(
                icon = Icons.Default.CloudDownload,
                title = "Whisper Tiny model",
                subtitle = if (modelReady) "Installed (~75 MB)" else "Download English weights to this phone",
                checked = modelReady,
                onCheckedChange = { checked ->
                    if (checked && supported) {
                        scope.launch { store.download() }
                    } else if (!checked && modelReady) {
                        scope.launch { store.delete() }
                    }
                }
            )
        }
        if (failed != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = failed.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

