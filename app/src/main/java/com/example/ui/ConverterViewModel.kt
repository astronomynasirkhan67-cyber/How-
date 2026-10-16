package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.model.ConversionOptions
import com.example.model.ConversionState
import com.example.model.QualityLevel
import com.example.model.Resolution
import com.example.model.VideoOrientation
import com.example.model.VideoSourceInfo
import com.example.service.VideoConverterEngine
import com.example.service.VideoMetadataExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ConverterUiState(
    val sourceVideo: VideoSourceInfo? = null,
    val options: ConversionOptions = ConversionOptions(),
    val conversionState: ConversionState = ConversionState.Idle,
    val isLoadingSource: Boolean = false,
    val previewModalUri: Uri? = null,
    val previewModalTitle: String? = null
)

@OptIn(UnstableApi::class)
class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val converterEngine = VideoConverterEngine(application)
    private var conversionJob: Job? = null

    /**
     * Handles video selection from Photo Picker.
     */
    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSource = true, conversionState = ConversionState.Idle) }
            try {
                val source = VideoMetadataExtractor.extract(getApplication(), uri)
                // Set default trim: 0 to min(20 seconds, total duration)
                val defaultTrimEnd = minOf(20_000L, source.durationMs.coerceAtLeast(1000L))

                // Suggest orientation based on original video aspect ratio
                val suggestedOrientation = if (source.effectiveHeight > source.effectiveWidth) {
                    VideoOrientation.PORTRAIT
                } else {
                    VideoOrientation.LANDSCAPE
                }

                _uiState.update { state ->
                    state.copy(
                        sourceVideo = source,
                        options = state.options.copy(
                            trimStartMs = 0L,
                            trimEndMs = defaultTrimEnd,
                            orientation = suggestedOrientation
                        ),
                        isLoadingSource = false,
                        conversionState = ConversionState.Idle
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingSource = false,
                        conversionState = ConversionState.Error(
                            e.message ?: "Failed to read video file. Please choose another video."
                        )
                    )
                }
            }
        }
    }

    fun updateResolution(resolution: Resolution) {
        _uiState.update { it.copy(options = it.options.copy(resolution = resolution)) }
    }

    fun updateOrientation(orientation: VideoOrientation) {
        _uiState.update { it.copy(options = it.options.copy(orientation = orientation)) }
    }

    fun updateRemoveAudio(removeAudio: Boolean) {
        _uiState.update { it.copy(options = it.options.copy(removeAudio = removeAudio)) }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _uiState.update {
            it.copy(options = it.options.copy(trimStartMs = startMs, trimEndMs = endMs))
        }
    }

    fun updateQuality(quality: QualityLevel) {
        _uiState.update { it.copy(options = it.options.copy(quality = quality)) }
    }

    fun updateFileName(name: String) {
        _uiState.update { it.copy(options = it.options.copy(outputFileName = name)) }
    }

    /**
     * Executes the video transcoding process.
     */
    fun startConversion() {
        val source = _uiState.value.sourceVideo ?: return
        val options = _uiState.value.options

        if (options.trimEndMs <= options.trimStartMs) {
            _uiState.update {
                it.copy(
                    conversionState = ConversionState.Error(
                        "Trim end time must be greater than start time."
                    )
                )
            }
            return
        }

        conversionJob?.cancel()
        conversionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(conversionState = ConversionState.Converting(progress = 0f))
            }

            try {
                val result = converterEngine.convert(
                    sourceInfo = source,
                    options = options,
                    onProgress = { progress ->
                        _uiState.update {
                            if (it.conversionState is ConversionState.Converting) {
                                it.copy(conversionState = ConversionState.Converting(progress))
                            } else it
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        conversionState = ConversionState.Success(
                            file = result.localFile,
                            outputUri = result.fileProviderUri,
                            originalSize = result.originalSize,
                            finalSize = result.finalSize,
                            durationMs = result.durationMs,
                            resolutionText = result.resolutionText,
                            savedRelativePath = result.relativePath
                        )
                    )
                }
            } catch (e: Exception) {
                val isStorage = e.message?.contains("storage", ignoreCase = true) == true
                _uiState.update {
                    it.copy(
                        conversionState = ConversionState.Error(
                            message = e.localizedMessage ?: "Conversion failed. Please try again.",
                            isStorageError = isStorage
                        )
                    )
                }
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        converterEngine.cancel()
        _uiState.update { it.copy(conversionState = ConversionState.Idle) }
    }

    fun reset() {
        conversionJob?.cancel()
        converterEngine.cancel()
        _uiState.update {
            ConverterUiState()
        }
    }

    fun clearError() {
        _uiState.update {
            if (it.conversionState is ConversionState.Error) {
                it.copy(conversionState = ConversionState.Idle)
            } else it
        }
    }

    fun openPlayerModal(uri: Uri, title: String) {
        _uiState.update {
            it.copy(previewModalUri = uri, previewModalTitle = title)
        }
    }

    fun closePlayerModal() {
        _uiState.update {
            it.copy(previewModalUri = null, previewModalTitle = null)
        }
    }

    /**
     * Shares the converted video via Android share sheet.
     */
    fun shareVideo(context: Context, uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Web Video")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Plays the video in the default system video player.
     */
    fun playInSystemPlayer(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // If no external player found, show inside app player modal
            openPlayerModal(uri, "Converted Video Preview")
        }
    }

    /**
     * Opens the Downloads folder.
     */
    fun openDownloadsFolder(context: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback for file managers
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path),
                        DocumentsContract.Document.MIME_TYPE_DIR
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Ignore fallback error
            }
        }
    }
}
