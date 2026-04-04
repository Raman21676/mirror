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
import com.mirror.target.core.RustBridge
import com.mirror.target.encoder.MediaCodecEncoder
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
    private lateinit var videoEncoder: MediaCodecEncoder

    override fun onCreate() {
        super.onCreate()
        Timber.d("MirrorTargetService created")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mirror::WakeLock")
        wakeLock.setReferenceCounted(false)
        
        cameraManager = CameraCaptureManager(this)
        audioManager = AudioCaptureManager(this)
        tcpServer = TcpServerManager(serviceScope)
        videoEncoder = MediaCodecEncoder(
            width = CameraCaptureManager.WIDTH,
            height = CameraCaptureManager.HEIGHT,
            bitrate = 1_000_000,
            fps = 15
        )
        
        // Wire camera -> encoder
        cameraManager.onFrameAvailable = { nv21Frame ->
            videoEncoder.encodeFrame(nv21Frame)
        }
        
        // Wire encoder -> mux -> TCP
        videoEncoder.onEncodedFrame = { encodedFrame ->
            // Mux the encoded frame (type 0x01 = Video)
            val muxed = RustBridge.nativeMuxPacket(0x01, encodedFrame)
            if (muxed != null) {
                // Send to connected client (skip encryption for Task 4)
                val sent = tcpServer.sendToClient(muxed)
                if (sent) {
                    Timber.v("Sent ${muxed.size} bytes (${encodedFrame.size} encoded)")
                }
            } else {
                Timber.w("Mux failed for ${encodedFrame.size} byte frame")
            }
        }
        
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
            // Start encoder first (before camera starts feeding it)
            videoEncoder.start()
            // Start TCP server
            tcpServer.startServer()
            // Start camera (will feed encoder)
            cameraManager.startCapture()
            // Start audio (not wired yet, but keep it running)
            audioManager.startCapture()
            
            isRunning = true
            Timber.i("Service started - camera streaming to TCP")
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
        videoEncoder.stop()
        tcpServer.stopServer()
        if (wakeLock.isHeld) wakeLock.release()
        serviceScope.cancel()
        Timber.i("Service stopped")
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
            .setContentText("Camera streaming active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
