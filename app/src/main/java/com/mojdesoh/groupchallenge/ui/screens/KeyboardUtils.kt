package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Explicitly requests the soft keyboard when this field gains focus, instead of relying on the
 * OS to trigger it automatically — focus-triggered auto-show has been unreliable in testing
 * (a focused field with no visible way to type into it), so fields ask for it directly.
 */
fun Modifier.showKeyboardOnFocus(keyboardController: SoftwareKeyboardController?): Modifier =
    onFocusChanged { if (it.isFocused) keyboardController?.show() }
