package com.thibautfihey.trackme
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity: AppCompatActivity(){
 private lateinit var map:MapView
 private lateinit var fused:FusedLocationProviderClient
 private var lastLoc:Location?=null
 private val REQ=1001
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  Configuration.getInstance().load(this, getSharedPreferences("osmdroid",MODE_PRIVATE))
  Configuration.getInstance().userAgentValue=packageName
  setContentView(R.layout.activity_main)
  map=findViewById(R.id.map)
  fused=LocationServices.getFusedLocationProviderClient(this)
  map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); map.controller.setZoom(17.0); map.controller.setCenter(GeoPoint(47.432,-0.586))
  checkPerms()
  findViewById<Button>(R.id.btn_share).setOnClickListener{ safeShareOnce() }
  findViewById<Button>(R.id.btn_request).setOnClickListener{ safeRequestRemote() }
  findViewById<Button>(R.id.btn_stop_remote).setOnClickListener{ safeStopRemote() }
  findViewById<Button>(R.id.btn_start_auto).setOnClickListener{ startAuto() }
  findViewById<Button>(R.id.btn_stop_auto).setOnClickListener{ stopService(Intent(this, ShareLocationService::class.java)) }
  findViewById<Button>(R.id.btn_clear).setOnClickListener{ HistoryManager.clear(this); refreshHistory() }
  findViewById<Button>(R.id.btn_save_code).setOnClickListener{ val c=findViewById<EditText>(R.id.edit_secret).text.toString(); getSharedPreferences("trackme",MODE_PRIVATE).edit().putString("secret_code",c).apply(); Toast.makeText(this,"Code sauvé: $c",Toast.LENGTH_SHORT).show() }
  findViewById<Button>(R.id.btn_test).setOnClickListener{ testWithMyNumber() }
  refreshHistory()
  LocalBroadcastManager.getInstance(this).registerReceiver(posReceiver, IntentFilter("NEW_POS_SMS"))
  if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startLoc()
 }
 override fun onRequestPermissionsResult(c:Int, p:Array<String>, r:IntArray){ super.onRequestPermissionsResult(c,p,r); if(c==REQ && r.isNotEmpty() && r[0]==PackageManager.PERMISSION_GRANTED){ startLoc(); Toast.makeText(this,"GPS activé",Toast.LENGTH_SHORT).show() } }
 private fun checkPerms(){ val perms=arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS); if(perms.any{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}) ActivityCompat.requestPermissions(this,perms,REQ) else startLoc() }
 private fun hasSmsPerm()=ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED
 private fun safeSendSms(phone:String, msg:String):Boolean{
  if(phone.isBlank()){ Toast.makeText(this,"Numéro vide",Toast.LENGTH_SHORT).show(); return false }
  if(!hasSmsPerm()){ checkPerms(); return false }
  return try{ SmsManager.getDefault().sendTextMessage(phone,null,msg,null,null); true }catch(e:Exception){ Toast.makeText(this,"Erreur SMS: ${e.message}",Toast.LENGTH_LONG).show(); false }
 }
 private fun startLoc(){
  if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
  try{
   fused.lastLocation.addOnSuccessListener{ it?.let{ lastLoc=it; map.controller.animateTo(GeoPoint(it.latitude,it.longitude)); addMarker(it.latitude,it.longitude,"Moi") } }
   val req=LocationRequest.create().apply{ interval=3000; fastestInterval=2000; priority=LocationRequest.PRIORITY_HIGH_ACCURACY }
   fused.requestLocationUpdates(req, object:LocationCallback(){ override fun onLocationResult(r:LocationResult){ r.lastLocation?.let{ lastLoc=it; map.controller.animateTo(GeoPoint(it.latitude,it.longitude)) } } }, mainLooper)
  }catch(e:Exception){}
 }
 private fun safeShareOnce(){ if(lastLoc==null){ Toast.makeText(this,"GPS pas prêt",Toast.LENGTH_SHORT).show(); startLoc(); return }; val p=findViewById<EditText>(R.id.edit_phone).text.toString(); val m="SHAREPOS:${lastLoc!!.latitude};${lastLoc!!.longitude};${System.currentTimeMillis()}"; if(safeSendSms(p,m)) Toast.makeText(this,"Envoyé",Toast.LENGTH_SHORT).show() }
 private fun safeRequestRemote(){ val p=findViewById<EditText>(R.id.edit_phone).text.toString(); val c=findViewById<EditText>(R.id.edit_secret).text.toString(); if(c.isBlank()){ Toast.makeText(this,"Code vide",Toast.LENGTH_SHORT).show(); return }; if(safeSendSms(p,"SHAREPOS_CMD:START:60:$c")) Toast.makeText(this,"Demande envoyée",Toast.LENGTH_SHORT).show() }
 private fun safeStopRemote(){ val p=findViewById<EditText>(R.id.edit_phone).text.toString(); val c=findViewById<EditText>(R.id.edit_secret).text.toString(); if(safeSendSms(p,"SHAREPOS_CMD:STOP:$c")) Toast.makeText(this,"Stop envoyé",Toast.LENGTH_SHORT).show() }
 private fun startAuto(){ val phones=findViewById<EditText>(R.id.edit_phone).text.toString(); if(phones.isBlank()){ Toast.makeText(this,"Numéro vide",Toast.LENGTH_SHORT).show(); return }; val i=Intent(this, ShareLocationService::class.java).apply{ putExtra("phones",phones); putExtra("interval",60) }; if(android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.O) startForegroundService(i) else startService(i) }
 private fun testWithMyNumber(){
   if(lastLoc==null){ Toast.makeText(this,"GPS pas prêt - réessaye 2s",Toast.LENGTH_SHORT).show(); startLoc(); return }
   val phone=findViewById<EditText>(R.id.edit_phone).text.toString().ifBlank { "+33748107513" }
   val lat=lastLoc!!.latitude; val lng=lastLoc!!.longitude
   val item=HistoryItem(phone,lat,lng,System.currentTimeMillis()); item.streetName="TEST - Ma position"
   HistoryManager.save(this,item); addMarker(lat,lng,"TEST $phone"); refreshHistory(); map.controller.animateTo(GeoPoint(lat,lng))
   Toast.makeText(this,"TEST OK injecté direct dans l'App",Toast.LENGTH_LONG).show()
   LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("NEW_POS_SMS").apply{ putExtra("lat",lat); putExtra("lng",lng); putExtra("from",phone); putExtra("street","TEST") })
 }
 fun addMarker(lat:Double,lng:Double,title:String){ val m=Marker(map); m.position=GeoPoint(lat,lng); m.title=title; m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM); map.overlays.add(m); map.invalidate() }
 private fun refreshHistory(){ val list=HistoryManager.getAll(this); val rv=findViewById<RecyclerView>(R.id.recycler_history); rv.layoutManager=LinearLayoutManager(this); rv.adapter=HistoryAdapter(list){ item-> map.controller.animateTo(GeoPoint(item.lat,item.lng)); addMarker(item.lat,item.lng,item.streetName) } }
 private val posReceiver=object:BroadcastReceiver(){ override fun onReceive(c:Context?, intent:Intent?){ val lat=intent?.getDoubleExtra("lat",0.0)?:0.0; val lng=intent?.getDoubleExtra("lng",0.0)?:0.0; val from=intent?.getStringExtra("from")?:"?"; val street=intent?.getStringExtra("street")?:"Rue..."; addMarker(lat,lng,"$from - $street"); refreshHistory() } }
}
class HistoryAdapter(private val list:List<HistoryItem>, private val onClick:(HistoryItem)->Unit): RecyclerView.Adapter<HistoryAdapter.VH>(){
 class VH(v:android.view.View): RecyclerView.ViewHolder(v){ val tv1=v.findViewById<TextView>(R.id.tv_line1); val tv2=v.findViewById<TextView>(R.id.tv_line2) }
 override fun onCreateViewHolder(p:android.view.ViewGroup,vt:Int):VH{ return VH(android.view.LayoutInflater.from(p.context).inflate(R.layout.item_history,p,false)) }
 override fun getItemCount()=list.size
 override fun onBindViewHolder(h:VH,i:Int){ val item=list[i]; h.tv1.text="${item.streetName} - ${item.phone} (x${item.passCount})"; h.tv2.text="${item.getDate()} - ${item.lat}, ${item.lng}"; h.itemView.setOnClickListener{ onClick(item) } }
}
