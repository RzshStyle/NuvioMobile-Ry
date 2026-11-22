package com.nuvio.app.features.streams.quality

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/** Fills in [PlaybackDeviceSupport] from this phone's decoders and screen (read once, off the main thread). */
internal object PlaybackDeviceSupportAndroid {
    @Volatile private var done = false

    fun init(context: Context) {
        if (done) return
        done = true
        val app = context.applicationContext
        Thread {
            runCatching { PlaybackDeviceSupport.hardwareAv1 = hasHardwareDecoder("video/av01") }
            runCatching {
                val display = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    @Suppress("DEPRECATION")
                    val types = display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
                    PlaybackDeviceSupport.dolbyVisionScreen =
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types || hasHardwareDecoder("video/dolby-vision")
                }
            }
        }.apply { name = "quality-device-check"; isDaemon = true }.start()
    }

    private fun hasHardwareDecoder(mime: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } && !isSoftware(info)
        }
    }.getOrDefault(false)

    private fun isSoftware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isSoftwareOnly
        val name = info.name.lowercase()
        return name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw.")
    }
}
