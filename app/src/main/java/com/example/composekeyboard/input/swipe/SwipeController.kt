package com.example.composekeyboard.input.swipe

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.example.composekeyboard.data.SwipeDictionary
import com.example.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the state of the gesture in flight and drives decoding.
 *
 * Lives across recompositions (`remember`ed by the keyboard) and is only touched
 * from the main thread; the decoder runs on [Dispatchers.Default] against
 * immutable snapshots so a long word never stutters the trail.
 */
@Stable
class SwipeController(
    private val geometry: SwipeKeyGeometry,
    private val dictionary: SwipeDictionary,
    private val scope: CoroutineScope
) : SwipeGestureHandler {

    /**
     * Neural decoder, when its weights are available.
     *
     * Set once from the service after the asset loads. Null means the model is
     * missing or failed to parse, in which case decoding silently falls back to
     * the geometric path -- less accurate, but a keyboard that still types beats
     * a keyboard that does not.
     */
    var neural: SwipeNeuralDecoder? = null

    /** Raw path of the gesture in flight. Read by the trail renderer. */
    val path = SwipeTrace()

    /** True from the moment a touch is recognised as a glide until it lifts. */
    var isSwiping by mutableStateOf(false)
        private set

    /** Best guess so far, shown live while the finger is still down. */
    var preview by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped for every point added to [path]. The trail renderer reads it inside
     * the draw phase, so a moving finger repaints the trail without ever
     * invalidating composition or layout.
     */
    var trailVersion by mutableIntStateOf(0)
        private set

    /** Ranked results of a completed gesture, best first. Reassigned per gesture. */
    var onResult: (List<String>) -> Unit = {}

    /** Fired once per gesture, when it is first recognised as a glide. */
    var onRecognized: () -> Unit = {}

    private var decodeJob: Job? = null
    private var previewJob: Job? = null
    private var lastPreviewAt = 0L

    override fun onTouchDown(position: Offset, startKey: Int, timeMillis: Long) {
        previewJob?.cancel()
        previewJob = null
        path.clear()
        path.add(position.x, position.y, timeMillis)
        preview = null
        lastPreviewAt = timeMillis
    }

    override fun onTouchMove(position: Offset, timeMillis: Long) {
        if (!path.add(position.x, position.y, timeMillis)) return
        if (!isSwiping) return
        trailVersion++
        updatePreview(timeMillis)
    }

    override fun onSwipeRecognized() {
        isSwiping = true
        trailVersion++
        onRecognized()
    }

    override fun onTouchUp() = finish(decode = true)

    override fun onTouchCancel() = finish(decode = false)

    /** Drops any in-flight gesture, e.g. when the keyboard layout changes. */
    fun cancel() = finish(decode = false)

    private fun finish(decode: Boolean) {
        previewJob?.cancel()
        previewJob = null

        val shouldDecode = decode && isSwiping
        val raw = if (shouldDecode) path.snapshot() else null
        val sampled = if (shouldDecode) path.sample(SwipeDecoder.SAMPLES) else null
        val keyMap = if (sampled != null) geometry.snapshot() else null

        path.clear()
        isSwiping = false
        preview = null
        trailVersion++

        if (sampled == null || keyMap == null) return

        decodeJob?.cancel()
        decodeJob = scope.launch {
            val words = withContext(Dispatchers.Default) {
                rank(raw, sampled, keyMap, MAX_SUGGESTIONS)
            }
            if (words.isNotEmpty()) onResult(words)
        }
    }

    /**
     * Refreshes the live preview, throttled so a fast finger cannot queue up
     * decodes faster than they complete. Uses the same full decode as lift-off,
     * so the preview is literally "the word you would get if you let go now".
     */
    private fun updatePreview(nowMillis: Long) {
        if (nowMillis - lastPreviewAt < PREVIEW_INTERVAL_MS) return
        if (previewJob?.isActive == true) return
        lastPreviewAt = nowMillis

        val sampled = path.sample(SwipeDecoder.SAMPLES) ?: return
        val keyMap = geometry.snapshot()
        previewJob = scope.launch {
            val words = withContext(Dispatchers.Default) {
                // Geometric decoder for the preview, deliberately.
                //
                // The network costs ~75ms per gesture on a mid-range phone
                // against the geometric decoder's ~0.1ms. Running the network
                // on every preview tick would keep a core busy for the whole
                // gesture for a hint that is about to be replaced anyway. The
                // preview is a guess; the word committed on lift-off is the
                // network's, and that is the one that has to be right.
                SwipeDecoder.decode(sampled, keyMap, dictionary, 1)
            }
            if (isSwiping) preview = words.firstOrNull()
        }
    }

    /**
     * Neural decode, falling back to the geometric decoder if the model is
     * absent or declines to produce anything.
     *
     * Both paths are given the same gesture; they simply read different things
     * out of it. The network sees the raw timestamped points -- it needs the
     * timing -- while the geometric scorer wants the arc-length resample that
     * makes its shape matching speed-invariant.
     */
    private fun rank(
        raw: SwipeTrace?,
        sampled: SampledSwipe?,
        keyMap: SwipeKeyMap,
        maxResults: Int
    ): List<String> {
        val model = neural
        if (model != null && raw != null) {
            val words = model.decode(raw, keyMap, maxResults)
            if (words.isNotEmpty()) return words
        }
        if (sampled == null) return emptyList()
        return SwipeDecoder.decode(sampled, keyMap, dictionary, maxResults)
    }

    companion object {
        const val MAX_SUGGESTIONS = SwipeConstants.MAX_SUGGESTIONS

        private const val PREVIEW_INTERVAL_MS = SwipeConstants.PREVIEW_INTERVAL_MS
    }
}
