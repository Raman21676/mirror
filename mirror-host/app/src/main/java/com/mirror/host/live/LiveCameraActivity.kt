package com.mirror.host.live

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mirror.host.R

/**
 * Activity for viewing live camera feed from target device.
 */
class LiveCameraActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_camera)
    }
}
