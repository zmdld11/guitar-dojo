package dev.zmdld11.guitardojo.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.zmdld11.guitardojo.MainActivity
import dev.zmdld11.guitardojo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 让节拍器在锁屏、切后台后继续响的前台服务。
 * 服务本身不产生声音，声音由 [MetronomeHolder] 里的引擎负责；这里只维持前台状态与通知。
 */
class MetronomeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        combine(MetronomeHolder.config, MetronomeHolder.isPlaying) { config, playing -> config.bpm to playing }
            .onEach { (bpm, playing) ->
                if (playing) promoteToForeground(bpm)
            }.launchIn(scope)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                MetronomeHolder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                MetronomeHolder.start()
                promoteToForeground(MetronomeHolder.config.value.bpm)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(bpm: Int) {
        val notification = buildNotification(bpm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(bpm: Int): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_metronome)
            .setContentTitle("节拍器 · $bpm BPM")
            .setContentText("点按停止")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "节拍器播放",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.zmdld11.guitardojo.action.STOP_METRONOME"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MetronomeService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MetronomeService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
