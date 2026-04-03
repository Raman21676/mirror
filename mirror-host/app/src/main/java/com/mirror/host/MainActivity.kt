package com.mirror.host

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mirror.host.databinding.ActivityMainBinding
import com.mirror.host.audio.AudioMonitorActivity
import com.mirror.host.gallery.GalleryBrowserActivity
import com.mirror.host.live.LiveCameraActivity
import com.mirror.host.map.MapTrackerActivity
import com.mirror.host.pairing.DevicePairingActivity
import timber.log.Timber

/**
 * Main activity for Mirror Host app.
 * This is the user's main phone for remote monitoring.
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
