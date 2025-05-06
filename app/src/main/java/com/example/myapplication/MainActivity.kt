package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.VideoUtils
import java.io.File

class MainActivity : ComponentActivity() {
    
    // Use MutableState for the trimmed video URI to ensure UI updates
    private var selectedVideoUri: Uri? = null
    private val trimmedVideoUri = mutableStateOf<Uri?>(null)
    private var exoPlayer: ExoPlayer? = null
    
    // Activity result launcher for video selection
    private val selectVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            trimmedVideoUri.value = null  // Reset the trimmed video when a new one is selected
            trimSelectedVideo(it)
        }
    }
    
    // Activity result launcher for permission requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            openVideoSelector()
        } else {
            Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VideoTrimmerScreen(
                    onSelectVideoClick = { checkAndRequestPermissions() },
                    trimmedVideoUri = trimmedVideoUri.value,
                    isProcessing = selectedVideoUri != null && trimmedVideoUri.value == null
                )
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            openVideoSelector()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }
    
    private fun openVideoSelector() {
        selectVideo.launch("video/*")
    }
    
    private fun trimSelectedVideo(uri: Uri) {
        Toast.makeText(this, "Trimming video...", Toast.LENGTH_SHORT).show()
        trimmedVideoUri.value = null // Reset when starting a new trim
        
        // Get the actual file path from the uri
        val inputPath = getRealPathFromUri(uri)
        
        // Create output file
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.mp4")
        
        // Show the file path in logcat
        android.util.Log.d("VideoTrimmer", "Output file: ${outputFile.absolutePath}")
        
        // Execute trimming in a background thread
        Thread {
            val success = VideoUtils.trimMp4Video(inputPath, outputFile.absolutePath)
            
            runOnUiThread {
                if (success) {
                    // Update the state variable to trigger recomposition
                    trimmedVideoUri.value = outputFile.toUri()
                    android.util.Log.d("VideoTrimmer", "Trimming complete, URI: ${trimmedVideoUri.value}")
                    Toast.makeText(this, "Video trimmed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to trim video", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun getRealPathFromUri(uri: Uri): String {
        // For simplicity, this is a basic implementation
        // In a production app, you would need a more robust solution to handle different URI types
        val filePathColumn = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, filePathColumn, null, null, null)
        var path = ""
        
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(filePathColumn[0])
                if (columnIndex >= 0) {
                    path = it.getString(columnIndex)
                }
            }
        }
        
        // If we couldn't resolve the path, return the URI string as fallback
        return path.ifEmpty { uri.toString() }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerScreen(
    onSelectVideoClick: () -> Unit,
    trimmedVideoUri: Uri?,
    isProcessing: Boolean
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    
    // Force recomposition when trimmedVideoUri changes
    val videoUriKey by rememberUpdatedState(trimmedVideoUri)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Trimmer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onSelectVideoClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Video")
            }
            
            // Status message
            if (trimmedVideoUri == null) {
                Text(
                    "Select a video to trim and play",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    "Video ready to play",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Video Player
            if (trimmedVideoUri != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            // Create new player when this view is first created
                            val newPlayer = ExoPlayer.Builder(ctx).build()
                            player = newPlayer
                            this.player = newPlayer
                            
                            // Configure player
                            useController = true
                            controllerAutoShow = true
                            
                            // Load the video
                            newPlayer.apply {
                                setMediaItem(MediaItem.fromUri(trimmedVideoUri))
                                prepare()
                                playWhenReady = false // Don't auto-play
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f/9f)
                        .border(2.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp))
                )
                
                // Debug info
                Text(
                    text = "Video path: ${trimmedVideoUri.path}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { player?.play() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Play")
                    }
                    
                    Button(
                        onClick = { player?.pause() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Pause")
                    }
                    
                    Button(
                        onClick = { 
                            player?.seekTo(0)
                            player?.play()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Restart")
                    }
                }
            } else {
                // Placeholder for video player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Processing video...")
                        }
                    } else {
                        Text("No video selected")
                    }
                }
            }
        }
    }
    
    // Clean up the player when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }
}