package com.github.gotify.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P0AlarmPolicyTest {
    @Test
    fun priorityEightIsCritical() {
        assertFalse(P0AlarmPolicy.isCritical(7))
        assertTrue(P0AlarmPolicy.isCritical(8))
        assertTrue(P0AlarmPolicy.isCritical(10))
    }

    @Test
    fun disabledOrLowPriorityDoesNotTrigger() {
        assertFalse(trigger(priority = 10, enabled = false))
        assertFalse(trigger(priority = 7, enabled = true))
    }

    @Test
    fun duplicateAndRecentMessagesAreSuppressed() {
        assertFalse(
            trigger(
                messageId = 33,
                lastMessageId = 33,
                now = 1_000L + P0AlarmPolicy.COOLDOWN_MILLIS
            )
        )
        assertFalse(
            trigger(
                messageId = 34,
                lastMessageId = 33,
                now = 1_000L + P0AlarmPolicy.COOLDOWN_MILLIS - 1
            )
        )
        assertTrue(
            trigger(
                messageId = 34,
                lastMessageId = 33,
                now = 1_000L + P0AlarmPolicy.COOLDOWN_MILLIS
            )
        )
    }

    private fun trigger(
        priority: Long = 8,
        enabled: Boolean = true,
        now: Long = 1_000L + P0AlarmPolicy.COOLDOWN_MILLIS,
        messageId: Long = 34,
        lastMessageId: Long = 33
    ): Boolean {
        return P0AlarmPolicy.shouldTrigger(
            priority = priority,
            enabled = enabled,
            nowMillis = now,
            lastTriggeredAtMillis = 1_000L,
            messageId = messageId,
            lastTriggeredMessageId = lastMessageId
        )
    }
}
