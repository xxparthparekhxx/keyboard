package io.github.xxparthparekhxx.composekeyboard.input.voice

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.xxparthparekhxx.composekeyboard.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed class WhisperModelState {
    data object Missing : WhisperModelState()
    data class Downloading(val fraction: Float) : WhisperModelState()
    data object Ready : WhisperModelState()
    data class Failed(val message: String) : WhisperModelState()
}

/**
 * First-run downloader for ggml-tiny.en. The ~77 MB weights stay out of the
 * APK; once on disk they never leave the device.
 *
 * Integrity: the URL is pinned to an immutable Hugging Face commit (not the
 * mutable `main` branch), every download is checked against the bundled
 * SHA-256 before it replaces any existing file, and a `.sha256` sidecar marks
 * verified files so the 77 MB hash is not recomputed on every keyboard open —
 * [isValid] stays a cheap size check on the hot path.
 */
class WhisperModelStore(private val context: Context) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<WhisperModelState> = _state.asStateFlow()

    /** Set while a download is in flight so [cancelDownload] can break it. */
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var downloadJob: Job? = null

    fun modelFile(): File = File(File(context.filesDir, MODELS_DIR), FILE_NAME)

    fun isReady(): Boolean = isValid(modelFile())

    /** True when the active network is metered (cellular, hotspot, …). */
    fun isActiveNetworkMetered(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.isActiveNetworkMetered == true
        } catch (_: Exception) {
            false
        }
    }

    fun refresh() {
        // Don't stomp a live progress bar.
        if (_state.value is WhisperModelState.Downloading) return
        _state.value = readState()
    }

    suspend fun download() {
        mutex.withLock {
            if (isValid(modelFile())) {
                _state.value = WhisperModelState.Ready
                return
            }
            _state.value = WhisperModelState.Downloading(0f)
            downloadJob = coroutineContext[Job]
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
                    activeConnection = connection
                    try {
                        connection.connect()
                        val code = connection.responseCode
                        if (code !in 200..299) {
                            throw IllegalStateException(context.getString(R.string.model_download_http, code))
                        }
                        val total = connection.contentLengthLong
                            .takeIf { it > 0 } ?: EXPECTED_BYTES
                        connection.inputStream.use { input ->
                            tmp.outputStream().use { output ->
                                val buf = ByteArray(DEFAULT_BUFFER)
                                var written = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                    written += n
                                    val fraction = (written.toFloat() / total.toFloat())
                                        .coerceIn(0f, 1f)
                                    _state.value = WhisperModelState.Downloading(fraction)
                                }
                            }
                        }
                        coroutineContext.ensureActive()
                        if (tmp.length() != EXPECTED_BYTES) {
                            tmp.delete()
                            throw IllegalStateException(context.getString(R.string.model_download_incomplete))
                        }
                        if (!verifySha256(tmp)) {
                            tmp.delete()
                            throw IllegalStateException(context.getString(R.string.model_integrity_failed))
                        }
                        if (target.exists()) target.delete()
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        writeVerifiedMarker(target)
                    } finally {
                        activeConnection = null
                        try {
                            connection.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                }
                _state.value = WhisperModelState.Ready
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    try {
                        File(modelFile().parentFile, "$FILE_NAME.tmp").delete()
                    } catch (_: Exception) {
                    }
                    _state.value = readState()
                    throw e
                }
                Log.e(TAG, "Failed to download Whisper Tiny", e)
                _state.value = WhisperModelState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.model_download_failed_generic)
                )
            } finally {
                downloadJob = null
            }
        }
    }

    /** Cancels an in-flight [download]; a no-op when nothing is downloading. */
    fun cancelDownload() {
        downloadJob?.cancel()
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }
    }

    suspend fun delete() {
        cancelDownload()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = modelFile()
                if (file.exists()) file.delete()
                verifiedMarker(file).delete()
            }
            _state.value = WhisperModelState.Missing
        }
    }

    /**
     * Re-verifies an existing on-disk model against the bundled hash.
     * Blocking; call from a background dispatcher. Used to grandfather files
     * fetched before hash verification existed — same size does not prove same
     * bytes.
     */
    suspend fun verifyExisting(): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile()
        if (!file.exists() || file.length() != EXPECTED_BYTES) return@withContext false
        if (verifySha256(file)) {
            try {
                writeVerifiedMarker(file)
            } catch (_: Exception) {
            }
            true
        } else {
            false
        }
    }

    private fun readState(): WhisperModelState =
        if (isValid(modelFile())) WhisperModelState.Ready else WhisperModelState.Missing

    private fun verifiedMarker(model: File): File = File(model.parentFile, "$FILE_NAME.sha256")

    private fun writeVerifiedMarker(model: File) {
        verifiedMarker(model).writeText(EXPECTED_SHA256)
    }

    companion object {
        private const val TAG = "WhisperModel"
        const val FILE_NAME = "ggml-tiny.en.bin"
        const val MODELS_DIR = "models"

        /**
         * Pinned to an immutable commit, not the mutable `main` branch: this
         * URL can never silently start serving different bytes. To update the
         * model, pin a new commit here *and* refresh [EXPECTED_SHA256] /
         * [EXPECTED_BYTES] to match.
         */
        const val PINNED_REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1"
        const val DOWNLOAD_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/$PINNED_REVISION/ggml-tiny.en.bin"

        /** `sha256sum ggml-tiny.en.bin` at the pinned revision. */
        const val EXPECTED_SHA256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"

        /** Exact byte size at the pinned revision (77,704,715 bytes, ~77 MB). */
        const val EXPECTED_BYTES = 77_704_715L

        /** Legacy floor kept for diagnostics; [EXPECTED_BYTES] is authoritative. */
        const val MIN_BYTES = 50_000_000L
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val USER_AGENT = "ComposeKeyboard/1.2"

        /**
         * Cheap hot-path check: exact size only. Full SHA-256 runs once per
         * download inside [download] (plus on-demand via [verifyExisting]), not
         * on every keyboard open.
         */
        internal fun isValid(file: File): Boolean =
            file.exists() && file.length() == EXPECTED_BYTES

        internal fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(DEFAULT_BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun verifySha256(file: File): Boolean {
            return try {
                sha256Of(file).equals(EXPECTED_SHA256, ignoreCase = true)
            } catch (e: Exception) {
                Log.e(TAG, "Hash verification failed", e)
                false
            }
        }

        @Volatile
        private var INSTANCE: WhisperModelStore? = null

        fun getInstance(context: Context): WhisperModelStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WhisperModelStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
