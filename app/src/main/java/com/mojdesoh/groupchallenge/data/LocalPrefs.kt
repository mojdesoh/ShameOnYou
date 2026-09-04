package com.mojdesoh.groupchallenge.data

import android.content.Context

/** Remembers this device's chosen display name, reused as a default across groups. */
class LocalPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("group_challenge_prefs", Context.MODE_PRIVATE)

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    companion object {
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
