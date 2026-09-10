
package com.thibautfihey.trackme
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
class ShareLocationService: Service() {
 private val handler=Handler(Looper.getMainLooper())
 private var phones:List<String> = emptyList()
 private var intervalSec=60
 private val runnable=object:Runnable{
  override fun run(){ sendPosToAll(); handler.postDelayed(this, intervalSec*1000L) }
 }
 override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int{
  phones=intent?.getStringExtra("phones")?.split(",")?.map{it.trim()}?: emptyList()
  intervalSec=intent?.getIntExtra("interval",60)?:60
  startForeground(1, createNotif())
  handler.post(runnable)
  return START_STICKY
 }
 private fun sendPosToAll(){
  if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
  LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener{ loc->
   if(loc==null) return@addOnSuccessListener
   val msg="SHAREPOS:${loc.latitude};${loc.longitude};${System.currentTimeMillis()}"
   for(p in phones){ try{ SmsManager.getDefault().sendTextMessage(p,null,msg,null,null) } catch(e:Exception){} }
  }
 }
 private fun createNotif(): Notification {
  val id="trackme_channel"
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){ val ch=NotificationChannel(id,"TrackMe",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(ch) }
  return NotificationCompat.Builder(this,id).setContentTitle("TrackMe actif").setContentText("Envoi chaque ${intervalSec}s vers ${phones.size} contact(s)").setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
 }
 override fun onBind(i:Intent?)=null
 override fun onDestroy(){ handler.removeCallbacks(runnable); super.onDestroy() }
}
