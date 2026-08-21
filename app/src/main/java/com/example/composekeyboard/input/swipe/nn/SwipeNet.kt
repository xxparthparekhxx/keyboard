package com.example.composekeyboard.input.swipe.nn

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The swipe encoder, run by hand.
 *
 * This is a straight transcription of the trained network — a temporal
 * convolutional trunk followed by two heads — with no inference library behind
 * it. The whole forward pass is about 35 million multiply-accumulates over
 * arrays that fit comfortably in cache, so plain Kotlin is fast enough and the
 * app keeps its zero-dependency build.
 *
 * The important structural point is what the network does *not* emit. It never
 * produces a score for "the Q key". It emits, at each step along the gesture, a
 * small 2D spatial pattern in cosine coefficients plus a scalar saying how
 * strongly the user was indicating a character there. The keyboard is applied
 * afterwards, by sampling that pattern at each key's position ([basisFor]).
 * That is why one set of weights works for a layout it never saw in training,
 * and why this app's rows — which have three different key widths — need no
 * special handling.
 *
 * Activations are kept time-major ([t * channels + c]) because the pointwise
 * projections dominate the arithmetic and want channels contiguous.
 */
class SwipeNet private constructor(private val w: Map<String, Tensor>) {

    class Tensor(val dims: IntArray, val data: FloatArray) {
        val rows: Int get() = dims[0]
        val cols: Int get() = if (dims.size > 1) dims[1] else 1
    }

    // Scratch buffers, sized once. A gesture decode must not allocate.
    private val feat = FloatArray(T_IN * F_IN)
    private var bufA = FloatArray(T_IN * DIM)
    private var bufB = FloatArray(T_IN * DIM)
    private val hidden = FloatArray(T_IN * EXPAND)
    private val gated = FloatArray(T_IN * (EXPAND / 2))
    private val seVec = FloatArray(DIM)
    private val seHid = FloatArray(SE_HIDDEN)
    private val head = FloatArray(T_OUT * HEAD_DIM)
    private val coeff = FloatArray(T_OUT * N_COEFF)
    private val gate = FloatArray(T_OUT)
    private val norms = FloatArray(EXPAND / 2)

    /** Per-timestep log emissions over 26 letters + blank, row-major [t][27]. */
    val emissions = FloatArray(T_OUT * (ALPHABET + 1))

    /**
     * Runs the network.
     *
     * @param xy trajectory as [x0,y0,x1,y1,...], [T_IN] points, already resampled
     *           uniformly in time and normalized to the keyboard's [0,1] box.
     * @param basis cosine basis for the active layout, from [basisFor].
     */
    fun forward(xy: FloatArray, basis: FloatArray) {
        savitzkyGolay(xy)
        conv1d(feat, bufA, w["stem.w"]!!, w["stem.b"]!!, T_IN, F_IN, DIM, 5, 1, 2)

        for (i in DILATIONS.indices) {
            block(i, DILATIONS[i])
        }

        // 2x adapter: halve the time axis, widen into the head
        conv1d(bufA, head, w["adapter.w"]!!, w["adapter.b"]!!, T_IN, DIM, HEAD_DIM, 2, 1, 0, stride = 2)

        linear(head, coeff, w["coeff.w"]!!, w["coeff.b"]!!, T_OUT, HEAD_DIM, N_COEFF)
        linear(head, gate, w["gate.w"]!!, w["gate.b"]!!, T_OUT, HEAD_DIM, 1)

        emit(basis)
    }

    // -- backbone ----------------------------------------------------------

    private fun block(index: Int, dilation: Int) {
        val dwW = w["b$index.dw.w"]!!
        val dwB = w["b$index.dw.b"]!!
        val pad = dilation * (DW_KERNEL - 1) / 2
        depthwise(bufA, bufB, dwW, dwB, T_IN, DIM, DW_KERNEL, dilation, pad)

        // 1x1 expansion, then a gated linear unit halves it again
        linear(bufB, hidden, w["b$index.pw1.w"]!!, w["b$index.pw1.b"]!!, T_IN, DIM, EXPAND)
        val half = EXPAND / 2
        for (t in 0 until T_IN) {
            val src = t * EXPAND
            val dst = t * half
            for (c in 0 until half) {
                gated[dst + c] = hidden[src + c] * sigmoid(hidden[src + half + c])
            }
        }

        grn(gated, w["b$index.grn.g"]!!, w["b$index.grn.b"]!!, T_IN, half)
        linear(gated, bufB, w["b$index.pw2.w"]!!, w["b$index.pw2.b"]!!, T_IN, half, DIM)
        squeezeExcite(bufB, index)

        for (i in 0 until T_IN * DIM) bufA[i] += bufB[i]
    }

