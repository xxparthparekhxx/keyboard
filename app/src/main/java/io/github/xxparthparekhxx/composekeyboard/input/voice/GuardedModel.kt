package io.github.xxparthparekhxx.composekeyboard.input.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A lazily loaded native resource whose every use, load and free is
 * serialized.
 *
 * Native inference cannot be interrupted: cancelling a coroutine only takes
 * effect once the blocking call returns. Holding the lock across [use] means
 * [release] waits for work in flight instead of freeing memory the native code
 * is still reading, and concurrent first uses load the resource exactly once.
 */
internal class GuardedModel<M : Any>(
    private val load: suspend () -> M,
    private val free: (M) -> Unit
) {
    private val mutex = Mutex()

    /** Guarded by [mutex]. */
    private var model: M? = null

    /** Runs [block] against the model, loading it first if needed. */
    suspend fun <R> use(block: suspend (M) -> R): R = mutex.withLock {
        val loaded = model ?: load().also { model = it }
        block(loaded)
    }

    /** Frees the model once nothing is using it. A later [use] reloads it. */
    suspend fun release() {
        mutex.withLock {
            val loaded = model ?: return
            model = null
            free(loaded)
        }
    }
}
