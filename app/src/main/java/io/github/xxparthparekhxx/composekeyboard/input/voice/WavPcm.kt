package io.github.xxparthparekhxx.composekeyboard.input.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal PCM16 little-endian WAV writer. Whisper.cpp accepts WAV and
 * resamples internally; we still record at 16 kHz mono so there is nothing
 * to convert on the hot path.
 */
object WavPcm {
    const val SAMPLE_RATE: Int = 16_000
    const val CHANNELS: Int = 1
    const val BITS_PER_SAMPLE: Int = 16

    fun writeMono16(file: File, samples: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * CHANNELS * BITS_PER_SAMPLE / 8)
        buffer.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        samples.forEach { buffer.putShort(it) }
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> out.write(buffer.array()) }
    }
}
