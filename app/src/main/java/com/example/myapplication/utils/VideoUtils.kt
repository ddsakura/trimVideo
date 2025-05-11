package com.example.myapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.util.Size
import java.nio.ByteBuffer

/**
 * Utility class for video processing operations
 */
object VideoUtils {
    /**
     * Trims an MP4 video to a specified duration
     *
     * @param inputPath Path to the source video file
     * @param outputPath Path where the trimmed video will be saved
     * @param startTimeUs Start time in microseconds (default is 0)
     * @param endTimeUs End time in microseconds (default is 30 seconds)
     * @return Boolean indicating success or failure of the trim operation
     */
    fun trimMp4Video(
        inputPath: String,
        outputPath: String,
        startTimeUs: Long = 0L,
        endTimeUs: Long = 30_000_000L,
    ): Boolean {
        var extractor = MediaExtractor()
        val muxer: MediaMuxer
        try {
            extractor.setDataSource(inputPath)
            val trackCount = extractor.trackCount
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1

            // Create muxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Select tracks
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true && videoTrackIndex == -1) {
                    extractor.selectTrack(i)
                    videoTrackIndex = i
                    muxerVideoTrack = muxer.addTrack(format)
                } else if (mime?.startsWith("audio/") == true && audioTrackIndex == -1) {
                    extractor.selectTrack(i)
                    audioTrackIndex = i
                    muxerAudioTrack = muxer.addTrack(format)
                }
            }

            if (videoTrackIndex == -1) {
                Log.e("Trim", "No video track found")
                return false
            }

            // Start muxing
            muxer.start()

            val bufferSize = 1 * 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            fun copyTrack(
                trackIndex: Int,
                muxerTrack: Int,
            ) {
                extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                // For audio tracks that don't have sync samples, we might need to start from an earlier point
                if (extractor.sampleTime > startTimeUs && trackIndex == audioTrackIndex) {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    // Skip frames until we're close to our target start time
                    while (extractor.sampleTime < startTimeUs - 500_000 && extractor.advance()) {
                        // Just advancing
                    }
                }

                // Adjust timing to make trimmed video start at 0
                var offsetUs = extractor.sampleTime - startTimeUs

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0 || extractor.sampleTime > endTimeUs) {
                        break
                    }
                    bufferInfo.presentationTimeUs = extractor.sampleTime - startTimeUs // Adjust timing
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    extractor.advance()
                }
            }

            // Reset extractor to make sure no tracks are selected
            extractor.release()
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            if (videoTrackIndex != -1) {
                extractor.selectTrack(videoTrackIndex)
                copyTrack(videoTrackIndex, muxerVideoTrack)
            }

            // Reset extractor again for audio track
            extractor.release()
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            if (audioTrackIndex != -1) {
                extractor.selectTrack(audioTrackIndex)
                copyTrack(audioTrackIndex, muxerAudioTrack)
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            return true
        } catch (e: Exception) {
            Log.e("Trim", "Error trimming video", e)
            return false
        }
    }

    /**
     * Get the duration of a video file
     *
     * @param context Application context
     * @param videoUri URI of the video file
     * @return Duration in milliseconds, -1 if unable to determine
     */
    fun getVideoDuration(
        context: Context,
        videoUri: Uri,
    ): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLong() ?: -1L
        } catch (e: Exception) {
            Log.e("VideoUtils", "Failed to get video duration", e)
            -1L
        } finally {
            retriever.release()
        }
    }

    /**
     * Extract frame from a video at a specified position
     *
     * @param context Application context
     * @param videoUri URI of the video file
     * @param timeUs Position in microseconds
     * @param targetSize Optional size for the thumbnail (null for original size)
     * @return Bitmap of the frame or null if extraction failed
     */
    fun getFrameAtTime(
        context: Context,
        videoUri: Uri,
        timeUs: Long,
        targetSize: Size? = null,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)

            val bitmap =
                retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )

            // Resize if needed and if we actually got a bitmap
            if (bitmap != null && targetSize != null) {
                val scaledBitmap =
                    Bitmap.createScaledBitmap(
                        bitmap,
                        targetSize.width,
                        targetSize.height,
                        true,
                    )
                // Release original if we created a new one
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                }
                scaledBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("VideoUtils", "Failed to extract frame", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Generate thumbnails for a video at regular intervals
     *
     * @param context Application context
     * @param videoUri URI of the video file
     * @param count Number of thumbnails to generate
     * @param targetSize Size for the thumbnails
     * @return List of bitmaps or empty list if extraction failed
     */
    fun generateThumbnails(
        context: Context,
        videoUri: Uri,
        count: Int,
        targetSize: Size,
    ): List<Bitmap> {
        val durationMs = getVideoDuration(context, videoUri)
        if (durationMs <= 0) return emptyList()

        val thumbnails = mutableListOf<Bitmap>()
        val intervalMs = durationMs / (count + 1)

        for (i in 1..count) {
            val timeMs = i * intervalMs
            getFrameAtTime(context, videoUri, timeMs * 1000, targetSize)?.let {
                thumbnails.add(it)
            }
        }

        return thumbnails
    }
}
