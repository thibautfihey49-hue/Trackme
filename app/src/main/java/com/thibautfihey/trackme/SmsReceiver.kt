package com.thibautfihey.trackme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsReceiverBroadcast : BroadcastReceiver() {
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
                when {
                    text.startsWith("POS:") -> {
                        val parts = text.removePrefix("POS:").split(",")
                        if (parts.size == 2) {
                            val lat = parts[0].toDoubleOrNull()
                            val lon = parts[1].toDoubleOrNull()
                            if (lat != null && lon != null) {
                                // Envoyer la position à l'activité via broadcast
                                val i = Intent("TRACKME_POS")
                                i.putExtra("lat", lat)
                                i.putExtra("lon", lon)
                                i.putExtra("from", from)
                                context.sendBroadcast(i)
                            }
                        }
                    }
                    text == "DEMANDE" -> {
                        val i = Intent("TRACKME_REQ")
                        i.putExtra("from", from)
                        context.sendBroadcast(i)
                    }
                }
            }
        } catch (e: Exception) {}
    }
}
