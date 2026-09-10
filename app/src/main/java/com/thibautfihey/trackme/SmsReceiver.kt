package com.thibautfihey.trackme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "TrackMeSMS"
        const val SMS_PREFIX = "TRACKME:"
        var onPositionReceived: ((lat:Double, lon:Double, from:String)->Unit)?=null
        var onRequestReceived: ((from:String)->Unit)?=null
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } else {
            @Suppress("DEPRECATION")
            val pdus = intent.extras?.get("pdus") as? Array<*>
            pdus?.map{ SmsMessage.createFromPdu(it as ByteArray) } ?: emptyList()
        }
        for(msg in messages) {
            val body = msg.messageBody ?: ""
            val from = msg.originatingAddress ?: ""
            if(body.startsWith(SMS_PREFIX)) {
                abortBroadcast()
                val content = body.removePrefix(SMS_PREFIX)
                if(content=="REQUEST") onRequestReceived?.invoke(from)
                else if(content.startsWith("POS:")) {
                    val c = content.removePrefix("POS:").split(",")
                    if(c.size==2) try { onPositionReceived?.invoke(c[0].toDouble(),c[1].toDouble(),from) }
                    catch(e:Exception){}
                }
                return
            }
        }
    }
}
