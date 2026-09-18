package io.github.xxparthparekhxx.composekeyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Minimal on-device smoke test: the companion app launches and shows its home
 * destination. Exists so the 5,000+ lines of Compose UI are testable *in
 * principle* — unit tests cannot touch them. Runs on an emulator
 * (see the `instrumented` CI job); avoids voice/native paths, which need
 * arm64 hardware.
 */
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeTitle_isDisplayed() {
        composeRule.onNodeWithText("Compose Keyboard").assertIsDisplayed()
    }
}
