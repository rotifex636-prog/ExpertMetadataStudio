package com.expert.metadata

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.media.MediaMetadataRetriever
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.ArrayList

private val Navy=Color(0xFF061326)
private val Panel=Color(0xFF0B2545)
private val Cyan=Color(0xFF00D7F6)
private val Muted=Color(0xFF9BB8CF)
private val Green=Color(0xFF22E98A)

class StudioActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { StudioApp(this) }}
}

@Composable
private fun StudioApp(activity:ComponentActivity){
 var page by remember{mutableStateOf("Home")}
 var picker by remember{mutableStateOf(false)}
 var selected by remember{mutableStateOf<List<Uri>>(emptyList())}
 var usa by remember{mutableStateOf(false)}
 var city by remember{mutableStateOf("New York")}
 var autoClean by remember{mutableStateOf(true)}
 var preserve by remember{mutableStateOf(true)}
 var batch by remember{mutableStateOf(true)}
 var notifications by remember{mutableStateOf(true)}
 var progress by remember{mutableFloatStateOf(0f)}
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Ready to Process")}
 var details by remember{mutableStateOf("")}
 var output by remember{mutableStateOf("")}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)){if(it.isNotEmpty()){selected=it;page="Details"}}
 val browse=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){if(it.isNotEmpty()){selected=it;page="Details"}}
 val record=rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()){if(it){page="Details"}}
 DisposableEffect(Unit){
  val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
   progress=i.getFloatExtra("progress",progress)
   status=i.getStringExtra("status")?:status
   details=i.getStringExtra("details")?:details
   busy=i.getBooleanExtra("busy",busy)
   if(!busy){output=details;page="Results"}
  }}
  ContextCompat.registerReceiver(activity,receiver,IntentFilter(ProcessingService.UPDATE),ContextCompat.RECEIVER_NOT_EXPORTED)
  onDispose{activity.unregisterReceiver(receiver)}
 }
 fun start(){
  if(selected.isEmpty())return
  busy=true;progress=0f;status="Starting";page="Processing"
  ContextCompat.startForegroundService(activity,Intent(activity,ProcessingService::class.java).apply{
   action=ProcessingService.START
   putParcelableArrayListExtra("uris",ArrayList(selected))
   putExtra("synthetic_usa",usa);putExtra("usa_city",city)
  })
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,background=Navy,surface=Panel)){
  Column(Modifier.fillMaxSize().background(Navy)){
   if(page=="Home"||page=="Files"||page=="Settings"||page=="About") {
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=26.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
     Text(if(page=="Home")"▣" else if(page=="Settings")"⚙" else "◈",fontSize=28.sp,color=Cyan)
     Spacer(Modifier.width(12.dp))
     Column {
      Text(if(page=="Home")"Expert Metadata Studio" else page,color=Color.White,fontWeight=FontWeight.Bold,fontSize=21.sp)
      if(page=="Home")Text("SMART MEDIA PROCESSOR",color=Cyan,fontSize=10.sp,letterSpacing=1.sp)
     }
    }
   }else{
    Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
     TextButton(onClick={page="Home"}){Text("‹",fontSize=32.sp,color=Color.White)}
     Text(when(page){"Details"->"Media Details";"Processing"->"Processing";else->"Results"},fontSize=22.sp,color=Color.White,fontWeight=FontWeight.Bold)
    }
   }
   Box(Modifier.weight(1f)){
    when(page){
     "Home"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF003C74),Cyan,Color(0xFF003C74)))),contentAlignment=Alignment.Center){
       Text("✦  CLEAN  •  PROTECT  •  EXPORT  ✦",color=Color.White,fontWeight=FontWeight.Bold)
      }
      StudioCard {
       Text("Metadata Cleaner & Editor",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
       Text("Remove, Clean & Rewrite Media Metadata with Original Quality when supported",color=Muted,fontSize=13.sp)
       Spacer(Modifier.height(8.dp))
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
        Feature("♢","Clean\nMetadata")
        Feature("🇺🇸","USA\nProfile")
        Feature("HD","Original\nQuality")
       }
       Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){Text("●  Ready to Process",color=Green)}
       PrimaryButton("▣  Select Media"){picker=true}
      }
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Recent Files",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold);TextButton(onClick={selected=emptyList()}){Text("Clear All")}}
      if(selected.isEmpty())StudioCard{Text("No files yet — select media to begin",color=Muted)}
      selected.take(5).forEach{uri->StudioCard{Text("▶  "+displayName(activity,uri),color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)}}
     }
     "Details"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      StudioCard{
       Box(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1D628A),Color(0xFF06192F)))),contentAlignment=Alignment.Center){
        Text("▶",fontSize=54.sp,color=Color.White)
       }
       Text(selected.firstOrNull()?.let{displayName(activity,it)}?:"No media selected",color=Color.White,fontWeight=FontWeight.Bold,maxLines=2)
       Text("${selected.size} file(s) selected",color=Muted)
      }
      StudioCard{
       Text("Metadata (Before Processing)",color=Color.White,fontWeight=FontWeight.Bold)
       val uri=selected.firstOrNull()
       val mime=uri?.let{activity.contentResolver.getType(it)}?:"Unknown"
       Info("Format",mime)
       Info("File name",uri?.let{displayName(activity,it)}?:"—")
       Info("Location","Not verified")
       Info("Other Tags","Detailed inspection not yet available")
      }
      OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth()){Text("＋ Add More Media")}
      PrimaryButton("🚀  Start Processing"){start()}
     }
     "Processing"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
      Spacer(Modifier.height(10.dp))
      Box(Modifier.size(190.dp),contentAlignment=Alignment.Center){
       CircularProgressIndicator(progress={progress},modifier=Modifier.fillMaxSize(),strokeWidth=13.dp,color=Cyan,trackColor=Color(0xFF17395B))
       Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${(progress*100).toInt()}%",color=Color.White,fontSize=39.sp,fontWeight=FontWeight.Bold);Text(if(busy)"Processing…" else status,color=Color.White)}
      }
      Text(status,color=Cyan)
      StudioCard{
       Text("Processing Pipeline",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
       listOf("Scan media","Clean supported metadata","Apply optional USA location","Export media","Verify output").forEachIndexed{index,label->
        val step=(progress*5).toInt()
        Info(if(index<step)"✓" else if(index==step)"◉" else "○",label)
       }
      }
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
       OutlinedButton(onClick={activity.startService(Intent(activity,ProcessingService::class.java).setAction(ProcessingService.CANCEL))},enabled=busy){Text("□ Cancel")}
       Button(onClick={page="Home"}){Text("Run in Background")}
      }
     }
     "Results"->Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      StudioCard{
       Text("Processing Result",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
       Text(status,color=Cyan)
       Text(output.ifBlank{"No output details available"},color=Color.White)
      }
      PrimaryButton("Select More Media"){picker=true}
      OutlinedButton(onClick={page="Home"},modifier=Modifier.fillMaxWidth()){Text("Home")}
     }
     "Settings"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Text("Processing Options",color=Cyan,fontWeight=FontWeight.Bold)
      StudioCard{
       Setting("Auto Clean Metadata","Clean supported non-essential tags",autoClean){autoClean=it}
       Setting("🇺🇸 Generate USA Profile","Synthetic location in supported MP4",usa){usa=it}
       if(usa){
        Text("USA City",color=Muted)
        listOf("New York","Los Angeles","Chicago").forEach{c->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=city==c,onClick={city=c});Text(c,color=Color.White)}}
       }
       Setting("Preserve Original Quality","Stream copy when supported",preserve){preserve=it}
       Text("MP4 remux uses stream copy. Images are re-exported.",color=Muted,fontSize=11.sp)
      }
      Text("General",color=Cyan,fontWeight=FontWeight.Bold)
      StudioCard{
       Info("Save Location","Movies/Pictures/ExpertMetadataStudio")
       Setting("Batch Processing","Process multiple files",batch){batch=it}
       Setting("Processing Notifications","Foreground progress",notifications){notifications=it}
       Info("App Theme","Dark (Blue)")
      }
      Text("Settings are UI preferences; some controls are informational until fully wired.",color=Muted,fontSize=12.sp)
     }
     "Files"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Text("Selected / Recent Media",color=Cyan,fontSize=18.sp,fontWeight=FontWeight.Bold)
      if(selected.isEmpty())StudioCard{Text("No files selected",color=Muted)}
      selected.forEach{uri->StudioCard{Text(displayName(activity,uri),color=Color.White)}}
      Text("Processed exports are saved in Movies/Pictures/ExpertMetadataStudio. Output history browser is not yet implemented.",color=Muted,fontSize=12.sp)
      PrimaryButton("Select Media"){picker=true}
     }
     "About"->Column(Modifier.padding(18.dp)){StudioCard{
      Text("Expert Metadata Studio",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
      Text("V0.7 UI Preview",color=Cyan)
      Text("MP4 remux and JPEG/PNG re-export. Synthetic USA metadata is not evidence of recording location. Output and metadata support vary by device.",color=Muted)
     }}
    }
   }
   if(page in listOf("Home","Files","Settings","About")){
    Row(Modifier.fillMaxWidth().background(Color(0xFF03101F)).padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
     listOf("Home" to "⌂","Files" to "▱","Settings" to "⚙","About" to "ⓘ").forEach{(name,symbol)->
      Column(Modifier.clickable{page=name}.padding(horizontal=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
       Text(symbol,color=if(page==name)Cyan else Color.White,fontSize=25.sp)
       Text(name,color=if(page==name)Cyan else Color.White,fontSize=11.sp)
      }
     }
    }
   }
  }
  if(picker)ModalBottomSheet(onDismissRequest={picker=false},containerColor=Panel){
   Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text("Select Media",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
    PickerChoice("▣","Choose from Gallery","Select videos and photos"){picker=false;gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}
    PickerChoice("▱","Browse Files","Select from downloads and folders"){picker=false;browse.launch(arrayOf("video/*","image/*"))}
    PickerChoice("▤","Batch Select","Choose multiple files"){picker=false;browse.launch(arrayOf("video/*","image/*"))}
    Text("Supported: MP4, JPG, JPEG, PNG",color=Muted)
    Spacer(Modifier.height(12.dp))
   }
  }
 }
}

@Composable private fun StudioCard(content:@Composable ColumnScope.()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp),content=content)
 }
}
@Composable private fun PrimaryButton(label:String,click:()->Unit){Button(onClick=click,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Cyan,contentColor=Navy)){Text(label,fontWeight=FontWeight.Bold)}}
@Composable private fun Feature(icon:String,label:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(icon,color=Cyan,fontSize=25.sp);Text(label,color=Color.White,fontSize=12.sp,lineHeight=16.sp)}}
@Composable private fun Info(key:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(key,color=Muted,fontSize=13.sp);Text(value,color=Color.White,fontSize=12.sp,maxLines=2)}}
@Composable private fun Setting(label:String,sub:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(label,color=Color.White,fontWeight=FontWeight.SemiBold);Text(sub,color=Muted,fontSize=11.sp)};Switch(checked=value,onCheckedChange=onChange)}}
@Composable private fun PickerChoice(icon:String,label:String,sub:String,click:()->Unit){Card(onClick=click,colors=CardDefaults.cardColors(containerColor=Color(0xFF12375D)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,color=Cyan,fontSize=27.sp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(label,color=Color.White,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=12.sp)};Text("›",color=Color.White,fontSize=26.sp)}}}
