package com.xeraphion.laporbang.ui.maps


import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.xeraphion.laporbang.Ztempdelete.GeofenceBroadcastReceiver

class GeofenceHelper(private val context: Context) {

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun addGeofence(
        id: String,
        lat: Double,
        lng: Double,
        radius: Float,
        transitionType: Int,
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitionType)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent()

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                // Geofence added successfully
            }
            .addOnFailureListener {
                // Handle failure
            }
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}