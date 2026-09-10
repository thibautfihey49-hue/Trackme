
package com.thibautfihey.trackme
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AlertDialog
object CommandManager {
 fun handle(context:Context, sender:String, body:String){
  val parts=body.split(":") // SHAREPOS_CMD:START:60:CODE
  if(parts.size<2) return
  val action=parts[1]
  val prefs=context.getSharedPreferences("trackme",Context.MODE_PRIVATE)
  val expectedCode=prefs.getString("secret_code","1234")?:"1234"
  val providedCode=parts.getOrNull(3) ?: ""
  if(expectedCode.isNotEmpty() && providedCode!=expectedCode && parts.size>=4){ return } // code faux
  if(!TrustedContactsManager.isAuthorized(context,sender)){
   // demande consentement (si app ouverte, sinon auto add la 1ere fois avec code bon)
   // Pour Termux headless, on auto-autorise si code bon
   if(providedCode==expectedCode){ TrustedContactsManager.add(context,sender) } else return
  }
  when(action){
   "START" -> {
    val interval=parts.getOrNull(2)?.toIntOrNull()?:60
    val i=Intent(context, ShareLocationService::class.java).apply{ putExtra("phones",sender); putExtra("interval",interval) }
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
   }
   "STOP" -> context.stopService(Intent(context, ShareLocationService::class.java))
  }
 }
}
