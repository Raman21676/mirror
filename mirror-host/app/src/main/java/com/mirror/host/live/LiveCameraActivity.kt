package com.mirror.host.live

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mirror.host.R
import com.mirror.host.audio.AudioPlayer
import com.mirror.host.core.RustBridge
import com.mirror.host.decoder.MediaCodecDecoder
import com.mirror.host.network.TransportManager
import timber.log.Timber

class LiveCameraActivity : AppCompatActivity() {

    private lateinit var videoContainer: FrameLayout
    private lateinit var statusOverlay: TextView
    private lateinit var infoOverlay: TextView
    private var surfaceView: SurfaceView? = null
    private var mediaDecoder: MediaCodecDecoder? = null
    private var audioPlayer: AudioPlayer? = null
    private var transportManager: TransportManager? = null

    companion object {
        const val EXTRA_TARGET_IP = "target_ip"
        const val EXTRA_USE_WEBRTC = "use_webrtc"
        val ENCRYPTION_KEY = ByteArray(32) { 0x42 }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_camera)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        videoContainer = findViewById(R.id.video_container)
        statusOverlay = findViewById(R.id.connection_status_overlay)
        infoOverlay = findViewById(R.id.connection_info_overlay)

        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP)
        val useWebRtcOnly = intent.getBooleanExtra(EXTRA_USE_WEBRTC, false)

        if (targetIp.isNullOrEmpty()) {
            Toast.makeText(this, "No target IP provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        audioPlayer = AudioPlayer().apply { start() }

        transportManager = TransportManager(this, lifecycleScope).apply {
            onConnectionStateChanged = { state ->
                runOnUiThread { updateConnectionStatus(state, targetIp) }
            }
            onDataReceived = { data ->
                processReceivedData(data)
            }
            onSignalingData = { data ->
                Timber.d("WebRTC signaling: $data")
            }
        }

        // Clear any stale demux data from previous sessions
        RustBridge.nativeClearDemux()

        if (useWebRtcOnly) {
            transportManager?.connectWebRtcOnly()
        } else {
            transportManager?.connect(targetIp)
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

    private fun updateConnectionStatus(state: TransportManager.ConnectionState, targetIp: String) {
        when (state) {
            TransportManager.ConnectionState.CONNECTED_TCP,
            TransportManager.ConnectionState.CONNECTED_WEBRTC -> {
                statusOverlay.visibility = View.GONE
                infoOverlay.visibility = View.VISIBLE
                infoOverlay.text = "Connected to $targetIp"
                // Create surface and decoder once connected
                if (surfaceView == null) {
                    createSurfaceView()
                }
            }
            TransportManager.ConnectionState.CONNECTING_TCP -> {
                statusOverlay.visibility = View.VISIBLE
                statusOverlay.text = "Connecting via TCP..."
            }
            TransportManager.ConnectionState.CONNECTING_WEBRTC -> {
                statusOverlay.visibility = View.VISIBLE
                statusOverlay.text = "Connecting via WebRTC..."
            }
            TransportManager.ConnectionState.DISCONNECTED -> {
                statusOverlay.visibility = View.VISIBLE
                statusOverlay.text = "Disconnected"
                infoOverlay.visibility = View.GONE
            }
            TransportManager.ConnectionState.FAILED -> {
                statusOverlay.visibility = View.VISIBLE
                statusOverlay.text = "Connection failed"
                infoOverlay.visibility = View.GONE
            }
        }
    }

    private fun createSurfaceView() {
        val sv = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    startDecoder(holder)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    stopDecoder()
                }
            })
        }
        surfaceView = sv
        videoContainer.addView(sv)
    }

    private fun startDecoder(holder: SurfaceHolder) {
        if (mediaDecoder != null) return
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            Timber.e("Surface invalid")
            return
        }
        mediaDecoder = MediaCodecDecoder(surface, 640, 480).apply { start() }
    }

    private fun stopDecoder() {
        mediaDecoder?.stop()
        mediaDecoder = null
    }

    private fun processReceivedData(encryptedData: ByteArray) {
        try {
            val decrypted = RustBridge.nativeDecryptPacket(encryptedData, ENCRYPTION_KEY)
            if (decrypted == null) {
                Timber.w("Decrypt failed")
                return
            }
            val payloads = RustBridge.nativeDemuxPacket(decrypted)
            if (payloads == null || payloads.isEmpty()) return

            payloads.forEach { payloadWithType ->
                if (payloadWithType.size < 1) return@forEach
                val type = payloadWithType[0].toInt() and 0xFF
                val payload = payloadWithType.copyOfRange(1, payloadWithType.size)
                when (type) {
                    0x01 -> feedFrame(payload)
                    0x02 -> playAudio(payload)
                    else -> Timber.w("Unknown type: 0x${type.toString(16)}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Process data error")
        }
    }

    fun feedFrame(encodedBytes: ByteArray) {
        if (encodedBytes.isEmpty()) return
        try {
            mediaDecoder?.decodeFrame(encodedBytes)
        } catch (e: Exception) {
            Timber.e(e, "Feed frame error")
        }
    }

    fun playAudio(audioBytes: ByteArray) {
        if (audioBytes.isEmpty()) return
        try {
            audioPlayer?.play(audioBytes)
        } catch (e: Exception) {
            Timber.e(e, "Play audio error")
        }
    }

    fun setWebRtcAnswer(answerSdp: String) {
        transportManager?.setWebRtcAnswer(answerSdp)
    }

    fun addIceCandidate(candidateJson: String) {
        transportManager?.addWebRtcIceCandidate(candidateJson)
    }

    fun isReady(): Boolean = mediaDecoder != null

    fun getConnectionState(): TransportManager.ConnectionState {
        return transportManager?.connectionState ?: TransportManager.ConnectionState.DISCONNECTED
    }
}
