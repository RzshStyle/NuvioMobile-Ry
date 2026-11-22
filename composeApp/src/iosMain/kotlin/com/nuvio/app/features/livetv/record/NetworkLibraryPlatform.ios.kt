package com.nuvio.app.features.livetv.record

/** Network folders aren't built on iOS yet: the library stays hidden there. */
internal actual object NetworkLibraryPlatform {
    actual val available: Boolean = false
    actual suspend fun list(config: NetworkFolderConfig): Map<String, String> = throw UnsupportedOperationException("Not available on this device")
    actual fun playUrl(config: NetworkFolderConfig, id: String): String = ""
    actual suspend fun delete(config: NetworkFolderConfig, id: String): Unit = throw UnsupportedOperationException("Not available on this device")
    actual fun configChanged(config: NetworkFolderConfig?) = Unit
    actual fun describe(e: Throwable, config: NetworkFolderConfig): String = "Network folders aren't available on this device"
}
