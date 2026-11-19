package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.model.LiveTvLoadReport
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.livetv_report_copied_full
import nuvio.composeapp.generated.resources.livetv_report_copied_short
import nuvio.composeapp.generated.resources.livetv_report_copy_full
import nuvio.composeapp.generated.resources.livetv_report_copy_short
import nuvio.composeapp.generated.resources.livetv_report_empty
import nuvio.composeapp.generated.resources.livetv_report_share
import nuvio.composeapp.generated.resources.livetv_report_subtitle
import nuvio.composeapp.generated.resources.livetv_report_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Start-Up Report: what Live TV did since the app started (see [LiveTvLoadReport]), newest
 * lines at the bottom. "Copy short" fits one Discord message; "Copy all" and "Share" send the
 * whole report.
 */
@Composable
internal fun StartUpReportDialog(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val lines by LiveTvLoadReport.lines.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val share = rememberReportSharer()
    val title = stringResource(Res.string.livetv_report_title)
    val copiedShort = stringResource(Res.string.livetv_report_copied_short)
    val copiedFull = stringResource(Res.string.livetv_report_copied_full)
    val listState = rememberLazyListState()
    // Opens at the newest lines.
    LaunchedEffect(Unit) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    DialogSurface(onDismissRequest = onDismiss, title = title, message = stringResource(Res.string.livetv_report_subtitle)) {
        Text(
            LiveTvLoadReport.buildLine + "\n" + LiveTvLoadReport.deviceLine,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textSecondary,
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            if (lines.isEmpty()) item {
                Text(stringResource(Res.string.livetv_report_empty), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
            }
            items(lines) { line ->
                Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = tokens.colors.textPrimary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.livetv_report_copy_short),
                    enabled = lines.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(LiveTvLoadReport.shortText()))
                        NuvioToastController.show(copiedShort)
                    },
                )
                DialogButton(
                    text = stringResource(Res.string.livetv_report_copy_full),
                    enabled = lines.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(LiveTvLoadReport.fullText()))
                        NuvioToastController.show(copiedFull)
                    },
                )
            }
            DialogButtons {
                if (share != null) DialogButton(
                    text = stringResource(Res.string.livetv_report_share),
                    style = DialogButtonStyle.Primary,
                    enabled = lines.isNotEmpty(),
                    onClick = { share(title, LiveTvLoadReport.fullText()) },
                )
                DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss)
            }
        }
    }
}
