
package com.thibautfihey.trackme
import android.content.Context
object TrustedContactsManager {
 private const val K="trusted"
 fun isAuthorized(c:Context, phone:String):Boolean{
  val set=c.getSharedPreferences("trackme",Context.MODE_PRIVATE).getStringSet(K, emptySet())?: emptySet()
  return set.contains(phone)
 }
 fun add(c:Context, phone:String){
  val prefs=c.getSharedPreferences("trackme",Context.MODE_PRIVATE)
  val set=prefs.getStringSet(K, emptySet())!!.toMutableSet(); set.add(phone)
  prefs.edit().putStringSet(K,set).apply()
 }
}
