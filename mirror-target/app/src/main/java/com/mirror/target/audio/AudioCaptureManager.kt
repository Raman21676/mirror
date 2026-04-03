package com.mirror.target.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import timber.log.Timber

class AudioCaptureManager(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCapturing = false

    private val bufferSize by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
    }

    fun startCapture() {
        if (isCapturing) return
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("AudioRecord init failed")
        }

        audioRecord?.startRecording()
        isCapturing = true
        
        captureJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (isCapturing && isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Process audio
                }
            }
        }
        Timber.i("Audio capture started")
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Timber.i("Audio capture stopped")
    }
}
