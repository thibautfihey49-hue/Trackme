package com.thibautfihey.trackme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var etPhoneNumber: EditText
    private lateinit var tvMyLocation: TextView
    private lateinit var btnRequestPosition: Button
    private lateinit var btnSendPosition: Button
    private lateinit var btnTrack: Button
    private lateinit var btnCenterMe: Button
    private lateinit var btnEnableGps: Button
    private lateinit var btnStopGps: Button

    private lateinit var locationManager: LocationManager
    private var myLocation: GeoPoint? = null
    private var isTracking = false

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialiser les vues
        mapView = findViewById(R.id.mapView)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        tvMyLocation = findViewById(R.id.tvMyLocation)
        btnRequestPosition = findViewById(R.id.btnRequestPosition)
        btnSendPosition = findViewById(R.id.btnSendPosition)
        btnTrack = findViewById(R.id.btnTrack)
        btnCenterMe = findViewById(R.id.btnCenterMe)
        btnEnableGps = findViewById(R.id.btnEnableGps)
        btnStopGps = findViewById(R.id.btnStopGps)

        // Configurer la carte
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // 🎯 DEMANDER LES PERMISSIONS AUTOMATIQUEMENT AU DÉMARRAGE
        demanderPermissions()

        // Boutons
        btnEnableGps.setOnClickListener { demanderPermissions() }
        btnCenterMe.setOnClickListener { centrerSurMoi() }
        btnSendPosition.setOnClickListener { envoyerPosition() }
        btnRequestPosition.setOnClickListener { demanderPosition() }
        btnTrack.setOnClickListener { suiviAutomatique() }
        btnStopGps.setOnClickListener { arreterGPS() }
    }

    // 🎯 DEMANDE DE PERMISSIONS — POPUP DIRECTE
    private fun demanderPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }

        if (permissions.isNotEmpty()) {
            // ✅ AFFICHE LA POPUP SYSTÈME — COMME SUR LES SITES WEB !
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            // ✅ Déjà autorisé → activer le GPS
            Toast.makeText(this, "✅ Permissions déjà accordées !", Toast.LENGTH_SHORT).show()
            activerGPS()
        }
    }

    // 📋 RÉPONSE DE LA POPUP
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val toutAccorde = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (toutAccorde) {
                Toast.makeText(this, "✅ Permissions accordées ! GPS activé 🎉", Toast.LENGTH_LONG).show()
                activerGPS()
            } else {
                Toast.makeText(this, "⚠️ Certaines permissions sont nécessaires", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun activerGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "👉 Active la localisation dans les paramètres", Toast.LENGTH_LONG).show()
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    90000, // 1min30
                    20f,   // 20 mètres
                    locationListener
                )
                val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (last != null) mettreAJourPosition(last)
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            mettreAJourPosition(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun mettreAJourPosition(location: Location) {
        myLocation = GeoPoint(location.latitude, location.longitude)
        tvMyLocation.text = "📍 Moi: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}\n🎯 Précision: ${location.accuracy.toInt()}m"
        mapView.controller.setCenter(myLocation)
        mapView.invalidate()
    }

    private fun centrerSurMoi() {
        myLocation?.let {
            mapView.controller.setCenter(it)
            Toast.makeText(this, "✅ Centré sur votre position", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "⚠️ Position non disponible", Toast.LENGTH_SHORT).show()
    }

    private fun envoyerPosition() {
        val numero = etPhoneNumber.text.toString().trim()
        if (numero.isEmpty()) {
            Toast.makeText(this, "⚠️ Entre d'abord un numéro !", Toast.LENGTH_SHORT).show()
            return
        }
        myLocation?.let {
            val msg = "POS:${it.latitude},${it.longitude}"
            android.telephony.SmsManager.getDefault().sendTextMessage(numero, null, msg, null, null)
            Toast.makeText(this, "✅ Position envoyée à $numero", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "⚠️ Position non disponible", Toast.LENGTH_SHORT).show()
    }

    private fun demanderPosition() {
        val numero = etPhoneNumber.text.toString().trim()
        if (numero.isEmpty()) {
            Toast.makeText(this, "⚠️ Entre d'abord un numéro !", Toast.LENGTH_SHORT).show()
            return
        }
        android.telephony.SmsManager.getDefault().sendTextMessage(numero, null, "GET_POS", null, null)
        Toast.makeText(this, "✅ Demande envoyée à $numero", Toast.LENGTH_SHORT).show()
    }

    private fun suiviAutomatique() {
        val numero = etPhoneNumber.text.toString().trim()
        if (numero.isEmpty()) {
            Toast.makeText(this, "⚠️ Entre d'abord un numéro !", Toast.LENGTH_SHORT).show()
            return
        }
        isTracking = !isTracking
        if (isTracking) {
            btnTrack.text = "⏹️ Arrêter le suivi"
            Toast.makeText(this, "🔄 Suivi automatique ACTIF !", Toast.LENGTH_SHORT).show()
        } else {
            btnTrack.text = "🔄 Suivi automatique"
            Toast.makeText(this, "⏹️ Suivi arrêté", Toast.LENGTH_SHORT).show()
        }
    }

    private fun arreterGPS() {
        locationManager.removeUpdates(locationListener)
        isTracking = false
        btnTrack.text = "🔄 Suivi automatique"
        Toast.makeText(this, "⏹️ GPS arrêté", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
