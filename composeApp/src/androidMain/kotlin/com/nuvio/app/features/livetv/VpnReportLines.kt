package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.model.LiveTvLoadReport
import com.nuvio.vpn.android.VpnPlaybackProtection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * VPN lines in the Start-Up Report: its progress lines, plus whether it's on and which profile,
 * a few seconds after start and again whenever the VPN setting or profile changes.
 */
internal object VpnReportLines {
    private var started = false
    private var last: String? = null
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vpn-report").apply { isDaemon = true } }

    fun start() {
        if (started) return
        started = true
        VpnPlaybackProtection.diagnostics = java.util.function.Consumer { line -> LiveTvLoadReport.add(line) }
        VpnPlaybackProtection.observeSettings { check() }
        worker.schedule({ check() }, 8, TimeUnit.SECONDS)
    }

    private fun check() {
        worker.execute {
            val line = VpnPlaybackProtection.reportLine()
            if (line != last) {
                last = line
                LiveTvLoadReport.add(line)
            }
        }
    }
}
