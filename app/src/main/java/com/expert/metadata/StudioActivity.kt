package com.expert.metadata

import android.content.*
import android.widget.Toast
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
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Navy=Color(0xFF061326)
private val Panel=Color(0xFF0B2545)
private val Cyan=Color(0xFF00D7F6)
private val Muted=Color(0xFF9BB8CF)
private val Green=Color(0xFF22E98A)

class StudioActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { StudioApp(this) }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioApp(activity:ComponentActivity){
 var page by remember{mutableStateOf("Home")}
 var picker by remember{mutableStateOf(false)}
 var selected by remember{mutableStateOf<List<Uri>>(emptyList())}
 val prefs=remember{activity.getSharedPreferences("studio_preferences",Context.MODE_PRIVATE)}
 var usa by remember{mutableStateOf(prefs.getBoolean("synthetic_usa",false))}
 var city by remember{mutableStateOf(prefs.getString("usa_city","New York")?:"New York")}
 LaunchedEffect(usa,city){prefs.edit().putBoolean("synthetic_usa",usa).putString("usa_city",city).apply()}
 var autoClean by remember{mutableStateOf(true)}
 var preserve by remember{mutableStateOf(true)}
 var batch by remember{mutableStateOf(true)}
 var notifications by remember{mutableStateOf(true)}
 var progress by remember{mutableFloatStateOf(0f)}
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Ready to Process")}
 var details by remember{mutableStateOf("")}
 var output by remember{mutableStateOf("")}
 var outputUri by remember{mutableStateOf<Uri?>(null)}
 var beforeSnapshot by remember{mutableStateOf<List<MetadataEntry>>(emptyList())}
 var compareTab by remember{mutableStateOf("Compare")}
 var changesOnly by remember{mutableStateOf(false)}
 var expandedGroups by remember{mutableStateOf(setOf("File","Media"))}
 val inspectedInput=rememberMediaMetadata(activity,selected.firstOrNull())
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)){if(it.isNotEmpty()){selected=it;page="Details"}}
 val browse=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){if(it.isNotEmpty()){selected=it;page="Details"}}
 val record=rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()){if(it){page="Details"}}
 DisposableEffect(Unit){
  val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
   progress=i.getFloatExtra("progress",progress)
   status=i.getStringExtra("status")?:status
   details=i.getStringExtra("details")?:details
   busy=i.getBooleanExtra("busy",busy)
   if(!busy){output=details;outputUri=i.getStringExtra("output_uri")?.takeIf{it.isNotBlank()}?.let{Uri.parse(it)};page="Results"}
  }}
  ContextCompat.registerReceiver(activity,receiver,IntentFilter(ProcessingService.UPDATE),ContextCompat.RECEIVER_NOT_EXPORTED)
  onDispose{activity.unregisterReceiver(receiver)}
 }
 fun start(){
  if(selected.isEmpty())return
  beforeSnapshot=inspectedInput?:emptyList()
  outputUri=null
  busy=true;progress=0f;status="Starting";page="Processing"
  ContextCompat.startForegroundService(activity,Intent(activity,ProcessingService::class.java).apply{
   action=ProcessingService.START
   putParcelableArrayListExtra("uris",ArrayList(selected))
   putExtra("synthetic_usa",usa);putExtra("usa_city",city)
  })
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,background=Navy,surface=Panel)){
  Column(Modifier.fillMaxSize().background(Navy).navigationBarsPadding()){
   if(page=="Home"||page=="Files"||page=="Settings"||page=="About") {
    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=26.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
     StudioLogo()
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
      PremiumWaveHeader()
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
      selected.take(5).forEach{uri->StudioCard{Row(verticalAlignment=Alignment.CenterVertically){MediaThumbnail(activity,uri,Modifier.size(width=84.dp,height=58.dp));Spacer(Modifier.width(12.dp));Text(displayName(activity,uri),color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)}}}
     }
     "Details"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      StudioCard{
       Box(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1D628A),Color(0xFF06192F)))),contentAlignment=Alignment.Center){
        selected.firstOrNull()?.let{MediaThumbnail(activity,it,Modifier.fillMaxSize())} ?: Text("▶",fontSize=54.sp,color=Color.White)
       }
       Text(selected.firstOrNull()?.let{displayName(activity,it)}?:"No media selected",color=Color.White,fontWeight=FontWeight.Bold,maxLines=2)
       Text("${selected.size} file(s) selected",color=Muted)
      }
      val inspected=inspectedInput
      StudioCard{
       Text("Full Metadata Inspector — Before Processing",color=Color.White,fontWeight=FontWeight.Bold)
       if(inspected==null)Text("Reading media metadata…",color=Muted)
       else if(inspected.isEmpty())Text("No readable metadata found",color=Muted)
       else {
        var lastGroup=""
        inspected.forEach{entry->
         if(entry.group!=lastGroup){
          lastGroup=entry.group
          Text(entry.group,color=Cyan,fontWeight=FontWeight.SemiBold)
         }
         Info(entry.label,entry.value)
        }
       }
       Info("USA export option",if(usa)"Synthetic: $city" else "Disabled")
       if(usa){Info("Auto timezone",usaZone(city));Info("USA local time (preview)",usaTime(city))}
      }
      OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth()){Text("＋ Add More Media")}
      Spacer(Modifier.height(18.dp))
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
     "Results"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      StudioCard{
       Text("Processing Result",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
       Text(status,color=Cyan)
       Text(output.ifBlank{"No output details available"},color=Color.White)
      }
      val afterSnapshot=rememberMediaMetadata(activity,outputUri)
      StudioCard{
       Text("Before / After Metadata",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
       if(outputUri!=null&&afterSnapshot!=null){
        val report=buildMetadataReport(selected.firstOrNull()?.let{displayName(activity,it)}?:"Unknown",output,beforeSnapshot,afterSnapshot,usa,city)
        Button(onClick={
         val clipboard=activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
         clipboard.setPrimaryClip(ClipData.newPlainText("Metadata comparison",report))
         Toast.makeText(activity,"Full metadata report copied",Toast.LENGTH_SHORT).show()
        },modifier=Modifier.fillMaxWidth()){Text("Copy Full Metadata Report")}
        OutlinedButton(onClick={
         val intent=Intent(Intent.ACTION_SEND).apply{
          type="text/plain"
          putExtra(Intent.EXTRA_SUBJECT,"Expert Metadata Studio Report")
          putExtra(Intent.EXTRA_TEXT,report)
         }
         activity.startActivity(Intent.createChooser(intent,"Share metadata report"))
        },modifier=Modifier.fillMaxWidth()){Text("Share Text Report")}
       }
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("Before","After","Compare").forEach{tab->
         OutlinedButton(onClick={compareTab=tab}){Text(tab,color=if(compareTab==tab)Cyan else Color.White)}
        }
       }
       if(outputUri==null)Text("No processed output available for comparison.",color=Muted)
       else if(afterSnapshot==null)Text("Inspecting processed file…",color=Muted)
       else{
        val before=beforeSnapshot.associateBy{it.group+" / "+it.label}
        val after=afterSnapshot.associateBy{it.group+" / "+it.label}
        val allKeys=(before.keys+after.keys).distinct().filterNot{it.startsWith("Inspector / ")}
        fun stateOf(key:String):String{
         val old=before[key]?.value?.takeUnless{it=="Not found"}
         val fresh=after[key]?.value?.takeUnless{it=="Not found"}
         return when{
          old==null&&fresh==null->"Not found"
          old==null->"Added"
          fresh==null->"Removed / not exposed"
          old==fresh->"Unchanged"
          else->"Changed"
         }
        }
        val counts=allKeys.groupingBy{stateOf(it)}.eachCount()
        Text("Summary  •  Added: ${counts["Added"]?:0}  •  Changed: ${counts["Changed"]?:0}",color=Cyan,fontWeight=FontWeight.Bold)
        Text("Unchanged: ${counts["Unchanged"]?:0}  •  Removed/not exposed: ${counts["Removed / not exposed"]?:0}  •  Not found: ${counts["Not found"]?:0}",color=Muted,fontSize=12.sp)
        Row(verticalAlignment=Alignment.CenterVertically){
         Checkbox(checked=changesOnly,onCheckedChange={changesOnly=it})
         Text("Show changed fields only",color=Color.White,fontSize=13.sp)
        }
        val keys=when(compareTab){
         "Before"->before.keys.toList()
         "After"->after.keys.toList()
         else->allKeys
        }.filterNot{it.startsWith("Inspector / ")}.filter{!changesOnly||stateOf(it) in listOf("Added","Changed","Removed / not exposed")}
        if(keys.isEmpty())Text("No matching fields.",color=Muted)
        keys.groupBy{it.substringBefore(" / ")}.forEach{(group,fields)->
         val expanded=group in expandedGroups
         OutlinedButton(onClick={expandedGroups=if(expanded)expandedGroups-group else expandedGroups+group},modifier=Modifier.fillMaxWidth()){
          Text((if(expanded)"▾ " else "▸ ")+group+" (${fields.size})",color=Cyan)
         }
         if(expanded)fields.forEach{key->
          val old=before[key]?.value
          val fresh=after[key]?.value
          val change=stateOf(key)
          Text(key.substringAfter(" / "),color=Cyan,fontWeight=FontWeight.SemiBold)
          if(compareTab!="After")Text("Before: ${old?:"Not found"}",color=Muted,fontSize=12.sp)
          if(compareTab!="Before")Text("After: ${fresh?:"Not found"}",color=Color.White,fontSize=12.sp)
          if(compareTab=="Compare")Text(change,color=if(change=="Unchanged"||change=="Not found")Muted else Green,fontSize=11.sp)
         }
        }
        Text("Date: Embedded media date (if exposed) is different from the phone/gallery file date. USA timezone preview does not rewrite recording timestamps.",color=Muted,fontSize=11.sp)
       }
       Text("Comparison covers Android-readable fields only. Missing fields are not proof that all metadata was erased. File timestamps and proprietary MP4 tags may remain.",color=Muted,fontSize=11.sp)
      }
      PrimaryButton("Select More Media"){picker=true}
      OutlinedButton(onClick={page="Home"},modifier=Modifier.fillMaxWidth()){Text("Home")}
     }
     "Settings"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Text("Processing Options",color=Cyan,fontWeight=FontWeight.Bold)
      StudioCard{
       Setting("Auto Clean Metadata","Clean supported non-essential tags",autoClean){autoClean=it}
       Setting("🇺🇸 Generate USA Profile","Optional synthetic GPS location for MP4 exports",usa){usa=it}
       if(usa){
        Text("USA City",color=Muted)
        listOf("New York","Los Angeles","Chicago").forEach{c->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=city==c,onClick={city=c});Text(c,color=Color.White)}}
        Info("Auto Timezone",usaZone(city))
        Info("Current Local Time",usaTime(city))
        Text("Timezone is a preview only. Video recording timestamps are not rewritten.",color=Muted,fontSize=11.sp)
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
      Text("V0.9 Performance Preview",color=Cyan)
      Text("MP4 remux and JPEG/PNG re-export. Synthetic USA metadata is not evidence of recording location. Output and metadata support vary by device.",color=Muted)
     }}
    }
   }
   if(page=="Details"){
    Box(Modifier.fillMaxWidth().background(Navy).padding(horizontal=18.dp,vertical=12.dp)){
     PrimaryButton("Start Processing"){start()}
    }
   }
   if(page in listOf("Home","Files","Settings","About")){
    Row(Modifier.fillMaxWidth().background(Color(0xFF03101F)).padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
     listOf("Home" to "⌂","Files" to "▱","Settings" to "⚙","About" to "ⓘ").forEach{(name,symbol)->
      Column(Modifier.clickable{page=name}.padding(horizontal=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
       PremiumNavIcon(name,page==name)
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
@Composable private fun Info(key:String,value:String){Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(key,color=Muted,fontSize=12.sp);Text(value,color=Color.White,fontSize=13.sp)}}
@Composable private fun Setting(label:String,sub:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(label,color=Color.White,fontWeight=FontWeight.SemiBold);Text(sub,color=Muted,fontSize=11.sp)};Switch(checked=value,onCheckedChange=onChange)}}
@Composable private fun PickerChoice(icon:String,label:String,sub:String,click:()->Unit){Card(onClick=click,colors=CardDefaults.cardColors(containerColor=Color(0xFF12375D)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,color=Cyan,fontSize=27.sp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(label,color=Color.White,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=12.sp)};Text("›",color=Color.White,fontSize=26.sp)}}}

private fun usaZone(city:String):String=when(city){"New York"->"America/New_York";"Chicago"->"America/Chicago";"Los Angeles"->"America/Los_Angeles";else->"America/New_York"}
private fun usaTime(city:String):String=ZonedDateTime.now(ZoneId.of(usaZone(city))).format(DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a z",Locale.US))