    /**
     * Global response normalization: rescale each channel by how energetic it is
     * relative to the mean channel, which keeps any one feature from dominating.
     */
    private fun grn(x: FloatArray, gamma: Tensor, beta: Tensor, t: Int, c: Int) {
        var mean = 0f
        for (ch in 0 until c) {
            var s = 0f
            for (i in 0 until t) {
                val v = x[i * c + ch]
                s += v * v
            }
            val n = sqrt(s)
            norms[ch] = n
            mean += n
        }
        mean = mean / c + 1e-6f
        for (i in 0 until t) {
            val base = i * c
            for (ch in 0 until c) {
                val v = x[base + ch]
                x[base + ch] = gamma.data[ch] * (v * (norms[ch] / mean)) + beta.data[ch] + v
            }
        }
    }

    /** Channel gating from a global average over time. */
    private fun squeezeExcite(x: FloatArray, index: Int) {
        java.util.Arrays.fill(seVec, 0f)
        for (t in 0 until T_IN) {
            val base = t * DIM
            for (c in 0 until DIM) seVec[c] += x[base + c]
        }
        val inv = 1f / T_IN
        for (c in 0 until DIM) seVec[c] *= inv

        val w1 = w["b$index.se1.w"]!!; val b1 = w["b$index.se1.b"]!!
        for (h in 0 until SE_HIDDEN) {
            var s = b1.data[h]
            val row = h * DIM
            for (c in 0 until DIM) s += w1.data[row + c] * seVec[c]
            seHid[h] = if (s > 0f) s else 0f
        }
        val w2 = w["b$index.se2.w"]!!; val b2 = w["b$index.se2.b"]!!
        for (c in 0 until DIM) {
            var s = b2.data[c]
            val row = c * SE_HIDDEN
            for (h in 0 until SE_HIDDEN) s += w2.data[row + h] * seHid[h]
            seVec[c] = sigmoid(s)
        }
        for (t in 0 until T_IN) {
            val base = t * DIM
            for (c in 0 until DIM) x[base + c] *= seVec[c]
        }
    }

    // -- heads -------------------------------------------------------------

    /**
     * Turns coefficients and the intention gate into a CTC emission distribution.
     *
     * Blank takes log(1 - lambda) and every character takes
     * log softmax(pattern sampled at that key) + log(lambda), so the gate alone
     * decides "is the user indicating anything here" while the spatial pattern
     * decides "where". Keeping those factored is what lets the same coefficients
     * be read out against any keyboard.
     */
    private fun emit(basis: FloatArray) {
        val keys = basis.size / N_COEFF
        val stride = ALPHABET + 1
        val z = FloatArray(keys)
        for (t in 0 until T_OUT) {
            val cBase = t * N_COEFF
            var maxZ = Float.NEGATIVE_INFINITY
            for (k in 0 until keys) {
                var s = 0f
                val bBase = k * N_COEFF
                for (i in 0 until N_COEFF) s += coeff[cBase + i] * basis[bBase + i]
                z[k] = s
                if (s > maxZ) maxZ = s
            }
            var sum = 0f
            for (k in 0 until keys) sum += exp(z[k] - maxZ)
            val logDenom = maxZ + ln(sum)

            val g = gate[t]
            val logLambda = -softplus(-g)      // log sigmoid(g)
            val logBlank = -softplus(g)        // log(1 - sigmoid(g))

            val outBase = t * stride
            for (k in 0 until keys) emissions[outBase + k] = z[k] - logDenom + logLambda
            emissions[outBase + ALPHABET] = logBlank
        }
    }

    // -- input features ----------------------------------------------------

