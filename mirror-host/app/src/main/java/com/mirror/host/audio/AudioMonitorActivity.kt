package com.mirror.host.audio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mirror.host.R

/**
 * Activity for monitoring audio from target device.
 */
class AudioMonitorActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_monitor)
    }
}
