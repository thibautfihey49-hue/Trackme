package com.thibautfihey.trackme
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*

class LocationService : Service() {
    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        var currentLocation: Location?=null
        var onLocationUpdate: ((Location)->Unit)?=null
        var isRunning = false
    }
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    override fun onCreate() {
        super.onCreate()
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID,"Service Localisation",NotificationManager.IMPORTANCE_LOW)
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        fused = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let{ currentLocation=it; onLocationUpdate?.invoke(it) }
            }
        }
    }
    override fun onStartCommand(i:Intent?,f:Int,id:Int):Int {
        startForeground(1, Notification.Builder(this,CHANNEL_ID)
            .setContentTitle("TrackMe").setContentText("Actif")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).build())
        try { fused.requestLocationUpdates(LocationRequest.Builder(5000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build(),callback,mainLooper) }
        catch(e:SecurityException){}
        isRunning=true
        return START_STICKY
    }
    override fun onDestroy() { super.onDestroy(); fused.removeLocationUpdates(callback); isRunning=false }
    override fun onBind(i:Intent?):IBinder?=null
}
