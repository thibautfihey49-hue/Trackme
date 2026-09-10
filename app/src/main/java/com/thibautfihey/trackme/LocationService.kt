package com.thibautfihey.trackme
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*

class LocationService : Service() {
    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        const val NOTIFICATION_ID = 12345
        var currentLocation: Location? = null
        var onLocationUpdate: ((Location) -> Unit)? = null
        var isRunning = false
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fused = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { 
                    currentLocation = it
                    onLocationUpdate?.invoke(it) 
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service de localisation TrackMe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Garde la position active en arrière-plan"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TrackMe")
            .setContentText("Localisation active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setColor(Color.parseColor("#2196F3"))
            .setPriority(Notification.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        try {
            val request = LocationRequest.Builder(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(3000)
                .build()
            fused.requestLocationUpdates(request, callback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permissions manquantes", e)
        }

        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fused.removeLocationUpdates(callback)
        } catch (e: Exception) {}
        isRunning = false
    }

    override fun onBind(i: Intent?): IBinder? = null
}
