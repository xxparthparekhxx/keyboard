package io.github.xxparthparekhxx.composekeyboard.input.voice

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig

/**
 * Isolated so ART never resolves the whisper JNI class unless this file's
 * methods actually run — [WhisperAbi.isSupported] must be true first.
 *
 * The model lives in a [GuardedModel]: [release] waits for a transcription
 * that is already inside native code instead of freeing the model underneath
 * it, and two first transcriptions cannot load the ~77 MB model twice.
 */
internal class WhisperCppEngine(context: Context, modelPath: String) {

    private val model = GuardedModel(
        load = { Whisper.loadModel(context, modelPath) },
        free = { Whisper.releaseModel(it) }
    )

    suspend fun transcribe(audioPath: String): String = model.use { loaded ->
        Whisper.transcribe(loaded, audioPath, WhisperConfig(language = "en")).text.trim()
    }

    suspend fun release() = model.release()
}
