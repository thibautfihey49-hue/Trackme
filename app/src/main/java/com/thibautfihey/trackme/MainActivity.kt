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
        setupSmsReceivers()
        requestNecessaryPermissions()
    }

    private fun setupSmsReceivers() {
        val mainActivity = this
        SmsReceiver.onPhotoReceived = { from, bitmap ->
            mainActivity.runOnUiThread {
                mainActivity.ivPhoto.visibility = View.VISIBLE
                mainActivity.ivPhoto.setImageBitmap(bitmap)
                mainActivity.tvStatus.text = "PHOTO REÇUE de $from"
            }
        }
        SmsReceiver.onVideoReceived = { from, videoUrl ->
            mainActivity.runOnUiThread {
                mainActivity.tvStatus.text = "VIDÉO REÇUE de $from"
            }
        }
        SmsReceiver.onPositionReceived = { lat, lon, from ->
            mainActivity.runOnUiThread {
                mainActivity.otherLocation = GeoPoint(lat, lon)
                mainActivity.updateOtherMarker()
                mainActivity.tvStatus.text = "POSITION: $lat / $lon de $from"
            }
        }
        SmsReceiver.onRequestReceived = { from ->
            mainActivity.runOnUiThread {
                mainActivity.currentLocation?.let {
                    mainActivity.sendSmsData(from, "POSITION:${it.latitude},${it.longitude}")
                    mainActivity.tvStatus.text = "Position envoyée à $from"
                }
            }
        }
    }

    private fun initMapDirect() {
        val startPoint = GeoPoint(47.47, -0.55)
        mapController.setCenter(startPoint)
    }

    private fun startLocationUpdatesDirect() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, this)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, this)
            } catch (e: Exception) {}
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
            map.overlays.add(marker)
            map.invalidate()
        }
    }

    fun sendMyPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) { tvStatus.text = "Entre un numéro"; return }
        currentLocation?.let {
            sendSmsData(num, "POSITION:${it.latitude},${it.longitude}")
            tvStatus.text = "Position envoyée !"
        }
    }

    fun requestPosition(v: View) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) { tvStatus.text = "Entre un numéro"; return }
        sendSmsData(num, "REQUEST_POSITION")
        tvStatus.text = "Demande envoyée..."
    }

    fun sendPhotoBack(v: View) = sendSmsCmd("PHOTO")
    fun sendPhotoFront(v: View) = sendSmsCmd("PHOTO:FRONT")
    fun sendVideoBack(v: View) = sendSmsCmd("VIDEO")
    fun sendVideoFront(v: View) = sendSmsCmd("VIDEO:FRONT")

    private fun sendSmsCmd(cmd: String) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) { tvStatus.text = "Entre un numéro"; return }
        sendSmsData(num, cmd)
        tvStatus.text = "Commande envoyée : $cmd"
    }

    private fun sendSmsData(num: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "Permission SMS manquante"
            return
        }
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(dest, null, 7777.toShort(),
                "TRACKME:$message".toByteArray(Charsets.UTF_8), null, null)
        } catch (e: Exception) {
            tvStatus.text = "Erreur: ${e.message}"
        }
    }

    private fun requestNecessaryPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.SEND_SMS)
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS)
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.CAMERA)
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (perms.isNotEmpty()) requestPermissionsLauncher.launch(perms.toTypedArray())
        else { tvStatus.text = "Prêt"; initMapDirect(); startLocationUpdatesDirect() }
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) {
            tvStatus.text = "Permissions OK"; initMapDirect(); startLocationUpdatesDirect()
        } else tvStatus.text = "Permissions refusées"
    }
}
