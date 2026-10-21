package com.github.gotify.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.gotify.NotificationSupport
import com.github.gotify.R
import com.github.gotify.messages.MessagesActivity

internal class P0AlarmService : Service() {
    companion object {
        const val ACTION_START = "com.github.gotify.action.P0_ALARM_START"
        const val ACTION_STOP = "com.github.gotify.action.P0_ALARM_STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        private const val NOTIFICATION_ID = -3
        private const val ALARM_DURATION_MILLIS = 30_000L

        fun start(context: Context, title: String, message: String) {
            val intent = Intent(context, P0AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getService(
                context,
                2,
                Intent(context, P0AlarmService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationSupport.createP0AlarmForegroundChannel(
                this,
                getSystemService(NotificationManager::class.java)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            P0AlarmScheduler.cancel(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notification = buildNotification(title, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAlarm()
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, ALARM_DURATION_MILLIS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun buildNotification(title: String, message: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MessagesActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationTitle = if (title.isBlank()) {
            getString(R.string.p0_alarm_active)
        } else {
            getString(R.string.p0_alarm_notification_title, title)
        }
        return NotificationCompat.Builder(this, NotificationSupport.Channel.P0_ALARM_FOREGROUND)
            .setSmallIcon(R.drawable.ic_gotify)
            .setContentTitle(notificationTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.p0_alarm_stop), stopPendingIntent(this))
            .build()
    }

    private fun startAlarm() {
        if (player != null) return

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:p0-alarm"
        ).apply {
            acquire(ALARM_DURATION_MILLIS + 5_000L)
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@P0AlarmService, alarmUri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopAlarm() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
