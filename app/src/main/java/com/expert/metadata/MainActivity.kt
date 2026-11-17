package com.expert.metadata

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

private val navy=Color(0xFF0B1425)
private val panel=Color(0xFF182A44)
private val blue=Color(0xFF38BDF8)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent {
   var files by remember{mutableStateOf<List<Uri>>(emptyList())}
   var showPicker by remember{mutableStateOf(false)}
   var usa by remember{mutableStateOf(false)}
   var city by remember{mutableStateOf("New York")}
   var busy by remember{mutableStateOf(false)}
   var progress by remember{mutableFloatStateOf(0f)}
   var status by remember{mutableStateOf("Select media to begin")}
   var details by remember{mutableStateOf("")}
   val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)){files=it;status="${it.size} items selected"}
   val browse=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){files=it;status="${it.size} items selected"}
   DisposableEffect(Unit){
    val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
     progress=intent.getFloatExtra("progress",progress)
     status=intent.getStringExtra("status")?:status
     details=intent.getStringExtra("details")?:details
     busy=intent.getBooleanExtra("busy",busy)
    }}
    ContextCompat.registerReceiver(this@MainActivity,receiver,IntentFilter(ProcessingService.UPDATE),ContextCompat.RECEIVER_NOT_EXPORTED)
    onDispose{unregisterReceiver(receiver)}
   }
   MaterialTheme(colorScheme=darkColorScheme(primary=blue,background=navy,surface=panel)){
    Column(Modifier.fillMaxSize().background(navy).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
     Spacer(Modifier.height(10.dp))
     Text("◈ Expert Metadata Studio",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
     Text("PRIVATE MEDIA CLEANER • V0.6 BUILD",color=blue,fontSize=11.sp)
     Card(colors=CardDefaults.cardColors(containerColor=panel)){
      Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
       Text("Metadata Cleaner & Editor",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
       Text("Clean supported metadata • Preserve original files",color=Color.LightGray)
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text("Synthetic USA location (MP4)",color=Color.White)
        Switch(checked=usa,onCheckedChange={usa=it},enabled=!busy)
       }
       if(usa){
        Text("Synthetic coordinates are not proof of origin",color=Color.LightGray,fontSize=12.sp)
        listOf("New York","Los Angeles","Chicago").forEach{item->
         Row { RadioButton(selected=city==item,onClick={city=item},enabled=!busy);Text(item,Modifier.padding(top=12.dp),color=Color.White) }
        }
       }
       Button(onClick={showPicker=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("SELECT MEDIA")}
      }
     }
     if(showPicker)AlertDialog(onDismissRequest={showPicker=false},title={Text("Select media")},text={
      Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
       Button(onClick={showPicker=false;gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}){Text("Gallery")}
       OutlinedButton(onClick={showPicker=false;browse.launch(arrayOf("video/*","image/*"))}){Text("Browse files")}
      }
     },confirmButton={TextButton(onClick={showPicker=false}){Text("Close")}})
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      Text("Selected files (${files.size})",color=Color.White,fontWeight=FontWeight.Bold)
      TextButton(onClick={files=emptyList()},enabled=!busy){Text("Clear")}
     }
     files.take(30).forEach{uri->
      Card(colors=CardDefaults.cardColors(containerColor=panel)){Text(displayName(this@MainActivity,uri),Modifier.fillMaxWidth().padding(14.dp),color=Color.White)}
     }
     Button(onClick={
      busy=true;progress=0f;details=""
      val intent=Intent(this@MainActivity,ProcessingService::class.java).apply{
       action=ProcessingService.START
       putParcelableArrayListExtra("uris",ArrayList(files))
       putExtra("synthetic_usa",usa);putExtra("usa_city",city)
      }
      ContextCompat.startForegroundService(this@MainActivity,intent)
      status="Processing started"
     },enabled=files.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"PROCESSING…" else "START CLEANING")}
     if(busy)OutlinedButton(onClick={startService(Intent(this@MainActivity,ProcessingService::class.java).setAction(ProcessingService.CANCEL))},modifier=Modifier.fillMaxWidth()){Text("CANCEL")}
     if(busy||progress>0f)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
     Text(status,color=blue)
     if(details.isNotBlank())Text(details,color=Color.White)
     Text("MP4 video remux: no re-encoding when compatible. JPEG/PNG: re-export (may alter quality). Output: Movies/Pictures/ExpertMetadataStudio. Originals stay unchanged.",color=Color.LightGray,fontSize=12.sp)
     Text("Not guaranteed to remove all embedded tags or media fingerprints. USA location is synthetic. Unsupported formats report an error.",color=Color.LightGray,fontSize=12.sp)
    }
   }
  }
 }
}

internal fun displayName(context:Context,uri:Uri):String=runCatching{
 context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}?:"media"
}.getOrDefault("media")

