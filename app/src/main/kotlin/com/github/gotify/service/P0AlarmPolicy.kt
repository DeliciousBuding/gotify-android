package com.github.gotify.service

internal object P0AlarmPolicy {
    const val P0_PRIORITY = 10L
    const val COOLDOWN_MILLIS = 5 * 60 * 1000L

    fun isCritical(priority: Long?): Boolean = priority == P0_PRIORITY

    fun shouldTrigger(
        priority: Long?,
        enabled: Boolean,
        nowMillis: Long,
        lastTriggeredAtMillis: Long,
        messageId: Long,
        lastTriggeredMessageId: Long
    ): Boolean {
        if (!enabled || !isCritical(priority)) return false
        if (messageId == lastTriggeredMessageId) return false
        if (lastTriggeredAtMillis <= 0L) return true
        if (nowMillis < lastTriggeredAtMillis) return true
        return nowMillis - lastTriggeredAtMillis >= COOLDOWN_MILLIS
    }
}
