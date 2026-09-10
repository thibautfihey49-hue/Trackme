
package com.thibautfihey.trackme
import java.text.SimpleDateFormat
import java.util.*
data class HistoryItem(val phone:String, val lat:Double, val lng:Double, val timestamp:Long, var streetName:String="Rue...", var passCount:Int=1){
 fun getDate():String = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(timestamp))
}
