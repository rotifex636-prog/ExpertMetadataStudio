package com.expert.metadata

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private val Navy = Color(0xFF0B1425)
private val Panel = Color(0xFF182A44)
private val Blue = Color(0xFF38BDF8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
            var message by remember { mutableStateOf("Select media to inspect. Processing engine is not yet installed.") }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                selected = uris
                message = if (uris.isEmpty()) "No files selected." else "${uris.size} file(s) selected. Scan-only prototype; no files changed."
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel)) {
                Column(Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text("◈ Expert Metadata Studio", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("SMART MEDIA PROCESSOR  •  DEVELOPMENT PREVIEW", color = Blue, fontSize = 11.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Metadata Cleaner & Editor", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Auto Clean  •  USA Profile  •  Quality Preserve", color = Color.LightGray)
                            Text("● AUTO MODE — PLANNED", color = Color(0xFF86EFAC), fontSize = 12.sp)
                            Button(onClick = { picker.launch(arrayOf("video/*", "image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("SELECT MEDIA") }
                        }
                    }
                    Text("Selected files", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                    if (selected.isEmpty()) Text("No media selected", color = Color.LightGray)
                    selected.take(30).forEach { uri ->
                        val name = remember(uri) {
                            runCatching {
                                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                                    if (c.moveToFirst()) "${c.getString(0)}  •  ${c.getLong(1)} bytes" else uri.lastPathSegment.orEmpty()
                                } ?: uri.lastPathSegment.orEmpty()
                            }.getOrDefault("Selected media")
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Text(name, Modifier.fillMaxWidth().padding(14.dp), color = Color.White)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Processing pipeline (coming next)", fontWeight = FontWeight.Bold)
                            listOf("1  Scan metadata", "2  Clean supported tags", "3  Generate synthetic USA profile", "4  Export without re-encoding", "5  Verify output").forEach { Text(it, color = Color.LightGray) }
                        }
                    }
                    Text(message, color = Blue)
                    Text("Privacy note: Synthetic locations are not evidence of recording origin. This prototype does not edit files.", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
    }
}
