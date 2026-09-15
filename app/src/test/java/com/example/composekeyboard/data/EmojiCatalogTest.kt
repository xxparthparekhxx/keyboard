package com.example.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {

    private val sample = """
        # group: Smileys & Emotion
        1F600                                                  ; fully-qualified     # 😀 E1.0 grinning face
        1F602                                                  ; fully-qualified     # 😂 E0.6 face with tears of joy
        263A                                                   ; unqualified         # ☺ E0.6 smiling face
        # group: People & Body
        1F44B                                                  ; fully-qualified     # 👋 E0.6 waving hand
        1F44B 1F3FB                                            ; fully-qualified     # 👋🏻 E1.0 waving hand: light skin tone
        1F469 200D 1F4BB                                       ; fully-qualified     # 👩‍💻 E4.0 woman technologist
        # group: Component
        1F3FB                                                  ; component           # 🏻 E1.0 light skin tone
        # group: Flags
        1F1FA 1F1F8                                            ; fully-qualified     # 🇺🇸 E0.6 flag: United States
    """.trimIndent()

    @Test
    fun parse_keepsFullyQualifiedAndDropsSkinTonesUnqualifiedAndComponents() {
        val entries = EmojiCatalog.parse(sample)
        val emojis = entries.map { it.emoji }

        assertTrue(emojis.contains("😀"))
        assertTrue(emojis.contains("😂"))
        assertTrue(emojis.contains("👋"))
        assertTrue(emojis.contains("👩‍💻"))
        assertTrue(emojis.contains("🇺🇸"))
        assertFalse(emojis.contains("☺"))
        assertFalse(emojis.any { it.contains("🏻") })
        assertEquals(5, entries.size)
    }

    @Test
    fun categoriesFrom_preserveUnicodeGroupOrder() {
        val cats = EmojiCatalog.categoriesFrom(EmojiCatalog.parse(sample))
        assertEquals(listOf("Smileys & Emotion", "People & Body", "Flags"), cats.map { it.name })
        assertTrue(cats.first { it.name == "People & Body" }.emojis.contains("👩‍💻"))
    }

    @Test
    fun search_matchesUnicodeNamesAndAliases() {
        val snapshot = EmojiCatalog.Snapshot(
            entries = EmojiCatalog.parse(sample),
            categories = EmojiCatalog.categoriesFrom(EmojiCatalog.parse(sample))
        )
        val grin = EmojiCatalog.search(snapshot, "grinn")
        assertTrue(grin.contains("😀"))

        val tech = EmojiCatalog.search(snapshot, "technologist")
        assertTrue(tech.contains("👩‍💻"))

        val fire = EmojiCatalog.search(snapshot, "fire")
        assertTrue(fire.contains("🔥"))
    }

    @Test
    fun unicodeAsset_coversAFullKeyboardSetWithoutSkinTones() {
        val asset = java.io.File("src/main/assets/emoji-test.txt")
        assertTrue("emoji-test.txt should be shipped as an asset", asset.exists())
        val entries = EmojiCatalog.parse(asset.readText())
        assertTrue("expected a full Unicode set, got ${entries.size}", entries.size > 1500)
        assertTrue(entries.any { it.emoji == "😀" })
        assertTrue(entries.any { it.emoji == "👩‍💻" })
        assertTrue(entries.any { it.emoji == "🇺🇸" })
        assertTrue(entries.any { it.group == "Animals & Nature" })
        assertFalse(entries.any { it.emoji.contains("🏻") })
    }
}
