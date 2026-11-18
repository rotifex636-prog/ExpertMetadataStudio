package com.expert.metadata

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Accent=Color(0xFF00D7F6)

@Composable
internal fun StudioLogo(){
 Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF073658)),contentAlignment=Alignment.Center){
  Icon(Icons.Default.Movie,contentDescription="Expert Metadata Studio logo",tint=Accent,modifier=Modifier.size(31.dp))
 }
}

@Composable
internal fun PremiumWaveHeader(){
 Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF071B35),Color(0xFF0C345D),Color(0xFF04152B))))){
  Canvas(Modifier.fillMaxSize()){
   val w=size.width;val h=size.height
   for(j in 0..3){
    val p=Path().apply{
     moveTo(0f,h*(.25f+j*.11f))
     cubicTo(w*.23f,h*(.95f-j*.07f),w*.58f,h*(-.1f+j*.07f),w,h*(.35f+j*.1f))
    }
    drawPath(p,Brush.horizontalGradient(listOf(Color(0x0000D7F6),Color(0xFF00D7F6),Color(0x0027A8FF))),style=Stroke(width=(2+j).dp.toPx(),cap=StrokeCap.Round))
   }
  }
  Row(Modifier.align(Alignment.Center).background(Color(0x5504142A),RoundedCornerShape(10.dp)).padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
   Text("SMART MEDIA PROCESSOR",color=Color.White,fontSize=12.sp,letterSpacing=1.sp)
  }
 }
}

@Composable
internal fun PremiumNavIcon(name:String,active:Boolean){
 val tint=if(active)Accent else Color(0xFFDBE7F5)
 val icon=when(name){
  "Home"->Icons.Default.Home
  "Files"->Icons.Default.Folder
  "Settings"->Icons.Default.Settings
  else->Icons.Default.Info
 }
 Icon(icon,contentDescription=name,tint=tint,modifier=Modifier.size(27.dp))
}

@Composable
internal fun MediaThumbnail(context:Context,uri:Uri,modifier:Modifier=Modifier){
 var bitmap by remember(uri){mutableStateOf<Bitmap?>(null)}
 LaunchedEffect(uri){
  bitmap=withContext(Dispatchers.IO){
   runCatching{context.contentResolver.loadThumbnail(uri,Size(320,240),null)}.getOrNull()
  }
 }
 Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF124467)),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap!!.asImageBitmap(),contentDescription="Media preview",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Icon(Icons.Default.Movie,contentDescription="Media",tint=Accent,modifier=Modifier.size(28.dp))
 }
}
