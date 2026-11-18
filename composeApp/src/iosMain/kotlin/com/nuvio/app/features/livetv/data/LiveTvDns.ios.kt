package com.nuvio.app.features.livetv.data

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.livetv.model.LiveChannel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect

internal actual object LiveTvDns {
    actual val supported: Boolean = false
    actual fun current(): DnsChoice = DnsChoice.SYSTEM
    actual fun set(value: DnsChoice) = Unit
    actual fun noteChannels(channels: List<LiveChannel>) = Unit
}

internal actual fun createXtreamHttpClient(): HttpClient = createApiHttpClient().config {
    followRedirects = true
    install(HttpRedirect) { allowHttpsDowngrade = true }
}
