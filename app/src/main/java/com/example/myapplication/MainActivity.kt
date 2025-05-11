package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    // Use MutableState for the trimmed video URI to ensure UI updates
    private val trimmedVideoUri = mutableStateOf<Uri?>(null)
    private var exoPlayer: ExoPlayer? = null

    // Activity result launcher for video selection
    private val selectVideo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Launch VideoTrimmerActivity instead of directly trimming
                val intent = VideoTrimmerActivity.createIntent(this, it)
                trimVideoLauncher.launch(intent)
            }
        }

    // Activity result launcher for VideoTrimmerActivity
    private val trimVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val trimmedUri = data?.getStringExtra("trimmed_video_uri")?.let { Uri.parse(it) }
                if (trimmedUri != null) {
                    trimmedVideoUri.value = trimmedUri
                    Toast.makeText(this, "Video trimmed successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Activity result launcher for permission requests
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
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
                MainScreen(
                    onSelectVideoClick = { checkAndRequestPermissions() },
                    trimmedVideoUri = trimmedVideoUri.value,
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSelectVideoClick: () -> Unit,
    trimmedVideoUri: Uri?,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Force recomposition when trimmedVideoUri changes
    val videoUriKey by rememberUpdatedState(trimmedVideoUri)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Trimmer") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onSelectVideoClick,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(
                    "SELECT VIDEO TO TRIM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            // Information text about the app
            Text(
                text = "This app allows you to trim videos with a custom interface.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            Text(
                text = "Features:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("• Select any video from your device")
                Text("• Preview the video before trimming")
                Text("• Choose any segment up to 30 seconds")
                Text("• Fine-tune start and end points with frame accuracy")
                Text("• Save the trimmed video to your device")
            }

            // Video Player (only shown when a video has been trimmed)
            if (trimmedVideoUri != null) {
                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Trimmed Video Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .border(2.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp)),
                )

                // Debug info
                Text(
                    text = "Saved to: ${trimmedVideoUri.path?.substringAfterLast('/')}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Button to select another video
                Button(
                    onClick = onSelectVideoClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                ) {
                    Text("TRIM ANOTHER VIDEO")
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
