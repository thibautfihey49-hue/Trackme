package com.thibautfihey.trackme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {
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
                val safeFrom = from.replace("'", "\\'")
                val safeText = text.replace("'", "\\'")
                MainActivity.webView?.evaluateJavascript("receiveSMS('$safeFrom', '$safeText')", null)
            }
        } catch (_: Exception) {}
    }
}
