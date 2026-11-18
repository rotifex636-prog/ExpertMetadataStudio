package com.expert.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun needsVideoTranscode(context: Context, uri: Uri): Boolean {
 val extractor=MediaExtractor()
 try {
  context.contentResolver.openFileDescriptor(uri,"r")?.use { fd ->
   extractor.setDataSource(fd.fileDescriptor)
   for(i in 0 until extractor.trackCount) {
    val mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
    if(mime.startsWith("video/") && mime !in setOf("video/avc","video/hevc","video/mp4v-es","video/3gpp","video/av01")) return true
   }
  } ?: error("Cannot open input")
 } finally { extractor.release() }
 return false
}

/** Transcodes video to AVC and audio to AAC when necessary. Must run off the main thread. */
internal fun transcodeMkvToMp4(context: Context, uri: Uri, onProgress:(Float)->Unit): File {
 val output=File.createTempFile("expert_transcoded_",".mp4",context.cacheDir)
 val finished=CountDownLatch(1)
 val failure=AtomicReference<Throwable?>()
 val handler=Handler(Looper.getMainLooper())
 val transformerRef=AtomicReference<Transformer?>()
 handler.post {
  try {
   val transformer=Transformer.Builder(context)
    .setVideoMimeType(MimeTypes.VIDEO_H264)
    .setAudioMimeType(MimeTypes.AUDIO_AAC)
    .addListener(object: Transformer.Listener {
     override fun onCompleted(composition:Composition, exportResult:ExportResult) { finished.countDown() }
     override fun onError(composition:Composition, exportResult:ExportResult, exportException:ExportException) {
      failure.set(exportException); finished.countDown()
     }
    }).build()
   transformerRef.set(transformer)
   transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build(),output.absolutePath)
  } catch(t:Throwable) { failure.set(t); finished.countDown() }
 }
 try {
  // Long MKV videos can take significant time on mobile hardware.
  while(!finished.await(1,TimeUnit.SECONDS)) {
   onProgress(0.05f)
  }
  failure.get()?.let { throw IllegalStateException("Video conversion failed: ${it.message}",it) }
  check(output.length()>0L){"Video conversion produced an empty file"}
  return output
 } catch(t:Throwable) {
  handler.post { transformerRef.get()?.cancel() }
  output.delete()
  throw t
 }
}
