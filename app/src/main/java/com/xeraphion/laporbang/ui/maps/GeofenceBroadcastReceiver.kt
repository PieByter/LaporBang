package com.xeraphion.laporbang.ui.maps

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.xeraphion.laporbang.R

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_GEOFENCE_EVENT) {
            val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

            if (geofencingEvent.hasError()) {
                val errorMessage =
                    GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
                Log.e(TAG, errorMessage)
                sendNotification(context, "Error: $errorMessage")
                return
            }

            val geofenceTransition = geofencingEvent.geofenceTransition
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    triggeringGeofences?.forEach { geofence ->
                        val geofenceTransitionDetails = "Anda memasuki area ${geofence.requestId}"
                        Log.i(TAG, geofenceTransitionDetails)
                        sendNotification(context, geofenceTransitionDetails)
                    }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    triggeringGeofences?.forEach { geofence ->
                        Log.i(TAG, "Keluar dari area ${geofence.requestId}, geofence akan di-reset")
                        resetGeofence(context, geofence)
                    }
                }
                else -> {
                    Log.e(TAG, "Invalid transition type: $geofenceTransition")
                }
            }
        }

//            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {
//                val geofenceTransitionString =
//                    when (geofenceTransition) {
//                        Geofence.GEOFENCE_TRANSITION_ENTER -> "Hati-hati, Anda mendekati lubang."
//                        Geofence.GEOFENCE_TRANSITION_DWELL -> "Anda berada di area lubang."
//                        else -> "Invalid transition type"
//                    }
//
//                val triggeringGeofences = geofencingEvent.triggeringGeofences
//                triggeringGeofences?.forEach { geofence ->
//                    val geofenceTransitionDetails =
//                        "$geofenceTransitionString ${geofence.requestId}"
//                    Log.i(TAG, geofenceTransitionDetails)
//                    sendNotification(context, geofenceTransitionDetails)
//                    resetGeofence(context, geofence)
//                }
//
//            } else {
//                val errorMessage = "Invalid transition type : $geofenceTransition"
//                Log.e(TAG, errorMessage)
//                sendNotification(context, errorMessage)
//            }
//        }
    }


    private fun sendNotification(context: Context, geofenceTransitionDetails: String) {
        val mNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Anda berada di area berlubang.")
            .setContentText("Hati-hati, Anda mendekati lubang.")
            .setSmallIcon(R.drawable.ic_notification)
//            .setContentTitle(geofenceTransitionDetails)
        val channel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        mBuilder.setChannelId(CHANNEL_ID)
        mNotificationManager.createNotificationChannel(channel)

        val notification = mBuilder.build()
        mNotificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun resetGeofence(context: Context, geofence: Geofence) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val geofencingClient = LocationServices.getGeofencingClient(context)
            geofencingClient.removeGeofences(listOf(geofence.requestId)).addOnCompleteListener {
                val geofencingRequest = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build()

                val geofencePendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, GeofenceBroadcastReceiver::class.java).apply {
                        action = ACTION_GEOFENCE_EVENT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener {
                        Log.d("Geofence", "Geofence reset successfully for ${geofence.requestId}")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("Geofence", "Failed to reset geofence: ${exception.message}")
                    }
            }
        } else {
            Log.e("Geofence", "Permission not granted for resetting geofence")
        }
    }


    companion object {
        private const val TAG = "GeofenceBroadcast"
        const val ACTION_GEOFENCE_EVENT = "GeofenceEvent"
        private const val CHANNEL_ID = "1"
        private const val CHANNEL_NAME = "Geofence Channel"
        private const val NOTIFICATION_ID = 1
    }
}