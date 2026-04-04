package com.mirror.target.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mirror.target.MainActivity
import com.mirror.target.R
import com.mirror.target.audio.AudioCaptureManager
import com.mirror.target.camera.CameraCaptureManager
import com.mirror.target.network.TcpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

class MirrorTargetService : Service() {

    companion object {
        const val ACTION_START = "com.mirror.target.action.START"
        const val ACTION_STOP = "com.mirror.target.action.STOP"
        const val CHANNEL_ID = "mirror_target_service"
        const val NOTIFICATION_ID = 1001
        
        @Volatile
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var cameraManager: CameraCaptureManager
    private lateinit var audioManager: AudioCaptureManager
    private lateinit var tcpServer: TcpServerManager

    override fun onCreate() {
        super.onCreate()
        Timber.d("MirrorTargetService created")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mirror::WakeLock")
        wakeLock.setReferenceCounted(false)
        
        cameraManager = CameraCaptureManager(this)
        audioManager = AudioCaptureManager(this)
        tcpServer = TcpServerManager(serviceScope)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (isRunning) return
        Timber.d("Starting foreground service")
        
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L)
        startForeground(NOTIFICATION_ID, createNotification())
        
        try {
            cameraManager.startCapture()
            audioManager.startCapture()
            tcpServer.startServer()
            isRunning = true
            Timber.i("Service started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cameraManager.stopCapture()
        audioManager.stopCapture()
        tcpServer.stopServer()
        if (wakeLock.isHeld) wakeLock.release()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mirror Target Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mirror Target")
            .setContentText("Surveillance active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
