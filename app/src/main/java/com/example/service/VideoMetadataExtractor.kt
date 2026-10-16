package com.example.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.VideoSourceInfo

object VideoMetadataExtractor {

    /**
     * Extracts video duration, dimensions, rotation and file size from a content Uri.
     * Throws IllegalArgumentException if the file is not a valid or readable video.
     */
    fun extract(context: Context, videoUri: Uri): VideoSourceInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unsupported file format or unreadable video.", e)
        }

        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        if (hasVideo == null || hasVideo.equals("no", ignoreCase = true)) {
            retriever.release()
            throw IllegalArgumentException("The selected file does not contain a valid video track.")
        }

        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

        val durationMs = durationStr?.toLongOrNull() ?: 0L
        val width = widthStr?.toIntOrNull() ?: 1280
        val height = heightStr?.toIntOrNull() ?: 720
        val rotation = rotationStr?.toIntOrNull() ?: 0

        retriever.release()

        // Extract file name and size from content resolver
        var displayName = "video.mp4"
        var sizeBytes = 0L

        try {
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback for direct file URIs
        }

        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {
                // Ignore fallback failure
            }
        }

        return VideoSourceInfo(
            uri = videoUri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            rotationDegrees = rotation
        )
    }
}
