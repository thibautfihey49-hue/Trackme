package com.thibautfihey.trackme

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var etNumber: EditText
    private lateinit var tvStatus: TextView
    private lateinit var map: MapView
    private lateinit var locationManager: LocationManager
    private var myLat = 47.4700
    private var myLon = -0.5500
    private lateinit var myMarker: Marker
    private lateinit var otherMarker: Marker

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "TRACKME_POS" -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    val from = intent.getStringExtra("from") ?: ""
                    runOnUiThread {
                        otherMarker.position = GeoPoint(lat, lon)
                        otherMarker.setVisible(true)
                        map.invalidate()
                        tvStatus.text = "✅ Reçu de $from\n📍 $lat, $lon"
                    }
                }
                "TRACKME_REQ" -> {
                    val from = intent?.getStringExtra("from") ?: ""
                    runOnUiThread {
                        val dest = if (from.startsWith("+")) from else "+$from"
                        try {
                            SmsManager.getDefault().sendDataMessage(
                                dest, null, 7777.toShort(),
                                "POS:$myLat,$myLon".toByteArray(Charsets.UTF_8),
                                null, null
                            )
                            tvStatus.text = "✅ Répondu à $from"
                        } catch (e: Exception) {
                            tvStatus.text = "❌ Erreur envoi: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🔑 CONTOURNEMENT OSMDroid — DÉSACTIVER LE CONTRÔLE DE PERMISSIONS
        try {
            System.setProperty("org.osmdroid.PermissionCheck.disabled", "true")
        } catch (_: Exception) {}

        setContentView(R.layout.activity_main)

        // 📂 Cache DANS le dossier de l'application — AUCUNE permission nécessaire
        val internalDir = File(filesDir, "osmdroid")
        internalDir.mkdirs()
        val tileDir = File(internalDir, "tiles")
        tileDir.mkdirs()

        val config = Configuration.getInstance()
        config.osmdroidBasePath = internalDir
        config.osmdroidTileCache = tileDir
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        etNumber = findViewById(R.id.etNumber)
        tvStatus = findViewById(R.id.tvStatus)
        map = findViewById(R.id.map)

        // 🗺️ Initialisation CARTE
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(12.5)
        map.controller.setCenter(GeoPoint(myLat, myLon))

        // 📍 Marqueur MOI
        myMarker = Marker(map)
        myMarker.position = GeoPoint(myLat, myLon)
        myMarker.title = "📍 Ma position"
        map.overlays.add(myMarker)

        // 📍 Marqueur AUTRE
        otherMarker = Marker(map)
        otherMarker.position = GeoPoint(0.0, 0.0)
        otherMarker.title = "📍 Autre position"
        otherMarker.setVisible(false)
        map.overlays.add(otherMarker)

        // 📡 Récepteur SMS
        registerReceiver(smsReceiver, IntentFilter("TRACKME_POS"))
        registerReceiver(smsReceiver, IntentFilter("TRACKME_REQ"))

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        tvStatus.text = "✅ PRÊT !\nEntre un numéro (+336...)"
        requestPermissions()
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.SEND_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECEIVE_SMS)

        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 100)
        else startGPS()
    }

    override fun onRequestPermissionsResult(rq: Int, p: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rq, p, res)
        if (rq == 100) {
            val ok = res.all { it == PackageManager.PERMISSION_GRANTED }
            tvStatus.text = if (ok) "✅ Permissions OK !" else "⚠️ Permissions limitées"
            if (ok) startGPS()
        }
    }

    private fun startGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)
            tvStatus.text = "✅ GPS démarré"
        } catch (e: Exception) {
            tvStatus.text = "⚠️ GPS indisponible"
        }
    }

    override fun onLocationChanged(location: Location) {
        myLat = location.latitude
        myLon = location.longitude
        myMarker.position = GeoPoint(myLat, myLon)
        map.controller.setCenter(GeoPoint(myLat, myLon))
        map.invalidate()
        tvStatus.text = "📍 $myLat\n$myLon"
    }

    fun sendMyPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) { tvStatus.text = "⚠️ Entre un numéro"; return }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(
                dest, null, 7777.toShort(),
                "POS:$myLat,$myLon".toByteArray(Charsets.UTF_8),
                null, null
            )
            tvStatus.text = "✅ Envoyé à $num"
        } catch (e: Exception) {
            tvStatus.text = "❌ ${e.message}"
        }
    }

    fun requestPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) { tvStatus.text = "⚠️ Entre un numéro"; return }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(
                dest, null, 7777.toShort(),
                "DEMANDE".toByteArray(Charsets.UTF_8),
                null, null
            )
            tvStatus.text = "⏳ Demande envoyée"
        } catch (e: Exception) {
            tvStatus.text = "❌ ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(smsReceiver) } catch (_: Exception) {}
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
