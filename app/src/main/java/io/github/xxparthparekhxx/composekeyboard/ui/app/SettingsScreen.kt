package io.github.xxparthparekhxx.composekeyboard.ui.app

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperAbi
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelState
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: KeyboardSettings,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(stringResource(R.string.set_section_typing))
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                SettingSwitchRow(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.set_number_row),
                    subtitle = stringResource(R.string.set_number_row_desc),
                    checked = settings.showNumberRow,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowNumberRow(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.set_glide),
                    subtitle = stringResource(R.string.set_glide_desc),
                    checked = settings.swipeTypingEnabled,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSwipeTypingEnabled(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.set_autocaps),
                    subtitle = stringResource(R.string.set_autocaps_desc),
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
                    title = stringResource(R.string.set_haptic),
                    subtitle = stringResource(R.string.set_haptic_desc),
                    checked = settings.hapticFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setHapticFeedback(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = stringResource(R.string.set_sounds),
                    subtitle = stringResource(R.string.set_sounds_desc),
                    checked = settings.soundFeedback,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setSoundFeedback(checked) }
                    },
                    showDivider = true
                )
                SettingSwitchRow(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.set_popups),
                    subtitle = stringResource(R.string.set_popups_desc),
                    checked = settings.showKeyPopups,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.setShowKeyPopups(checked) }
                    }
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.set_section_size))
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                ScaleSetting(
                    title = stringResource(R.string.set_height),
                    subtitle = stringResource(R.string.set_height_desc),
                    value = settings.heightMultiplier,
                    valueRange = 0.70f..1.40f,
                    steps = 13,
                    presets = listOf(
                        stringResource(R.string.size_short) to 0.75f,
                        stringResource(R.string.size_compact) to 0.85f,
                        stringResource(R.string.size_standard) to 1.0f,
                        stringResource(R.string.size_tall) to 1.15f,
                        stringResource(R.string.size_extra_tall) to 1.30f
                    ),
                    selectionTolerance = 0.04f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setHeightMultiplier(value) }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                ScaleSetting(
                    title = stringResource(R.string.set_key_text),
                    subtitle = stringResource(R.string.set_key_text_desc),
                    value = settings.fontScale,
                    valueRange = 0.75f..1.40f,
                    steps = 12,
                    presets = listOf(
                        stringResource(R.string.size_small) to 0.85f,
                        stringResource(R.string.size_normal) to 1.00f,
                        stringResource(R.string.size_large) to 1.15f,
                        stringResource(R.string.size_extra_large) to 1.30f,
                        stringResource(R.string.size_huge) to 1.40f
                    ),
                    selectionTolerance = 0.035f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setFontScale(value) }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                ScaleSetting(
                    title = stringResource(R.string.set_emoji_size),
                    subtitle = stringResource(R.string.set_emoji_size_desc),
                    value = settings.emojiScale,
                    valueRange = 0.75f..1.40f,
                    steps = 12,
                    presets = listOf(
                        stringResource(R.string.size_small) to 0.85f,
                        stringResource(R.string.size_normal) to 1.00f,
                        stringResource(R.string.size_large) to 1.15f,
                        stringResource(R.string.size_extra_large) to 1.30f,
                        stringResource(R.string.size_huge) to 1.40f
                    ),
                    selectionTolerance = 0.035f,
                    onValueChange = { value ->
                        onUpdateSettings { it.setEmojiScale(value) }
                    }
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.set_about))
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = stringResource(R.string.set_about_version, appVersionName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.set_about_body),
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

    SectionHeader(stringResource(R.string.set_voice))
    Spacer(modifier = Modifier.height(8.dp))
    CompanionCard {
        Text(
            text = if (!supported) {
                stringResource(R.string.set_voice_unsupported)
            } else if (hasMic && modelReady) {
                stringResource(R.string.set_voice_ready)
            } else {
                stringResource(R.string.set_voice_intro)
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            icon = Icons.Default.Mic,
            title = stringResource(R.string.set_mic),
            subtitle = if (hasMic) stringResource(R.string.set_mic_allowed) else stringResource(R.string.set_mic_required),
            checked = hasMic,
            onCheckedChange = { checked ->
                if (checked) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            showDivider = true
        )
        if (downloading != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.set_downloading, (downloading.fraction * 100).toInt()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { downloading.fraction },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            SettingSwitchRow(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.set_model_title),
                subtitle = if (modelReady) stringResource(R.string.set_model_installed) else stringResource(R.string.set_model_download),
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

