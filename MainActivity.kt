package com.konasl.nagad

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var outputPath by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pick a video to export to 4K") }
    var progress by remember { mutableStateOf(0f) }
    var isExporting by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(inputUri) {
        inputUri?.let {
            exoPlayer.setMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUri = it
            status = "Video loaded. Ready to export."
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("4K Exporter - com.konasl.nagad") }) }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("PICK VIDEO")
                }

                if (inputUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }

                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (isExporting) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(logs.takeLast(500), style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    enabled = inputUri != null && !isExporting,
                    onClick = {
                        scope.launch {
                            isExporting = true
                            progress = 0f
                            status = "Preparing..."
                            try {
                                // 1. Copy input uri to cache file for ffmpeg access
                                val inputFile = withContext(Dispatchers.IO) {
                                    val temp = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
                                    context.contentResolver.openInputStream(inputUri!!)?.use { ins ->
                                        FileOutputStream(temp).use { out -> ins.copyTo(out) }
                                    }
                                    temp
                                }

                                // 2. Prepare output dir: Movies/4K_Nagad_Export/
                                val outDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "4K_Nagad_Export").apply { mkdirs() }
                                } else {
                                    File(Environment.getExternalStorageDirectory(), "Movies/4K_Nagad_Export").apply { mkdirs() }
                                }
                                val outFile = File(outDir, "4K_Nagad_${System.currentTimeMillis()}.mp4")
                                outputPath = outFile.absolutePath

                                // 3. FFmpeg command - exact as required
                                // -i $input -vf scale=3840:2160:flags=lanczos -c:v libx264 -b:v 40M -preset ultrafast -pix_fmt yuv420p -c:a copy $output
                                val cmd = "-i ${inputFile.absolutePath} -vf scale=3840:2160:flags=lanczos -c:v libx264 -b:v 40M -preset ultrafast -pix_fmt yuv420p -c:a copy ${outFile.absolutePath}"

                                status = "Exporting to 4K..."
                                FFmpegKit.executeAsync(cmd, { session ->
                                    val rc = session.returnCode
                                    scope.launch {
                                        isExporting = false
                                        if (ReturnCode.isSuccess(rc)) {
                                            // Insert to MediaStore on Android 10+
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val values = ContentValues().apply {
                                                    put(MediaStore.Video.Media.DISPLAY_NAME, outFile.name)
                                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/4K_Nagad_Export")
                                                    put(MediaStore.Video.Media.IS_PENDING, 0)
                                                }
                                                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                            }
                                            status = "Done! Saved to: ${outFile.absolutePath}"
                                            progress = 1f
                                        } else {
                                            status = "Failed: ${session.failStackTrace ?: rc}"
                                        }
                                    }
                                }, { log -> logs = log.message }, { statistics ->
                                    val pct = statistics.progress
                                    if (pct > 0) progress = (pct / 100f).coerceIn(0f, 1f)
                                    logs = "Progress: ${statistics.progress}% Time: ${statistics.time}"
                                })

                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (isExporting) "EXPORTING..." else "EXPORT TO 4K (3840x2160)")
                }

                if (outputPath.isNotEmpty()) {
                    Text("Last output: $outputPath", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.weight(1f))
                Text("Package: com.konasl.nagad | SDK 36 | FFmpeg GPL", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
