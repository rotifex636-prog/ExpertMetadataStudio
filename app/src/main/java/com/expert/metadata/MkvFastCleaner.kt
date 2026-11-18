package com.expert.metadata

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile

/**
 * Lossless MKV metadata sanitization. Preserves container length and media bytes.
 * Replaces known metadata elements with EBML Void (same byte length).
 * Does not promise removal of arbitrary proprietary or encoded metadata.
 */
internal fun cleanMkvFast(context:Context,source:Uri,destination:java.io.FileDescriptor,onProgress:(Float)->Unit) {
 val temp=File.createTempFile("mkv_fast_",".mkv",context.cacheDir)
 try {
  context.contentResolver.openInputStream(source)?.use { input ->
   temp.outputStream().buffered().use { out ->
    val buf=ByteArray(1024*1024)
    var copied=0L
    val total=context.contentResolver.openAssetFileDescriptor(source,"r")?.use { it.length } ?: -1L
    while(true) {
     val n=input.read(buf)
     if(n<0)break
     out.write(buf,0,n);copied+=n
     if(total>0)onProgress((copied.toFloat()/total*.65f).coerceIn(0f,.65f))
    }
   }
  } ?: error("Cannot open MKV")
  RandomAccessFile(temp,"rw").use { file ->
   val length=file.length()
   fun vint(pos:Long,id:Boolean):Pair<Long,Int>? {
    if(pos>=length)return null
    file.seek(pos)
    val first=file.readUnsignedByte()
    if(first==0)return null
    val bytes=Integer.numberOfLeadingZeros(first)-24+1
    if(bytes !in 1..8 || pos+bytes>length)return null
    var value=(if(id)first else first and (0xff ushr bytes)).toLong()
    for(i in 1 until bytes)value=(value shl 8) or file.readUnsignedByte().toLong()
    return value to bytes
   }
   fun voidElement(pos:Long,total:Long,idBytes:Int) {
    // A one-byte Void ID plus size vint needs at least two bytes.
    if(total<2)return
    var sizeBytes=1
    while(sizeBytes<8 && total-1-sizeBytes >= (1L shl (7*sizeBytes))-1)sizeBytes++
    val payload=total-1-sizeBytes
    file.seek(pos)
    file.write(0xEC)
    val encoded=payload or (1L shl (7*sizeBytes))
    for(i in sizeBytes-1 downTo 0)file.write(((encoded ushr (i*8)) and 255).toInt())
    val zero=ByteArray(8192)
    var left=payload
    while(left>0) { val n=minOf(left,zero.size.toLong()).toInt();file.write(zero,0,n);left-=n }
   }
   val metadata=setOf(0x1254C367L,0x4461L,0x7BA9L,0x4D80L,0x5741L,0x536EL,0xBFL)
   val containers=setOf(0x1A45DFA3L,0x18538067L,0x1549A966L,0x1654AE6BL,0xAEL)
   fun scan(start:Long,end:Long,depth:Int) {
    if(depth>5)return
    var pos=start
    while(pos<end && pos<length) {
     val id=vint(pos,true)?:break
     val sz=vint(pos+id.second,false)?:break
     val header=id.second+sz.second
     val payload=pos+header
     val maxSize=(1L shl (7*sz.second))-1
     val unknown=sz.first==maxSize
     val next=if(unknown)end else payload+sz.first
     if(next<=pos || next>end || next>length)break
     if(id.first in metadata && !unknown)voidElement(pos,next-pos,id.second)
     else if(id.first in containers)scan(payload,next,depth+1)
     pos=next
    }
   }
   scan(0,length,0)
  }
  // Copy sanitized MKV to MediaStore without modifying video/audio streams.
  java.io.FileOutputStream(destination).use { out ->
   temp.inputStream().buffered().use { input ->
    val buf=ByteArray(1024*1024)
    var copied=0L
    val total=temp.length().coerceAtLeast(1)
    while(true) {
     val n=input.read(buf)
     if(n<0)break
     out.write(buf,0,n);copied+=n
     onProgress(.65f+.35f*(copied.toFloat()/total))
    }
   }
  }
 } finally { temp.delete() }
}
