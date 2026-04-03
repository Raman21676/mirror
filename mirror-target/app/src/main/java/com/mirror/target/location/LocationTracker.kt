package com.mirror.target.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class LocationTracker(private val context: Context, private val scope: CoroutineScope) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null
    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) return
        
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000
        ).build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { 
                    scope.launch { Timber.v("Location: ${it.latitude}, ${it.longitude}") }
                }
            }
        }

        callback?.let { client.requestLocationUpdates(request, it, Looper.getMainLooper()) }
        isTracking = true
        Timber.i("Location tracking started")
    }

    fun stopTracking() {
        if (!isTracking) return
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        isTracking = false
        Timber.i("Location tracking stopped")
    }
}
