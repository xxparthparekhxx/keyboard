package io.github.xxparthparekhxx.composekeyboard.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Guards the download-integrity contract without touching the network: the URL
 * must stay pinned, the hash must be a real SHA-256, and [isValid] must accept
 * exactly the pinned byte count.
 */
class WhisperModelStoreTest {

    @Test
    fun downloadUrl_isPinnedToImmutableRevision() {
        assertTrue(
            "URL must pin an immutable commit, not resolve/main: ${WhisperModelStore.DOWNLOAD_URL}",
            WhisperModelStore.DOWNLOAD_URL.contains(
                "/resolve/${WhisperModelStore.PINNED_REVISION}/"
            )
        )
        assertFalse(WhisperModelStore.DOWNLOAD_URL.contains("/resolve/main/"))
        assertEquals(40, WhisperModelStore.PINNED_REVISION.length)
    }

    @Test
    fun expectedHash_isWellFormedSha256() {
        assertTrue(
            WhisperModelStore.EXPECTED_SHA256.matches(Regex("[0-9a-f]{64}"))
        )
        assertEquals(77_704_715L, WhisperModelStore.EXPECTED_BYTES)
    }

    @Test
    fun isValid_acceptsOnlyExactPinnedSize() {
        val dir = kotlin.io.path.createTempDirectory("whisper-test").toFile()
        try {
            val missing = File(dir, "ggml-tiny.en.bin")
            assertFalse(WhisperModelStore.isValid(missing))

            val short = File(dir, "short.bin")
            RandomAccessFile(short, "rw").use { it.setLength(60_000_000L) }
            assertFalse(WhisperModelStore.isValid(short))

            val exact = File(dir, "exact.bin")
            RandomAccessFile(exact, "rw").use { it.setLength(WhisperModelStore.EXPECTED_BYTES) }
            assertTrue(WhisperModelStore.isValid(exact))

            val over = File(dir, "over.bin")
            RandomAccessFile(over, "rw").use { it.setLength(WhisperModelStore.EXPECTED_BYTES + 1) }
            assertFalse(WhisperModelStore.isValid(over))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sha256Of_matchesKnownVector() {
        val f = File.createTempFile("sha-test", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                WhisperModelStore.sha256Of(f)
            )
        } finally {
            f.delete()
        }
    }
}
