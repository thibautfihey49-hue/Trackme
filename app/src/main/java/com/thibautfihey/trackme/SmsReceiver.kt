package com.thibautfihey.trackme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage

object SmsReceiver {
    var onPositionReceived: ((Double, Double, String) -> Unit)? = null
    var onRequestReceived: ((String) -> Unit)? = null
}

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
                                android.os.Handler(context.mainLooper).post {
                                    SmsReceiver.onPositionReceived?.invoke(lat, lon, from)
                                }
                            }
                        }
                    }
                    text == "DEMANDE" -> {
                        android.os.Handler(context.mainLooper).post {
                            SmsReceiver.onRequestReceived?.invoke(from)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silencieux — ne crash jamais
        }
    }
}
