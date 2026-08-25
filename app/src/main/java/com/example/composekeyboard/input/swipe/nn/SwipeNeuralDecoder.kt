package com.example.composekeyboard.input.swipe.nn

import android.content.Context
import android.util.Log
import com.example.composekeyboard.data.SwipeDictionary
import com.example.composekeyboard.input.swipe.SwipeKeyMap
import com.example.composekeyboard.input.swipe.SwipeTrace

/**
 * Neural swipe decoding: encoder, then lexicon-constrained beam search.
 *
 * Replaces the geometric decoder's shape matching with a network that reads
 * intention out of the gesture — including its *timing*, which the geometric
 * path discarded entirely at resampling time. On the FUTO evaluation corpus the
 * geometric decoder scores 73% top-1 against this path's 93%, and most of the
 * recovered ground is words the old scorer could not distinguish even in
 * principle (doubled letters, and letters that sit on a straight line between
 * their neighbours).
 *
 * Everything here has to mirror `ml/swipe/preprocess.py` exactly. A model is
 * only as good as the agreement between how it was fed in training and how it
 * is fed in production, and the failure mode is silent: no crash, just quietly
 * worse predictions. The two places that matter are the resampling (uniform in
 * *time*, via a 60 Hz intermediate) and the normalization box.
 *
 * MODEL ASSET: The encoder weights file `swipe_encoder.bin` is located in
 * `app/src/main/assets/swipe_encoder.bin`. To re-train or export updated weights:
 *
 * 1. Train the model using the ML pipeline in `ml/` (requires Python/PyTorch)
 * 2. Export weights using `ml/tools/export_weights.py` (outputs `swipe_encoder.bin`)
 * 3. Copy the generated file to `app/src/main/assets/swipe_encoder.bin`
 *
 * If the asset is ever missing or fails to load, the decoder gracefully falls back to the geometric path
 * (see [SwipeController.rank]), which provides functional swipe typing
 * at ~73% top-1 accuracy.
 */
