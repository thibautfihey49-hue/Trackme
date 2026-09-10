package com.thibautfihey.trackme
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
object StreetPreviewManager {
 private val client=OkHttpClient()
 fun getStreetName(lat:Double,lng:Double,cb:(String)->Unit){
  val req=Request.Builder().url("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json").header("User-Agent","TrackMe/5.0").build()
  client.newCall(req).enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException){ cb("Rue inconnue") }
   override fun onResponse(call:Call,resp:Response){
    try{ val obj=JSONObject(resp.body?.string()?:"{}"); val road=obj.optJSONObject("address")?.optString("road") ?: obj.optJSONObject("address")?.optString("pedestrian") ?: "Rue inconnue"; cb(road) } catch(ex:Exception){ cb("Rue inconnue") }
   }
  })
 }
}
