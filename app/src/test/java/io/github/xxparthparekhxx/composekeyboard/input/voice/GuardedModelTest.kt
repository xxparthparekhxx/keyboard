package io.github.xxparthparekhxx.composekeyboard.input.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GuardedModelTest {

    private class FakeModel(val id: Int) {
        @Volatile var freed = false
    }

    @Test
    fun `release waits for a use in flight instead of freeing underneath it`() = runBlocking {
        val loads = AtomicInteger()
        val guarded = GuardedModel(
            load = { FakeModel(loads.incrementAndGet()) },
            free = { it.freed = true }
        )
        val inside = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var freedWhileInUse = true

        val user = launch(Dispatchers.Default) {
            guarded.use { model ->
                inside.complete(Unit)
                finish.await()
                freedWhileInUse = model.freed
            }
        }
        inside.await()
        val releaser = launch(Dispatchers.Default) { guarded.release() }
        repeat(20) { yield() }
        assertFalse("release must not complete while the model is in use", releaser.isCompleted)

        finish.complete(Unit)
        withTimeout(5_000) {
            user.join()
            releaser.join()
        }
        assertFalse(freedWhileInUse)
    }

    @Test
    fun `concurrent first uses load the model once`() = runBlocking {
        val loads = AtomicInteger()
        val guarded = GuardedModel(
            load = { FakeModel(loads.incrementAndGet()) },
            free = { }
        )
        val ids = (1..16).map {
            async(Dispatchers.Default) { guarded.use { it.id } }
        }.awaitAll()

        assertEquals(1, loads.get())
        assertTrue(ids.all { it == 1 })
    }

    @Test
    fun `use after release reloads a fresh model`() = runBlocking {
        val loads = AtomicInteger()
        val freed = mutableListOf<Int>()
        val guarded = GuardedModel(
            load = { FakeModel(loads.incrementAndGet()) },
            free = { freed += it.id }
        )
        assertEquals(1, guarded.use { it.id })
        guarded.release()
        assertEquals(listOf(1), freed)
        assertEquals(2, guarded.use { it.id })
    }

    @Test
    fun `release without a loaded model is a no-op`() = runBlocking {
        var frees = 0
        val guarded = GuardedModel(load = { FakeModel(1) }, free = { frees++ })
        guarded.release()
        assertEquals(0, frees)
    }
}
