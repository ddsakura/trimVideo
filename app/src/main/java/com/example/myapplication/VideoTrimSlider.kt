package com.example.myapplication

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.min
import androidx.core.graphics.createBitmap

@Composable
fun VideoTrimSlider(
    thumbnails: List<Bitmap>,
    isLoadingThumbnails: Boolean,
    videoDurationMs: Long,
    maxTrimDurationMs: Long,
    currentPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val sliderHeight = 80.dp

    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)
    val currentMaxTrimDurationMs by rememberUpdatedState(maxTrimDurationMs)

    var sliderWidthPx by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(sliderHeight + 40.dp)
                .padding(horizontal = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sliderHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { size ->
                        sliderWidthPx = size.width.toFloat()
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingThumbnails) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    thumbnails.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            if (videoDurationMs > 0 && sliderWidthPx > 0) {
                val startRatio = currentTrimStartMs.toFloat() / videoDurationMs
                val endRatio = currentTrimEndMs.toFloat() / videoDurationMs

                // Left dimmed overlay
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { (startRatio * sliderWidthPx).toDp() })
                            .background(Color.Black.copy(alpha = 0.6f))
                            .align(Alignment.CenterStart),
                )

                // Right dimmed overlay
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { ((1 - endRatio) * sliderWidthPx).toDp() })
                            .background(Color.Black.copy(alpha = 0.6f))
                            .align(Alignment.CenterEnd),
                )

                // Left Handle
                Box(
                    modifier =
                        Modifier
                            .offset(x = with(density) { (startRatio * sliderWidthPx).toDp() - 20.dp })
                            .width(40.dp)
                            .fillMaxHeight()
                            .background(Color.Transparent)
                            .align(Alignment.CenterStart)
                            .pointerInput(videoDurationMs, sliderWidthPx) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (sliderWidthPx == 0f) return@detectDragGestures

                                        val dragRatio = dragAmount.x / sliderWidthPx
                                        val dragMs = (dragRatio * videoDurationMs).toLong()

                                        // Calculate the minimum allowed start position based on the maximum trim duration
                                        val minAllowedStartBasedOnMaxDuration =
                                            (currentTrimEndMs - currentMaxTrimDurationMs).coerceAtLeast(
                                                0L,
                                            )

                                        val newStartMs =
                                            (currentTrimStartMs + dragMs).coerceIn(
                                                minAllowedStartBasedOnMaxDuration,
                                                (currentTrimEndMs - MIN_TRIM_DURATION_MS).coerceAtLeast(0L),
                                            )
                                        onTrimStartChanged(newStartMs)
                                    },
                                )
                            },
                ) {
                    // Visible part of the handle
                    Box(
                        modifier =
                            Modifier
                                .width(8.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                                .align(Alignment.Center),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                                .align(Alignment.Center),
                    )
                }

                // Right Handle
                Box(
                    modifier =
                        Modifier
                            .offset(with(density) { -((1 - endRatio) * sliderWidthPx).toDp() + 20.dp })
                            .width(40.dp)
                            .fillMaxHeight()
                            .background(Color.Transparent)
                            .align(Alignment.CenterEnd)
                            .pointerInput(videoDurationMs, sliderWidthPx) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (sliderWidthPx == 0f) return@detectDragGestures

                                        val dragRatio = dragAmount.x / sliderWidthPx
                                        val dragMs = (dragRatio * videoDurationMs).toLong()

                                        val maxAllowedEndBasedOnTrimStart = currentTrimStartMs + currentMaxTrimDurationMs
                                        val absoluteMaxEnd = videoDurationMs

                                        val actualMaxAllowedEndMs = min(maxAllowedEndBasedOnTrimStart, absoluteMaxEnd)

                                        val newEndMs =
                                            (currentTrimEndMs + dragMs).coerceIn(
                                                (currentTrimStartMs + MIN_TRIM_DURATION_MS).coerceAtMost(videoDurationMs),
                                                actualMaxAllowedEndMs,
                                            )
                                        onTrimEndChanged(newEndMs)
                                    },
                                )
                            },
                ) {
                    // Visible part of the handle
                    Box(
                        modifier =
                            Modifier
                                .width(8.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                                .align(Alignment.Center),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                                .align(Alignment.Center),
                    )
                }

                // Selected region border
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { ((endRatio - startRatio) * sliderWidthPx).toDp() })
                            .offset(x = with(density) { (startRatio * sliderWidthPx).toDp() })
                            .border(2.dp, Color.White)
                            .align(Alignment.CenterStart),
                )

                // Current position indicator
                if (currentPositionMs >= currentTrimStartMs && currentPositionMs <= currentTrimEndMs) {
                    val effectiveDurationForPosition = currentTrimEndMs - currentTrimStartMs
                    if (effectiveDurationForPosition > 0) {
                        val positionInTrimWindowMs = currentPositionMs - currentTrimStartMs
                        val positionRatioInTrimWindow = positionInTrimWindowMs.toFloat() / effectiveDurationForPosition

                        val selectedWindowWidthPx = (endRatio - startRatio) * sliderWidthPx
                        val indicatorOffsetXPx = startRatio * sliderWidthPx + positionRatioInTrimWindow * selectedWindowWidthPx

                        Box(
                            modifier =
                                Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(Color.Yellow)
                                    .offset(x = with(density) { indicatorOffsetXPx.toDp() - 1.5.dp })
                                    .align(Alignment.CenterStart),
                        )
                    }
                }
            }
        }

        // Duration indicators
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = 8.dp),
        ) {
            if (maxTrimDurationMs < videoDurationMs && sliderWidthPx > 0) {
                val maxDurationRatio = maxTrimDurationMs.toFloat() / videoDurationMs
                Box(
                    modifier =
                        Modifier
                            .width(with(density) { (maxDurationRatio * sliderWidthPx).toDp() })
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                            .align(Alignment.CenterStart),
                )
            }

            Text(
                text = "Selected: ${formatDuration(currentTrimEndMs - currentTrimStartMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            if (maxTrimDurationMs < videoDurationMs) {
                Text(
                    text = "Max: ${formatDuration(maxTrimDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = (ms % 1000) / 10 // This gives tenths of a second
    // Use Locale.US for a consistent format (e.g., "." as decimal separator)
    // Or use Locale.getDefault() if you want it to adapt to the user's locale
    return String.format(Locale.US, "%02d:%02d.%01d", minutes, seconds, milliseconds)
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun VideoTrimSliderPreview() {
    val thumbnails =
        remember {
            List(8) { index ->
                val color =
                    when (index % 4) {
                        0 -> android.graphics.Color.RED
                        1 -> android.graphics.Color.GREEN
                        2 -> android.graphics.Color.BLUE
                        else -> android.graphics.Color.YELLOW
                    }
                val bitmap = createBitmap(120, 80)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(color)
                bitmap
            }
        }

    MaterialTheme {
        VideoTrimSlider(
            thumbnails = thumbnails,
            isLoadingThumbnails = false,
            videoDurationMs = 60000L,
            maxTrimDurationMs = 30000L,
            currentPositionMs = 15000L,
            trimStartMs = 10000L,
            trimEndMs = 25000L,
            onTrimStartChanged = {},
            onTrimEndChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun VideoTrimSliderLoadingPreview() {
    MaterialTheme {
        VideoTrimSlider(
            thumbnails = emptyList(),
            isLoadingThumbnails = true,
            videoDurationMs = 60000L,
            maxTrimDurationMs = 30000L,
            currentPositionMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 30000L,
            onTrimStartChanged = {},
            onTrimEndChanged = {},
        )
    }
}
