package com.mirror.target.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import timber.log.Timber

/**
 * H.264 video encoder using Android MediaCodec.
 * Encodes NV21 frames at 640x480, 1 Mbps, 15 fps.
 */
class MediaCodecEncoder(
    private val width: Int = 640,
    private val height: Int = 480,
    private val bitrate: Int = 1_000_000, // 1 Mbps
    private val fps: Int = 15
) {
    private var encoder: MediaCodec? = null
    private var isEncoding = false
    private var presentationTimeUs = 0L

    var onEncodedFrame: ((ByteArray) -> Unit)? = null

    fun start() {
        if (isEncoding) return

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second between I-frames
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                }
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            isEncoding = true
            presentationTimeUs = 0L
            startOutputThread()
            Timber.i("H.264 encoder started: ${width}x${height} @ ${bitrate / 1000}kbps, ${fps}fps")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start encoder")
        }
    }

    fun stop() {
        isEncoding = false
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            Timber.i("H.264 encoder stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping encoder")
        }
    }

    /**
     * Encode an NV21 frame.
     * The frame should be in NV21 format (Y plane + interleaved VU planes).
     */
    fun encodeFrame(nv21Data: ByteArray) {
        if (!isEncoding) return

        val codec = encoder ?: return

        try {
            val inputBufferId = codec.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                inputBuffer?.clear()
                
                // Convert NV21 to NV12 (MediaCodec expects NV12 for COLOR_FormatYUV420SemiPlanar)
                // NV12: Y plane + interleaved UV planes
                // NV21: Y plane + interleaved VU planes
                // We need to swap U and V
                val nv12Data = nv21ToNv12(nv21Data)
                
                inputBuffer?.put(nv12Data)
                codec.queueInputBuffer(
                    inputBufferId,
                    0,
                    nv12Data.size,
                    presentationTimeUs,
                    0
                )
                presentationTimeUs += 1_000_000 / fps
            }
        } catch (e: Exception) {
            Timber.e(e, "Error encoding frame")
        }
    }

    private fun startOutputThread() {
        Thread {
            while (isEncoding) {
                try {
                    val bufferInfo = MediaCodec.BufferInfo()
                    val outputBufferId = encoder?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1

                    when {
                        outputBufferId >= 0 -> {
                            val outputBuffer = encoder?.getOutputBuffer(outputBufferId)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer?.get(chunk)
                            outputBuffer?.clear()

                            encoder?.releaseOutputBuffer(outputBufferId, false)

                            // Notify callback with encoded frame
                            if (chunk.isNotEmpty()) {
                                onEncodedFrame?.invoke(chunk)
                            }
                        }
                        outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = encoder?.outputFormat
                            Timber.d("Encoder output format changed: $format")
                        }
                    }
                } catch (e: Exception) {
                    if (isEncoding) Timber.e(e, "Error in output loop")
                }
            }
        }.apply {
            name = "EncoderOutputThread"
            start()
        }
    }

    /**
     * Convert NV21 to NV12 by swapping U and V planes.
     * NV21: YYYY... VUVUVU...
     * NV12: YYYY... UVUVUV...
     */
    private fun nv21ToNv12(nv21: ByteArray): ByteArray {
        val size = width * height
        val nv12 = ByteArray(nv21.size)
        
        // Copy Y plane (same for both)
        System.arraycopy(nv21, 0, nv12, 0, size)
        
        // Swap V and U (interleaved)
        var i = size
        while (i < nv21.size - 1) {
            nv12[i] = nv21[i + 1] // U from V position
            nv12[i + 1] = nv21[i] // V from U position
            i += 2
        }
        
        return nv12
    }
}
