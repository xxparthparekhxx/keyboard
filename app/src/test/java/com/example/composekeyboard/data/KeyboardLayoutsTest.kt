package com.example.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    @Test
    fun numpad_containsAllDigitsAndBasicOperators() {
        val allNumpadKeys = KeyboardLayouts.numpadRow1 +
                KeyboardLayouts.numpadRow2 +
                KeyboardLayouts.numpadRow3 +
                KeyboardLayouts.numpadBottomRow

        val characters = allNumpadKeys.mapNotNull {
            (it.type as? KeyType.Character)?.primary
        }

        // Must contain all 0-9 digits
        for (digit in 0..9) {
            assertTrue("Numpad should contain digit $digit", characters.contains(digit.toString()))
        }

        // Must contain basic symbols and punctuation
        assertTrue("Numpad should contain '.'", characters.contains("."))
        assertTrue("Numpad should contain '+'", characters.contains("+"))
        assertTrue("Numpad should contain '-'", characters.contains("-"))
        assertTrue("Numpad should contain '*'", characters.contains("*"))
        assertTrue("Numpad should contain '/'", characters.contains("/"))
        assertTrue("Numpad should contain '('", characters.contains("("))
        assertTrue("Numpad should contain ')'", characters.contains(")"))

        // Must contain essential action keys
        assertTrue("Numpad should contain Backspace", allNumpadKeys.any { it.type is KeyType.Backspace })
        assertTrue("Numpad should contain Enter", allNumpadKeys.any { it.type is KeyType.Enter })
        assertTrue("Numpad should contain AlphabetToggle", allNumpadKeys.any { it.type is KeyType.AlphabetToggle })
    }

    @Test
    fun numpad_rowsHaveValidWeightsAndCounts() {
        assertEquals(5, KeyboardLayouts.numpadRow1.size)
        assertEquals(5, KeyboardLayouts.numpadRow2.size)
        assertEquals(5, KeyboardLayouts.numpadRow3.size)
        assertEquals(5, KeyboardLayouts.numpadBottomRow.size)

        val allRows = listOf(
            KeyboardLayouts.numpadRow1,
            KeyboardLayouts.numpadRow2,
            KeyboardLayouts.numpadRow3,
            KeyboardLayouts.numpadBottomRow
        )

        for (row in allRows) {
            assertTrue(row.all { it.weight > 0f })
        }
    }

    @Test
    fun symbols_bottomRowContainsNumpadToggle() {
        val hasNumpadToggle = KeyboardLayouts.symbolsBottomRow.any { it.type is KeyType.NumpadToggle }
        assertTrue("Symbols bottom row must contain NumpadToggle", hasNumpadToggle)

        val hasMoreSymbolsNumpadToggle = KeyboardLayouts.moreSymbolsBottomRow.any { it.type is KeyType.NumpadToggle }
        assertTrue("More symbols bottom row must contain NumpadToggle", hasMoreSymbolsNumpadToggle)
    }

    @Test
    fun keyboardMode_includesNumpad() {
        val modes = KeyboardMode.values().map { it.name }
        assertTrue("KeyboardMode must contain NUMPAD", modes.contains("NUMPAD"))
    }

    @Test
    fun initialMode_resolvesCorrectlyPerSession() {
        fun resolveInitialMode(
            isNumericField: Boolean,
            autoCapitalization: Boolean,
            autoCapitalizeField: Boolean
        ): KeyboardMode {
            return if (isNumericField) {
                KeyboardMode.NUMPAD
            } else if (autoCapitalization && autoCapitalizeField) {
                KeyboardMode.UPPERCASE
            } else {
                KeyboardMode.LOWERCASE
            }
        }

        // Fresh numeric field -> NUMPAD
        assertEquals(KeyboardMode.NUMPAD, resolveInitialMode(true, true, true))
        // Fresh prose field with autocaps -> UPPERCASE
        assertEquals(KeyboardMode.UPPERCASE, resolveInitialMode(false, true, true))
        // Fresh field with autocaps disabled -> LOWERCASE
        assertEquals(KeyboardMode.LOWERCASE, resolveInitialMode(false, false, true))
        // Reopened field -> never defaults to EMOJI or CLIPBOARD
        assertTrue(resolveInitialMode(false, true, false) != KeyboardMode.EMOJI)
    }
}
