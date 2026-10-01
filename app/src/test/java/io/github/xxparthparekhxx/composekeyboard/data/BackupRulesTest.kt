package io.github.xxparthparekhxx.composekeyboard.data

import android.content.Context
import android.content.ContextWrapper
import io.github.xxparthparekhxx.composekeyboard.input.voice.WhisperModelStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory

/**
 * Cross-checks the backup exclusion XMLs against the storage paths the code
 * actually persists, so renaming a filename in code can never silently stop
 * excluding the clipboard history (which may hold copied secrets), the learned
 * words or the ~77 MB Whisper model from backups.
 *
 * The expected values are derived from the code, not copied from the XML:
 * [WhisperModelStore.FILE_NAME] and [WhisperModelStore.MODELS_DIR] are const
 * vals and are referenced directly; [ClipboardHistoryManager]'s history file
 * name is read off a live instance's private `historyFile` field;
 * [SwipeDictionary]'s `USER_FILE` is its private const companion field. The
 * `.tmp` variants are constructed the same way
 * [ClipboardHistoryManager.writeAtomically] and [WhisperModelStore.download]
 * construct them.
 *
 * The XMLs are parsed from the source tree — the exact files aapt2 packages
 * into the APK — because in this build setup the packaged resource table is
 * not exposed to JVM unit tests (the Robolectric runtime table contains no
 * app package at all). Plain JUnit is used for the same reason.
 */
class BackupRulesTest {

    /** File name [ClipboardHistoryManager] persists to (its private `historyFile`). */
    private val clipboardFileName: String by lazy {
        val manager = ClipboardHistoryManager(DummyContext())
        val field = ClipboardHistoryManager::class.java.getDeclaredField("historyFile")
        field.isAccessible = true
        (field.get(manager) as File).name
    }

    /** File name [SwipeDictionary] persists to (its private const `USER_FILE`). */
    private val userWordsFileName: String by lazy {
        val field = SwipeDictionary::class.java.getDeclaredField("USER_FILE")
        field.isAccessible = true
        field.get(null) as String
    }

    /**
     * Paths the code persists, as the XMLs must exclude them: the two
     * persisted files plus their [ClipboardHistoryManager.writeAtomically]
     * temp files, and the models/ directory that subsumes the Whisper model,
     * its `.tmp` and its `.sha256` marker.
     */
    private val expectedExcludedPaths: Set<String>
        get() = setOf(
            clipboardFileName,
            clipboardFileName + ".tmp",
            userWordsFileName,
            userWordsFileName + ".tmp",
            WhisperModelStore.MODELS_DIR + "/",
        )

    /** WhisperModelStore writes that the models/ directory rule must cover. */
    private val modelStoreWrites: List<String>
        get() = listOf(
            WhisperModelStore.MODELS_DIR + "/" + WhisperModelStore.FILE_NAME,
            // download() writes "$FILE_NAME.tmp" next to the target, then renames.
            WhisperModelStore.MODELS_DIR + "/" + WhisperModelStore.FILE_NAME + ".tmp",
            // verifiedMarker() writes "$FILE_NAME.sha256" next to the model.
            WhisperModelStore.MODELS_DIR + "/" + WhisperModelStore.FILE_NAME + ".sha256",
        )

    /** Every path a rule may legitimately name: the expected ones plus the model files. */
    private val allowedExcludedPaths: Set<String>
        get() = expectedExcludedPaths + modelStoreWrites

    @Test
    fun dataExtractionRules_excludeExactlyThePathsTheCodePersists() {
        val rules = excludeRules(sourceRes("data_extraction_rules.xml"))
        checkSection(rules, "cloud-backup")
        checkSection(rules, "device-transfer")
    }

    @Test
    fun backupRules_excludeExactlyThePathsTheCodePersists() {
        val rules = excludeRules(sourceRes("backup_rules.xml"))
        checkSection(rules, "full-backup-content")
    }

    @Test
    fun clipboardHistory_excludedInAllThreeSchemes() {
        // Per the data_extraction_rules.xml comment, the clipboard JSON may hold
        // copied passwords/OTPs: it must be excluded from every scheme.
        val modern = excludeRules(sourceRes("data_extraction_rules.xml"))
        val legacy = excludeRules(sourceRes("backup_rules.xml"))
        val all = modern + legacy
        for (section in listOf("cloud-backup", "device-transfer", "full-backup-content")) {
            val excludes = all[section] ?: emptySet()
            assertTrue(
                "clipboard history missing from <$section>",
                excludes.contains("file" to clipboardFileName)
            )
        }
    }

    @Test
    fun modelsDirectoryExclusion_coversEveryFileTheModelStoreWrites() {
        val modern = excludeRules(sourceRes("data_extraction_rules.xml"))
        val legacy = excludeRules(sourceRes("backup_rules.xml"))
        val paths = (modern.values + legacy.values).flatten().map { it.second }.toSet()
        for (write in modelStoreWrites) {
            val covered = paths.any { write == it || (it.endsWith("/") && write.startsWith(it)) }
            assertTrue("$write is not covered by any exclude rule", covered)
        }
    }

    /**
     * Asserts that [section] excludes every path the code persists and nothing
     * the code never writes, all with domain="file" (the app's files dir).
     */
    private fun checkSection(rules: Map<String, Set<Pair<String, String>>>, section: String) {
        val excludes: Set<Pair<String, String>> =
            rules[section] ?: throw AssertionError("$section section not found in the backup XML")
        assertTrue(
            "all rules in <$section> must use domain=\"file\" (the app's files dir)",
            excludes.all { it.first == "file" }
        )
        val paths = excludes.map { it.second }.toSet()
        val missing = expectedExcludedPaths - paths
        assertTrue("not excluded: $missing", missing.isEmpty())
        val unknown = paths - allowedExcludedPaths
        assertTrue("excluded but the code never writes them: $unknown", unknown.isEmpty())
    }

    /**
     * Parses a backup XML from the source tree and collects the `<exclude>`
     * rules (domain, path) per section tag (`cloud-backup`, `device-transfer`,
     * `full-backup-content`).
     */
    private fun excludeRules(file: File): Map<String, Set<Pair<String, String>>> {
        val result = HashMap<String, MutableSet<Pair<String, String>>>()
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newSAXParser()
        val handler = object : DefaultHandler() {
            private var section: String? = null

            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                if (qName == "cloud-backup" || qName == "device-transfer" || qName == "full-backup-content") {
                    section = qName
                    result.getOrPut(qName) { mutableSetOf() }
                } else if (qName == "exclude") {
                    val s = section ?: throw AssertionError("<exclude> outside a section")
                    val domain = attrs.getValue("domain")
                    val path = attrs.getValue("path")
                    require(domain != null) { "<exclude> in <$s> has no domain attribute" }
                    require(path != null) { "<exclude> in <$s> has no path attribute" }
                    result[s]!!.add(domain to path)
                }
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                if (qName == section) section = null
            }
        }
        parser.parse(file, handler)
        return result
    }

    /**
     * The backup XML under res/xml, resolved relative to the module dir the
     * unit tests run in (with fallbacks, as SwipeNeuralDecoderTest does for
     * assets).
     */
    private fun sourceRes(name: String): File {
        val candidates = listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
            File("../app/src/main/res/xml/$name"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("$name not found; working dir ${File(".").absolutePath}")
    }

    /** Minimal context for [ClipboardHistoryManager]; no services, temp files dir. */
    private class DummyContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        override fun getSystemService(name: String): Any? = null
    }
}
