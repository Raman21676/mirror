package com.mirror.host.screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mirror.host.R

/**
 * Activity for viewing target device screen mirror.
 */
class ScreenMirrorActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_mirror)
    }
}
