package com.thibautfihey.trackme

import android.Manifest
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var etNumber: EditText
    private lateinit var tvStatus: TextView
    private var map: MapView? = null
    private var locationManager: LocationManager? = null
    private var myLat = 47.47
    private var myLon = -0.55

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumber = findViewById(R.id.etNumber)
        tvStatus = findViewById(R.id.tvStatus)

        try {
            map = findViewById(R.id.map)
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
            map?.setTileSource(TileSourceFactory.MAPNIK)
            map?.setMultiTouchControls(true)
            map?.controller?.setZoom(15.0)
            map?.controller?.setCenter(GeoPoint(myLat, myLon))
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur carte: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur GPS: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        tvStatus.text = "✅ PRÊT !\n→ Entre un numéro (ex: +336...)"
        
        requestPermissions()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startGPS()
            return
        }

        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.SEND_SMS)
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECEIVE_SMS)

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        } else {
            startGPS()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val ok = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            tvStatus.text = if (ok) "✅ Permissions OK !" else "⚠️ Permissions limitées"
            startGPS()
        }
    }

    private fun startGPS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)
        } catch (e: Exception) {
            tvStatus.text = "⚠️ GPS indisponible"
        }
    }

    override fun onLocationChanged(location: Location) {
        myLat = location.latitude
        myLon = location.longitude
        tvStatus.text = "📍 $myLat\n$myLon"
        try {
            map?.controller?.setCenter(GeoPoint(myLat, myLon))
        } catch (e: Exception) {}
    }

    fun sendMyPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro"
            return
        }
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
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro"
            return
        }
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

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
