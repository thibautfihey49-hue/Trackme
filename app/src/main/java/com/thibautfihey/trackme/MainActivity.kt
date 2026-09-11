package com.thibautfihey.trackme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.telephony.SmsManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity(), LocationListener {
    
    // ===== TOUTES LES PROPRIÉTÉS EN PREMIER =====
    private lateinit var etNumber: EditText
    private lateinit var ivPhoto: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private lateinit var locationManager: LocationManager
    private var currentLocation: GeoPoint? = null
    private var otherLocation: GeoPoint? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumber = findViewById(R.id.etNumber)
        ivPhoto = findViewById(R.id.ivPhoto)
        tvStatus = findViewById(R.id.tvStatus)
        map = findViewById(R.id.map)

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        mapController = map.controller
        mapController.setZoom(15.0)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        SmsReceiver.onPhotoReceived = { from, bitmap ->
            runOnUiThread {
                ivPhoto.visibility = View.VISIBLE
                ivPhoto.setImageBitmap(bitmap)
                tvStatus.text = "✅ PHOTO REÇUE !\nDe : $from"
            }
        }

        SmsReceiver.onVideoReceived = { from, videoUrl ->
            runOnUiThread {
                tvStatus.text = "✅ VIDÉO REÇUE !\nDe : $from\nLien : $videoUrl"
            }
        }

        SmsReceiver.onPositionReceived = { lat, lon, from ->
            runOnUiThread {
                otherLocation = GeoPoint(lat, lon)
                updateOtherMarker()
                tvStatus.text = "✅ POSITION REÇUE !\nDe : $from\nLat: $lat\nLon: $lon"
            }
        }
        
        SmsReceiver.onRequestReceived = { from ->
            runOnUiThread {
                currentLocation?.let {
                    sendSmsData(from, "POSITION:${it.latitude},${it.longitude}")
                    tvStatus.text = "✅ Position envoyée à $from"
                } ?: run {
                    tvStatus.text = "⚠️ Position pas encore disponible"
                }
            }
        }

        requestNecessaryPermissions()
    }

    private fun initMapDirect() {
        val startPoint = GeoPoint(47.47, -0.55)
        mapController.setCenter(startPoint)
    }

    private fun startLocationUpdatesDirect() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000L, 10f, this
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L, 10f, this
                )
            } catch (e: Exception) {
                tvStatus.text = "⚠️ GPS indisponible"
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = GeoPoint(location.latitude, location.longitude)
        updateMyMarker()
    }

    private fun updateMyMarker() {
        currentLocation?.let { point ->
            map.overlays.removeAll { it is Marker && it.id == "my_position" }
            val marker = Marker(map)
            marker.id = "my_position"
            marker.position = point
            marker.title = "Ma position"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(marker)
            mapController.setCenter(point)
            map.invalidate()
        }
    }

    private fun updateOtherMarker() {
        otherLocation?.let { point ->
            map.overlays.removeAll { it is Marker && it.id == "other_position" }
            val marker = Marker(map)
            marker.id = "other_position"
            marker.position = point
            marker.title = "Position de l'autre"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(marker)
            map.invalidate()
        }
    }

    fun sendMyPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord"
            return
        }
        currentLocation?.let {
            sendSmsData(num, "POSITION:${it.latitude},${it.longitude}")
            tvStatus.text = "✅ Position envoyée !"
        } ?: run {
            tvStatus.text = "⚠️ Position GPS non disponible encore"
        }
    }

    fun requestPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord"
            return
        }
        sendSmsData(num, "REQUEST_POSITION")
        tvStatus.text = "⏳ Demande de position envoyée..."
    }

    fun sendPhotoBack(v: View) = sendSmsCmd("PHOTO")
    fun sendPhotoFront(v: View) = sendSmsCmd("PHOTO:FRONT")
    fun sendVideoBack(v: View) = sendSmsCmd("VIDEO")
    fun sendVideoFront(v: View) = sendSmsCmd("VIDEO:FRONT")

    private fun sendSmsCmd(cmd: String) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord"
            return
        }
        sendSmsData(num, cmd)
        tvStatus.text = "⏳ Commande envoyée : $cmd"
    }

    private fun sendSmsData(num: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "⚠️ Permission SEND_SMS manquante"
            return
        }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(
                dest, null, 7777.toShort(),
                "TRACKME:$message".toByteArray(Charsets.UTF_8), null, null
            )
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur : ${e.message}"
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.SEND_SMS)
            }
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.CAMERA)
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            tvStatus.text = "✅ Toutes permissions déjà accordées ! Prêt."
            initMapDirect()
            startLocationUpdatesDirect()
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ===== LE LAUNCHER TOUT EN BAS — APRÈS TOUTES LES PROPRIÉTÉS =====
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            tvStatus.text = "✅ Toutes permissions accordées ! Prêt."
            initMapDirect()
            startLocationUpdatesDirect()
        } else {
            tvStatus.text = "⚠️ Permissions refusées — Certaines fonctionnalités limitées"
        }
    }
}
