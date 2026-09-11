package com.thibautfihey.trackme

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.telephony.SmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var etNumber: EditText
    private lateinit var ivPhoto: ImageView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumber = findViewById(R.id.etNumber)
        ivPhoto = findViewById(R.id.ivPhoto)
        tvStatus = findViewById(R.id.tvStatus)

        // ===== RÉCEPTEURS PHOTO & VIDÉO =====
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
    }

    // ===== FONCTIONS D'ENVOI DE COMMANDES =====
    fun sendPhotoBack(v: View) = sendSms("PHOTO")
    fun sendPhotoFront(v: View) = sendSms("PHOTO:FRONT")
    fun sendVideoBack(v: View) = sendSms("VIDEO")
    fun sendVideoFront(v: View) = sendSms("VIDEO:FRONT")

    private fun sendSms(cmd: String) {
        val num = etNumber.text.toString().trim()
        if (num.isEmpty()) {
            tvStatus.text = "⚠️ Entre un numéro d'abord"
            return
        }
        val dest = if (num.startsWith("+")) num else "+$num"
        SmsManager.getDefault().sendDataMessage(
            dest, null, 7777.toShort(),
            "TRACKME:$cmd".toByteArray(Charsets.UTF_8), null, null
        )
        tvStatus.text = "⏳ Commande envoyée : $cmd"
    }
}
