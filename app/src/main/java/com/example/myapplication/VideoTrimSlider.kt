package com.example.myapplication

import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap

/**
 * 视频裁剪滑块组件，允许用户通过拖动滑块来选择视频裁剪的开始和结束时间
 * 
 * @param thumbnails 视频缩略图列表
 * @param isLoadingThumbnails 缩略图是否正在加载
 * @param videoDurationMs 视频总时长 (毫秒)
 * @param maxTrimDurationMs 最大允许的裁剪时长 (毫秒)
 * @param currentPositionMs 当前播放位置 (毫秒)
 * @param trimStartMs 裁剪起始时间 (毫秒)
 * @param trimEndMs 裁剪结束时间 (毫秒)
 * @param onTrimStartChanged 裁剪起始时间变化的回调
 * @param onTrimEndChanged 裁剪结束时间变化的回调
 */
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

    // 使用单独的状态用于拖动操作，避免与播放器更新干扰
    var sliderWidthPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(sliderHeight + 40.dp)
                .padding(horizontal = 8.dp),
    ) {
        // 缩略图背景
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
            // 缩略图行或加载指示器
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

            // 选择叠加层 - 显示在缩略图上方
            if (videoDurationMs > 0 && sliderWidthPx > 0) {
                val startRatio = trimStartMs.toFloat() / videoDurationMs
                val endRatio = trimEndMs.toFloat() / videoDurationMs

                // 左侧暗区
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { (startRatio * sliderWidthPx).toDp() })
                        .background(Color.Black.copy(alpha = 1f))
                        .align(Alignment.CenterStart)
                )

                // 右侧暗区
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { ((1 - endRatio) * sliderWidthPx).toDp() })
                        .background(Color.Black.copy(alpha = 1f))
                        .align(Alignment.CenterEnd)
                )

                // 左侧手柄 - 改进版，更容易拖动
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { (startRatio * sliderWidthPx).toDp() - 20.dp })
                        .width(40.dp) // 更宽的触摸区域
                        .fillMaxHeight()
                        .background(Color.Red.copy(alpha = 0f)) // 调试时可设为半透明查看触摸区域
                        .align(Alignment.CenterStart)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    val dragRatio = dragAmount.x / sliderWidthPx
                                    val dragMs = (dragRatio * videoDurationMs).toLong()

                                    // 计算新位置，同时尊重限制
                                    val newStartMs = (trimStartMs + dragMs).coerceIn(
                                        0L, // 不能低于0
                                        trimEndMs - 2000L // 最小2秒间隔
                                    )

                                    onTrimStartChanged(newStartMs)
                                }
                            )
                        }
                ) {
                    // 手柄的可见部分
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .align(Alignment.Center)
                    )

                    // 手柄圆形指示器
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.White, CircleShape)
                            .align(Alignment.Center)
                    )
                }

                // 右侧手柄 - 改进版，更容易拖动
                Box(
                    modifier = Modifier
                        .offset(with(density) { -((1 - endRatio) * sliderWidthPx).toDp() + 20.dp })
                        //.offset(x = with(density) { (endRatio * sliderWidthPx).toDp() - 0.dp })
                        .width(40.dp) // 更宽的触摸区域
                        .fillMaxHeight()
                        .background(Color.Red.copy(alpha = 0.0f)) // 调试时可设为半透明查看触摸区域
                        .align(Alignment.CenterEnd)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    val dragRatio = dragAmount.x / sliderWidthPx
                                    val dragMs = (dragRatio * videoDurationMs).toLong()
                                    Log.d("dragMs", "dragMs: $dragMs")

                                    // 计算最大允许结束时间
                                    val maxAllowedEndMs = min(
                                        videoDurationMs,
                                        trimStartMs + maxTrimDurationMs
                                    )

                                    // 计算新位置，同时尊重限制
                                    val newEndMs = (trimEndMs + dragMs).coerceIn(
                                        trimStartMs, // 最小2秒间隔
                                        maxAllowedEndMs
                                    )

                                    Log.d("newEndMs", "newEndMs: $newEndMs")

                                    onTrimEndChanged(newEndMs)
                                }
                            )
                        }
                ) {
                    // 手柄的可见部分
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                            .align(Alignment.Center)
                    )

                    // 手柄圆形指示器
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.White, CircleShape)
                            .align(Alignment.Center)
                    )
                }

                // 选择区域边框
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { ((endRatio - startRatio) * sliderWidthPx).toDp() })
                        .offset(x = with(density) { (startRatio * sliderWidthPx).toDp() })
                        .border(2.dp, Color.White)
                        .align(Alignment.CenterStart)
                )

                // 位置指示器
                if (currentPositionMs in trimStartMs..trimEndMs) {
                    val positionRatio = currentPositionMs.toFloat() / videoDurationMs
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(Color.Red)
                            .offset(x = with(density) { (positionRatio * sliderWidthPx).toDp() - 1.5.dp })
                    )
                }
            }
        }

        // 时长指示器和最大限制
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = 8.dp),
        ) {
            // 最大时长指示器
            if (maxTrimDurationMs < videoDurationMs) {
                val maxDurationRatio = maxTrimDurationMs.toFloat() / videoDurationMs
                Box(
                    modifier =
                        Modifier
                            .width(with(density) { (maxDurationRatio * sliderWidthPx).toDp() })
                            .height(4.dp)
                            .background(Color.Blue, RoundedCornerShape(2.dp)),
                )
            }

            // 时长标签
            Text(
                text = "Selected: ${formatDuration(trimEndMs - trimStartMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Blue,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            if (maxTrimDurationMs < videoDurationMs) {
                Text(
                    text = "Max: ${formatDuration(maxTrimDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Blue,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

// 辅助函数，格式化时长
fun formatDuration(durationMs: Long): String {
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun VideoTrimSliderPreview() {
    // 创建模拟的缩略图列表
    val context = LocalContext.current
    val thumbnails = remember {
        List(8) { index ->
            // 创建简单的纯色Bitmap作为示例
            val color = when (index % 4) {
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

    // 模拟视频参数
    val videoDurationMs = 60000L // 1分钟视频
    val maxTrimDurationMs = 30000L // 最大裁剪长度为30秒
    val currentPositionMs = 15000L // 当前播放位置
    val trimStartMs = 10000L // 裁剪开始时间
    val trimEndMs = 30000L // 裁剪结束时间

    MaterialTheme {
        VideoTrimSlider(
            thumbnails = thumbnails,
            isLoadingThumbnails = false,
            videoDurationMs = videoDurationMs,
            maxTrimDurationMs = maxTrimDurationMs,
            currentPositionMs = currentPositionMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            onTrimStartChanged = { /* 预览中无需实现 */ },
            onTrimEndChanged = { /* 预览中无需实现 */ }
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
            onTrimStartChanged = { /* 预览中无需实现 */ },
            onTrimEndChanged = { /* 预览中无需实现 */ }
        )
    }
}