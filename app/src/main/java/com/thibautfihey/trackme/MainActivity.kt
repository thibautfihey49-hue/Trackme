package com.thibautfihey.trackme

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.provider.Telephony
import android.telephony.SmsMessage

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val smsReceiver = SmsReceiver()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        // ✅ Configuration WebView pour GPS + JS
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true) // 📍 GPS activé pour la page web
        }
        
        // 🔗 Interface JS ↔ Android pour l'envoi de SMS données
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        
        // 📄 Charger la page locale
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
        
        // 📍 Autoriser la demande de GPS depuis la page web
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    val resources = request.resources
                    val permissions = mutableListOf<String>()
                    for (res in resources) {
                        if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE || 
                            res == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                            res == "android.webkit.resource.LOCATION") {
                            permissions.add(res)
                        }
                    }
                    if (permissions.isNotEmpty()) {
                        request.grant(permissions.toTypedArray())
                    } else {
                        request.grant(resources)
                    }
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")

        // 📋 Demander toutes les permissions au démarrage
        checkPermissions()
        
        // 📡 Enregistrer le récepteur SMS
        registerReceiver(smsReceiver, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.SEND_SMS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECEIVE_SMS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_SMS)

        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
    }

    // 🔗 Interface accessible depuis JavaScript
    class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun sendDataSMS(destination: String, port: Int, message: String) {
            try {
                val sm = SmsManager.getDefault()
                sm.sendDataMessage(
                    destination,
                    null,
                    port.toShort(),
                    message.toByteArray(Charsets.UTF_8),
                    null,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 📡 Récepteur SMS — passe les messages à la page web
    inner class SmsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                context ?: return
                intent ?: return
                
                val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Telephony.Sms.Intents.getMessagesFromIntent(intent)
                } else {
                    @Suppress("DEPRECATION")
                    val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
                    pdus.map { android.telephony.SmsMessage.createFromPdu(it as ByteArray) }.toTypedArray()
                }

                for (msg in messages) {
                    val from = msg.originatingAddress ?: continue
                    val text = msg.messageBody ?: continue
                    
                    // Échapper les apostrophes pour JS
                    val safeFrom = from.replace("'", "\\'")
                    val safeText = text.replace("'", "\\'")
                    
                    // Envoyer au JavaScript
                    runOnUiThread {
                        webView.evaluateJavascript("receiveSMS('$safeFrom', '$safeText')", null)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }
}
