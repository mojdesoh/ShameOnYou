package com.mojdesoh.groupchallenge.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.ChallengeResultEvaluator
import com.mojdesoh.groupchallenge.data.buildResultMessage
import com.mojdesoh.groupchallenge.notification.showResultNotification

/** Fires once at the challenge deadline and announces the outcome to this device's user. */
class PeriodEndWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(ReminderWorker.KEY_GROUP_ID) ?: return Result.failure()
        val repository = ChallengeRepository()

        val group = repository.getGroupOnce(groupId) ?: return Result.success()
        val challenge = group.challenge ?: return Result.success()
        val members = repository.getMembersOnce(groupId)
        val entries = repository.getEntriesOnce(groupId)

        val result = ChallengeResultEvaluator.evaluate(challenge, members, entries)
        val (title, body) = buildResultMessage(group.name, challenge, result)

        if (hasNotificationPermission()) {
            showResultNotification(applicationContext, groupId, title, body)
        }
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
