
package com.thibautfihey.trackme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.localbroadcastmanager.content.LocalBroadcastManager
class SmsReceiver: BroadcastReceiver(){
 override fun onReceive(context:Context, intent:Intent){
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
  val msgs=Telephony.Sms.Intents.getMessagesFromIntent(intent)
  for(sms in msgs){
   val body=sms.messageBody?:continue
   val from=sms.originatingAddress?:continue
   when{
    body.startsWith("SHAREPOS_CMD:") -> { abortBroadcast(); CommandManager.handle(context,from,body) }
    body.startsWith("SHAREPOS:") -> {
     abortBroadcast()
     try{
      val d=body.removePrefix("SHAREPOS:").split(";")
      val lat=d[0].toDouble(); val lng=d[1].toDouble()
      val item=HistoryItem(from,lat,lng,System.currentTimeMillis())
      StreetPreviewManager.getStreetName(lat,lng){ street-> item.streetName=street; HistoryManager.save(context,item); LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("NEW_POS_SMS").apply{ putExtra("lat",lat); putExtra("lng",lng); putExtra("from",from); putExtra("street",street) }) }
      if(item.streetName=="Rue..."){ HistoryManager.save(context,item) }
     } catch(e:Exception){}
    }
   }
  }
 }
}
