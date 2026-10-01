package io.github.xxparthparekhxx.composekeyboard.data

/**
 * Bounds of the inline autofill chip (API 30+), in dp.
 *
 * The IME builds its `android.widget.inline.InlinePresentationSpec` from these
 * values, and the `InlineSuggestionChip` composable must render within them —
 * `InlineSuggestion.inflate()` rejects any size outside the spec, so the two
 * sides must stay in lockstep.
 */
object InlineChipSpec {

    const val HEIGHT_DP = 40
    const val MIN_WIDTH_DP = 48
    const val MAX_WIDTH_DP = 240
}
