package com.mirror.target

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mirror.target.service.MirrorTargetService
import com.mirror.target.util.PermissionHelper
import timber.log.Timber

/**
 * Main activity for Mirror Target app.
 * This runs on the old phone acting as CCTV server.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }

    private lateinit var statusTextView: TextView
    private lateinit var ipTextView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var pairButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initViews()
        checkPermissions()
        updateStatus()
    }

    private fun initViews() {
        statusTextView = findViewById(R.id.status_text)
        ipTextView = findViewById(R.id.ip_text)
        ipTextView.text = "IP: ${getWifiIpAddress() ?: "Unknown"}"
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        pairButton = findViewById(R.id.pair_button)
        settingsButton = findViewById(R.id.settings_button)

        startButton.setOnClickListener { startService() }
        stopButton.setOnClickListener { stopService() }
        pairButton.setOnClickListener { startPairing() }
        settingsButton.setOnClickListener { openSettings() }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        
        // Check battery optimization
        if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            showBatteryOptimizationDialog()
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun startService() {
        if (!PermissionHelper.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, MirrorTargetService::class.java)
        intent.action = MirrorTargetService.ACTION_START
        startForegroundService(intent)
        
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun stopService() {
        val intent = Intent(this, MirrorTargetService::class.java)
        intent.action = MirrorTargetService.ACTION_STOP
        startService(intent)
        
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun startPairing() {
        startActivity(Intent(this, PairingActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    private fun getWifiIpAddress(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return null
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xFF,
                ipInt shr 8 and 0xFF,
                ipInt shr 16 and 0xFF,
                ipInt shr 24 and 0xFF
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get WiFi IP")
            null
        }
    }

    private fun updateStatus() {
        val isRunning = MirrorTargetService.isRunning
        statusTextView.text = if (isRunning) {
            getString(R.string.status_running)
        } else {
            getString(R.string.status_stopped)
        }
        statusTextView.setTextColor(
            if (isRunning) {
                getColor(android.R.color.holo_green_dark)
            } else {
                getColor(android.R.color.holo_red_dark)
            }
        )
        
        startButton.isEnabled = !isRunning
        stopButton.isEnabled = isRunning
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!PermissionHelper.hasPermissions(this, REQUIRED_PERMISSIONS)) {
                Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_LONG).show()
            }
        }
    }
}
