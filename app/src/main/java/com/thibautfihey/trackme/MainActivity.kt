package com.thibautfihey.trackme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var etNumber: EditText
    private lateinit var tvStatus: TextView
    private lateinit var map: MapView
    private lateinit var locationManager: LocationManager
    private var myLat = 0.0
    private var myLon = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumber = findViewById(R.id.etNumber)
        tvStatus = findViewById(R.id.tvStatus)
        map = findViewById(R.id.map)

        // Initialisation carte
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(47.47, -0.55))

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        tvStatus.text = "✅ Application démarrée !\nEn attente de permissions..."

        requestPermissions()
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.SEND_SMS)
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        } else {
            startGPS()
        }
    }

    private fun startGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
                tvStatus.text = "✅ GPS démarré !\nEntre un numéro en haut"
            } catch (e: Exception) {
                tvStatus.text = "⚠️ GPS indisponible"
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        myLat = location.latitude
        myLon = location.longitude
        tvStatus.text = "📍 Ma position :\n$myLat\n$myLon"
        map.controller.setCenter(GeoPoint(myLat, myLon))
    }

    fun envoyerPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord !"
            return
        }
        if (myLat == 0.0) {
            tvStatus.text = "⏳ Position GPS pas encore disponible...\nBouge un peu !"
            return
        }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(dest, null, 7777.toShort(),
                "POS:$myLat,$myLon".toByteArray(Charsets.UTF_8), null, null)
            tvStatus.text = "✅ Position envoyée à $num"
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur : ${e.message}"
        }
    }

    fun demanderPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord !"
            return
        }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(dest, null, 7777.toShort(),
                "DEMANDE".toByteArray(Charsets.UTF_8), null, null)
            tvStatus.text = "⏳ Demande envoyée à $num"
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur : ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            tvStatus.text = "✅ Permissions OK !\nDémarrage GPS..."
            startGPS()
        }
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
