package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.input.voice.VoiceInputController
import io.github.xxparthparekhxx.composekeyboard.input.voice.VoiceUiState
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors

@Composable
fun VoiceInputView(
    hapticEnabled: Boolean,
    onTextCommitted: (String) -> Unit,
    onRequestMicPermission: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    val view = LocalView.current
    val context = LocalContext.current
    val controller = remember { VoiceInputController.getInstance(context) }
    val state by controller.state.collectAsState()

    fun triggerHaptic() {
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    LaunchedEffect(Unit) {
        controller.refresh()
    }
    DisposableEffect(Unit) {
        onDispose { controller.cancelRecording() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.headerBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.voice_title),
                color = colors.keyTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .semantics { role = Role.Button }
                    .clickable {
                        triggerHaptic()
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.desc_close),
                    tint = colors.headerIconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val ui = state) {
                VoiceUiState.UnsupportedDevice -> {
                    StatusCopy(
                        title = stringResource(R.string.voice_unsupported_title),
                        body = stringResource(R.string.voice_unsupported_body)
                    )
                }
                VoiceUiState.NeedPermission -> {
                    StatusCopy(
                        title = stringResource(R.string.voice_permission_title),
                        body = stringResource(R.string.voice_permission_body)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = stringResource(R.string.voice_allow_mic),
                        onClick = {
                            triggerHaptic()
                            onRequestMicPermission()
                        }
                    )
                }
                VoiceUiState.NeedModel -> {
                    val metered = remember { controller.isMeteredDownload() }
                    StatusCopy(
                        title = stringResource(R.string.voice_download_title),
                        body = if (metered) {
                            stringResource(R.string.voice_download_body_metered)
                        } else {
                            stringResource(R.string.voice_download_body)
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = stringResource(R.string.voice_download_action),
                        onClick = {
                            triggerHaptic()
                            controller.downloadModel()
                        }
                    )
                }
                is VoiceUiState.Downloading -> {
                    StatusCopy(
                        title = stringResource(R.string.voice_downloading_title),
                        body = stringResource(R.string.voice_downloading_body, (ui.fraction * 100).toInt())
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { ui.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.actionKeyBackground,
                        trackColor = colors.accentKeyBackground
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = stringResource(R.string.action_cancel),
                        onClick = {
                            triggerHaptic()
                            controller.cancelModelDownload()
                        }
                    )
                }
                VoiceUiState.Idle -> {
                    MicButton(
                        recording = false,
                        onClick = {
                            triggerHaptic()
                            controller.startRecording(onTextCommitted)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.voice_tap_to_dictate),
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.voice_engine_line),
                        color = colors.headerIconColor,
                        fontSize = 12.sp
                    )
                }
                is VoiceUiState.Recording -> {
                    MicButton(
                        recording = true,
                        onClick = {
                            triggerHaptic()
                            controller.stopAndTranscribe()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = formatElapsed(ui.elapsedMs),
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = stringResource(R.string.voice_tap_to_stop),
                        color = colors.headerIconColor,
                        fontSize = 12.sp
                    )
                }
                VoiceUiState.Transcribing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = colors.actionKeyBackground,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.voice_transcribing),
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                is VoiceUiState.Failed -> {
                    StatusCopy(title = stringResource(R.string.voice_failed_title), body = ui.message)
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = stringResource(R.string.voice_try_again),
                        onClick = {
                            triggerHaptic()
                            controller.refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCopy(title: String, body: String) {
    val colors = LocalKeyboardColors.current
    Text(
        text = title,
        color = colors.keyTextColor,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        color = colors.headerIconColor,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.actionKeyBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.actionKeyTextColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MicButton(recording: Boolean, onClick: () -> Unit) {
    val colors = LocalKeyboardColors.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (recording) colors.actionKeyBackground else colors.accentKeyBackground)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (recording) stringResource(R.string.desc_stop_recording) else stringResource(R.string.desc_start_recording),
            tint = if (recording) colors.actionKeyTextColor else colors.accentKeyTextColor,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
