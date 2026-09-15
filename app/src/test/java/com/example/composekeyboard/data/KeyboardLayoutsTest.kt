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

        assertTrue("Numpad should contain '.'", characters.contains("."))
        assertTrue("Numpad should contain ','", characters.contains(","))
        assertTrue("Numpad should contain '-'", characters.contains("-"))

        val allPopups = allNumpadKeys.flatMap { (it.type as? KeyType.Character)?.popup ?: emptyList() }
        assertTrue("Numpad popups should contain '+'", allPopups.contains("+"))
        assertTrue("Numpad popups should contain '*'", allPopups.contains("*"))
        assertTrue("Numpad popups should contain '#'", allPopups.contains("#"))
        assertTrue("Numpad popups should contain '/'", allPopups.contains("/"))
        assertTrue("Numpad popups should contain '%'", allPopups.contains("%"))
        assertTrue("Numpad popups should contain '='", allPopups.contains("="))
        assertTrue("Numpad popups should contain '('", allPopups.contains("("))
        assertTrue("Numpad popups should contain ')'", allPopups.contains(")"))

        // Must contain essential action keys
        assertTrue("Numpad should contain Backspace", allNumpadKeys.any { it.type is KeyType.Backspace })
        assertTrue("Numpad should contain Enter", allNumpadKeys.any { it.type is KeyType.Enter })
        assertTrue("Numpad should contain AlphabetToggle", allNumpadKeys.any { it.type is KeyType.AlphabetToggle })
    }

    @Test
    fun numpad_rowsHaveValidWeightsAndCounts() {
        assertEquals(4, KeyboardLayouts.numpadRow1.size)
        assertEquals(4, KeyboardLayouts.numpadRow2.size)
        assertEquals(4, KeyboardLayouts.numpadRow3.size)
        assertEquals(4, KeyboardLayouts.numpadBottomRow.size)

        val zero = KeyboardLayouts.numpadBottomRow[1].type as KeyType.Character
        val eight = KeyboardLayouts.numpadRow3[1].type as KeyType.Character
        assertEquals("0 should sit under 8", "0", zero.primary)
        assertEquals("8", eight.primary)

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
        val modes = KeyboardMode.entries.map { it.name }
        assertTrue("KeyboardMode must contain NUMPAD", modes.contains("NUMPAD"))
        assertTrue("KeyboardMode must contain VOICE", modes.contains("VOICE"))
    }

    @Test
    fun qwertyRow1_containsNumbersOnlyWhenNumberRowIsHidden() {
        val expectedNumbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val popupsWithNumbers = KeyboardLayouts.getQwertyRow1(showNumberRow = false).mapNotNull {
            (it.type as? KeyType.Character)?.popup?.firstOrNull()
        }
        assertEquals(expectedNumbers, popupsWithNumbers)

        // When number row is visible, top row only has accents and no redundant numbers
        val popupsWhenNumberRowVisible = KeyboardLayouts.getQwertyRow1(showNumberRow = true).mapNotNull {
            (it.type as? KeyType.Character)?.popup?.firstOrNull()
        }
        assertTrue(popupsWhenNumberRowVisible.none { it in "0".."9" })
    }

    @Test
    fun qwertyBottomRow_periodAndCommaContainPunctuationPopups() {
        val periodKey = KeyboardLayouts.qwertyBottomRow.firstNotNullOf {
            (it.type as? KeyType.Character)?.takeIf { char -> char.primary == "." }
        }
        assertTrue("Period key should have multiple punctuation popups", periodKey.popup.isNotEmpty())
        assertTrue("Period key popup should contain '!'", periodKey.popup.contains("!"))
        assertTrue("Period key popup should contain '?'", periodKey.popup.contains("?"))

        val commaKey = KeyboardLayouts.qwertyBottomRow.firstNotNullOf {
            (it.type as? KeyType.Character)?.takeIf { char -> char.primary == "," }
        }
        assertTrue("Comma key should have punctuation popups", commaKey.popup.isNotEmpty())
    }

    @Test
    fun keyboardThemeType_entriesContainAllThemes() {
        val themes = KeyboardThemeType.entries
        assertTrue(themes.contains(KeyboardThemeType.MATERIAL_DARK))
        assertTrue(themes.contains(KeyboardThemeType.MATERIAL_LIGHT))
        assertTrue(themes.contains(KeyboardThemeType.AMOLED))
        assertTrue(themes.contains(KeyboardThemeType.CUSTOM))
    }

    @Test
    fun initialMode_resolvesCorrectlyPerSession() {
        // Fresh numeric field -> NUMPAD
        assertEquals(KeyboardMode.NUMPAD, Capitalization.initialMode(true, true))
        // Empty / sentence-start prose field -> UPPERCASE
        assertEquals(KeyboardMode.UPPERCASE, Capitalization.initialMode(false, true))
        // Mid-sentence after dismiss, or autocaps off -> LOWERCASE
        assertEquals(KeyboardMode.LOWERCASE, Capitalization.initialMode(false, false))
        // Reopened field -> never defaults to EMOJI or CLIPBOARD
        assertTrue(Capitalization.initialMode(false, false) != KeyboardMode.EMOJI)
    }

    @Test
    fun emailAndUriBottomRows_exposeAddressKeys() {
        val emailKeys = KeyboardLayouts.emailBottomRow.mapNotNull { (it.type as? KeyType.Character)?.primary }
        assertTrue(emailKeys.contains("@"))
        assertTrue(emailKeys.contains("."))
        val emailDot = KeyboardLayouts.emailBottomRow.firstNotNullOf {
            (it.type as? KeyType.Character)?.takeIf { char -> char.primary == "." }
        }
        assertTrue(emailDot.popup.contains(".com"))

        val uriKeys = KeyboardLayouts.uriBottomRow.mapNotNull { (it.type as? KeyType.Character)?.primary }
        assertTrue(uriKeys.contains("/"))
        assertTrue(uriKeys.contains("."))
        assertEquals(KeyboardLayouts.emailBottomRow, KeyboardLayouts.qwertyBottomRowFor(FieldInputKind.EMAIL))
        assertEquals(KeyboardLayouts.uriBottomRow, KeyboardLayouts.qwertyBottomRowFor(FieldInputKind.URI))
        assertEquals(KeyboardLayouts.qwertyBottomRow, KeyboardLayouts.qwertyBottomRowFor(FieldInputKind.TEXT))
    }

    @Test
    fun emojiSearchBottomRow_exposesAbcToLeaveSearch() {
        assertTrue(KeyboardLayouts.emojiSearchBottomRow.any { it.type is KeyType.AlphabetToggle })
        assertTrue(KeyboardLayouts.emojiSearchBottomRow.any { it.type is KeyType.EmojiToggle })
    }

    @Test
    fun phonePad_exposesDialerKeys() {
        val rows = KeyboardLayouts.numpadRowsFor(FieldInputKind.PHONE)
        val characters = rows.flatten().mapNotNull { (it.type as? KeyType.Character)?.primary }
        for (digit in 0..9) {
            assertTrue("Phone pad should contain $digit", characters.contains(digit.toString()))
        }
        assertTrue(characters.contains("*"))
        assertTrue(characters.contains("#"))
        assertTrue(characters.contains("+"))
        assertTrue(rows.flatten().any { it.type is KeyType.Backspace })
        assertTrue(rows.flatten().any { it.type is KeyType.Enter })
    }

    @Test
    fun datetimePad_exposesDateTimeSeparators() {
        val characters = KeyboardLayouts.numpadRowsFor(FieldInputKind.DATETIME)
            .flatten()
            .mapNotNull { (it.type as? KeyType.Character)?.primary }
        assertTrue(characters.contains("/"))
        assertTrue(characters.contains(":"))
        for (digit in 0..9) {
            assertTrue(characters.contains(digit.toString()))
        }
    }
}
