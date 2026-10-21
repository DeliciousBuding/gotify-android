package com.github.gotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class P0AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        P0AlarmService.start(
            context,
            intent.getStringExtra(P0AlarmService.EXTRA_TITLE) ?: "",
            intent.getStringExtra(P0AlarmService.EXTRA_MESSAGE) ?: ""
        )
    }
}
