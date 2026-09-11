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
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
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
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // ✅ WakeLock : CPU ne se met pas en veille
        val powerMgr = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerMgr.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "TrackMe:WakeLock"
        ).apply { acquire(10*60*1000L) }
        
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
                "TrackMe — Service actif",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Localisation & SMS actifs en arrière-plan"
                enableLights(false)
                enableVibration(false)
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🟢 TrackMe — SERVICE ACTIF")
            .setContentText("Ferme l'app → le service continue de fonctionner")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setColor(Color.parseColor("#00C853"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        try {
            val request = LocationRequest.Builder(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(3000)
                .build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
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
        try {
            wakeLock?.release()
        } catch (e: Exception) {}
        isRunning = false
        Log.w("LocationService", "Service détruit — redémarrage auto")
    }

    // ✅ QUAND TU FERMES L'APP → SEUL LE SERVICE SE RELANCE, PAS L'INTERFACE
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocationService", "✅ App fermée par l'utilisateur — SERVICE CONTINUE EN ARRIÈRE-PLAN")
        
        // ✅ RELANCE SEULEMENT LE SERVICE, PAS L'ACTIVITÉ
        val restartIntent = Intent(applicationContext, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
}
