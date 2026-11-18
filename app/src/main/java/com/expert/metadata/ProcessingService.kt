package com.expert.metadata

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ProcessingService : Service() {
 companion object {
  const val START="com.expert.metadata.START"
  const val CANCEL="com.expert.metadata.CANCEL"
  const val UPDATE="com.expert.metadata.UPDATE"
 }
 private val cancel=AtomicBoolean(false)
 private val running=AtomicBoolean(false)
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("processing","Media processing",NotificationManager.IMPORTANCE_LOW))}
 private fun note(label:String,p:Int):Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,ProcessingService::class.java).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,"processing").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Expert Metadata Studio").setContentText(label).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,p,false).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancel",stop).build()
 }
 private fun send(label:String,p:Float,details:String,busy:Boolean,outputUri:String=""){
  if(busy)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(101,note(label,(p*100).toInt()))
  sendBroadcast(Intent(UPDATE).setPackage(packageName).apply{putExtra("status",label);putExtra("progress",p);putExtra("details",details);putExtra("busy",busy);putExtra("output_uri",outputUri)})
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==CANCEL){cancel.set(true);return START_NOT_STICKY}
  if(intent?.action!=START||!running.compareAndSet(false,true))return START_NOT_STICKY
  @Suppress("DEPRECATION") val uris=intent.getParcelableArrayListExtra<Uri>("uris")?:arrayListOf()
  if(uris.isEmpty()){running.set(false);stopSelf();return START_NOT_STICKY}
  cancel.set(false);startForeground(101,note("Starting",0))
  val usa=if(intent.getBooleanExtra("synthetic_usa",false))intent.getStringExtra("usa_city") else null
  val clearTimestamps=intent.getBooleanExtra("clear_mp4_timestamps",true)
  Thread {
   val report=mutableListOf<String>()
   var firstOutputUri=""
   try {
    for((index,uri) in uris.withIndex()){
     if(cancel.get())break
     val name=runCatching{displayName(this,uri)}.getOrDefault("Selected media ${index+1}")
     val result=runCatching{process(this,uri,{fraction->
      if(cancel.get())throw InterruptedException("Cancelled")
      send("Processing ${index+1}/${uris.size}: $name",(index+fraction)/uris.size,report.joinToString("\n"),true)
     },usa,clearTimestamps)}
     report.add(result.fold({value->
      val parts=value.split("|",limit=2)
      if(firstOutputUri.isEmpty()&&parts.size==2)firstOutputUri=parts[1]
      "✓ $name → ${parts[0]}"
     },{"✕ $name: ${it.message}"}))
    }
   } catch(e:Exception) {
    report.add("✕ Processing stopped: ${e.message?:e.javaClass.simpleName}")
   } finally {
    val failed=report.any{it.startsWith("✕")}
    val finalStatus=when{cancel.get()->"Cancelled";failed->"Finished with errors";else->"Finished"}
    send(finalStatus,if(cancel.get())0f else 1f,report.joinToString("\n"),false,firstOutputUri)
    running.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
   }
  }.start()
  return START_NOT_STICKY
 }
}
