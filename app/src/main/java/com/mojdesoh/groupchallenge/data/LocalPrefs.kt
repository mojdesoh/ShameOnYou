package com.mojdesoh.groupchallenge.data

import android.content.Context

/** Remembers which group this device belongs to, so the app resumes there on relaunch. */
class LocalPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("group_challenge_prefs", Context.MODE_PRIVATE)

    var groupId: String?
        get() = prefs.getString(KEY_GROUP_ID, null)
        set(value) { prefs.edit().putString(KEY_GROUP_ID, value).apply() }

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    fun clearGroup() {
        prefs.edit().remove(KEY_GROUP_ID).apply()
    }

    companion object {
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
