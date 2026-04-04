package com.mirror.host.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import timber.log.Timber

/**
 * H.264 video decoder using Android MediaCodec.
 * Decodes incoming H.264 frames and renders to a Surface.
 */
class MediaCodecDecoder(
    private val surface: Surface,
    private val width: Int = 640,
    private val height: Int = 480
) {
    private var decoder: MediaCodec? = null
    private var isDecoding = false
    private var frameCount = 0

    fun start() {
        if (isDecoding) return

        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    // Optional: set buffer sizes
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 4)
                }
                configure(format, surface, null, 0)
                start()
            }
            isDecoding = true
            frameCount = 0
            startOutputThread()
            Timber.i("H.264 decoder started: ${width}x${height}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start decoder")
        }
    }

    fun stop() {
        isDecoding = false
        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
            Timber.i("H.264 decoder stopped (rendered $frameCount frames)")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping decoder")
        }
    }

    /**
     * Feed an encoded H.264 frame to the decoder.
     * Automatically detects codec config (SPS/PPS) vs regular frames.
     */
    fun decodeFrame(encodedData: ByteArray) {
        if (!isDecoding || encodedData.isEmpty()) return

        val codec = decoder ?: return

        try {
            // Check if this is codec config (contains SPS/PPS NAL units)
            val isCodecConfig = isCodecConfigFrame(encodedData)
            if (isCodecConfig) {
                Timber.d("Received codec config (SPS/PPS): ${encodedData.size} bytes")
            }

            val inputBufferId = codec.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                inputBuffer?.clear()
                inputBuffer?.put(encodedData)
                
                val flags = if (isCodecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                codec.queueInputBuffer(
                    inputBufferId,
                    0,
                    encodedData.size,
                    System.nanoTime() / 1000, // presentation time in microseconds
                    flags
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error feeding frame to decoder")
        }
    }

    /**
     * Check if this is a codec config frame (contains SPS/PPS).
     * H.264 NAL unit types: 7 = SPS, 8 = PPS
     */
    private fun isCodecConfigFrame(data: ByteArray): Boolean {
        if (data.size < 5) return false
        
        // Check for AnnexB format start code (0x00 0x00 0x00 0x01 or 0x00 0x00 0x01)
        var offset = 0
        while (offset < data.size - 4) {
            // Check for 4-byte start code
            if (data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() && 
                data[offset + 2] == 0x00.toByte() && data[offset + 3] == 0x01.toByte()) {
                // NAL unit type is in the lower 5 bits of the first byte after start code
                val nalUnitType = (data[offset + 4].toInt() and 0x1F)
                if (nalUnitType == 7 || nalUnitType == 8) {
                    return true // Contains SPS (7) or PPS (8)
                }
                offset += 4
            } 
            // Check for 3-byte start code
            else if (data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() && 
                     data[offset + 2] == 0x01.toByte()) {
                val nalUnitType = (data[offset + 3].toInt() and 0x1F)
                if (nalUnitType == 7 || nalUnitType == 8) {
                    return true // Contains SPS (7) or PPS (8)
                }
                offset += 3
            } else {
                offset++
            }
        }
        return false
    }

    private fun startOutputThread() {
        Thread {
            while (isDecoding) {
                try {
                    val bufferInfo = MediaCodec.BufferInfo()
                    val outputBufferId = decoder?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1

                    when {
                        outputBufferId >= 0 -> {
                            // Render the output buffer to the surface
                            decoder?.releaseOutputBuffer(outputBufferId, true)
                            frameCount++
                            
                            // Log every 30 frames (approx 2 seconds at 15fps)
                            if (frameCount % 30 == 0) {
                                Timber.i("Frame rendered to surface: $frameCount total frames")
                            }
                        }
                        outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder?.outputFormat
                            Timber.d("Decoder output format changed: $format")
                        }
                        outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet, continue
                        }
                    }
                } catch (e: Exception) {
                    if (isDecoding) Timber.e(e, "Error in decoder output loop")
                }
            }
        }.apply {
            name = "DecoderOutputThread"
            start()
        }
    }
}
