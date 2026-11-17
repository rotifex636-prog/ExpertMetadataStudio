package com.expert.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class MetadataEntry(val group:String,val label:String,val value:String)

@Composable
internal fun rememberMediaMetadata(context:Context,uri:Uri?):List<MetadataEntry>? {
 var result by remember(uri){mutableStateOf<List<MetadataEntry>?>(null)}
 LaunchedEffect(uri){
  result=if(uri==null) emptyList() else withContext(Dispatchers.IO){inspectMedia(context,uri)}
 }
 return result
}

internal fun inspectMedia(context:Context,uri:Uri):List<MetadataEntry>{
 val out=mutableListOf<MetadataEntry>()
 fun add(group:String,label:String,value:String?){
  if(!value.isNullOrBlank())out+=MetadataEntry(group,label,value)
 }
 val resolver=context.contentResolver
 add("File","Name",runCatching{displayName(context,uri)}.getOrNull())
 add("File","MIME type",resolver.getType(uri))
 runCatching{
  resolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use{cursor->
   if(cursor.moveToFirst()){
    val i=cursor.getColumnIndex(OpenableColumns.SIZE)
    if(i>=0&&!cursor.isNull(i)){
     val bytes=cursor.getLong(i)
     add("File","Size",String.format(Locale.US,"%.2f MB (%d bytes)",bytes/1048576.0,bytes))
    }
   }
  }
 }
 // MediaStore fields describe the library entry, not necessarily the embedded recording date.
 val columns=listOf(
  "Date added (device library)" to MediaStore.MediaColumns.DATE_ADDED,
  "Date modified (device library)" to MediaStore.MediaColumns.DATE_MODIFIED,
  "Date taken (device library)" to MediaStore.MediaColumns.DATE_TAKEN
 )
 for((label,column) in columns){
  runCatching{
   resolver.query(uri,arrayOf(column),null,null,null)?.use{cursor->
    if(cursor.moveToFirst()){
     val index=cursor.getColumnIndex(column)
     if(index>=0&&!cursor.isNull(index)){
      val raw=cursor.getLong(index)
      if(raw>0){
       val millis=if(column==MediaStore.MediaColumns.DATE_TAKEN)raw else raw*1000L
       val instant=Instant.ofEpochMilli(millis)
       val formatted=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(instant)
       add("Device Library",label,"$formatted (UTC: $instant)")
      }
     }
    }
   }
  }
 }
 val retriever=MediaMetadataRetriever()
 try{
  retriever.setDataSource(context,uri)
  val fields=listOf(
   Triple("Video","Width",MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
   Triple("Video","Height",MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
   Triple("Video","Rotation",MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
   Triple("Video","Frame count",MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT),
   Triple("Video","Bitrate (bps)",MediaMetadataRetriever.METADATA_KEY_BITRATE),
   Triple("Media","Duration (ms)",MediaMetadataRetriever.METADATA_KEY_DURATION),
   Triple("Media","MIME",MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
   Triple("Media","Date",MediaMetadataRetriever.METADATA_KEY_DATE),
   Triple("Media","Year",MediaMetadataRetriever.METADATA_KEY_YEAR),
   Triple("Media","Title",MediaMetadataRetriever.METADATA_KEY_TITLE),
   Triple("Media","Artist",MediaMetadataRetriever.METADATA_KEY_ARTIST),
   Triple("Media","Album",MediaMetadataRetriever.METADATA_KEY_ALBUM),
   Triple("Media","Author",MediaMetadataRetriever.METADATA_KEY_AUTHOR),
   Triple("Media","Writer",MediaMetadataRetriever.METADATA_KEY_WRITER),
   Triple("Media","Genre",MediaMetadataRetriever.METADATA_KEY_GENRE),
   Triple("Media","Compilation",MediaMetadataRetriever.METADATA_KEY_COMPILATION),
   Triple("Media","Embedded GPS",MediaMetadataRetriever.METADATA_KEY_LOCATION),
   Triple("Media","Capture frame rate",MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
  )
  fields.forEach{(group,label,key)->add(group,label,retriever.extractMetadata(key))}
 }catch(e:Exception){add("Inspector","Media retriever",e.message?:"Could not inspect media")}
 finally{runCatching{retriever.release()}}
 val extractor=MediaExtractor()
 try{
  resolver.openFileDescriptor(uri,"r")?.use{fd->
   extractor.setDataSource(fd.fileDescriptor)
   add("Tracks","Count",extractor.trackCount.toString())
   for(i in 0 until extractor.trackCount){
    val format=extractor.getTrackFormat(i)
    val group="Track ${i+1}"
    val keys=listOf(
     "mime" to MediaFormat.KEY_MIME,
     "width" to MediaFormat.KEY_WIDTH,
     "height" to MediaFormat.KEY_HEIGHT,
     "frame rate" to MediaFormat.KEY_FRAME_RATE,
     "bitrate" to MediaFormat.KEY_BIT_RATE,
     "sample rate" to MediaFormat.KEY_SAMPLE_RATE,
     "channels" to MediaFormat.KEY_CHANNEL_COUNT,
     "duration (us)" to MediaFormat.KEY_DURATION,
     "language" to MediaFormat.KEY_LANGUAGE,
     "rotation" to MediaFormat.KEY_ROTATION
    )
    keys.forEach{(label,key)->if(format.containsKey(key))add(group,label,runCatching{when{key==MediaFormat.KEY_MIME||key==MediaFormat.KEY_LANGUAGE->format.getString(key);key==MediaFormat.KEY_DURATION->format.getLong(key).toString();else->format.getInteger(key).toString()}}.getOrNull())}
   }
  }
 }catch(e:Exception){add("Inspector","Track extractor",e.message?:"Could not inspect tracks")}
 finally{runCatching{extractor.release()}}
 if(resolver.getType(uri)?.contains("mp4",true)==true||displayName(context,uri).endsWith(".mp4",true)){
  runCatching{out.addAll(inspectMp4Headers(context,uri))}.onFailure{add("Inspector","MP4 header parser",it.message?:"Unavailable")}
 }
 if(out.none{it.group=="Media"&&it.label=="Embedded GPS"})add("Media","Embedded GPS","Not found")
 if(out.none{it.group=="Media"&&it.label=="Date"})add("Media","Embedded recording date","Not found")
 add("Inspector","Coverage","Android-exposed media and available MediaStore dates plus MP4 movie/track header timestamps; proprietary tags may remain invisible")
 return out
}
