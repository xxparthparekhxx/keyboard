package io.github.xxparthparekhxx.composekeyboard.input.voice

import android.os.Build

/**
 * The whisper.cpp AAR we ship only contains `arm64-v8a` native code.
 * Referencing the Whisper class on other ABIs can load the missing JNI
 * library, so callers must check this *before* touching [WhisperCppEngine].
 */
object WhisperAbi {
    fun isSupported(): Boolean = Build.SUPPORTED_ABIS.contains("arm64-v8a")
}
