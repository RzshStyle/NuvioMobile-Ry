package com.nuvio.app.features.livetv

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun LiveTvTab(modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Live TV is available in the Android app.", style = MaterialTheme.typography.bodyLarge)
    }
}
