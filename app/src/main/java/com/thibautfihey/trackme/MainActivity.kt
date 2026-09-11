package com.thibautfihey.trackme
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import java.io.File
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

    private val perms = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.WAKE_LOCK
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }.toTypedArray()
    
    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.all { it.value }) checkBatteryOptimization()
        else {
            Toast.makeText(this, "⚠️ Toutes les permissions sont requises", Toast.LENGTH_LONG).show()
            tvStatus.text = "❌ Permissions refusées"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val baseDir = File(filesDir, "osmdroid")
        if (!baseDir.exists()) baseDir.mkdirs()
        val tileDir = File(baseDir, "tiles")
        if (!tileDir.exists()) tileDir.mkdirs()
        
        val osmConfig = Configuration.getInstance()
        osmConfig.osmdroidBasePath = baseDir
        osmConfig.osmdroidTileCache = tileDir
        osmConfig.load(this, getSharedPreferences("osm", MODE_PRIVATE))
        
        initViews()
        checkPermissions()
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
        map.isTilesScaledToDpi = true
        map.controller.setCenter(GeoPoint(47.4768, -0.5435))
        
        btnReq.setOnClickListener { reqPos() }
        btnSend.setOnClickListener { sendPos() }
        btnTrack.setOnClickListener { toggleTrack() }
    }

    private fun checkPermissions() {
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (needed.isEmpty()) checkBatteryOptimization()
        else {
            tvStatus.text = "⏳ Demande des permissions..."
            reqPerm.launch(needed)
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
            val pkg = packageName
            if (!powerMgr.isIgnoringBatteryOptimizations(pkg)) {
                tvStatus.text = "⚠️ Désactiver l'optimisation batterie\npour que le service continue en arrière-plan"
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
                startActivity(intent)
                return
            }
        }
        initApp()
    }

    private fun initApp() {
        tvStatus.text = "🟢 SERVICE DÉMARRÉ\n✅ Ferme l'app → le service continue !\n📍 Suivi toutes les minutes actif"
        startService(Intent(this, LocationService::class.java))
        
        SmsReceiver.onRequestReceived = { from ->
            sendPosTo(from)
            runOnUiThread { 
                tvStatus.text = "📩 Demande de $from → Répondu\n✅ Service actif en arrière-plan" 
            }
        }
        
        SmsReceiver.onPositionReceived = { lat, lon, from ->
            runOnUiThread {
                otherLoc = GeoPoint(lat, lon)
                updateOtherMarker()
                tvStatus.text = "✅ POSITION REÇUE DE $from\n📍 ${"%.6f".format(lat)}, ${"%.6f".format(lon)}"
            }
        }
        
        LocationService.onLocationUpdate = { loc ->
            runOnUiThread {
                myLoc = GeoPoint(loc.latitude, loc.longitude)
                tvMyLoc.text = "📍 MA POSITION:\n${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}"
                updateMyMarker()
            }
        }
    }

    private fun normalizeNumber(dest: String): String {
        var n = dest.trim().replace("\\s".toRegex(), "").replace("-", "")
        if (n.startsWith("06") && n.length == 10) n = "+33" + n.drop(1)
        if (n.startsWith("07") && n.length == 10) n = "+33" + n.drop(1)
        if (n.startsWith("336") && n.length == 11) n = "+$n"
        if (n.startsWith("337") && n.length == 11) n = "+$n"
        if (n.startsWith("00336")) n = "+33" + n.drop(4)
        if (n.startsWith("00337")) n = "+33" + n.drop(4)
        return n
    }

    private fun sendSms(dest: String, msg: String) {
        val finalDest = normalizeNumber(dest)
        try {
            val data = msg.toByteArray(Charsets.UTF_8)
            sms.sendDataMessage(finalDest, null, 7777.toShort(), data, null, null)
            return
        } catch (e: Exception) {}
        try {
            sms.sendTextMessage(finalDest, null, msg, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNum(): String? {
        val n = etNum.text.toString().trim()
        if (n.isEmpty()) {
            Toast.makeText(this, "Entrez un numéro", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!n.matches(Regex("^(\\+?33|0033)?[67]\\d{8}|0[67]\\d{8}$"))) {
            Toast.makeText(this, "⚠️ Seulement 06 ou 07 accepté", Toast.LENGTH_SHORT).show()
            return null
        }
        return n
    }

    private fun reqPos() {
        val n = getNum() ?: return
        tvStatus.text = "📤 Demande de position..."
        sendSms(n, "TRACKME:REQUEST")
    }

    private fun sendPosTo(dest: String) {
        val l = myLoc ?: run {
            Toast.makeText(this, "⚠️ Position pas encore disponible", Toast.LENGTH_SHORT).show()
            return
        }
        sendSms(dest, "TRACKME:POS:${l.latitude},${l.longitude}")
    }

    private fun sendPos() {
        val n = getNum() ?: return
        sendPosTo(n)
        tvStatus.text = "📤 Position envoyée !\n✅ Service actif en arrière-plan"
    }

    private fun toggleTrack() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
            btnTrack.text = "🔄 SUIVRE TOUTES LES MINUTES"
            tvStatus.text = "✅ Suivi ARRÊTÉ\n✅ Service continue en arrière-plan"
        } else {
            val n = getNum() ?: return
            btnTrack.text = "⏹️ ARRÊTER LE SUIVI"
            tvStatus.text = "🔄 SUIVI EN COURS — Toutes les minutes\n✅ Ferme l'app → ça continue !"
            
            timer = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        sendSms(n, "TRACKME:REQUEST")
                    }
                }, 0, 60000)
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

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    // ✅ Quand tu fermes l'app → minimiser, NE PAS arrêter le service
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
