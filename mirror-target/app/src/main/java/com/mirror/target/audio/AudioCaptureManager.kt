package com.mirror.target.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import timber.log.Timber

/**
 * Audio capture manager using AudioRecord.
 * 44100Hz, mono, 16-bit PCM. Runs on dedicated thread.
 */
class AudioCaptureManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    var onAudioData: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var isCapturing = false

    // Buffer size: min buffer * 4 as recommended
    private val bufferSize by lazy {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) {
            Timber.e("getMinBufferSize returned error: $minBuffer")
            4096 * 4 // Fallback
        } else {
            minBuffer * 4
        }
    }

    fun startCapture() {
        if (isCapturing) return

        val bufferSizeActual = bufferSize
        Timber.i("Audio buffer size: $bufferSizeActual bytes")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSizeActual
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("AudioRecord init failed")
        }

        audioRecord?.startRecording()
        isCapturing = true

        // Start dedicated capture thread
        captureThread = Thread {
            val buffer = ShortArray(bufferSizeActual / 2)
            
            while (isCapturing && !Thread.currentThread().isInterrupted) {
                try {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Convert ShortArray to ByteArray (PCM 16-bit)
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val sample = buffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        
                        // Notify callback
                        onAudioData?.invoke(byteBuffer)
                    }
                } catch (e: Exception) {
                    if (isCapturing) Timber.e(e, "Audio capture error")
                }
            }
        }.apply {
            name = "AudioCaptureThread"
            start()
        }

        Timber.i("Audio capture started: ${SAMPLE_RATE}Hz, mono, 16-bit, buffer=$bufferSizeActual")
    }

    fun stopCapture() {
        isCapturing = false
        captureThread?.interrupt()
        captureThread?.join(500)
        captureThread = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        Timber.i("Audio capture stopped")
    }
}
