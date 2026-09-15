package com.example.composekeyboard.input.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class WhisperModelState {
    data object Missing : WhisperModelState()
    data class Downloading(val fraction: Float) : WhisperModelState()
    data object Ready : WhisperModelState()
    data class Failed(val message: String) : WhisperModelState()
}

/**
 * First-run downloader for ggml-tiny.en. The ~75 MB weights stay out of the
 * APK; once on disk they never leave the device.
 */
class WhisperModelStore(private val context: Context) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<WhisperModelState> = _state.asStateFlow()

    fun modelFile(): File = File(File(context.filesDir, MODELS_DIR), FILE_NAME)

    fun isReady(): Boolean = isValid(modelFile())

    fun refresh() {
        _state.value = readState()
    }

    suspend fun download() {
        mutex.withLock {
            if (isValid(modelFile())) {
                _state.value = WhisperModelState.Ready
                return
            }
            _state.value = WhisperModelState.Downloading(0f)
            try {
                withContext(Dispatchers.IO) {
                    val target = modelFile()
                    val tmp = File(target.parentFile, "$FILE_NAME.tmp")
                    target.parentFile?.mkdirs()
                    if (tmp.exists()) tmp.delete()

                    val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 20_000
                        readTimeout = 60_000
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                    connection.connect()
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Download failed (HTTP $code)")
                    }
                    val total = connection.contentLengthLong.coerceAtLeast(0L)
                    connection.inputStream.use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(DEFAULT_BUFFER)
                            var written = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                written += n
                                val fraction = if (total > 0L) {
                                    (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                _state.value = WhisperModelState.Downloading(fraction)
                            }
                        }
                    }
                    if (writtenTooSmall(tmp)) {
                        tmp.delete()
                        throw IllegalStateException("Downloaded file was incomplete")
                    }
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                }
                _state.value = WhisperModelState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download Whisper Tiny", e)
                _state.value = WhisperModelState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: "Could not download Whisper Tiny"
                )
            }
        }
    }

    suspend fun delete() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = modelFile()
                if (file.exists()) file.delete()
            }
            _state.value = WhisperModelState.Missing
        }
    }

    private fun readState(): WhisperModelState =
        if (isValid(modelFile())) WhisperModelState.Ready else WhisperModelState.Missing

    companion object {
        private const val TAG = "WhisperModel"
        const val FILE_NAME = "ggml-tiny.en.bin"
        const val MODELS_DIR = "models"
        const val DOWNLOAD_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
        const val MIN_BYTES = 50_000_000L
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val USER_AGENT = "ComposeKeyboard/1.2"

        internal fun isValid(file: File): Boolean =
            file.exists() && file.length() >= MIN_BYTES

        private fun writtenTooSmall(file: File): Boolean = file.length() < MIN_BYTES

        @Volatile
        private var INSTANCE: WhisperModelStore? = null

        fun getInstance(context: Context): WhisperModelStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WhisperModelStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
