package com.xeraphion.laporbang.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap

class LocationHelper(private val context: Context, private val googleMap: GoogleMap) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun getMyLocation(requestPermissionLauncher: ActivityResultLauncher<String>) {
        if (checkPermissions()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    googleMap.isMyLocationEnabled = true
                } ?: Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun checkPermissions(): Boolean {
        val foregroundPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return foregroundPermission && backgroundPermission
    }

}