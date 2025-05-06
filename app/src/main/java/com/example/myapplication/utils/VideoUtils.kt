package com.example.myapplication.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
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
     * @return Boolean indicating success or failure of the trim operation
     */
    fun trimMp4Video(inputPath: String, outputPath: String): Boolean {
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

            val endTimeUs = 30_000_000L // 30 seconds in microseconds

            fun copyTrack(trackIndex: Int, muxerTrack: Int) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0 || extractor.sampleTime > endTimeUs) {
                        break
                    }
                    bufferInfo.presentationTimeUs = extractor.sampleTime
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
}