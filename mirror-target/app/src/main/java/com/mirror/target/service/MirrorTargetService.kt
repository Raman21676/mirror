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
import com.mirror.target.network.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

/**
 * Mirror Target Service - Streams camera and audio to Host app.
 * 
 * Supports two transports:
 * 1. TCP - Same network (fast, zero overhead)
 * 2. WebRTC DataChannel - Cross-network (automatic fallback)
 * 
 * All data is encrypted with AES-256-GCM via Rust bridge.
 */
class MirrorTargetService : Service() {

    companion object {
        const val ACTION_START = "com.mirror.target.action.START"
        const val ACTION_STOP = "com.mirror.target.action.STOP"
        const val CHANNEL_ID = "mirror_target_service"
        const val NOTIFICATION_ID = 1001
        
        @Volatile
        var isRunning = false
            private set
            
        // 32-byte AES-256 key (in production, generate and exchange securely)
        val ENCRYPTION_KEY = ByteArray(32) { 0x42 } // TODO: Use secure key exchange
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var cameraManager: CameraCaptureManager
    private lateinit var audioManager: AudioCaptureManager
    private lateinit var transportManager: TransportManager
    private lateinit var videoEncoder: MediaCodecEncoder

    override fun onCreate() {
        super.onCreate()
        Timber.d("MirrorTargetService created")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mirror::WakeLock")
        wakeLock.setReferenceCounted(false)
        
        cameraManager = CameraCaptureManager(this)
        audioManager = AudioCaptureManager(this)
        transportManager = TransportManager(this, serviceScope)
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
        
        // Wire encoder -> mux -> encrypt -> transport
        videoEncoder.onEncodedFrame = { encodedFrame, isCodecConfig ->
            sendVideoFrame(encodedFrame, isCodecConfig)
        }
        
        // When client connects, send codec config
        transportManager.onClientConnected = { transportType ->
            Timber.i("Client connected via $transportType")
            val codecConfig = videoEncoder.codecConfig
            if (codecConfig != null) {
                Timber.i("Sending codec config to new client")
                sendVideoFrame(codecConfig, true)
            }
        }
        
        transportManager.onClientDisconnected = {
            Timber.i("Client disconnected")
        }
        
        // Wire audio -> mux -> encrypt -> transport
        audioManager.onAudioData = { audioBytes ->
            sendAudioData(audioBytes)
        }
        
        createNotificationChannel()
    }
    
    private fun sendVideoFrame(frameData: ByteArray, isCodecConfig: Boolean) {
        try {
            // Mux the frame (type 0x01 = Video)
            val muxed = RustBridge.nativeMuxPacket(0x01, frameData)
            if (muxed == null) {
                Timber.w("Mux failed for video frame")
                return
            }
            
            // Encrypt with AES-256-GCM
            val encrypted = RustBridge.nativeEncryptPacket(muxed, ENCRYPTION_KEY)
            if (encrypted == null) {
                Timber.w("Encryption failed for video frame")
                return
            }
            
            // Send via active transport
            val sent = transportManager.send(encrypted)
            if (sent && isCodecConfig) {
                Timber.i("Sent codec config: ${encrypted.size} bytes (encrypted)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to send video frame")
        }
    }
    
    private fun sendAudioData(audioBytes: ByteArray) {
        try {
            // Mux the audio (type 0x02 = Audio)
            val muxed = RustBridge.nativeMuxPacket(0x02, audioBytes)
            if (muxed == null) {
                Timber.w("Mux failed for audio")
                return
            }
            
            // Encrypt with AES-256-GCM
            val encrypted = RustBridge.nativeEncryptPacket(muxed, ENCRYPTION_KEY)
            if (encrypted == null) {
                Timber.w("Encryption failed for audio")
                return
            }
            
            // Send via active transport
            transportManager.send(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send audio data")
        }
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
            // Start transport manager (TCP server + WebRTC ready)
            transportManager.start()
            // Start camera (will feed encoder)
            cameraManager.startCapture()
            // Start audio
            audioManager.startCapture()
            
            isRunning = true
            Timber.i("Service started - camera streaming active")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start")
            stopSelf()
        }
    }
    
    /**
     * Initialize WebRTC for cross-network connections.
     * Call this from UI when user enables "Cross-network mode".
     */
    fun initializeWebRtc() {
        transportManager.initializeWebRtc()
    }
    
    /**
     * Set WebRTC offer from scanned QR code.
     */
    fun setWebRtcOffer(offerSdp: String) {
        transportManager.createWebRtcAnswer(offerSdp)
    }
    
    /**
     * Add ICE candidate from scanned QR.
     */
    fun addIceCandidate(candidateJson: String) {
        transportManager.addWebRtcIceCandidate(candidateJson)
    }
    
    /**
     * Get current signaling data to display as QR.
     */
    fun setSignalingCallback(callback: (String) -> Unit) {
        transportManager.onSignalingData = callback
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cameraManager.stopCapture()
        audioManager.stopCapture()
        videoEncoder.stop()
        transportManager.stop()
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
