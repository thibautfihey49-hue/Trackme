package com.thibautfihey.trackme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class TrackerService : Service() {

    companion object {
        const val CHANNEL_ID = "TRACKER_SERVICE"
        const val NOTIFICATION_ID = 1337
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val TAG = "TrackMeService"
        
        var isRunning = false
        var lastLocation: Location? = null
    }

    private lateinit var locationManager: LocationManager
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            Log.d(TAG, "📍 Position: ${location.latitude}, ${location.longitude} — Précision: ${location.accuracy}m")
            
            MainActivity.webView?.let { webView ->
                val js = "if(typeof mettreAJourPosition === 'function') mettreAJourPosition(${location.latitude}, ${location.longitude}, ${location.accuracy})"
                webView.evaluateJavascript(js, null)
            }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isRunning = true
        Log.d(TAG, "✅ Service DÉMARRÉ — Tourne en arrière-plan")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY // ✅ Si système le tue → IL REDÉMARRE TOUT SEUL
    }

    private fun startTracking() {
        try {
            // ✅ VÉRIFICATION DES PERMISSIONS — ÉVITE LES CRASH
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                // ✅ GPS — Màj toutes les 30s ou si déplacement de 10m
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    30000,        // ⏱️ Intervalle min (ms)
                    10f,          // 📍 Distance min (m)
                    locationListener
                )
                
                // ✅ Réseau — Si GPS faible ou indisponible
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    30000,        // ⏱️ Intervalle min (ms)
                    10f,          // 📍 Distance min (m)
                    locationListener
                )
                Log.d(TAG, "✅ GPS + Réseau ACTIF — Màj toutes 30s / 10m")
            } else {
                Log.e(TAG, "❌ Permission GPS non accordée !")
            }
            
            // ✅ Mettre en PREMIÈRE PLAN → SYSTÈME NE PEUT PAS LE TUER
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "✅ Service en PREMIÈRE PLAN — Protégé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur démarrage GPS", e)
        }
    }

    private fun stopTracking() {
        try {
            locationManager.removeUpdates(locationListener)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            Log.d(TAG, "⏹️ Service ARRÊTÉ")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur arrêt service", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TrackMe — Suivi GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service de suivi GPS en arrière-plan"
                setShowBadge(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 TrackMe — ACTIF")
            .setContentText("Suivi GPS en cours — Tourne en arrière-plan")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // ✅ IMPOSSIBLE DE SUPPRIMER PAR ERREUR
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
