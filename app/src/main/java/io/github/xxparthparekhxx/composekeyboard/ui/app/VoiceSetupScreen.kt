package io.github.xxparthparekhxx.composekeyboard.ui.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.input.voice.VoiceInputController
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperAbi
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelState
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelStore
import kotlinx.coroutines.launch

/** Snapshot of everything voice typing needs, shared by the Settings row and the setup page. */
internal data class VoiceStatus(
    val supported: Boolean,
    val hasMic: Boolean,
    val model: WhisperModelState
) {
    val ready: Boolean get() = supported && hasMic && model is WhisperModelState.Ready
}

/**
 * Mic permission and model state, re-read on every resume so the status is
 * right after the user comes back from system settings.
 */
@Composable
internal fun rememberVoiceStatus(): VoiceStatus {
    val context = LocalContext.current
    val store = remember { WhisperModelStore.getInstance(context) }
    val modelState by store.state.collectAsState()
    var hasMic by remember { mutableStateOf(hasMicPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMic = hasMicPermission(context)
                store.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return VoiceStatus(
        supported = WhisperAbi.isSupported(),
        hasMic = hasMic,
        model = modelState
    )
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** Detail page: the two setup steps for voice typing, in order. */
@Composable
fun VoiceSetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { WhisperModelStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val status = rememberVoiceStatus()
    // The permission dialog pauses the activity, so the resume re-read in
    // rememberVoiceStatus picks up the answer.
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val hasMic = status.hasMic
    val ready = status.ready

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            CompanionCard(
                containerColor = if (ready) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(
                        icon = if (status.supported) Icons.Default.Mic else Icons.Default.MicOff,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.voice_engine_line),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when {
                        !status.supported -> stringResource(R.string.set_voice_unsupported)
                        ready -> stringResource(R.string.set_voice_ready)
                        else -> stringResource(R.string.set_voice_intro)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (status.supported) {
            item {
                SettingsGroup(title = stringResource(R.string.set_voice_step_mic)) {
                    SetupStepTile(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.voice_permission_title),
                        body = if (hasMic) {
                            stringResource(R.string.set_mic_allowed)
                        } else {
                            stringResource(R.string.voice_permission_body)
                        },
                        done = hasMic,
                        action = if (hasMic) {
                            null
                        } else {
                            {
                                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                    Text(stringResource(R.string.voice_allow_mic))
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.set_voice_step_model)) {
                    ModelStepTile(
                        model = status.model,
                        isMetered = remember(status.model) { store.isActiveNetworkMetered() },
                        onDownload = { VoiceInputController.getInstance(context).downloadModel() },
                        onCancel = { store.cancelDownload() },
                        onDelete = { scope.launch { store.delete() } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStepTile(
    model: WhisperModelState,
    isMetered: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    when (model) {
        is WhisperModelState.Ready -> SetupStepTile(
            icon = Icons.Default.CloudDownload,
            title = stringResource(R.string.set_model_title),
            body = stringResource(R.string.set_model_installed),
            done = true,
            action = {
                OutlinedButton(onClick = onDelete) {
                    Text(stringResource(R.string.set_model_delete))
                }
            }
        )
        is WhisperModelState.Downloading -> SetupStepTile(
            icon = Icons.Default.CloudDownload,
            title = stringResource(R.string.voice_downloading_title),
            body = stringResource(R.string.voice_downloading_body, (model.fraction * 100).toInt()),
            done = false,
            extra = {
                LinearProgressIndicator(
                    progress = { model.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            action = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
        is WhisperModelState.Missing, is WhisperModelState.Failed -> SetupStepTile(
            icon = Icons.Default.CloudDownload,
            title = stringResource(R.string.voice_download_title),
            body = if (isMetered) {
                stringResource(R.string.voice_download_body_metered)
            } else {
                stringResource(R.string.voice_download_body)
            },
            done = false,
            extra = if (model is WhisperModelState.Failed) {
                {
                    Text(
                        text = model.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                null
            },
            action = {
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.voice_download_action))
                }
            }
        )
    }
}
