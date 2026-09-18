package io.github.xxparthparekhxx.composekeyboard

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardHistoryManager
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.theme.CompanionTheme
import io.github.xxparthparekhxx.composekeyboard.ui.app.CompanionApp
import io.github.xxparthparekhxx.composekeyboard.ui.app.checkIsImeEnabled
import io.github.xxparthparekhxx.composekeyboard.ui.app.checkIsImeSelected

class MainActivity : ComponentActivity() {

    private lateinit var preferences: KeyboardPreferences
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private val isImeEnabledState = mutableStateOf(false)
    private val isImeSelectedState = mutableStateOf(false)
    private val requestMicState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = KeyboardPreferences.getInstance(this)
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)

        refreshImeStatus()
        requestMicState.value = intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)

        setContent {
            val settings by preferences.settings.collectAsState()
            val clipboardItems by clipboardHistoryManager.history.collectAsState()

            CompanionTheme {
                CompanionApp(
                    settings = settings,
                    isImeEnabled = isImeEnabledState.value,
                    isImeSelected = isImeSelectedState.value,
                    clipboardItems = clipboardItems,
                    openVoiceSetup = requestMicState.value,
                    onVoiceSetupConsumed = { requestMicState.value = false },
                    onRefreshStatus = { refreshImeStatus() },
                    onTogglePinClip = { id -> clipboardHistoryManager.togglePin(id) },
                    onDeleteClip = { id -> clipboardHistoryManager.deleteClip(id) },
                    onClearAllClips = { clipboardHistoryManager.clearAllUnpinned() },
                    onCopyClip = { text -> clipboardHistoryManager.copyToSystemClipboard(text) },
                    onSaveCustomTheme = { customColors ->
                        preferences.setCustomColors(customColors)
                    },
                    onUpdateSettings = { updateAction ->
                        updateAction(preferences)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)) {
            requestMicState.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshImeStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshImeStatus()
        }
    }

    private fun refreshImeStatus() {
        isImeEnabledState.value = checkIsImeEnabled(this)
        isImeSelectedState.value = checkIsImeSelected(this)
    }

    companion object {
        const val EXTRA_REQUEST_MIC: String = "request_mic"
    }
}