internal fun process(context:Context,uri:Uri,onProgress:(Float)->Unit,usaCity:String?=null,clearTimestamps:Boolean=true):String{
 val resolver=context.contentResolver
 val name=displayName(context,uri)
 val mime=resolver.getType(uri).orEmpty().lowercase(Locale.US)
 val ext=name.substringAfterLast('.', "").lowercase(Locale.US)
 val video=mime=="video/mp4"||ext=="mp4"||ext=="m4v"
 val png=mime=="image/png"||ext=="png"
 val jpeg=mime=="image/jpeg"||ext=="jpg"||ext=="jpeg"
 require(video||png||jpeg){"Only MP4, JPEG and PNG supported"}
 val suffix=if(video)"mp4" else if(png)"png" else "jpg"
 val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
 val outputName="Clean_${stamp}.$suffix"
 val values=ContentValues().apply{
  put(MediaStore.MediaColumns.DISPLAY_NAME,outputName)
  put(MediaStore.MediaColumns.MIME_TYPE,if(video)"video/mp4" else if(png)"image/png" else "image/jpeg")
  put(MediaStore.MediaColumns.RELATIVE_PATH,(if(video)Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)+"/ExpertMetadataStudio")
  put(MediaStore.MediaColumns.IS_PENDING,1)
 }
 val collection=if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
 val destination=resolver.insert(collection,values)?:error("Cannot create output")
 try{
  if(video){
   // Stream-copy MP4 directly into the pending MediaStore file: no temp file and no second copy.
   resolver.openFileDescriptor(destination,"w")?.use{fd->
    remux(context,uri,fd.fileDescriptor,onProgress,usaCity)
   }?:error("Cannot open video output")
   if(clearTimestamps)clearMp4HeaderTimes(context,destination)
  }else{
   // Image re-export intentionally remains separate; JPEG may be lossy.
   resolver.openInputStream(uri)?.use{input->
    val bitmap=BitmapFactory.decodeStream(input)?:error("Cannot decode image")
    try{
     resolver.openOutputStream(destination,"w")?.use{out->
      check(bitmap.compress(if(png)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,if(png)100 else 95,out))
     }?:error("Cannot write image")
    }finally{bitmap.recycle()}
   }?:error("Cannot open image")
   onProgress(.9f)
  }
  resolver.update(destination,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
  onProgress(1f)
  return "$outputName|$destination"
 }catch(e:Exception){
  resolver.delete(destination,null,null)
  throw e
 }
}

private fun remux(context:Context,uri:Uri,output:java.io.FileDescriptor,onProgress:(Float)->Unit,usaCity:String?){
 val extractor=MediaExtractor()
 var muxer:MediaMuxer?=null
 try{
  context.contentResolver.openFileDescriptor(uri,"r")?.use{descriptor->
   extractor.setDataSource(descriptor.fileDescriptor)
   val m=MediaMuxer(output,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   muxer=m
   when(usaCity){
    "New York"->m.setLocation(40.7128f,-74.0060f)
    "Los Angeles"->m.setLocation(34.0522f,-118.2437f)
    "Chicago"->m.setLocation(41.8781f,-87.6298f)
   }
   val tracks=mutableMapOf<Int,Int>()
   var bufferSize=1024*1024
   for(i in 0 until extractor.trackCount){
    val format=extractor.getTrackFormat(i)
    val mime=format.getString(MediaFormat.KEY_MIME).orEmpty()
    if(mime.startsWith("audio/")||mime.startsWith("video/")){
     tracks[i]=m.addTrack(format);extractor.selectTrack(i)
     if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))bufferSize=maxOf(bufferSize,format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtMost(32*1024*1024))
    }
   }
   check(tracks.isNotEmpty()){"No compatible tracks"}
   m.start()
   val buffer=ByteBuffer.allocateDirect(bufferSize)
   val info=MediaCodec.BufferInfo()
   val duration=(0 until extractor.trackCount).mapNotNull{i->extractor.getTrackFormat(i).let{if(it.containsKey(MediaFormat.KEY_DURATION))it.getLong(MediaFormat.KEY_DURATION) else null}}.maxOrNull()?:0L
   while(true){
    val track=extractor.sampleTrackIndex
    if(track<0)break
    val target=tracks[track]
    if(target==null){extractor.advance();continue}
    buffer.clear()
    val size=extractor.readSampleData(buffer,0)
    if(size<0)break
    info.offset=0;info.size=size;info.presentationTimeUs=extractor.sampleTime
    info.flags=extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC
    m.writeSampleData(target,buffer,info)
    if(duration>0)onProgress((info.presentationTimeUs.toFloat()/duration).coerceIn(0f,.9f))
    extractor.advance()
   }
   m.stop()
  }?:error("Cannot open video")
 }finally{runCatching{muxer?.release()};extractor.release()}
}
