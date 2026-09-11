package com.thibautfihey.trackme

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.provider.Telephony
import android.telephony.SmsMessage

class MainActivity : AppCompatActivity() {

    companion object {
        lateinit var webView: WebView
            private set
    }

    private val SMS_PORT = 7777
    private val SMS_SENT_ACTION = "com.thibautfihey.trackme.SMS_SENT"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MainActivity.webView = WebView(this)
        setContentView(MainActivity.webView)

        MainActivity.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setGeolocationEnabled(true)
        }
        
        MainActivity.webView.addJavascriptInterface(WebAppInterface(this), "Android")
        MainActivity.webView.webViewClient = object : WebViewClient() {}
        
        MainActivity.webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }

        MainActivity.webView.loadUrl("file:///android_asset/index.html")
        checkPermissions()
        
        // ✅ RÉCEPTEUR DES SMS
        val smsFilter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, smsFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, smsFilter)
        }
        
        val sentFilter = IntentFilter(SMS_SENT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, sentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsSentReceiver, sentFilter)
        }
        
        // ✅ RÉCEPTEUR DES MISES À JOUR DE POSITION DU SERVICE
        val positionFilter = IntentFilter(TrackerService.ACTION_POSITION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionUpdateReceiver, positionFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(positionUpdateReceiver, positionFilter)
        }
    }

    // ✅ RECEVOIR LA POSITION DU SERVICE ET L'ENVOYER À LA WEBVIEW
    private val positionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val lat = intent.getDoubleExtra(TrackerService.EXTRA_LAT, 0.0)
            val lon = intent.getDoubleExtra(TrackerService.EXTRA_LON, 0.0)
            val acc = intent.getFloatExtra(TrackerService.EXTRA_ACC, 0f)
            
            if (lat != 0.0 && lon != 0.0) {
                runOnUiThread {
                    val js = "mettreAJourPosition($lat, $lon, $acc)"
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.SEND_SMS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECEIVE_SMS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_SMS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.FOREGROUND_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val ok = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Toast.makeText(this, if(ok) "✅ Toutes permissions accordées" else "⚠️ Certaines permissions manquent", Toast.LENGTH_SHORT).show()
        }
    }

    inner class WebAppInterface(private val context: Context) {
        
        @JavascriptInterface
        fun demarrerService() {
            val intent = Intent(context, TrackerService::class.java)
            intent.action = TrackerService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(context, "✅ Service de suivi DÉMARRÉ — Tourne en arrière-plan", Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun arreterService() {
            val intent = Intent(context, TrackerService::class.java)
            intent.action = TrackerService.ACTION_STOP
            context.startService(intent)
            Toast.makeText(context, "⏹️ Service arrêté", Toast.LENGTH_SHORT).show()
        }
        
        @JavascriptInterface
        fun sendPositionSMS(destination: String, latitude: Double, longitude: Double) {
            val message = "POS:$latitude,$longitude"
            sendDataSMS(destination, message)
        }

        @JavascriptInterface
        fun sendRequestSMS(destination: String) {
            val message = "DEMANDE"
            sendDataSMS(destination, message)
        }

        @JavascriptInterface
        fun sendDataSMS(destination: String, message: String) {
            try {
                val sm = SmsManager.getDefault()
                val data = message.toByteArray(Charsets.UTF_8)
                
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(SMS_SENT_ACTION),
                    PendingIntent.FLAG_IMMUTABLE
                )
                
                sm.sendDataMessage(
                    destination,
                    null,
                    SMS_PORT.toShort(),
                    data,
                    sentIntent,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                fallbackToTextSMS(destination, message)
            }
        }

        private fun fallbackToTextSMS(destination: String, message: String) {
            try {
                val sm = SmsManager.getDefault()
                sm.sendTextMessage(destination, null, message, null, null)
                runOnUiThread {
                    Toast.makeText(context, "📱 Envoyé par SMS texte classique", Toast.LENGTH_SHORT).show()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
                runOnUiThread {
                    Toast.makeText(context, "❌ Échec de l'envoi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (resultCode == RESULT_OK) {
                runOnUiThread {
                    webView.evaluateJavascript("smsEnvoye(true)", null)
                }
            } else {
                runOnUiThread {
                    webView.evaluateJavascript("smsEnvoye(false)", null)
                }
            }
        }
    }

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                context ?: return
                intent ?: return
                
                val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Telephony.Sms.Intents.getMessagesFromIntent(intent)
                } else {
                    @Suppress("DEPRECATION")
                    val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
                    pdus.map { SmsMessage.createFromPdu(it as ByteArray) }.toTypedArray()
                }

                for (msg in messages) {
                    val from = msg.originatingAddress ?: continue
                    val text = msg.messageBody ?: continue
                    
                    val displayFrom = when {
                        from.startsWith("+33") -> "0" + from.substring(3)
                        from.startsWith("+") -> from
                        else -> from
                    }
                    
                    val safeFrom = displayFrom.replace("'", "\\'")
                    val safeText = text.replace("'", "\\'")
                    
                    runOnUiThread {
                        webView.evaluateJavascript("recevoirSMS('$safeFrom', '$safeText')", null)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        unregisterReceiver(smsSentReceiver)
        unregisterReceiver(positionUpdateReceiver)
    }
}
