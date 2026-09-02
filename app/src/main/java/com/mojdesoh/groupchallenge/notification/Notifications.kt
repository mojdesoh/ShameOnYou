package com.mojdesoh.groupchallenge.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mojdesoh.groupchallenge.MainActivity

const val CHANNEL_REMINDER = "weekly_reminder"
const val CHANNEL_RESULT = "period_result"

const val EXTRA_GROUP_ID = "extra_group_id"
const val EXTRA_DESTINATION = "extra_destination"
const val DESTINATION_ENTRY = "entry"
const val DESTINATION_RESULT = "result"

fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_REMINDER, "Weekly reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A weekly nudge to log your progress"
        }
    )
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_RESULT, "Challenge results", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Announces whether your group reached its goal"
        }
    )
}

private fun openAppIntent(context: Context, groupId: String, destination: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_GROUP_ID, groupId)
        putExtra(EXTRA_DESTINATION, destination)
    }
    return PendingIntent.getActivity(
        context,
        (groupId + destination).hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun showReminderNotification(context: Context, groupId: String, challengeTitle: String, unit: String) {
    val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("Log this week's number")
        .setContentText("How did \"$challengeTitle\" go this week? Enter your $unit.")
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context, groupId, DESTINATION_ENTRY))
        .build()
    NotificationManagerCompat.from(context).notify(groupId.hashCode(), notification)
}

fun showResultNotification(context: Context, groupId: String, title: String, body: String) {
    val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context, groupId, DESTINATION_RESULT))
        .build()
    NotificationManagerCompat.from(context).notify((groupId + "result").hashCode(), notification)
}