class SwipeNeuralDecoder private constructor(
    private val net: SwipeNet,
    @Volatile private var beam: SwipeBeam,
    @Volatile private var beamVersion: Int = 0
) {

    /**
     * Rebuilds the search beam's lexicon trie when the dictionary has gained
     * words since the trie was last built. Safe to call concurrently from
     * background threads.
     *
     * Gated on [SwipeDictionary.lexiconVersion] rather than the learned-word
     * counter: a rebuild walks ~150k words and allocates ~70 MB, so it is worth
     * doing when the user teaches a genuinely new word and not for the score
     * boost that every ordinary typed word produces.
     */
    fun updateBeam(dictionary: SwipeDictionary) {
        val currentVersion = dictionary.lexiconVersion
        if (currentVersion == beamVersion) return
        val (words, scores) = dictionary.allWords()
        val newBeam = SwipeBeam.build(words, scores)
        this.beam = newBeam
        this.beamVersion = currentVersion
        Log.i(TAG, "rebuilt trie (version $currentVersion) over ${words.size} words")
    }

    private val xy = FloatArray(SwipeNet.T_IN * 2)
    private val keyX = FloatArray(SwipeDictionary.ALPHABET)
    private val keyY = FloatArray(SwipeDictionary.ALPHABET)
    private val results = arrayOfNulls<String>(MAX_RESULTS)
    private val scores = FloatArray(MAX_RESULTS)

    private var basis: FloatArray? = null
    private var basisKey = 0

    /**
     * Ranks words for one gesture. Call it from a background dispatcher.
     *
     * Synchronized because the scratch buffers are reused across calls and a
     * live preview can still be in flight when the finger lifts; two decodes
     * sharing those arrays would corrupt each other silently.
     *
     * Returns an empty list if the gesture is too short to resample.
     */
    @Synchronized
    fun decode(trace: SwipeTrace, keys: SwipeKeyMap, maxResults: Int = 4): List<String> {
        if (!resampleUniformTime(trace, keys)) return emptyList()

        val started = if (LOG_TIMING) System.nanoTime() else 0L
        val phi = basisFor(keys)
        net.forward(xy, phi)
        val encoded = if (LOG_TIMING) System.nanoTime() else 0L

        val n = beam.search(net.emissions, BEAM_WIDTH, results, scores)
        if (LOG_TIMING) {
            val done = System.nanoTime()
            Log.d(TAG, "encoder %.1fms  beam %.1fms  total %.1fms".format(
                (encoded - started) / 1e6, (done - encoded) / 1e6, (done - started) / 1e6))
        }
        if (n == 0) return emptyList()
        val take = minOf(n, maxResults)
        return ArrayList<String>(take).apply {
            for (i in 0 until take) add(results[i]!!)
        }
    }

    /**
     * Cosine basis for the live keyboard, cached until the layout moves.
     *
     * Both the key centres and the trajectory are normalized by the bounding box
     * of the letter keys, which is the convention the training layouts use --
     * their keys span exactly [0,1] on both axes. Normalizing by anything else
     * (the view bounds, say) would shift the whole gesture relative to where the
     * model expects the keyboard to be.
     */
    private fun basisFor(keys: SwipeKeyMap): FloatArray {
        val hash = keys.layoutHash()
        val cached = basis
        if (cached != null && hash == basisKey) return cached

        val box = keys.letterBounds()
        val invW = 1f / box[2]
        val invH = 1f / box[3]
        for (i in 0 until SwipeDictionary.ALPHABET) {
            keyX[i] = (keys.centerX[i] - box[0]) * invW
            keyY[i] = (keys.centerY[i] - box[1]) * invH
        }
        val built = SwipeNet.basisFor(keyX, keyY)
        basis = built
        basisKey = hash
        return built
    }

    /**
     * Resamples the finger path to [SwipeNet.T_IN] points spaced evenly in time
     * and normalized into the keyboard's unit box.
     *
     * Time spacing, not arc length. The geometric decoder deliberately resampled
     * by arc length to make shape matching speed-invariant, which threw away
     * every trace of how long the finger lingered anywhere. That hesitation is
     * exactly the evidence that separates "putt" from "put", so here it is kept.
     *
     * The 60 Hz intermediate pass mirrors training, where it normalizes away the
     * difference between 60/90/120/240 Hz panels before the fixed-width
     * derivative filter runs.
     */
    private fun resampleUniformTime(trace: SwipeTrace, keys: SwipeKeyMap): Boolean {
        val n = trace.size
        if (n < 2) return false
        val t0 = trace.timeAt(0)
        val duration = (trace.timeAt(n - 1) - t0).toFloat()
        if (duration <= 0f) return false

        val box = keys.letterBounds()
        val invW = 1f / box[2]
        val invH = 1f / box[3]

        val n60 = ((duration / 1000f * RESAMPLE_HZ).toInt() + 1).coerceAtLeast(2)
        val x60 = FloatArray(n60)
        val y60 = FloatArray(n60)

        var src = 0
        for (i in 0 until n60) {
            val target = duration * i / (n60 - 1)
            while (src < n - 2 && (trace.timeAt(src + 1) - t0).toFloat() < target) src++
            val ta = (trace.timeAt(src) - t0).toFloat()
            val tb = (trace.timeAt(src + 1) - t0).toFloat()
            val f = if (tb > ta) ((target - ta) / (tb - ta)).coerceIn(0f, 1f) else 0f
            x60[i] = trace.x(src) + (trace.x(src + 1) - trace.x(src)) * f
            y60[i] = trace.y(src) + (trace.y(src + 1) - trace.y(src)) * f
        }

        // second pass: the 60 Hz grid down (or up) to exactly T_IN points
        for (i in 0 until SwipeNet.T_IN) {
            val u = (n60 - 1).toFloat() * i / (SwipeNet.T_IN - 1)
            val a = u.toInt().coerceAtMost(n60 - 2)
            val f = u - a
            val px = x60[a] + (x60[a + 1] - x60[a]) * f
            val py = y60[a] + (y60[a + 1] - y60[a]) * f
            xy[i * 2] = (px - box[0]) * invW
            xy[i * 2 + 1] = (py - box[1]) * invH
        }
        return true
    }

    companion object {
        private const val TAG = "SwipeNeural"

        /** Flip on to get per-gesture latency in logcat. */
        private const val LOG_TIMING = false
        private const val RESAMPLE_HZ = 60f
        private const val MAX_RESULTS = 8

        /**
         * Beam width. The paper's own sweep has accuracy saturating by 100;
         * 50 gives up roughly half a point of top-1 for half the search cost,
         * which is the better trade on a phone.
         */
        const val BEAM_WIDTH = 50

        private const val ASSET_MODEL = "swipe_encoder.bin"

        /** Blocking; call from a background dispatcher. Null if the asset is missing. */
        fun load(context: Context, dictionary: SwipeDictionary): SwipeNeuralDecoder? {
            return try {
                val net = context.assets.open(ASSET_MODEL).use { SwipeNet.load(it) }
                val initialVersion = dictionary.lexiconVersion
                val words = dictionary.allWords()
                val beam = SwipeBeam.build(words.first, words.second)
                Log.i(TAG, "encoder loaded; trie over ${words.first.size} words")
                SwipeNeuralDecoder(net, beam, initialVersion)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load neural decoder; falling back to geometric", e)
                null
            }
        }
    }
}
