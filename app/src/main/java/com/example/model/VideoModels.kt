package com.example.model

import android.net.Uri
import java.io.File
import java.util.Locale

/**
 * Information extracted from the user's picked video file.
 */
data class VideoSourceInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
) {
    /**
     * Effective width considering camera rotation metadata.
     */
    val effectiveWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    /**
     * Effective height considering camera rotation metadata.
     */
    val effectiveHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    val formattedDuration: String
        get() = formatDuration(durationMs)

    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

enum class Resolution(val width: Int, val height: Int, val label: String, val subtitle: String) {
    P480(854, 480, "480p", "SD • Fast load"),
    P720(1280, 720, "720p", "HD • Recommended"),
    P1080(1920, 1080, "1080p", "FHD • Sharp")
}

enum class VideoOrientation(val label: String, val aspectDesc: String) {
    LANDSCAPE("Landscape", "16:9"),
    PORTRAIT("Portrait", "9:16")
}

enum class QualityLevel(val bitrateBps: Int, val label: String, val description: String) {
    LOW(1_500_000, "Low", "~1.5 Mbps (smallest file)"),
    MEDIUM(3_000_000, "Medium", "~3.0 Mbps (aims under 20 MB)"),
    HIGH(5_500_000, "High", "~5.5 Mbps (crisp quality)")
}

data class ConversionOptions(
    val resolution: Resolution = Resolution.P720,
    val orientation: VideoOrientation = VideoOrientation.LANDSCAPE,
    val removeAudio: Boolean = true,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 20_000L,
    val quality: QualityLevel = QualityLevel.MEDIUM,
    val outputFileName: String = "space-background.mp4"
) {
    /**
     * Calculates the target output width and height based on orientation.
     */
    val targetWidth: Int
        get() = if (orientation == VideoOrientation.LANDSCAPE) resolution.width else resolution.height

    val targetHeight: Int
        get() = if (orientation == VideoOrientation.LANDSCAPE) resolution.height else resolution.width

    /**
     * Estimates output size based on selected bitrate and duration.
     */
    fun estimateSizeBytes(): Long {
        val durationSec = ((trimEndMs - trimStartMs).coerceAtLeast(1000L)) / 1000.0
        // Bitrate in bits/s -> bytes/s = bitrate / 8
        val videoBytes = (quality.bitrateBps / 8.0) * durationSec
        val audioBytes = if (removeAudio) 0.0 else (128_000 / 8.0) * durationSec
        return (videoBytes + audioBytes).toLong()
    }
}

sealed interface ConversionState {
    data object Idle : ConversionState
    data class Converting(val progress: Float) : ConversionState
    data class Success(
        val file: File,
        val outputUri: Uri,
        val originalSize: Long,
        val finalSize: Long,
        val durationMs: Long,
        val resolutionText: String,
        val savedRelativePath: String
    ) : ConversionState
    data class Error(val message: String, val isStorageError: Boolean = false) : ConversionState
}

// Utility formatting functions
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}