    /**
     * Position, velocity, acceleration, speed and curvature, via a 7-tap
     * Savitzky-Golay filter — a least-squares quadratic through a sliding
     * window, which differentiates without amplifying touch noise the way naive
     * finite differences would.
     */
    private fun savitzkyGolay(xy: FloatArray) {
        val k = w["savgol"]!!.data          // (3, 7), pre-flipped for correlation
        val half = SG_WINDOW / 2
        for (t in 0 until T_IN) {
            var px = 0f; var py = 0f
            var vx = 0f; var vy = 0f
            var ax = 0f; var ay = 0f
            for (j in 0 until SG_WINDOW) {
                // replicate padding at both ends
                val idx = (t + j - half).coerceIn(0, T_IN - 1)
                val x = xy[idx * 2]
                val y = xy[idx * 2 + 1]
                val k0 = k[j]; val k1 = k[SG_WINDOW + j]; val k2 = k[2 * SG_WINDOW + j]
                px += k0 * x; py += k0 * y
                vx += k1 * x; vy += k1 * y
                ax += k2 * x; ay += k2 * y
            }
            val base = t * F_IN
            feat[base] = px; feat[base + 1] = py
            feat[base + 2] = vx; feat[base + 3] = vy
            feat[base + 4] = ax; feat[base + 5] = ay
            feat[base + 6] = sqrt(vx * vx + vy * vy + 1e-8f)
            feat[base + 7] = 0f     // curvature, filled below
        }
        // curvature: change in heading between consecutive steps, unwrapped
        var prev = atan2(feat[3], feat[2])
        for (t in 1 until T_IN) {
            val base = t * F_IN
            val theta = atan2(feat[base + 3], feat[base + 2])
            var d = theta - prev
            while (d > PI_F) d -= TWO_PI
            while (d < -PI_F) d += TWO_PI
            feat[base + 7] = d.coerceIn(-2f, 2f)
            prev = theta
        }
    }

    // -- primitives --------------------------------------------------------

    private fun conv1d(
        src: FloatArray, dst: FloatArray, weight: Tensor, bias: Tensor,
        tIn: Int, cIn: Int, cOut: Int, kernel: Int, dilation: Int, pad: Int, stride: Int = 1
    ) {
        val tOut = (tIn + 2 * pad - dilation * (kernel - 1) - 1) / stride + 1
        for (t in 0 until tOut) {
            val outBase = t * cOut
            val start = t * stride - pad
            for (o in 0 until cOut) {
                var s = bias.data[o]
                val wBase = o * cIn * kernel
                for (j in 0 until kernel) {
                    val idx = start + j * dilation
                    if (idx < 0 || idx >= tIn) continue
                    val inBase = idx * cIn
                    val wj = wBase + j
                    for (c in 0 until cIn) s += weight.data[wj + c * kernel] * src[inBase + c]
                }
                dst[outBase + o] = s
            }
        }
    }

    private fun depthwise(
        src: FloatArray, dst: FloatArray, weight: Tensor, bias: Tensor,
        tIn: Int, channels: Int, kernel: Int, dilation: Int, pad: Int
    ) {
        for (t in 0 until tIn) {
            val outBase = t * channels
            val start = t - pad
            for (c in 0 until channels) {
                var s = bias.data[c]
                val wBase = c * kernel
                for (j in 0 until kernel) {
                    val idx = start + j * dilation
                    if (idx < 0 || idx >= tIn) continue
                    s += weight.data[wBase + j] * src[idx * channels + c]
                }
                dst[outBase + c] = s
            }
        }
    }

