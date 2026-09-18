package io.github.xxparthparekhxx.composekeyboard.ui.app

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** The settings string is colon-delimited "id/subtype" entries; match whole components. */
private fun settingContainsIme(setting: String, packageName: String): Boolean =
    setting.split(':').any { entry -> entry.substringBefore('/') == packageName }

fun checkIsImeEnabled(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: return false
        val packageName = context.packageName
        enabledList.any { it.packageName == packageName }
    } catch (e: Exception) {
        false
    }
}

fun checkIsImeSelected(context: Context): Boolean {
    return try {
        val currentIme = Settings.Secure.getString(
            context.contentResolver,
            "default_input_method"
        )
        if (!currentIme.isNullOrEmpty()) {
            settingContainsIme(currentIme, context.packageName)
        } else {
            false
        }
    } catch (e: Throwable) {
        false
    }
}
