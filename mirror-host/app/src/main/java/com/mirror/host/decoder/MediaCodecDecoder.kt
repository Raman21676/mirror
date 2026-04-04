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
            startInputThread()
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
            Timber.i("H.264 decoder stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping decoder")
        }
    }

    /**
     * Feed an encoded H.264 frame to the decoder.
     */
    fun decodeFrame(encodedData: ByteArray) {
        if (!isDecoding || encodedData.isEmpty()) return

        val codec = decoder ?: return

        try {
            val inputBufferId = codec.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                inputBuffer?.clear()
                inputBuffer?.put(encodedData)
                codec.queueInputBuffer(
                    inputBufferId,
                    0,
                    encodedData.size,
                    System.nanoTime() / 1000, // presentation time in microseconds
                    0
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error feeding frame to decoder")
        }
    }

    private fun startInputThread() {
        // Input is handled by decodeFrame() calls from main thread
        // This method is kept for symmetry but we don't need a separate thread
        // since we're being fed data via TCP callback
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
