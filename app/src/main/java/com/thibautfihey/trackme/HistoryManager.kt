
package com.thibautfihey.trackme
import android.content.Context
import android.location.Location
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
object HistoryManager {
 private const val KEY="history_v4"
 fun getAll(c:Context): MutableList<HistoryItem> {
  val json=c.getSharedPreferences("trackme",Context.MODE_PRIVATE).getString(KEY,"[]")
  return Gson().fromJson(json, object:TypeToken<MutableList<HistoryItem>>(){}.type) ?: mutableListOf()
 }
 fun save(c:Context, newItem:HistoryItem){
  val list=getAll(c)
  val existing=list.find{ dist(it.lat,it.lng,newItem.lat,newItem.lng)<30 || (it.streetName==newItem.streetName && it.streetName!="Rue..." && dist(it.lat,it.lng,newItem.lat,newItem.lng)<100) }
  if(existing!=null){
   val idx=list.indexOf(existing); list[idx]=existing.copy(timestamp=System.currentTimeMillis(), passCount=existing.passCount+1, streetName = if(newItem.streetName!="Rue...") newItem.streetName else existing.streetName)
  } else { list.add(0,newItem); if(list.size>100) list.removeAt(list.size-1) }
  c.getSharedPreferences("trackme",Context.MODE_PRIVATE).edit().putString(KEY,Gson().toJson(list)).apply()
 }
 fun clear(c:Context){ c.getSharedPreferences("trackme",Context.MODE_PRIVATE).edit().remove(KEY).apply() }
 private fun dist(a1:Double,b1:Double,a2:Double,b2:Double):Float{ val r=FloatArray(1); Location.distanceBetween(a1,b1,a2,b2,r); return r[0] }
}
