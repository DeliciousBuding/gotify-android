package com.github.gotify.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import com.github.gotify.R
import com.github.gotify.client.model.Message
import com.github.gotify.messages.MessagesActivity
import org.tinylog.kotlin.Logger

internal object P0AlarmScheduler {
    private const val PREFS_NAME = "p0_alarm"
    private const val PREF_LAST_TRIGGERED_AT = "last_triggered_at"
    private const val PREF_LAST_MESSAGE_ID = "last_message_id"
    private const val ALARM_DELAY_MILLIS = 1_000L

    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.setting_key_p0_alarm),
            false
        )
    }

    fun trigger(context: Context, message: Message): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val shouldTrigger = P0AlarmPolicy.shouldTrigger(
            priority = message.priority,
            enabled = isEnabled(context),
            nowMillis = now,
            lastTriggeredAtMillis = prefs.getLong(PREF_LAST_TRIGGERED_AT, 0L),
            messageId = message.id,
            lastTriggeredMessageId = prefs.getLong(PREF_LAST_MESSAGE_ID, Long.MIN_VALUE)
        )

        if (!shouldTrigger) return false

        prefs.edit()
            .putLong(PREF_LAST_TRIGGERED_AT, now)
            .putLong(PREF_LAST_MESSAGE_ID, message.id)
            .apply()

        val title = message.title ?: ""
        val body = message.message
        schedule(context, title, body)
        Logger.info("P0 alarm scheduled for message ${message.id}")
        return true
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun schedule(context: Context, title: String, body: String) {
        val operation = alarmPendingIntent(context, title, body)
        val showIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MessagesActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + ALARM_DELAY_MILLIS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            Logger.warn("Exact alarms unavailable; starting P0 alarm immediately")
            P0AlarmService.start(context, title, body)
            return
        }

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                operation
            )
        } catch (e: SecurityException) {
            Logger.warn(e, "Unable to schedule exact P0 alarm; starting immediately")
            P0AlarmService.start(context, title, body)
        }
    }

    private fun alarmPendingIntent(
        context: Context,
        title: String = "",
        body: String = ""
    ): PendingIntent {
        val intent = Intent(context, P0AlarmReceiver::class.java).apply {
            putExtra(P0AlarmService.EXTRA_TITLE, title)
            putExtra(P0AlarmService.EXTRA_MESSAGE, body)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
