package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A channel (or catch-up programme) the Live TV tab asks the app to play. */
data class LiveTvPlayRequest(
    val channelId: String,
    val title: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val logo: String? = null,
    val subtitle: String? = null,
)

/**
 * The Live TV tab: the user's own IPTV playlists (Xtream Codes or M3U + EPG).
 * Implemented on Android with the shared iptv-core engine.
 */
@Composable
expect fun LiveTvTab(modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit)
