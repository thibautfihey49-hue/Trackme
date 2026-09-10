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
  var handled=false
  for(sms in msgs){
   val body=sms.messageBody?:continue
   val from=sms.originatingAddress?:continue
   when{
    body.startsWith("SHAREPOS_CMD:") -> { handled=true; CommandManager.handle(context,from,body) }
    body.startsWith("SHAREPOS:") -> {
     handled=true
     try{
      val d=body.removePrefix("SHAREPOS:").split(";")
      if(d.size>=2){
        val lat=d[0].toDouble(); val lng=d[1].toDouble()
        val item=HistoryItem(from,lat,lng,System.currentTimeMillis())
        StreetPreviewManager.getStreetName(lat,lng){ street->
          item.streetName=street; HistoryManager.save(context,item)
          LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("NEW_POS_SMS").apply{ putExtra("lat",lat); putExtra("lng",lng); putExtra("from",from); putExtra("street",street) })
        }
        HistoryManager.save(context,item)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("NEW_POS_SMS").apply{ putExtra("lat",lat); putExtra("lng",lng); putExtra("from",from); putExtra("street","Rue en cours...") })
      }
     }catch(e:Exception){}
    }
   }
  }
  if(handled){ try{ abortBroadcast() }catch(e:Exception){} }
 }
}
