package com.mirror.host

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mirror.host.databinding.ActivityMainBinding
import com.mirror.host.audio.AudioMonitorActivity
import com.mirror.host.gallery.GalleryBrowserActivity
import com.mirror.host.live.LiveCameraActivity
import com.mirror.host.map.MapTrackerActivity
import com.mirror.host.network.TcpClientManager
import com.mirror.host.pairing.DevicePairingActivity
import timber.log.Timber

/**
 * Main activity for Mirror Host app.
 * This is the user's main phone for remote monitoring.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tcpClient: TcpClientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize TCP client
        tcpClient = TcpClientManager(lifecycleScope)
        tcpClient.onConnectionStateChanged = { connected ->
            runOnUiThread {
                updateConnectionUI(connected)
            }
        }
        tcpClient.onDataReceived = { data ->
            Timber.d("Received ${data.size} bytes from Target")
        }

        // Setup connection UI
        setupConnectionUI()

        // Setup bottom navigation
        setupBottomNavigation()
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpClient.disconnect()
    }

    private fun setupConnectionUI() {
        binding.connectButton.setOnClickListener {
            if (tcpClient.isConnected) {
                disconnect()
            } else {
                connect()
            }
        }

        binding.testButton.setOnClickListener {
            if (tcpClient.sendTestPacket()) {
                Toast.makeText(this, "Test packet sent!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send test packet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connect() {
        val ip = binding.ipInput.text?.toString()?.trim() ?: ""
        if (ip.isEmpty()) {
            binding.ipInputLayout.error = "Please enter an IP address"
            return
        }
        binding.ipInputLayout.error = null

        Toast.makeText(this, "Connecting to $ip...", Toast.LENGTH_SHORT).show()
        tcpClient.connect(ip)
    }

    private fun disconnect() {
        tcpClient.disconnect()
    }

    private fun updateConnectionUI(connected: Boolean) {
        if (connected) {
            binding.connectionStatus.text = "Connected"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.connectButton.text = "Disconnect"
            binding.testButton.isEnabled = true
            binding.ipInput.isEnabled = false
        } else {
            binding.connectionStatus.text = "Disconnected"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.connectButton.text = "Connect"
            binding.testButton.isEnabled = false
            binding.ipInput.isEnabled = true
        }
    }

    private fun setupBottomNavigation() {
        val navView: BottomNavigationView = binding.navView

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_devices -> {
                    startActivity(Intent(this, DevicePairingActivity::class.java))
                    true
                }
                R.id.navigation_live -> {
                    startActivity(Intent(this, LiveCameraActivity::class.java))
                    true
                }
                R.id.navigation_map -> {
                    startActivity(Intent(this, MapTrackerActivity::class.java))
                    true
                }
                R.id.navigation_gallery -> {
                    startActivity(Intent(this, GalleryBrowserActivity::class.java))
                    true
                }
                R.id.navigation_settings -> {
                    // TODO: Create SettingsActivity later
                    true
                }
                else -> false
            }
        }
    }
}
