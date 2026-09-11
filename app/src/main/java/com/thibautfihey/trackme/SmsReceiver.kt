package com.thibautfihey.trackme
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "TrackMeSMS"
        const val SMS_PREFIX = "TRACKME:"
        const val PORT = 7777
        
        var onPositionReceived: ((lat: Double, lon: Double, from: String) -> Unit)? = null
        var onPhotoReceived: ((from: String, bitmap: Bitmap) -> Unit)? = null
        var onVideoReceived: ((from: String, url: String) -> Unit)? = null
        var onRequestReceived: ((from: String) -> Unit)? = null
        
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        try {
            val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else {
                @Suppress("DEPRECATION")
                val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
                pdus.map { SmsMessage.createFromPdu(it as ByteArray) }.toTypedArray()
            }

            for (msg in messages) {
                val messageBody = msg.messageBody ?: continue
                val originatingAddress = msg.originatingAddress ?: continue
                
                Log.d(TAG, "SMS reçu de $originatingAddress : $messageBody")
                
                if (messageBody.startsWith(SMS_PREFIX)) {
                    val content = messageBody.removePrefix(SMS_PREFIX)
                    handleCommand(context, originatingAddress, content)
                    abortBroadcast()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur réception SMS", e)
        }
    }

    private fun handleCommand(context: Context, from: String, command: String) {
        when {
            command.startsWith("POSITION:") -> {
                val coords = command.removePrefix("POSITION:").split(",")
                if (coords.size == 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lon = coords[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        onPositionReceived?.invoke(lat, lon, from)
                    }
                }
            }
            
            command == "REQUEST_POSITION" -> {
                onRequestReceived?.invoke(from)
            }
            
            command.startsWith("PHOTO_URL:") -> {
                val url = command.removePrefix("PHOTO_URL:")
                downloadAndDisplayPhoto(context, from, url)
            }
            
            command.startsWith("VIDEO_URL:") -> {
                val url = command.removePrefix("VIDEO_URL:")
                onVideoReceived?.invoke(from, url)
            }
        }
    }

    private fun downloadAndDisplayPhoto(context: Context, from: String, url: String) {
        Thread {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val inputStream = response.body?.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    android.os.Handler(context.mainLooper).post {
                        onPhotoReceived?.invoke(from, bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur téléchargement photo", e)
            }
        }.start()
    }
}
