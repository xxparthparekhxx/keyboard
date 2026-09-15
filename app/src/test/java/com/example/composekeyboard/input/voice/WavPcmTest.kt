package com.example.composekeyboard.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

class WavPcmTest {

    @Test
    fun writeMono16_writesRiffHeaderAndSampleCount() {
        val dir = createTempDirectory("wav-pcm").toFile()
        val file = File(dir, "clip.wav")
        val samples = shortArrayOf(0, 1, -1, 32767, -32768)
        WavPcm.writeMono16(file, samples, sampleRate = 16_000)

        val bytes = file.readBytes()
        assertEquals(44 + samples.size * 2, bytes.size)
        assertEquals("RIFF", bytes.decodeToString(0, 4))
        assertEquals("WAVE", bytes.decodeToString(8, 12))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(22)
        assertEquals(1, buffer.short.toInt())
        assertEquals(16_000, buffer.int)
        buffer.position(40)
        assertEquals(samples.size * 2, buffer.int)
    }

    @Test
    fun modelFile_isInvalidUntilLargeEnough() {
        val dir = createTempDirectory("whisper-model").toFile()
        val missing = File(dir, "missing.bin")
        assertFalse(WhisperModelStore.isValid(missing))

        val tiny = File(dir, "tiny.bin")
        tiny.writeBytes(ByteArray(1024))
        assertFalse(WhisperModelStore.isValid(tiny))
    }

    @Test
    fun downloadUrl_pointsAtOfficialTinyEn() {
        assertTrue(WhisperModelStore.DOWNLOAD_URL.contains("ggml-tiny.en.bin"))
        assertEquals("ggml-tiny.en.bin", WhisperModelStore.FILE_NAME)
    }
}
