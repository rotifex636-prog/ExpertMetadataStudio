package com.expert.metadata

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.time.Instant

/** Read-only ISO BMFF header inspector. Never scans media payload or changes the file. */
internal fun inspectMp4Headers(context:Context,uri:Uri):List<MetadataEntry>{
 val entries=mutableListOf<MetadataEntry>()
 val fd=context.contentResolver.openFileDescriptor(uri,"r")?:return entries
 fd.use{
  FileInputStream(it.fileDescriptor).channel.use{channel->
   val size=channel.size()
   fun read(pos:Long,length:Int):ByteBuffer?{
    if(pos<0||length<0||pos>size-length)return null
    val b=ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
    var at=pos
    while(b.hasRemaining()){
     val n=channel.read(b,at)
     if(n<=0)return null
     at+=n
    }
    b.flip()
    return b
   }
   var trackNumber=0
   fun date(seconds:Long):String=if(seconds==0L)"Unset (MP4 epoch zero)" else runCatching{Instant.ofEpochSecond(Math.subtractExact(seconds,2082844800L)).toString()}.getOrElse{"Invalid"}
   fun scan(start:Long,end:Long,depth:Int,track:Int=0){
    if(depth>3||end<=start)return
    var pos=start
    var count=0
    while(pos+8<=end&&count++<2000){
     val h=read(pos,8)?:break
     val shortSize=h.int.toLong() and 0xffffffffL
     val typeBytes=ByteArray(4);h.get(typeBytes)
     val type=String(typeBytes,Charsets.ISO_8859_1)
     val header=if(shortSize==1L)16L else 8L
     val boxSize=when(shortSize){0L->end-pos;1L->read(pos+8,8)?.long?:break;else->shortSize}
     if(boxSize<header||boxSize>end-pos)break
     val body=pos+header
     if(type=="ftyp"){
      read(body,8)?.let{v->
       val brand=ByteArray(4);v.get(brand)
       entries+=MetadataEntry("MP4 Container","Major brand",String(brand,Charsets.ISO_8859_1))
       entries+=MetadataEntry("MP4 Container","Brand version",v.int.toUInt().toString())
      }
     }
     if(type=="mvhd"){
      val v=read(body,if(read(body,1)?.get(0)?.toInt()==1)28 else 20)
      if(v!=null){
       val version=v.get(0).toInt()
       val creation=if(version==1)v.getLong(4) else v.getInt(4).toLong() and 0xffffffffL
       val modification=if(version==1)v.getLong(12) else v.getInt(8).toLong() and 0xffffffffL
       entries+=MetadataEntry("MP4 Container","Movie header creation time (UTC)",date(creation))
       entries+=MetadataEntry("MP4 Container","Movie header modification time (UTC)",date(modification))
      }
     }
     if(type=="tkhd"||type=="mdhd"){
      val v=read(body,if(read(body,1)?.get(0)?.toInt()==1)24 else 16)
      if(v!=null){
       val version=v.get(0).toInt()
       val creation=if(version==1)v.getLong(4) else v.getInt(4).toLong() and 0xffffffffL
       val modification=if(version==1)v.getLong(12) else v.getInt(8).toLong() and 0xffffffffL
       val label=if(type=="tkhd")"Track header" else "Media header"
       val group="MP4 Track $track"
       entries+=MetadataEntry(group,"$label creation time (UTC)",date(creation))
       entries+=MetadataEntry(group,"$label modification time (UTC)",date(modification))
      }
     }
     if(type=="trak"){
      trackNumber++
      scan(body,pos+boxSize,depth+1,trackNumber)
     }else if(type=="moov"||type=="udta"||type=="mdia")scan(body,pos+boxSize,depth+1,track)
     pos+=boxSize
    }
   }
   scan(0,size,0)
  }
 }
 return entries
}
