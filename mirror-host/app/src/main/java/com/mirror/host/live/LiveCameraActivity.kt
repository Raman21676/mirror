package com.mirror.host.live

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mirror.host.audio.AudioPlayer
import com.mirror.host.core.RustBridge
import com.mirror.host.decoder.MediaCodecDecoder
import com.mirror.host.network.TransportManager
import timber.log.Timber

/**
 * Activity for viewing live camera feed from target device.
 * Displays H.264 decoded video stream and plays audio.
 * 
 * Supports two transport modes:
 * 1. TCP - Same network (fast, tried first)
 * 2. WebRTC DataChannel - Cross-network (automatic fallback)
 * 
 * All received data is decrypted using AES-256-GCM via Rust bridge.
 */
class LiveCameraActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private var mediaDecoder: MediaCodecDecoder? = null
    private var audioPlayer: AudioPlayer? = null
    private var transportManager: TransportManager? = null

    companion object {
        const val EXTRA_TARGET_IP = "target_ip"
        const val EXTRA_USE_WEBRTC = "use_webrtc"
        
        // Must match the key used by Target
        val ENCRYPTION_KEY = ByteArray(32) { 0x42 } // TODO: Use secure key exchange
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on while viewing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Create SurfaceView programmatically (fills screen)
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Timber.i("Surface created, starting decoder")
                    startDecoder(holder)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Timber.d("Surface changed: ${width}x${height}")
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Timber.i("Surface destroyed, stopping decoder")
                    stopDecoder()
                }
            })
        }
        
        setContentView(surfaceView)
        
        // Start audio player
        audioPlayer = AudioPlayer().apply {
            start()
        }
        
        // Get connection parameters
        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP)
        val useWebRtcOnly = intent.getBooleanExtra(EXTRA_USE_WEBRTC, false)
        
        // Initialize transport manager
        transportManager = TransportManager(this, lifecycleScope).apply {
            onConnectionStateChanged = { state ->
                Timber.i("Connection state changed: $state")
                // Could update UI here to show connection status
            }
            
            onDataReceived = { encryptedData ->
                // Decrypt → demux → route (handles both video and audio)
                processReceivedData(encryptedData)
            }
            
            onSignalingData = { signalingData ->
                // WebRTC signaling data (SDP/ICE) to display as QR
                Timber.d("WebRTC signaling data: $signalingData")
                // TODO: Display as QR code for Target to scan
            }
        }
        
        // Connect to target
        if (targetIp != null) {
            if (useWebRtcOnly) {
                Timber.i("Connecting via WebRTC only (cross-network)")
                transportManager?.connectWebRtcOnly()
            } else {
                Timber.i("Connecting to $targetIp (TCP first, WebRTC fallback)")
                transportManager?.connect(targetIp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDecoder()
        audioPlayer?.stop()
        audioPlayer = null
        transportManager?.dispose()
        transportManager = null
    }

    private fun startDecoder(holder: SurfaceHolder) {
        if (mediaDecoder != null) return
        
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            Timber.e("Surface is null or invalid")
            return
        }
        
        mediaDecoder = MediaCodecDecoder(surface, 640, 480).apply {
            start()
        }
    }

    private fun stopDecoder() {
        mediaDecoder?.stop()
        mediaDecoder = null
    }
    
    /**
     * Process received encrypted data: decrypt → demux → route by type.
     * Unified handling for both TCP and WebRTC transports.
     */
    private fun processReceivedData(encryptedData: ByteArray) {
        try {
            // Step 1: Decrypt
            val decrypted = RustBridge.nativeDecryptPacket(encryptedData, ENCRYPTION_KEY)
            if (decrypted == null) {
                Timber.w("Failed to decrypt data")
                return
            }
            
            // Step 2: Demux (extract payloads from [type][timestamp][len][payload][auth])
            val payloads = RustBridge.nativeDemuxPacket(decrypted)
            if (payloads == null || payloads.isEmpty()) {
                return
            }
            
            // Step 3: Route by type
            payloads.forEach { payloadWithType ->
                if (payloadWithType.size < 1) return@forEach
                
                val type = payloadWithType[0].toInt() and 0xFF
                val payload = payloadWithType.copyOfRange(1, payloadWithType.size)
                
                when (type) {
                    0x01 -> feedFrame(payload)   // Video
                    0x02 -> playAudio(payload)   // Audio
                    else -> Timber.w("Unknown payload type: 0x${type.toString(16)}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing received data")
        }
    }

    /**
     * Feed a decoded frame to the video decoder.
     */
    fun feedFrame(encodedBytes: ByteArray) {
        if (encodedBytes.isEmpty()) return
        
        try {
            mediaDecoder?.decodeFrame(encodedBytes)
        } catch (e: Exception) {
            Timber.e(e, "Error feeding frame")
        }
    }

    /**
     * Play raw PCM audio.
     */
    fun playAudio(audioBytes: ByteArray) {
        if (audioBytes.isEmpty()) return
        
        try {
            audioPlayer?.play(audioBytes)
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio")
        }
    }

    /**
     * Set WebRTC answer from scanned QR code.
     */
    fun setWebRtcAnswer(answerSdp: String) {
        transportManager?.setWebRtcAnswer(answerSdp)
    }
    
    /**
     * Add ICE candidate from scanned QR.
     */
    fun addIceCandidate(candidateJson: String) {
        transportManager?.addWebRtcIceCandidate(candidateJson)
    }

    /**
     * Check if the decoder is ready to receive frames.
     */
    fun isReady(): Boolean {
        return mediaDecoder != null
    }
    
    /**
     * Get current connection state.
     */
    fun getConnectionState(): TransportManager.ConnectionState {
        return transportManager?.connectionState ?: TransportManager.ConnectionState.DISCONNECTED
    }
}
