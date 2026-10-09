package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Robolectric for android.util.Log, which the broken-table path logs through.
@RunWith(RobolectricTestRunner::class)
class NextWordPredictorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun predictor(file: File = File(tmp.root, "pairs.txt")) =
        NextWordPredictor(file).also { it.load() }

    @Test
    fun predict_ranksFollowersByHowOftenTheyFollowed() {
        val p = predictor()
        repeat(3) { p.learn("good", "morning") }
        repeat(2) { p.learn("good", "night") }
        p.learn("good", "luck")

        assertEquals(listOf("morning", "night", "luck"), p.predict("good"))
        assertEquals(listOf("morning"), p.predict("good", maxCount = 1))
    }

    @Test
    fun predict_isCaseInsensitiveAndUnknownWordsPredictNothing() {
        val p = predictor()
        p.learn("I", "am")
        assertEquals(listOf("am"), p.predict("i"))
        assertEquals(listOf("am"), p.predict("I"))
        assertTrue(p.predict("unknown").isEmpty())
    }

    @Test
    fun learn_ignoresNonWords() {
        val p = predictor()
        p.learn("hello", "123")
        p.learn("😀", "hello")
        p.learn("hello", "")
        assertTrue(p.predict("hello").isEmpty())
        assertTrue(p.predict("😀").isEmpty())
    }

    @Test
    fun unlearn_takesBackOneSighting() {
        val p = predictor()
        repeat(2) { p.learn("see", "you") }
        p.unlearn("see", "you")
        assertEquals(listOf("you"), p.predict("see"))
        p.unlearn("see", "you")
        assertTrue(p.predict("see").isEmpty())
    }

    @Test
    fun followersPerWord_areCappedKeepingTheStrongestAndTheNewest() {
        val p = predictor()
        repeat(NextWordPredictor.MAX_FOLLOWERS) { i ->
            repeat(2) { p.learn("the", "word" + ('a' + i)) }
        }
        p.learn("the", "newcomer")

        val all = p.predict("the", maxCount = 100)
        assertEquals(NextWordPredictor.MAX_FOLLOWERS, all.size)
        assertTrue("newest pair must survive the cap", "newcomer" in all)
    }

    @Test
    fun persist_roundTripsThroughTheFile() {
        val file = File(tmp.root, "pairs.txt")
        val first = predictor(file)
        repeat(2) { first.learn("thank", "you") }
        first.learn("thank", "goodness")
        first.persist()

        val second = predictor(file)
        assertEquals(listOf("you", "goodness"), second.predict("thank"))
    }

    @Test
    fun persist_neverWritesBeforeTheFileWasLoaded() {
        val file = File(tmp.root, "pairs.txt")
        file.writeText("keep\tme\t5\n")
        val p = NextWordPredictor(file)
        p.learn("new", "pair")
        p.persist()
        assertEquals("keep\tme\t5\n", file.readText())

        p.load()
        assertEquals(listOf("me"), p.predict("keep"))
        assertEquals(listOf("pair"), p.predict("new"))
    }

    @Test
    fun normalize_acceptsOneLetterWordsAndRejectsSymbols() {
        assertEquals("i", NextWordPredictor.normalize("I"))
        assertEquals("don't", NextWordPredictor.normalize("Don't"))
        assertNull(NextWordPredictor.normalize("42"))
        assertNull(NextWordPredictor.normalize("a.b"))
        assertNull(NextWordPredictor.normalize(""))
    }

    // --- shipped table ------------------------------------------------------

    private val seedText = """
        # comment lines are skipped
        good	morning,120	luck,90	night,60
        new	York,89	year,11
        and	I,15	the,54
    """.trimIndent()

    private fun seeded(file: File = File(tmp.root, "pairs.txt")) =
        NextWordPredictor(file, openSeed = { seedText.byteInputStream() }).also { it.load() }

    @Test
    fun seed_predictsOutOfTheBoxInItsOwnOrderAndCasing() {
        val p = seeded()
        assertEquals(listOf("morning", "luck", "night"), p.predict("good"))
        assertEquals(listOf("York", "year"), p.predict("New"))
        assertEquals(listOf("the", "I"), p.predict("and"))
    }

    @Test
    fun learnedPairs_outrankTheSeedAndMergeWithIt() {
        val p = seeded()
        p.learn("good", "night")
        assertEquals(listOf("night", "morning", "luck"), p.predict("good"))

        p.learn("good", "game")
        assertEquals("game", p.predict("good", maxCount = 5)[1])
    }

    @Test
    fun learnedWords_takeTheSeedsCasing() {
        val p = seeded()
        p.learn("i", "think")
        p.learn("so", "i")
        assertEquals(listOf("I"), p.predict("so"))
    }

    @Test
    fun persist_writesOnlyLearnedPairsNotTheSeed() {
        val file = File(tmp.root, "pairs.txt")
        val p = seeded(file)
        p.learn("good", "game")
        p.persist()
        assertEquals("good\tgame\t1\n", file.readText())
    }

    @Test
    fun brokenSeed_onlyCostsTheHeadStart() {
        val p = NextWordPredictor(File(tmp.root, "pairs.txt"), openSeed = { error("no asset") })
        p.load()
        p.learn("good", "game")
        assertEquals(listOf("game"), p.predict("good"))
    }

    @Test
    fun shippedTable_parsesAndCoversCommonWords() {
        val asset = listOf(
            File("src/main/assets/next_words.txt"),
            File("app/src/main/assets/next_words.txt"),
            File("../app/src/main/assets/next_words.txt")
        ).firstOrNull { it.exists() }
        assertTrue("next_words.txt not found", asset != null)
        val p = NextWordPredictor(File(tmp.root, "pairs.txt"), openSeed = { asset!!.inputStream() })
        p.load()
        for (word in listOf("the", "i", "thank", "how", "want", "good")) {
            assertTrue("no prediction after \"$word\"", p.predict(word).isNotEmpty())
        }
        assertEquals("you", p.predict("thank").first())
    }
}
