package com.thibautfihey.trackme
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var etNum: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvMyLoc: TextView
    private lateinit var btnReq: Button
    private lateinit var btnSend: Button
    private lateinit var btnTrack: Button
    private var myLoc: GeoPoint? = null
    private var otherLoc: GeoPoint? = null
    private var myMarker: Marker? = null
    private var otherMarker: Marker? = null
    private var timer: Timer? = null
    private val sms = SmsManager.getDefault()

    private val perms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )
    
    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.all { it.value }) initApp()
        else Toast.makeText(this, "Permissions requises", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        initViews()
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            initApp()
        } else {
            reqPerm.launch(perms)
        }
    }

    private fun initViews() {
        map = findViewById(R.id.mapView)
        etNum = findViewById(R.id.etPhoneNumber)
        tvStatus = findViewById(R.id.tvStatus)
        tvMyLoc = findViewById(R.id.tvMyLocation)
        btnReq = findViewById(R.id.btnRequestPosition)
        btnSend = findViewById(R.id.btnSendPosition)
        btnTrack = findViewById(R.id.btnTrack)
        
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        
        btnReq.setOnClickListener { reqPos() }
        btnSend.setOnClickListener { sendPos() }
        btnTrack.setOnClickListener { toggleTrack() }
    }

    private fun initApp() {
        startService(Intent(this, LocationService::class.java))
        
        SmsReceiver.onRequestReceived = { from ->
            sendPosTo(from)
            runOnUiThread { tvStatus.text = "📩 Demande reçue → Répondu" }
        }
        
        SmsReceiver.onPositionReceived = { lat, lon, from ->
            runOnUiThread {
                otherLoc = GeoPoint(lat, lon)
                updateOtherMarker()
                tvStatus.text = "✅ De $from\n${"%.6f".format(lat)}, ${"%.6f".format(lon)}"
            }
        }
        
        LocationService.onLocationUpdate = { loc ->
            runOnUiThread {
                myLoc = GeoPoint(loc.latitude, loc.longitude)
                tvMyLoc.text = "📍 Ma pos: ${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}"
                updateMyMarker()
            }
        }
        
        tvStatus.text = "✅ Prêt — entrez un numéro"
    }

    private fun sendSms(dest: String, msg: String) {
        try {
            val finalDest = if (!dest.startsWith("+")) "+$dest" else dest
            sms.sendTextMessage(finalDest, null, msg, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNum(): String? {
        val n = etNum.text.toString().trim()
        if (n.isEmpty()) {
            Toast.makeText(this, "Entrez un numéro", Toast.LENGTH_SHORT).show()
            return null
        }
        return n
    }

    private fun reqPos() {
        val n = getNum() ?: return
        tvStatus.text = "📥 Demande..."
        sendSms(n, "TRACKME:REQUEST")
    }

    private fun sendPosTo(dest: String) {
        val l = myLoc ?: return
        sendSms(dest, "TRACKME:POS:${l.latitude},${l.longitude}")
    }

    private fun sendPos() {
        val n = getNum() ?: return
        sendPosTo(n)
        tvStatus.text = "📤 Position envoyée"
    }

    private fun toggleTrack() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
            btnTrack.text = "🔄 Suivre 1 min"
            tvStatus.text = "✅ Arrêté"
        } else {
            val n = getNum() ?: return
            btnTrack.text = "⏹️ Arrêter"
            tvStatus.text = "🔄 Suivi en cours..."
            var sec = 60
            timer = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            sec -= 10
                            if (sec <= 0) {
                                cancel()
                                timer = null
                                btnTrack.text = "🔄 Suivre 1 min"
                                tvStatus.text = "✅ Terminé"
                            } else {
                                sendSms(n, "TRACKME:REQUEST")
                                tvStatus.text = "🔄 Encore $sec s..."
                            }
                        }
                    }
                }, 0, 10000)
            }
        }
    }

    private fun updateMyMarker() {
        val l = myLoc ?: return
        if (myMarker == null) {
            myMarker = Marker(map).apply {
                position = l
                title = "Moi"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(myMarker)
        } else {
            myMarker!!.position = l
        }
        map.controller.setCenter(l)
    }

    private fun updateOtherMarker() {
        val l = otherLoc ?: return
        if (otherMarker == null) {
            otherMarker = Marker(map).apply {
                position = l
                title = "Autre"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_mylocation)
            }
            map.overlays.add(otherMarker)
        } else {
            otherMarker!!.position = l
        }
        map.invalidate()
    }
}
