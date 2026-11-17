package com.nuvio.app.features.livetv.ui

import androidx.compose.runtime.Composable

// Live TV targets Android; on iOS the report is copied instead of shared.
@Composable
internal actual fun rememberReportSharer(): ((title: String, text: String) -> Unit)? = null
