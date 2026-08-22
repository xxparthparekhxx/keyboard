package com.example.composekeyboard.input.swipe

/**
 * Centralized constants for the swipe typing engine.
 *
 * All tolerances are expressed in "key widths" (the average letter key width)
 * so they scale automatically with keyboard size, screen density, and the
 * user's height multiplier setting.
 *
 * Weights control how much each signal contributes to the final score. They
 * were tuned on a development corpus; changing them requires re-evaluation.
 */
object SwipeConstants {

    // --- Gating -------------------------------------------------------------

    /** Minimum gesture length (in key widths) to attempt decoding. Shorter is a tap. */
    const val MIN_TRACE_KEY_WIDTHS = 0.7f

    /** How far from the touch-down point a word's first letter may sit. */
    const val START_RADIUS = 1.25f

    /** Lift-off is usually the sloppiest part of a gesture, so allow more room. */
    const val END_RADIUS = 1.40f

    /** Max starting-key candidates considered (strict pass). */
    const val MAX_ENDPOINT_CANDIDATES = 4

    /** Relaxed-pass multiplier for all distance tolerances. */
    const val RELAXED_SCALE = 1.55f

    /** Max starting-key candidates in relaxed pass. */
    const val RELAXED_ENDPOINT_CANDIDATES = 6

    /** Corridor half-width every interior letter must fall inside. */
    const val CORRIDOR = 1.15f

    /** A corner has to be explained by a letter this close to it. */
    const val PIVOT_RADIUS = 1.25f

    /** Letters within this of the path cost nothing; only real drift is charged. */
    const val TUNNEL = 0.42f

    // --- Weights ------------------------------------------------------------

    /** Shape similarity weight (silhouette match). */
    const val W_SHAPE = 1.90f

    /** Location drift weight (corridor walk distance). */
    const val W_LOCATION = 1.50f

    /** Length plausibility weight (ideal vs actual path length). */
    const val W_LENGTH = 0.35f

    /** Lexicon prior weight (log frequency + user boost). */
    const val W_FREQUENCY = 1.35f

    /** Charged per rank when the word's first letter is not the nearest key. */
    const val START_RANK_PENALTY = 0.22f

    /** Charged per rank when the word's last letter is not the nearest key. */
    const val END_RANK_PENALTY = 0.20f

    // --- Pivot Detection ----------------------------------------------------

    /** Turn sharper than this counts as a deliberate corner (radians ≈ 62°). */
    const val PIVOT_ANGLE = 1.08f

    /** Maximum pivots tracked per gesture. */
    const val MAX_PIVOTS = 8

    // --- Resampling & Decoding ----------------------------------------------

    /** Points the gesture and each candidate's ideal path are resampled to. */
    const val SAMPLES = 32

    /** Max suggestions returned by the decoder. */
    const val MAX_SUGGESTIONS = 4

    /** Preview decode throttle (ms) — ~18 Hz max. */
    const val PREVIEW_INTERVAL_MS = 55L

    // --- Neural Decoder -----------------------------------------------------

    /** Beam width for CTC prefix search. */
    const val BEAM_WIDTH = 50

    /** Max frontier size during beam search. */
    const val MAX_FRONTIER = 8192

    /** Pruning gamma (length normalization exponent). */
    const val GAMMA_PRUNE = 0.2582f

    /** Pruning beta (length bias). */
    const val BETA_PRUNE = 0.9722f

    /** Scoring gamma (length normalization exponent). */
    const val GAMMA_SCORE = 0.3499f

    /** Scoring lambda (frequency weight). */
    const val LAMBDA_FREQ = 0.0351f

    /** Scoring beta (length bonus). */
    const val BETA_LEN = 0.6065f

    /** Resampling target Hz for uniform-time grid. */
    const val RESAMPLE_HZ = 60f

    /** Input timesteps to encoder. */
    const val T_IN = 64

    /** Output timesteps from encoder (after 2x adapter). */
    const val T_OUT = 32

    // --- Trail Rendering ----------------------------------------------------

    /** How much of the recent path stays visible behind the finger (ms). */
    const val TRAIL_DURATION_MS = 320L

    /** Hard cap on trail segments drawn per frame. */
    const val MAX_TRAIL_SEGMENTS = 96

    // --- Dictionary ---------------------------------------------------------

    /** Learning step added per user confirmation. */
    const val LEARN_STEP = 6

    /** Max boost a learned word can accumulate. */
    const val MAX_BOOST = 60

    /** Max learned words retained. */
    const val MAX_LEARNED_WORDS = 5000

    /** Sightings needed before unknown word becomes gesture-reachable. */
    const val NEW_WORD_THRESHOLD = LEARN_STEP * 2

    /** Base score for user-added words. */
    const val USER_BASE_SCORE = 120

    /** Max score value (8-bit). */
    const val MAX_SCORE = 255

    // --- Geometry -----------------------------------------------------------

    /** Minimum letter keys placed before geometry is considered ready. */
    const val MIN_PLACED_KEYS = 20

    /** Hit slop for letterAt() in pixels. */
    const val HIT_SLOP_PX = 3f

    /** Swipe recognition threshold multiplier. */
    const val SWIPE_THRESHOLD_MULTIPLIER = 1.2f

    /** Minimum swipe threshold as fraction of key width. */
    const val SWIPE_MIN_KEY_WIDTH_FRACTION = 0.55f

    /** Spacebar cursor drag threshold (px). */
    const val DRAG_THRESHOLD_PX = 35f

    /** Initial capacity for the raw trace buffer. */
    const val INITIAL_CAPACITY = 256

    /** Below this the sample adds nothing but noise and buffer pressure (px). */
    const val MIN_POINT_SPACING_PX = 1.5f

    // --- Persistence --------------------------------------------------------

    /** Debounce delay for learned word saves (ms). */
    const val SAVE_DEBOUNCE_MS = 4000L
}