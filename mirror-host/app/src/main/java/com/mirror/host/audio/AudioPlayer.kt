package com.mirror.host.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import timber.log.Timber

/**
 * Audio player using AudioTrack for raw PCM playback.
 * 44100Hz, mono, 16-bit PCM.
 */
class AudioPlayer {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    // Buffer size
    private val bufferSize by lazy {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) {
            Timber.e("getMinBufferSize returned error: $minBuffer")
            4096 * 4 // Fallback
        } else {
            minBuffer * 4
        }
    }

    fun start() {
        if (isPlaying) return

        val bufferSizeActual = bufferSize
        Timber.i("AudioTrack buffer size: $bufferSizeActual bytes")

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AUDIO_FORMAT)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeActual)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
        
        Timber.i("Audio player started: ${SAMPLE_RATE}Hz, mono, 16-bit, buffer=$bufferSizeActual")
    }

    fun stop() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Timber.i("Audio player stopped")
    }

    /**
     * Play raw PCM audio bytes.
     * Bytes should be 16-bit little-endian PCM.
     */
    fun play(bytes: ByteArray) {
        if (!isPlaying || bytes.isEmpty()) return

        try {
            val written = audioTrack?.write(bytes, 0, bytes.size) ?: 0
            if (written < 0) {
                Timber.e("AudioTrack write error: $written")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio")
        }
    }
}
