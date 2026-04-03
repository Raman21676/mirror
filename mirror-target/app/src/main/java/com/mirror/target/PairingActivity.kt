package com.mirror.target

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity for pairing with host device via QR code.
 */
class PairingActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
    }
}
