package com.example.composekeyboard.input.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VoiceUiState {
    data object UnsupportedDevice : VoiceUiState()
    data object NeedPermission : VoiceUiState()
    data object NeedModel : VoiceUiState()
    data class Downloading(val fraction: Float) : VoiceUiState()
    data object Idle : VoiceUiState()
    data class Recording(val elapsedMs: Long) : VoiceUiState()
    data object Transcribing : VoiceUiState()
    data class Failed(val message: String) : VoiceUiState()
}

class VoiceInputController(context: Context) {

    private val appContext = context.applicationContext
    private val store = WhisperModelStore.getInstance(appContext)
    private val engine = WhisperCppEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var recorder: VoiceRecorder? = null
    private var recordJob: Job? = null
    private var tickJob: Job? = null
    private var onText: ((String) -> Unit)? = null

    fun refresh() {
        val current = _state.value
        if (current is VoiceUiState.Recording ||
            current is VoiceUiState.Transcribing ||
            current is VoiceUiState.Downloading
        ) {
            return
        }
        _state.value = initialState()
    }

    fun downloadModel() {
        if (!WhisperAbi.isSupported()) {
            _state.value = VoiceUiState.UnsupportedDevice
            return
        }
        scope.launch {
            _state.value = VoiceUiState.Downloading(0f)
            val collectJob = launch {
                store.state.collect { modelState ->
                    when (modelState) {
                        is WhisperModelState.Downloading -> {
                            _state.value = VoiceUiState.Downloading(modelState.fraction)
                        }
                        else -> { }
                    }
                }
            }
            store.download()
            collectJob.cancel()
            _state.value = if (store.isReady()) {
                VoiceUiState.Idle
            } else {
                val failed = store.state.value as? WhisperModelState.Failed
                VoiceUiState.Failed(failed?.message ?: "Download failed")
            }
        }
    }

    fun startRecording(onText: (String) -> Unit) {
        this.onText = onText
        if (!hasMicPermission()) {
            _state.value = VoiceUiState.NeedPermission
            return
        }
        if (!store.isReady()) {
            _state.value = VoiceUiState.NeedModel
            return
        }
        if (recordJob?.isActive == true) return

        val rec = VoiceRecorder(appContext.cacheDir)
        recorder = rec
        _state.value = VoiceUiState.Recording(0L)
        recordJob = scope.launch(Dispatchers.IO) {
            try {
                rec.start()
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                withContext(Dispatchers.Main.immediate) {
                    _state.value = VoiceUiState.Failed(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not record audio"
                    )
                }
            }
        }
        tickJob = scope.launch {
            while (recordJob?.isActive == true) {
                delay(200)
                val recNow = recorder ?: break
                val elapsed = recNow.elapsedMs()
                _state.value = VoiceUiState.Recording(elapsed)
                if (elapsed >= VoiceRecorder.MAX_SECONDS * 1000L) {
                    stopAndTranscribe()
                    break
                }
            }
        }
    }

    fun stopAndTranscribe() {
        val rec = recorder ?: return
        val commit = onText
        tickJob?.cancel()
        rec.stop()
        scope.launch {
            recordJob?.join()
            if (!rec.hasAudio()) {
                _state.value = VoiceUiState.Failed("That was too short — try speaking a bit longer.")
                return@launch
            }
            _state.value = VoiceUiState.Transcribing
            try {
                val text = withContext(Dispatchers.Default) {
                    val wav = rec.writeWav()
                    engine.ensureLoaded(appContext, store.modelFile().absolutePath)
                    engine.transcribe(wav.absolutePath)
                }
                if (text.isBlank()) {
                    _state.value = VoiceUiState.Failed("Didn't catch that. Try again.")
                } else {
                    val committed = if (text.endsWith(" ")) text else "$text "
                    commit?.invoke(committed)
                    _state.value = VoiceUiState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _state.value = VoiceUiState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: "Transcription failed"
                )
            }
        }
    }

    fun cancelRecording() {
        tickJob?.cancel()
        recorder?.stop()
        recordJob?.cancel()
        recorder = null
        val current = _state.value
        if (current is VoiceUiState.Recording || current is VoiceUiState.Transcribing) {
            _state.value = initialState()
        }
    }

    fun release() {
        cancelRecording()
        engine.release()
    }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initialState(): VoiceUiState {
        if (!WhisperAbi.isSupported()) return VoiceUiState.UnsupportedDevice
        if (!hasMicPermission()) return VoiceUiState.NeedPermission
        if (!store.isReady()) return VoiceUiState.NeedModel
        return VoiceUiState.Idle
    }

    companion object {
        private const val TAG = "VoiceInput"

        @Volatile
        private var INSTANCE: VoiceInputController? = null

        fun getInstance(context: Context): VoiceInputController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceInputController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
