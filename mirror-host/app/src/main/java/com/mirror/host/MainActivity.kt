package com.mirror.host

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mirror.host.databinding.ActivityMainBinding
import com.mirror.host.audio.AudioMonitorActivity
import com.mirror.host.gallery.GalleryBrowserActivity
import com.mirror.host.live.LiveCameraActivity
import com.mirror.host.map.MapTrackerActivity
import com.mirror.host.pairing.DevicePairingActivity
import com.mirror.host.settings.SettingsActivity
import timber.log.Timber

/**
 * Main activity for Mirror Host app.
 * Dashboard for entering Target IP and navigating to features.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupConnectionUI()
        setupBottomNavigation()
    }

    private fun setupConnectionUI() {
        binding.connectButton.setOnClickListener {
            val ip = binding.ipInput.text?.toString()?.trim() ?: ""
            if (ip.isEmpty()) {
                binding.ipInputLayout.error = "Please enter Target IP address"
                return@setOnClickListener
            }
            binding.ipInputLayout.error = null
            // Launch live view directly
            goLive(ip)
        }

        binding.testButton.setOnClickListener {
            val ip = binding.ipInput.text?.toString()?.trim() ?: ""
            if (ip.isEmpty()) {
                binding.ipInputLayout.error = "Please enter Target IP first"
                return@setOnClickListener
            }
            binding.ipInputLayout.error = null
            goLive(ip)
        }
    }

    private fun goLive(targetIp: String) {
        val intent = Intent(this, LiveCameraActivity::class.java).apply {
            putExtra(LiveCameraActivity.EXTRA_TARGET_IP, targetIp)
        }
        startActivity(intent)
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
                    val ip = binding.ipInput.text?.toString()?.trim() ?: ""
                    if (ip.isEmpty()) {
                        Toast.makeText(this, "Please enter Target IP first", Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        goLive(ip)
                        true
                    }
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
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
