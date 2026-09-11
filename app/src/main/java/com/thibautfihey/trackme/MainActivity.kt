package com.thibautfihey.trackme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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

class MainActivity : AppCompatActivity() {
    private lateinit var etNumber: EditText
    private lateinit var ivPhoto: ImageView
    private lateinit var tvStatus: TextView
    
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            tvStatus.text = "✅ Toutes permissions accordées ! Prêt."
        } else {
            tvStatus.text = "⚠️ Permissions refusées — L'application ne fonctionnera pas correctement"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumber = findViewById(R.id.etNumber)
        ivPhoto = findViewById(R.id.ivPhoto)
        tvStatus = findViewById(R.id.tvStatus)

        // Demander les permissions au démarrage
        requestNecessaryPermissions()

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
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            tvStatus.text = "✅ Toutes permissions déjà accordées ! Prêt."
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
        
        // Vérifier permission SEND_SMS avant d'envoyer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "⚠️ Permission SEND_SMS manquante — Redémarre l'application"
            requestNecessaryPermissions()
            return
        }
        
        val dest = if (num.startsWith("+")) num else "+$num"
        try {
            SmsManager.getDefault().sendDataMessage(
                dest, null, 7777.toShort(),
                "TRACKME:$cmd".toByteArray(Charsets.UTF_8), null, null
            )
            tvStatus.text = "⏳ Commande envoyée : $cmd"
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur envoi : ${e.message}"
        }
    }
}
