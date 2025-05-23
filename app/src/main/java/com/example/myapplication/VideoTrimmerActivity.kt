package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class VideoTrimmerActivity : ComponentActivity() {
    private var videoUri: Uri? = null
    private var player: ExoPlayer? = null
    private val maxTrimDurationMs = 30_000L // 30 seconds max trim duration

    companion object {
        private const val EXTRA_VIDEO_URI = "extra_video_uri"

        fun createIntent(
            context: Context,
            videoUri: Uri,
        ): Intent =
            Intent(context, VideoTrimmerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri.toString())
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract video URI from intent
        intent.getStringExtra(EXTRA_VIDEO_URI)?.let {
            videoUri = it.toUri()
        }

        if (videoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VideoTrimmerScreen(
                    videoUri = videoUri!!,
                    maxTrimDurationMs = maxTrimDurationMs,
                    onTrimVideo = { startMs, endMs ->
                        if (endMs - startMs >= MIN_TRIM_DURATION_MS) {
                            trimVideo(startMs, endMs)
                        } else {
                            Toast
                                .makeText(
                                    this,
                                    "Selected duration must be at least ${MIN_TRIM_DURATION_MS / 1000} second(s)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun trimVideo(
        startTimeMs: Long,
        endTimeMs: Long,
    ) {
        val inputPath = getRealPathFromUri(videoUri!!)
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.mp4")

        Toast.makeText(this, "Starting video trim...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val success =
                VideoUtils.trimMp4Video(
                    inputPath,
                    outputFile.absolutePath,
                    startTimeMs * 1000, // Convert ms to μs
                    endTimeMs * 1000, // Convert ms to μs
                )

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast
                        .makeText(
                            this@VideoTrimmerActivity,
                            "Video trimmed successfully: ${outputFile.name}",
                            Toast.LENGTH_LONG,
                        ).show()

                    // Return the trimmed video URI to the calling activity
                    setResult(
                        RESULT_OK,
                        Intent().apply {
                            putExtra("trimmed_video_uri", outputFile.toUri().toString())
                        },
                    )
                    finish()
                } else {
                    Toast
                        .makeText(
                            this@VideoTrimmerActivity,
                            "Failed to trim video",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    private fun getRealPathFromUri(uri: Uri): String {
        // For simplicity, this is a basic implementation
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
        player?.release()
        player = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    maxTrimDurationMs: Long,
    onTrimVideo: (Long, Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var currentPlayerPositionMs by remember { mutableLongStateOf(0L) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoadingThumbnails by remember { mutableStateOf(true) }

    // Trim selection values - using stable remember for these values
    var trimStartMs by remember(videoUri) { mutableLongStateOf(0L) }
    var trimEndMs by remember(videoUri) { mutableLongStateOf(maxTrimDurationMs) }

    // Get video duration
    LaunchedEffect(videoUri) {
        videoDurationMs = VideoUtils.getVideoDuration(context, videoUri)

        // Initialize end position - default to max allowed duration or video length
        trimEndMs = min(maxTrimDurationMs, videoDurationMs)

        // Load thumbnails in background
        withContext(Dispatchers.IO) {
            val thumbSize = android.util.Size(120, 80)
            val thumbs =
                VideoUtils.generateThumbnails(
                    context,
                    videoUri,
                    12, // Generate 8 thumbnails
                    thumbSize,
                )

            withContext(Dispatchers.Main) {
                thumbnails = thumbs
                isLoadingThumbnails = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim Video") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (trimEndMs - trimStartMs >= MIN_TRIM_DURATION_MS) {
                                onTrimVideo(trimStartMs, trimEndMs)
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        "Selected duration must be at least ${MIN_TRIM_DURATION_MS / 1000} second(s)",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Check, "Apply Trim")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Video Preview
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            val newPlayer = ExoPlayer.Builder(ctx).build()
                            player = newPlayer
                            this.player = newPlayer

                            // Configure player
                            useController = false

                            // Load the video
                            newPlayer.apply {
                                setMediaItem(MediaItem.fromUri(videoUri))
                                prepare()
                                playWhenReady = false

                                // Don't automatically seek when trim handles change - this can cause feedback loops
                                addListener(
                                    object : Player.Listener {
                                        override fun onPlaybackStateChanged(state: Int) {
                                            if (state == Player.STATE_ENDED) {
                                                isPlaying = false
                                                // Only seek if we're not currently dragging
                                                newPlayer.seekTo(trimStartMs)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (player == null) {
                    CircularProgressIndicator(color = Color.White)
                }

                // Play/Pause overlay button
                IconButton(
                    onClick = {
                        player?.let { exoPlayer ->
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                // Ensure we're within the trimming region
                                val currentPos = exoPlayer.currentPosition
                                if (currentPos < trimStartMs || currentPos >= trimEndMs) {
                                    exoPlayer.seekTo(trimStartMs)
                                }
                                exoPlayer.play()
                            }
                            isPlaying = !isPlaying
                        }
                    },
                    modifier =
                        Modifier
                            .size(56.dp)
                            .shadow(4.dp, CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = CircleShape,
                            ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Time selection and scrubber
            Text(
                text = "Selected: ${formatDuration(trimEndMs - trimStartMs)} / ${formatDuration(maxTrimDurationMs)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )

            // 使用从独立文件导入的 VideoTrimSlider
            VideoTrimSlider(
                thumbnails = thumbnails,
                isLoadingThumbnails = isLoadingThumbnails,
                videoDurationMs = videoDurationMs,
                maxTrimDurationMs = maxTrimDurationMs,
                currentPositionMs = currentPlayerPositionMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                onTrimStartChanged = { newStartMs ->
                    trimStartMs = newStartMs
                    // player?.seekTo(newStartMs)
                },
                onTrimEndChanged = { newEndMs ->
                    trimEndMs = newEndMs
                    if (currentPlayerPositionMs > newEndMs) {
                        player?.seekTo(trimStartMs)
                    }
                },
                onCurrentPositionChanged = { newPosMs ->
                    currentPlayerPositionMs = newPosMs
                    player?.seekTo(newPosMs)
                    if (isPlaying) {
                        player?.pause()
                        isPlaying = false
                    }
                },
            )

            // Track player position for the progress indicator
            DisposableEffect(player) {
                val listener =
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            // Handle playback state changes only
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            // Only update currentPlayerPositionMs without affecting trim values
                            // This prevents unwanted feedback between player position and trim handles
                            currentPlayerPositionMs = newPosition.positionMs
                        }
                    }

                player?.addListener(listener)

                onDispose {
                    player?.removeListener(listener)
                }
            }

            // Show trim controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Start Time")
                    Text(
                        formatDuration(trimStartMs),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("End Time")
                    Text(
                        formatDuration(trimEndMs),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Trim action button
            Button(
                onClick = {
                    if (trimEndMs - trimStartMs >= MIN_TRIM_DURATION_MS) {
                        onTrimVideo(trimStartMs, trimEndMs)
                    } else {
                        Toast
                            .makeText(
                                context,
                                "Selected duration must be at least ${MIN_TRIM_DURATION_MS / 1000} second(s)",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "TRIM VIDEO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
