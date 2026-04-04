package com.mirror.host.live

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mirror.host.decoder.MediaCodecDecoder
import com.mirror.host.network.TcpClientManager
import timber.log.Timber

/**
 * Activity for viewing live camera feed from target device.
 * Displays H.264 decoded video stream.
 */
class LiveCameraActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private var mediaDecoder: MediaCodecDecoder? = null
    private var tcpClient: TcpClientManager? = null

    companion object {
        const val EXTRA_TARGET_IP = "target_ip"
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
        
        // Get TCP client from application or create new connection
        // For now, we'll get the IP from intent and connect
        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP)
        if (targetIp != null) {
            connectToTarget(targetIp)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDecoder()
        tcpClient?.disconnect()
        tcpClient = null
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

    private fun connectToTarget(ip: String) {
        // Create TCP client and set up frame callback
        tcpClient = TcpClientManager(lifecycleScope).apply {
            onFrameReceived = { payload ->
                // Feed demuxed video frames to decoder
                feedFrame(payload)
            }
            connect(ip)
        }
    }

    /**
     * Feed a decoded frame to the video decoder.
     * Called by TcpClientManager after demuxing.
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
     * Check if the decoder is ready to receive frames.
     */
    fun isReady(): Boolean {
        return mediaDecoder != null
    }
}
