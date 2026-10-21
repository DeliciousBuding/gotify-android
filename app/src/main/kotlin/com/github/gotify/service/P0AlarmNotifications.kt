package com.github.gotify.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.github.gotify.NotificationSupport
import com.github.gotify.R
import com.github.gotify.Utils
import com.github.gotify.messages.MessagesActivity

internal object P0AlarmNotifications {
    private const val TEST_NOTIFICATION_ID = -4

    fun postSoundFallback(context: Context, title: String, message: String, messageId: Long) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        NotificationSupport.createGeneralChannels(context, notificationManager)
        val contentTitle = if (title.isBlank()) {
            context.getString(R.string.p0_alarm_fallback_title)
        } else {
            context.getString(R.string.p0_alarm_notification_title, title)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MessagesActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            context,
            NotificationSupport.Channel.MESSAGES_IMPORTANCE_HIGH
        )
            .setSmallIcon(R.drawable.ic_gotify)
            .setContentTitle(contentTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        val notificationId = if (messageId > 0L) {
            Utils.longToInt(messageId)
        } else {
            TEST_NOTIFICATION_ID
        }
        notificationManager.notify(notificationId, notification)
    }
}
