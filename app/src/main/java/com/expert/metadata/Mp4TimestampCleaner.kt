package com.expert.metadata

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Clears ISO BMFF mvhd/tkhd/mdhd creation and modification times only.
 * No media samples are rewritten. Throws on malformed structures instead of silently claiming success.
 */
internal fun clearMp4HeaderTimes(context:Context,uri:Uri):Int {
 val descriptor=context.contentResolver.openFileDescriptor(uri,"rw")?:error("Cannot reopen MP4 for timestamp cleaning")
 descriptor.use { fd ->
  val channel=FileOutputStream(fd.fileDescriptor).channel
  val size=channel.size()
  fun read(at:Long,n:Int):ByteBuffer {
   require(at>=0&&n>=0&&at<=size-n){"Invalid MP4 box bounds"}
   val b=ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN)
   var pos=at
   while(b.hasRemaining()){
    val count=channel.read(b,pos)
    check(count>0){"Incomplete MP4 header"}
    pos+=count
   }
   b.flip()
   return b
  }
  fun clear(at:Long,n:Int){
   val b=ByteBuffer.allocate(n)
   var pos=at
   while(b.hasRemaining()){
    val count=channel.write(b,pos)
    check(count>0){"Cannot clear MP4 timestamp"}
    pos+=count
   }
  }
  var modified=0
  fun walk(start:Long,end:Long,depth:Int){
   require(depth<=6){"MP4 nesting too deep"}
   var pos=start
   var boxes=0
   while(pos+8<=end){
    check(boxes++<10000){"Too many MP4 boxes"}
    val h=read(pos,8)
    val n=h.int.toLong() and 0xffffffffL
    val name=ByteArray(4);h.get(name)
    val type=String(name,Charsets.ISO_8859_1)
    val header=if(n==1L)16L else 8L
    val length=when(n){0L->end-pos;1L->read(pos+8,8).long;else->n}
    require(length>=header&&length<=end-pos){"Invalid MP4 box size"}
    val body=pos+header
    if(type=="mvhd"||type=="tkhd"||type=="mdhd"){
     val version=read(body,1).get(0).toInt() and 255
     require(version==0||version==1){"Unsupported MP4 header version"}
     val bytes=if(version==1)16 else 8
     require(body+4+bytes<=pos+length){"Truncated MP4 timestamp fields"}
     clear(body+4,bytes)
     modified++
    }
    if(type=="moov"||type=="trak"||type=="mdia")walk(body,pos+length,depth+1)
    pos+=length
   }
  }
  try{
   walk(0,size,0)
   check(modified>0){"No MP4 timestamp headers found"}
   channel.force(true)
  }finally{channel.close()}
  return modified
 }
}
