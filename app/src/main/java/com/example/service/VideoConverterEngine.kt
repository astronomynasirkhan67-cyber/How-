package com.example.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.ConversionOptions
import com.example.model.VideoSourceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@UnstableApi
class VideoConverterEngine(private val context: Context) {

    private var currentTransformer: Transformer? = null
    private var tempOutputFile: File? = null

    /**
     * Converts the source video based on options using Media3 Transformer.
     * Enforces H.264 video codec and web-ready MP4 fast-start muxing.
     */
    suspend fun convert(
        sourceInfo: VideoSourceInfo,
        options: ConversionOptions,
        onProgress: (Float) -> Unit
    ): ConversionResult = withContext(Dispatchers.Main) {
        // Verify available storage before encoding
        val estimatedBytes = options.estimateSizeBytes()
        val cacheUsable = context.cacheDir.usableSpace
        if (cacheUsable < (estimatedBytes * 2).coerceAtLeast(30L * 1024 * 1024)) {
            throw IOException("Not enough storage space on device. Estimated: ${(estimatedBytes / 1024 / 1024)} MB, free: ${(cacheUsable / 1024 / 1024)} MB")
        }

        // Clean up previous temp file if any
        tempOutputFile?.delete()

        // Prepare temporary working file in app cache
        val tempFile = File(context.cacheDir, "web_video_${System.currentTimeMillis()}.mp4")
        tempOutputFile = tempFile

        // Configure video trim range
        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(options.trimStartMs)
            .setEndPositionMs(options.trimEndMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceInfo.uri)
            .setClippingConfiguration(clippingConfig)
            .build()

        // Configure Presentation effect for Resolution & Crop (scale to fill, no stretch)
        val presentation = Presentation.createForWidthAndHeight(
            options.targetWidth,
            options.targetHeight,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        // Build edited media item with video effects and optional audio removal
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(options.removeAudio)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(presentation)
                )
            )
            .build()

        // Configure bitrate for target quality
        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(options.quality.bitrateBps)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .build()

        // Suspend coroutine until transformer export finishes or errors
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            exportException.cause ?: exportException
                        )
                    }
                }
            }

            // Media3 Transformer forces H.264 (AVC) and default fast-start MP4 structure
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()

            currentTransformer = transformer

            continuation.invokeOnCancellation {
                transformer.cancel()
                tempFile.delete()
            }

            // Start transcoding
            try {
                transformer.start(editedMediaItem, tempFile.absolutePath)
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            // Poll progress on main looper
            val progressHolder = ProgressHolder()
            val handler = Handler(Looper.getMainLooper())
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    handler.postDelayed(this, 150)
                }
            }
            handler.post(progressRunnable)
        }

        // Conversion completed successfully. Now save to Downloads/WebVideoConverter/
        val sanitizedName = options.outputFileName.trim().let {
            if (it.endsWith(".mp4", ignoreCase = true)) it else "$it.mp4"
        }

        val savedInfo = withContext(Dispatchers.IO) {
            saveToDownloads(context, tempFile, sanitizedName)
        }

        val duration = options.trimEndMs - options.trimStartMs
        val resolutionText = "${options.targetWidth}x${options.targetHeight} (${options.resolution.label})"

        ConversionResult(
            localFile = savedInfo.file,
            contentUri = savedInfo.uri,
            fileProviderUri = savedInfo.fileProviderUri,
            originalSize = sourceInfo.sizeBytes,
            finalSize = savedInfo.size,
            durationMs = duration,
            resolutionText = resolutionText,
            relativePath = "Downloads/WebVideoConverter/$sanitizedName"
        )
    }

    /**
     * Cancels active conversion job.
     */
    fun cancel() {
        currentTransformer?.cancel()
        currentTransformer = null
        tempOutputFile?.delete()
    }

    private fun saveToDownloads(
        context: Context,
        tempFile: File,
        fileName: String
    ): SavedDestination {
        val finalSize = tempFile.length()
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/WebVideoConverter"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create file entry in MediaStore Downloads.")

            resolver.openOutputStream(uri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Failed to write video data to Downloads.")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            // Keep a local cached copy for direct FileProvider / playback access if needed
            val persistentDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "WebVideoConverter").apply { mkdirs() }
            val persistentFile = File(persistentDir, fileName)
            tempFile.copyTo(persistentFile, overwrite = true)

            val providerUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    persistentFile
                )
            } catch (_: Exception) {
                uri
            }

            return SavedDestination(
                file = persistentFile,
                uri = uri,
                fileProviderUri = providerUri,
                size = finalSize
            )
        } else {
            // Android 9 (API 28) and lower
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WebVideoConverter"
            ).apply { mkdirs() }

            val destinationFile = File(downloadsDir, fileName)
            tempFile.copyTo(destinationFile, overwrite = true)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            val providerUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destinationFile
            )

            return SavedDestination(
                file = destinationFile,
                uri = Uri.fromFile(destinationFile),
                fileProviderUri = providerUri,
                size = finalSize
            )
        }
    }

    data class SavedDestination(
        val file: File,
        val uri: Uri,
        val fileProviderUri: Uri,
        val size: Long
    )

    data class ConversionResult(
        val localFile: File,
        val contentUri: Uri,
        val fileProviderUri: Uri,
        val originalSize: Long,
        val finalSize: Long,
        val durationMs: Long,
        val resolutionText: String,
        val relativePath: String
    )
}
