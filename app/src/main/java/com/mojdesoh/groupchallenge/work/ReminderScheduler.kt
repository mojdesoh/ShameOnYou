package com.mojdesoh.groupchallenge.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the weekly reminder chain and the one-time period-end notification for a
 * challenge, entirely on-device via WorkManager. Note: WorkManager delivery isn't exact
 * to the minute (Doze/battery optimization can push a run back by a while) — acceptable
 * for a weekly nudge, worth revisiting with exact alarms if tighter timing matters later.
 */
object ReminderScheduler {

    fun scheduleForNewChallenge(
        context: Context,
        groupId: String,
        challengeTitle: String,
        unit: String,
        endAtMillis: Long
    ) {
        scheduleNextReminder(context, groupId, challengeTitle, unit, endAtMillis, ReminderWorker.SEVEN_DAYS_MILLIS)
        schedulePeriodEnd(context, groupId, endAtMillis)
    }

    fun scheduleNextReminder(
        context: Context,
        groupId: String,
        challengeTitle: String,
        unit: String,
        endAtMillis: Long,
        delayMillis: Long
    ) {
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_GROUP_ID, groupId)
            .putString(ReminderWorker.KEY_CHALLENGE_TITLE, challengeTitle)
            .putString(ReminderWorker.KEY_UNIT, unit)
            .putLong(ReminderWorker.KEY_END_AT_MILLIS, endAtMillis)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            reminderWorkName(groupId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun schedulePeriodEnd(context: Context, groupId: String, endAtMillis: Long) {
        val delay = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_GROUP_ID, groupId)
            .build()

        val request = OneTimeWorkRequestBuilder<PeriodEndWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            periodEndWorkName(groupId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context, groupId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(reminderWorkName(groupId))
        WorkManager.getInstance(context).cancelUniqueWork(periodEndWorkName(groupId))
    }

    private fun reminderWorkName(groupId: String) = "reminder-$groupId"
    private fun periodEndWorkName(groupId: String) = "period-end-$groupId"
}
