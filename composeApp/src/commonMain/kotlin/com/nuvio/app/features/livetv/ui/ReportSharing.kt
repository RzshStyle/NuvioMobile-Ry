package com.nuvio.app.features.livetv.ui

import androidx.compose.runtime.Composable

/**
 * Opens the phone's share sheet with [text] (to send a report to Discord, a chat or email).
 * Null where there is no share sheet: the report is copied instead.
 */
@Composable
internal expect fun rememberReportSharer(): ((title: String, text: String) -> Unit)?