    /**
     * Pointwise projection — around 90% of the arithmetic in the whole network,
     * so it is worth writing carefully.
     *
     * Eight output channels are accumulated per pass, so each loaded input value
     * feeds eight multiply-adds instead of one. The load saving matters less
     * than the eight independent accumulator chains: a single chain stalls on
     * floating-point latency, and ARM cores have far more issue slots than one
     * dependent chain can fill.
     */
    private fun linear(
        src: FloatArray, dst: FloatArray, weight: Tensor, bias: Tensor,
        rows: Int, cIn: Int, cOut: Int
    ) {
        val wd = weight.data
        val bd = bias.data
        for (t in 0 until rows) {
            val inBase = t * cIn
            val outBase = t * cOut
            var o = 0
            while (o + 8 <= cOut) {
                val w0 = o * cIn; val w1 = w0 + cIn; val w2 = w1 + cIn; val w3 = w2 + cIn
                val w4 = w3 + cIn; val w5 = w4 + cIn; val w6 = w5 + cIn; val w7 = w6 + cIn
                var s0 = bd[o]; var s1 = bd[o + 1]; var s2 = bd[o + 2]; var s3 = bd[o + 3]
                var s4 = bd[o + 4]; var s5 = bd[o + 5]; var s6 = bd[o + 6]; var s7 = bd[o + 7]
                for (c in 0 until cIn) {
                    val v = src[inBase + c]
                    s0 += wd[w0 + c] * v; s1 += wd[w1 + c] * v
                    s2 += wd[w2 + c] * v; s3 += wd[w3 + c] * v
                    s4 += wd[w4 + c] * v; s5 += wd[w5 + c] * v
                    s6 += wd[w6 + c] * v; s7 += wd[w7 + c] * v
                }
                dst[outBase + o] = s0; dst[outBase + o + 1] = s1
                dst[outBase + o + 2] = s2; dst[outBase + o + 3] = s3
                dst[outBase + o + 4] = s4; dst[outBase + o + 5] = s5
                dst[outBase + o + 6] = s6; dst[outBase + o + 7] = s7
                o += 8
            }
            while (o < cOut) {
                var s = bd[o]
                val wBase = o * cIn
                for (c in 0 until cIn) s += wd[wBase + c] * src[inBase + c]
                dst[outBase + o] = s
                o++
            }
        }
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    /** log(1 + e^x), guarded so large |x| cannot overflow. */
    private fun softplus(x: Float): Float =
        if (x > 20f) x else if (x < -20f) exp(x) else ln(1f + exp(x))

    companion object {
        const val T_IN = 64
        const val T_OUT = 32
        const val ALPHABET = 26
        const val N_DCT = 8
        const val N_COEFF = N_DCT * N_DCT

        private const val F_IN = 8
        private const val DIM = 128
        private const val EXPAND = 512
        private const val HEAD_DIM = 256
        private const val SE_HIDDEN = 32
        private const val DW_KERNEL = 7
        private const val SG_WINDOW = 7
        private val DILATIONS = intArrayOf(1, 2, 3, 5, 8)

        // Threading the time axis was tried and reverted: on a big.LITTLE phone
        // the extra chunks land on efficiency cores and the join waits for the
        // slowest, which measured 1.5-5x *slower* than staying on one big core.

        private const val PI_F = Math.PI.toFloat()
        private const val TWO_PI = 2f * PI_F

        /**
         * Separable cosine basis for a layout: row k holds
         * cos(pi*u*x_k) * cos(pi*v*y_k) for u,v in 0 until N_DCT.
         *
         * Depends only on where the keys are, so it is rebuilt when the keyboard
         * is resized and reused for every gesture in between.
         *
         * @param keyX per-key centre x, normalized to [0,1] across the keyboard.
         * @param keyY per-key centre y, likewise.
         */
        fun basisFor(keyX: FloatArray, keyY: FloatArray): FloatArray {
            val n = keyX.size
            val out = FloatArray(n * N_COEFF)
            val cu = FloatArray(N_DCT)
            val cv = FloatArray(N_DCT)
            for (k in 0 until n) {
                for (u in 0 until N_DCT) {
                    cu[u] = cos(PI_F * u * keyX[k])
                    cv[u] = cos(PI_F * u * keyY[k])
                }
                val base = k * N_COEFF
                for (u in 0 until N_DCT) {
                    val a = cu[u]
                    for (v in 0 until N_DCT) out[base + u * N_DCT + v] = a * cv[v]
                }
            }
            return out
        }

        /** Reads the exported weight file. */
        fun load(stream: InputStream): SwipeNet {
            val input = DataInputStream(stream.buffered(1 shl 16))
            val magic = ByteArray(4).also { input.readFully(it) }
            require(String(magic) == "SWEN") { "not a swipe encoder file" }
            require(readLE(input) == 1) { "unsupported encoder version" }
            val count = readLE(input)

            val map = HashMap<String, Tensor>(count * 2)
            repeat(count) {
                val name = String(ByteArray(readLE(input)).also { input.readFully(it) })
                val dims = IntArray(readLE(input)) { readLE(input) }
                var n = 1
                for (d in dims) n *= d
                val bytes = ByteArray(n * 4)
                input.readFully(bytes)
                val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val data = FloatArray(n)
                fb.get(data)
                map[name] = Tensor(dims, data)
            }
            return SwipeNet(map)
        }

        private fun readLE(input: DataInputStream): Int {
            val b = ByteArray(4).also { input.readFully(it) }
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
        }
    }
}
