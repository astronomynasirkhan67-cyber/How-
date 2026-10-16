package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ConversionOptions
import com.example.model.QualityLevel
import com.example.model.Resolution
import com.example.model.VideoOrientation
import com.example.model.formatDuration
import com.example.model.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Web Video Converter", appName)
    }

    @Test
    fun `verify formatting utility functions`() {
        assertEquals("00:20", formatDuration(20_000L))
        assertEquals("01:30", formatDuration(90_000L))
        assertEquals("5.0 MB", formatFileSize(5L * 1024 * 1024))
    }

    @Test
    fun `verify target dimensions for landscape and portrait`() {
        val landscapeOpts = ConversionOptions(
            resolution = Resolution.P720,
            orientation = VideoOrientation.LANDSCAPE
        )
        assertEquals(1280, landscapeOpts.targetWidth)
        assertEquals(720, landscapeOpts.targetHeight)

        val portraitOpts = ConversionOptions(
            resolution = Resolution.P720,
            orientation = VideoOrientation.PORTRAIT
        )
        assertEquals(720, portraitOpts.targetWidth)
        assertEquals(1280, portraitOpts.targetHeight)
    }

    @Test
    fun `verify estimated file size under 20MB for 20 second loop`() {
        val options = ConversionOptions(
            quality = QualityLevel.MEDIUM,
            trimStartMs = 0L,
            trimEndMs = 20_000L,
            removeAudio = true
        )
        val estimatedBytes = options.estimateSizeBytes()
        // 3 Mbps * 20 sec / 8 = 7.5 MB
        val estimatedMb = estimatedBytes / (1024 * 1024)
        assertTrue("Estimated size should be under 20MB, but was $estimatedMb MB", estimatedMb < 20)
    }
}
