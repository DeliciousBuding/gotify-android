package com.github.gotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class P0AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(P0AlarmService.EXTRA_TITLE) ?: ""
        val message = intent.getStringExtra(P0AlarmService.EXTRA_MESSAGE) ?: ""
        val messageId = intent.getLongExtra(P0AlarmService.EXTRA_MESSAGE_ID, 0L)
        if (!P0AlarmService.start(context, title, message, messageId)) {
            P0AlarmNotifications.postSoundFallback(context, title, message, messageId)
        }
    }
}
