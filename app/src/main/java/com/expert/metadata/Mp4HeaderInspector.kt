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
   var cameraMake:String?=null
   var cameraModel:String?=null
   fun readTag(at:Long,end:Long):String?{
    val count=(end-at).coerceAtMost(256L).toInt()
    if(count<=0)return null
    val bytes=read(at,count)?:return null
    val data=ByteArray(count);bytes.get(data)
    val value=String(data,Charsets.UTF_8).trim().trim('\u0000')
    return value.takeIf{it.isNotBlank()&&it.all{ch->!ch.isISOControl()}}
   }
   val mdtaKeys=mutableMapOf<Int,String>()
   fun parseKeys(start:Long,end:Long){
    val header=read(start,8)?:return
    header.int
    val count=header.int
    if(count<0||count>4096)return
    var cursor=start+8
    for(index in 1..count){
     val item=read(cursor,8)?:break
     val itemSize=item.int.toLong() and 0xffffffffL
     val namespace=ByteArray(4);item.get(namespace)
     if(itemSize<8||itemSize>end-cursor)break
     if(String(namespace,Charsets.ISO_8859_1)=="mdta"&&itemSize<=512){
      readTag(cursor+8,cursor+itemSize)?.let{mdtaKeys[index]=it}
     }
     cursor+=itemSize
    }
   }
   fun parseMdtaValue(start:Long,end:Long):String?{
    val header=read(start,8)?:return null
    val boxSize=header.int.toLong() and 0xffffffffL
    val name=ByteArray(4);header.get(name)
    if(String(name,Charsets.ISO_8859_1)!="data"||boxSize<16||boxSize>end-start)return null
    val flags=read(start+8,8)?:return null
    val type=flags.int and 0x00ffffff
    if(type!=1&&type!=0)return null
    return readTag(start+16,start+boxSize)
   }
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
     if(type=="keys")parseKeys(body,pos+boxSize)
     if(depth>=2&&type.length==4&&mdtaKeys.isNotEmpty()){
      val raw=type.toByteArray(Charsets.ISO_8859_1)
      val index=((raw[0].toInt() and 255) shl 24) or ((raw[1].toInt() and 255) shl 16) or ((raw[2].toInt() and 255) shl 8) or (raw[3].toInt() and 255)
      val key=mdtaKeys[index]
      if(key=="com.apple.quicktime.make"||key=="com.apple.quicktime.model"){
       val value=parseMdtaValue(body,pos+boxSize)
       if(value!=null){if(key.endsWith(".make"))cameraMake=value else cameraModel=value}
      }
     }
     if(type=="©mak"||type=="©mod"){
      val nested=read(body,8)
      val hasData=nested!=null&&nested.int.toLong()>=16L&&String(ByteArray(4).also{nested.get(it)},Charsets.ISO_8859_1)=="data"
      val value=readTag(body+if(hasData)16 else 0,pos+boxSize)
      if(type=="©mak")cameraMake=value else cameraModel=value
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
     }else if(type=="moov"||type=="udta"||type=="mdia"||type=="ilst"||type=="meta")scan(body+if(type=="meta")4 else 0,pos+boxSize,depth+1,track)
     pos+=boxSize
    }
   }
   scan(0,size,0)
   entries+=MetadataEntry("Camera Metadata","Make (supported MP4 atoms)",cameraMake?:"Not found / unsupported")
   entries+=MetadataEntry("Camera Metadata","Model (supported MP4 atoms)",cameraModel?:"Not found / unsupported")
  }
 }
 return entries
}
