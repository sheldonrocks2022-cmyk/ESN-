package com.esn.jarvis
object JarvisDiscordSnowflake {
 const val DISCORD_EPOCH=1420070400000L
 fun createdAtMillis(id:String):Long?=try{val n=id.toLong();(n ushr 22)+DISCORD_EPOCH}catch(_:Throwable){null}
 fun ageDays(id:String,now:Long=System.currentTimeMillis()):Long?=createdAtMillis(id)?.let{((now-it).coerceAtLeast(0L))/86400000L}
 fun isNewerThanDays(id:String,days:Int,now:Long=System.currentTimeMillis())=(ageDays(id,now)?:Long.MAX_VALUE)<days
}