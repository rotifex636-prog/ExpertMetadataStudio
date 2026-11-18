package com.expert.metadata

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun buildMetadataReport(
 sourceName:String,
 result:String,
 before:List<MetadataEntry>,
 after:List<MetadataEntry>,
 usaEnabled:Boolean,
 city:String,
 cameraProfile:String
):String {
 val first=before.associateBy{it.group+" / "+it.label}
 val second=after.associateBy{it.group+" / "+it.label}
 val keys=(first.keys+second.keys).distinct().sorted()
 fun clean(v:String?):String?=v?.takeUnless{it=="Not found"}
 fun state(key:String):String{
  val old=clean(first[key]?.value)
  val newer=clean(second[key]?.value)
  return when {
   old==null&&newer==null->"Not found"
   old==null->"Added"
   newer==null->"Removed / not exposed"
   old==newer->"Unchanged"
   else->"Changed"
  }
 }
 val counts=keys.groupingBy{state(it)}.eachCount()
 return buildString{
  appendLine("EXPERT METADATA STUDIO — BEFORE / AFTER REPORT")
  appendLine("Source: $sourceName")
  appendLine("Processing: $result")
  appendLine("Synthetic USA GPS option: "+if(usaEnabled)city else "OFF")
  appendLine("Selected camera profile (app preference only): $cameraProfile")
  appendLine("Camera profile is not written to MP4 camera/model metadata in this build.")
  val utcText=second["MP4 Container / Movie header creation time (UTC)"]?.value
  val instant=runCatching{Instant.parse(utcText)}.getOrNull()
  if(instant!=null){
   val zone=when(city){"New York"->"America/New_York";"Chicago"->"America/Chicago";"Los Angeles"->"America/Los_Angeles";else->"UTC"}
   val format=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
   appendLine("Output MP4 creation UTC: $instant")
   appendLine("Output MP4 creation in device timezone: "+format.withZone(ZoneId.systemDefault()).format(instant))
   if(usaEnabled)appendLine("Output MP4 creation in selected USA timezone: "+format.withZone(ZoneId.of(zone)).format(instant))
   appendLine("Timezone conversions above are display-only, not embedded timestamp edits.")
  }
  appendLine("Fields: ${keys.size}; Added: ${counts["Added"]?:0}; Changed: ${counts["Changed"]?:0}; Unchanged: ${counts["Unchanged"]?:0}; Removed/not exposed: ${counts["Removed / not exposed"]?:0}; Not found: ${counts["Not found"]?:0}")
  appendLine()
  for(key in keys){
   appendLine("[$key] ${state(key)}")
   appendLine("  BEFORE: ${first[key]?.value?:"Not found"}")
   appendLine("  AFTER: ${second[key]?.value?:"Not found"}")
  }
  appendLine()
  appendLine("LIMITATIONS: Android-readable metadata only. 'Removed / not exposed' does not prove complete deletion. File-system dates and proprietary MP4 atoms may not be included. USA location is synthetic; timezone preview does not rewrite embedded timestamps.")
 }
}
