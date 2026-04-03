package com.mirror.host.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.mirror.host.R

/**
 * Activity for viewing target device location on map.
 */
class MapTrackerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_tracker)
        
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // Add a marker at default location and move camera
        val defaultLocation = LatLng(0.0, 0.0)
        mMap.addMarker(MarkerOptions().position(defaultLocation).title("Target Device"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(defaultLocation))
    }
}
