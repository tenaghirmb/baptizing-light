package com.omi.baptizinglight.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.omi.baptizinglight.R
import com.omi.baptizinglight.domain.Configs.WAKE_LOCK_TIMEOUT
import com.omi.baptizinglight.domain.model.FlashlightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlashlightForegroundService : Service() {
    private val flashlightService by lazy { FlashlightService.getInstance(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val flashlightService = FlashlightService.getInstance(this)

        // 监听真值源
        serviceScope.launch {
            flashlightService.currentMode.collect { mode ->
                if (mode == FlashlightMode.OFF) {
                    // 核心修复：关掉通知并停止服务
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            flashlightService.setMode(FlashlightMode.OFF)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // 1. 创建通知（前台服务必须有通知，否则会被秒杀）
        val notification = createNotification()
        // 指定服务类型为 SPECIAL_USE (针对 Android 14+ 的适配)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        // 2. 持有 WakeLock，防止 CPU 进入休眠
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Light:WakeLock")
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT)

        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotification(): Notification {
        val channelId = "flashlight_service_channel"
        val channelName = this.getString(R.string.notification_channel_desc)

        // 1. 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = this@FlashlightForegroundService.getString(R.string.notification_channel)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 2. 增加“关闭”按钮的 Intent (点击通知里的按钮直接关灯)
        val stopIntent = Intent(this, FlashlightForegroundService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 构建通知
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(this.getString(R.string.notification_title))
            .setContentText(this.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_flashlight_active)
            .setOngoing(true) // 禁止用户滑动删除
            .addAction(R.drawable.ic_stop, this.getString(R.string.notification_stop), stopPendingIntent) // 增加快捷停止按钮
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}