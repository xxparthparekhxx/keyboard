package io.github.xxparthparekhxx.composekeyboard.input.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class VoiceRecorder(private val cacheDir: File) {

    private val recording = AtomicBoolean(false)
    private val samples = ShortArray(WavPcm.SAMPLE_RATE * MAX_SECONDS)

    @Volatile
    private var sampleCount: Int = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (!recording.compareAndSet(false, true)) return
        sampleCount = 0
        val minBuf = AudioRecord.getMinBufferSize(
            WavPcm.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuf, WavPcm.SAMPLE_RATE)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                WavPcm.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            recording.set(false)
            Log.e(TAG, "Could not open microphone", e)
            throw e
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recording.set(false)
            throw IllegalStateException("Microphone is not available")
        }

        val chunk = ShortArray(bufferSize / 2)
        recorder.startRecording()
        try {
            while (recording.get() && sampleCount < samples.size) {
                val n = recorder.read(chunk, 0, min(chunk.size, samples.size - sampleCount))
                if (n > 0) {
                    System.arraycopy(chunk, 0, samples, sampleCount, n)
                    sampleCount += n
                } else if (n < 0) {
                    break
                }
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) { }
            recorder.release()
            recording.set(false)
        }
    }

    fun stop() {
        recording.set(false)
    }

    fun writeWav(): File {
        val file = File(cacheDir, "voice-input.wav")
        WavPcm.writeMono16(file, samples.copyOf(sampleCount))
        return file
    }

    fun elapsedMs(): Long = sampleCount * 1000L / WavPcm.SAMPLE_RATE

    fun hasAudio(): Boolean = sampleCount > WavPcm.SAMPLE_RATE / 5

    companion object {
        private const val TAG = "VoiceRecorder"
        const val MAX_SECONDS: Int = 30
    }
}
