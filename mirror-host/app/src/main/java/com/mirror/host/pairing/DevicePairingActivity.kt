package com.mirror.host.pairing

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mirror.host.R

/**
 * Activity for pairing with target device.
 */
class DevicePairingActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_pairing)
    }
}
