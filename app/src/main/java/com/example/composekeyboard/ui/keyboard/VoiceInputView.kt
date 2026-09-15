package com.example.composekeyboard.ui.keyboard

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.input.voice.VoiceInputController
import com.example.composekeyboard.input.voice.VoiceUiState
import com.example.composekeyboard.theme.LocalKeyboardColors

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
                text = "Voice typing",
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
                    contentDescription = "Close",
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
                        title = "Needs a 64-bit ARM phone",
                        body = "Whisper Tiny’s native library ships for arm64. This device can’t run it."
                    )
                }
                VoiceUiState.NeedPermission -> {
                    StatusCopy(
                        title = "Microphone access",
                        body = "Android only grants the mic from the companion app, not from the keyboard overlay."
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = "Allow microphone",
                        onClick = {
                            triggerHaptic()
                            onRequestMicPermission()
                        }
                    )
                }
                VoiceUiState.NeedModel -> {
                    StatusCopy(
                        title = "Download Whisper Tiny",
                        body = "On-device English speech recognition. About 75 MB, stored only on this phone."
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = "Download model",
                        onClick = {
                            triggerHaptic()
                            controller.downloadModel()
                        }
                    )
                }
                is VoiceUiState.Downloading -> {
                    StatusCopy(
                        title = "Downloading Whisper Tiny",
                        body = "${(ui.fraction * 100).toInt()}% · stays on device after this"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = ui.fraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.actionKeyBackground,
                        trackColor = colors.accentKeyBackground
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
                        text = "Tap to dictate",
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Whisper Tiny · English · on-device",
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
                        text = "Tap to stop",
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
                        text = "Transcribing…",
                        color = colors.keyTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                is VoiceUiState.Failed -> {
                    StatusCopy(title = "Couldn't transcribe", body = ui.message)
                    Spacer(modifier = Modifier.height(14.dp))
                    ActionChip(
                        label = "Try again",
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
            contentDescription = if (recording) "Stop recording" else "Start recording",
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
