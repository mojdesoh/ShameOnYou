package com.mojdesoh.groupchallenge.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mojdesoh.groupchallenge.notification.showReminderNotification

/**
 * Fires once, shows the weekly "log your number" notification, then re-enqueues itself
 * 7 days later — unless the challenge period has ended, in which case it stops the chain.
 * There is no server pushing this; every device schedules its own copy on the same
 * 7-day cadence from when the challenge started, which is what keeps everyone in sync
 * without needing a paid Cloud Functions plan.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()
        val challengeTitle = inputData.getString(KEY_CHALLENGE_TITLE) ?: ""
        val unit = inputData.getString(KEY_UNIT) ?: ""
        val endAtMillis = inputData.getLong(KEY_END_AT_MILLIS, 0L)

        if (hasNotificationPermission()) {
            showReminderNotification(applicationContext, groupId, challengeTitle, unit)
        }

        val nextFireAt = System.currentTimeMillis() + SEVEN_DAYS_MILLIS
        if (nextFireAt < endAtMillis) {
            ReminderScheduler.scheduleNextReminder(
                applicationContext, groupId, challengeTitle, unit, endAtMillis, SEVEN_DAYS_MILLIS
            )
        }
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val KEY_GROUP_ID = "group_id"
        const val KEY_CHALLENGE_TITLE = "challenge_title"
        const val KEY_UNIT = "unit"
        const val KEY_END_AT_MILLIS = "end_at_millis"
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
