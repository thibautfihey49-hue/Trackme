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

                // ICI on ne fait RIEN qui puisse crasher
                // Pas de callback vers l'activité — ça crash trop souvent
                // On ajoutera ça plus tard quand le reste marchera
            }
        } catch (e: Exception) {
            // Silence total — jamais de crash
        }
    }
}
