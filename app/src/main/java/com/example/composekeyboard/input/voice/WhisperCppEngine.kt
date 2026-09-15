package com.example.composekeyboard.input.voice

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel

/**
 * Isolated so ART never resolves the whisper JNI class unless this file's
 * methods actually run — [WhisperAbi.isSupported] must be true first.
 */
internal class WhisperCppEngine {

    @Volatile
    private var model: WhisperModel? = null

    suspend fun ensureLoaded(context: Context, modelPath: String) {
        if (model != null) return
        model = Whisper.loadModel(context, modelPath)
    }

    suspend fun transcribe(audioPath: String): String {
        val loaded = model ?: error("Whisper model is not loaded")
        val result = Whisper.transcribe(
            loaded,
            audioPath,
            WhisperConfig(language = "en")
        )
        return result.text.trim()
    }

    fun release() {
        val loaded = model ?: return
        model = null
        Whisper.releaseModel(loaded)
    }
}
