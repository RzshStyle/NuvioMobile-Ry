package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.data.CivilTime
import com.nuvio.app.features.livetv.data.LiveTvTimeZones
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.record.LiveTvRecorder
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.LiveTvRecordingScheduler
import com.nuvio.app.features.livetv.record.RecordingNotices
import com.nuvio.app.features.livetv.record.RecordingRules
import com.nuvio.app.features.livetv.record.RecordingSchedule
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.record.SeriesRule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.nuvio.app.features.livetv.record.RepeatRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_delete
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.livetv_rec_cancel
import nuvio.composeapp.generated.resources.livetv_rec_catchup_save
import nuvio.composeapp.generated.resources.livetv_rec_empty
import nuvio.composeapp.generated.resources.livetv_rec_keep_all
import nuvio.composeapp.generated.resources.livetv_rec_keep_last
import nuvio.composeapp.generated.resources.livetv_rec_manual_title
import nuvio.composeapp.generated.resources.livetv_rec_record_now
import nuvio.composeapp.generated.resources.livetv_rec_recorded
import nuvio.composeapp.generated.resources.livetv_rec_recording
import nuvio.composeapp.generated.resources.livetv_rec_schedule
import nuvio.composeapp.generated.resources.livetv_rec_scheduled
import nuvio.composeapp.generated.resources.livetv_rec_series
import nuvio.composeapp.generated.resources.livetv_rec_repeat_stop
import nuvio.composeapp.generated.resources.livetv_rec_repeat_pick
import nuvio.composeapp.generated.resources.livetv_rec_repeat_weekends
import nuvio.composeapp.generated.resources.livetv_rec_repeat_weekdays
import nuvio.composeapp.generated.resources.livetv_rec_repeat_every_day
import nuvio.composeapp.generated.resources.livetv_rec_repeat_once
import nuvio.composeapp.generated.resources.livetv_rec_repeat
import nuvio.composeapp.generated.resources.livetv_rec_series_all
import nuvio.composeapp.generated.resources.livetv_rec_series_new_only
import nuvio.composeapp.generated.resources.livetv_rec_series_stop
import nuvio.composeapp.generated.resources.livetv_rec_start_time_hint
import nuvio.composeapp.generated.resources.livetv_rec_stop
import nuvio.composeapp.generated.resources.livetv_rec_today
import nuvio.composeapp.generated.resources.livetv_rec_tomorrow
import nuvio.composeapp.generated.resources.livetv_recordings
import org.jetbrains.compose.resources.stringResource

/**
 * Recordings: what's recording now, what's set to record (shows and series rules), and what's
 * been recorded, with its status, length and size. Tap a recording to play it; long-press (or
 * tap a scheduled one) for its options.
 */
@Composable
internal fun RecordingsScreen(
    onBack: () -> Unit,
    onPlay: (LiveTvRecording) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val recordings by remember { LiveTvRecordingScheduler.start(); LiveTvRecorder.recordings }.collectAsStateWithLifecycle()
    val schedules by LiveTvRecordingScheduler.schedules.collectAsStateWithLifecycle()
    val series by LiveTvRecordingScheduler.series.collectAsStateWithLifecycle()
    val repeats by LiveTvRecordingScheduler.repeats.collectAsStateWithLifecycle()
    val notice by RecordingNotices.current.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(Unit) { LiveTvRecordingScheduler.fireOverdue() }
    LaunchedEffect(notice) { if (notice != null) { NuvioToastController.show(notice.orEmpty()); RecordingNotices.clear() } }
    val now by produceState(LiveTvClock.nowMs()) { while (true) { value = LiveTvClock.nowMs(); delay(15_000) } }

    val recordingNow = recordings.filter { it.isRecording }
    val recorded = recordings.filter { !it.isRecording }

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().liveTvSideInsets()) {
        NuvioScreenHeader(
            title = stringResource(Res.string.livetv_recordings),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            if (recordings.isEmpty() && schedules.isEmpty() && series.isEmpty() && repeats.isEmpty()) {
                item { Text(stringResource(Res.string.livetv_rec_empty), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(vertical = NuvioTokens.Space.s24)) }
            }
            if (recordingNow.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.livetv_rec_recording)) }
                items(recordingNow.size, key = { "r:" + recordingNow[it].id }) { i ->
                    RecordingRow(recordingNow[i], now, onClick = { onPlay(recordingNow[i]) }, onLongClick = { sheet = recordingNow[i] })
                }
            }
            if (schedules.isNotEmpty() || series.isNotEmpty() || repeats.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.livetv_rec_scheduled)) }
                items(series.size, key = { "s:" + series[it].id }) { i -> SeriesRow(series[i], onClick = { sheet = series[i] }) }
                items(repeats.size, key = { "m:" + repeats[it].id }) { i -> RepeatRow(repeats[i], onClick = { sheet = repeats[i] }) }
                items(schedules.size, key = { "p:" + schedules[it].id }) { i -> ScheduleRow(schedules[i], onClick = { sheet = schedules[i] }) }
            }
            if (recorded.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.livetv_rec_recorded)) }
                items(recorded.size, key = { "d:" + recorded[it].id }) { i ->
                    RecordingRow(recorded[i], now, onClick = { if (recorded[i].status == RecordingStatus.DONE) onPlay(recorded[i]) else sheet = recorded[i] }, onLongClick = { sheet = recorded[i] })
                }
            }
        }
    }

    when (val s = sheet) {
        is LiveTvRecording -> RecordingSheet(s, onDismiss = { sheet = null }, onPlay = { onPlay(s) })
        is RecordingSchedule -> ScheduleSheet(s, onDismiss = { sheet = null })
        is SeriesRule -> SeriesSheet(s, onDismiss = { sheet = null })
        is RepeatRule -> RepeatSheet(s, onDismiss = { sheet = null })
        else -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.nuvio.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s4))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(rec: LiveTvRecording, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val liveSeconds = if (rec.isRecording && !rec.fromCatchup) ((now - rec.startedAtMs) / 1000.0).coerceAtLeast(rec.durationSec) else rec.durationSec
    val info = listOfNotNull(
        rec.channelName.takeIf { it.isNotBlank() },
        formatLocalDateTime(rec.programStartMs.takeIf { it > 0 } ?: rec.startedAtMs),
        RecordingRules.durationLabel(liveSeconds).takeIf { liveSeconds >= 60 },
        RecordingRules.sizeLabel(rec.sizeBytes).takeIf { rec.sizeBytes > 0 },
    ).joinToString("  ·  ")
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surfaceCard), contentAlignment = Alignment.Center) {
            rec.channelLogo?.takeIf { it.isNotBlank() }?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s4), contentScale = ContentScale.Fit) }
                ?: Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.textMuted)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(rec.title + (rec.episode?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(info, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            rec.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (rec.status == RecordingStatus.FAILED) tokens.colors.danger else tokens.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        when {
            rec.isRecording -> Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.danger, modifier = Modifier.size(NuvioTokens.Icon.sm))
            rec.status == RecordingStatus.DONE -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.textMuted)
            else -> Unit
        }
    }
}

@Composable
private fun ScheduleRow(s: RecordingSchedule, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sub = listOfNotNull(
        s.channel.name,
        formatLocalDateTime(s.startMs) + " – " + formatClock(s.stopMs),
        if (s.useCatchup) stringResource(Res.string.livetv_rec_catchup_save) else null,
    ).joinToString("  ·  ")
    SimpleRow(icon = Icons.Filled.Schedule, title = s.title + (s.episode?.let { "  ·  $it" } ?: ""), subtitle = sub, onClick = onClick, tint = tokens.colors.accent)
}

@Composable
private fun SeriesRow(r: SeriesRule, onClick: () -> Unit) {
    val keep = if (r.keepLast > 0) stringResource(Res.string.livetv_rec_keep_last, r.keepLast) else stringResource(Res.string.livetv_rec_keep_all)
    val which = if (r.newOnly) stringResource(Res.string.livetv_rec_series_new_only) + "  ·  " else ""
    SimpleRow(icon = Icons.Filled.FiberManualRecord, title = stringResource(Res.string.livetv_rec_series) + ": " + r.title, subtitle = r.channel.name + "  ·  " + which + keep, onClick = onClick, tint = MaterialTheme.nuvio.colors.accent)
}

/** A repeating recording set by hand: its days and time. */
@Composable
private fun RepeatRow(r: RepeatRule, onClick: () -> Unit) {
    SimpleRow(
        icon = Icons.Filled.Schedule,
        title = r.title,
        subtitle = r.channel.name + "  ·  " + RecordingRules.daysLabel(r.days) + "  ·  " + minuteLabel(r.minuteOfDay),
        onClick = onClick,
        tint = MaterialTheme.nuvio.colors.accent,
    )
}

/** "20:30" for a time of day in minutes after midnight. */
private fun minuteLabel(minuteOfDay: Int): String =
    (minuteOfDay / 60).toString().padStart(2, '0') + ":" + (minuteOfDay % 60).toString().padStart(2, '0')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSheet(r: RepeatRule, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(r.channel.name + "  ·  " + RecordingRules.daysLabel(r.days) + "  ·  " + minuteLabel(r.minuteOfDay), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        NuvioBottomSheetDivider()
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_repeat_stop), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.removeRepeat(r.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

@Composable
private fun SimpleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface)
            .combinedClickableCompat(onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSheet(rec: LiveTvRecording, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12)) {
            Text(rec.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(rec.channelName, formatLocalDateTime(rec.startedAtMs), rec.endedAtMs?.let { formatClock(it) }, RecordingRules.sizeLabel(rec.sizeBytes).takeIf { rec.sizeBytes > 0 }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
            )
            rec.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = NuvioTokens.Space.s6)) }
        }
        NuvioBottomSheetDivider()
        if (rec.status != RecordingStatus.FAILED) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.action_play), icon = Icons.Filled.PlayArrow, onClick = { close(onPlay) })
        }
        if (rec.isRecording) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_stop), icon = Icons.Filled.Stop, onClick = { LiveTvRecorder.stop(rec.id); close() })
        }
        NuvioBottomSheetActionRow(title = stringResource(Res.string.action_delete), icon = Icons.Filled.Delete, onClick = { LiveTvRecorder.delete(rec.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(s: RecordingSchedule, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(s.channel.name + "  ·  " + formatLocalDateTime(s.startMs) + " – " + formatClock(s.stopMs), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            Text(
                (if (s.useCatchup) stringResource(Res.string.livetv_rec_catchup_save) + "  ·  " else "") + formatLocalDateTime(LiveTvRecordingScheduler.startsAt(s)),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary,
            )
        }
        NuvioBottomSheetDivider()
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_cancel), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.cancel(s.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

private val KEEP_CHOICES = listOf(0, 1, 2, 3, 5, 10, 20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesSheet(r: SeriesRule, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(r.channel.name, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                Chip(label = stringResource(Res.string.livetv_rec_series_all), selected = !r.newOnly, onClick = { if (r.newOnly) { LiveTvRecordingScheduler.setNewOnly(r.id, false); close() } })
                Chip(label = stringResource(Res.string.livetv_rec_series_new_only), selected = r.newOnly, onClick = { if (!r.newOnly) { LiveTvRecordingScheduler.setNewOnly(r.id, true); close() } })
            }
            Text(stringResource(Res.string.livetv_rec_keep_last, r.keepLast), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                KEEP_CHOICES.forEach { n ->
                    Chip(label = if (n == 0) stringResource(Res.string.livetv_rec_keep_all) else n.toString(), selected = n == r.keepLast, onClick = { LiveTvRecordingScheduler.setKeep(r.id, n) })
                }
            }
        }
        NuvioBottomSheetDivider()
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_series_stop), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.removeSeries(r.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

private val DURATIONS_MIN = listOf(30, 60, 90, 120, 180, 240)

/**
 * Record by hand: a title, when to start ("Now", or a time today or tomorrow) and how long for.
 * Recorded like a show from the guide (a channel with catch-up is saved after it ends).
 */
@Composable
internal fun ManualRecordingDialog(channel: LiveChannel, scheduleOnly: Boolean, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var title by remember { mutableStateOf(channel.name) }
    var startNow by remember { mutableStateOf(!scheduleOnly) }
    var tomorrow by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(60) }
    // Repeat: empty = once; otherwise the days (1 = Sunday … 7 = Saturday) it records every week.
    var repeatDays by remember { mutableStateOf(emptySet<Int>()) }
    var pickingDays by remember { mutableStateOf(false) }
    val now = LiveTvClock.nowMs()
    val repeating = repeatDays.isNotEmpty() || pickingDays
    val startMs: Long? = when {
        repeating -> parseMinuteOfDay(time)?.let { nextAiring(now, it, repeatDays) }
        startNow -> now
        else -> parseTime(time, tomorrow, now)
    }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (scheduleOnly) Res.string.livetv_rec_schedule else Res.string.livetv_rec_record_now)) {
        NuvioInputField(value = title, onValueChange = { title = it }, placeholder = stringResource(Res.string.livetv_rec_manual_title))
        if (!repeating) Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            if (!scheduleOnly) Chip(label = stringResource(Res.string.livetv_rec_record_now), selected = startNow, onClick = { startNow = true })
            Chip(label = stringResource(Res.string.livetv_rec_today), selected = !startNow && !tomorrow, onClick = { startNow = false; tomorrow = false })
            Chip(label = stringResource(Res.string.livetv_rec_tomorrow), selected = !startNow && tomorrow, onClick = { startNow = false; tomorrow = true })
        }
        // Repeat: the same time every week on the days picked.
        Text(stringResource(Res.string.livetv_rec_repeat), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val presets = listOf(
                Res.string.livetv_rec_repeat_once to emptySet<Int>(),
                Res.string.livetv_rec_repeat_every_day to (1..7).toSet(),
                Res.string.livetv_rec_repeat_weekdays to setOf(2, 3, 4, 5, 6),
                Res.string.livetv_rec_repeat_weekends to setOf(1, 7),
            )
            presets.forEach { (label, days) ->
                Chip(label = stringResource(label), selected = !pickingDays && repeatDays == days, onClick = {
                    pickingDays = false; repeatDays = days; if (days.isNotEmpty()) startNow = false
                })
            }
            Chip(label = stringResource(Res.string.livetv_rec_repeat_pick), selected = pickingDays, onClick = { pickingDays = true; startNow = false })
        }
        if (pickingDays) Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Monday first, Sunday last.
            listOf(2, 3, 4, 5, 6, 7, 1).forEach { d ->
                Chip(label = RecordingRules.DAY_NAMES[d - 1], selected = d in repeatDays, onClick = {
                    repeatDays = if (d in repeatDays) repeatDays - d else repeatDays + d
                })
            }
        }
        if (repeating || !startNow) {
            NuvioInputField(value = time, onValueChange = { time = it }, placeholder = stringResource(Res.string.livetv_rec_start_time_hint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            DURATIONS_MIN.forEach { m -> Chip(label = if (m % 60 == 0) "${m / 60} h" else "$m min", selected = m == minutes, onClick = { minutes = m }) }
        }
        startMs?.let { Text(formatLocalDateTime(it) + " – " + formatClock(it + minutes * 60_000L), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(if (startNow) Res.string.livetv_rec_record_now else Res.string.livetv_rec_schedule),
                style = DialogButtonStyle.Primary,
                enabled = startMs != null,
                onClick = {
                    val start = startMs ?: return@DialogButton
                    val message = LiveTvRecordingScheduler.addManual(channel, title.trim(), start, start + minutes * 60_000L, repeatDays)
                    NuvioToastController.show(message)
                    onDismiss()
                },
            )
        }
    }
}

/** "20:30" or "8:30 pm" as minutes after midnight; null when it isn't a time. */
private fun parseMinuteOfDay(text: String): Int? {
    val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*([ap]m?)?\s*$""", RegexOption.IGNORE_CASE).find(text) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    val ampm = m.groupValues[3].lowercase()
    if (ampm.startsWith("p") && hour < 12) hour += 12
    if (ampm.startsWith("a") && hour == 12) hour = 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/** The next start at [minuteOfDay] on one of [days] (the phone's time), from [now] on; null with no days. */
private fun nextAiring(now: Long, minuteOfDay: Int, days: Set<Int>): Long? {
    if (days.isEmpty()) return null
    val zone = LiveTvTimeZones.SYSTEM
    val today = CivilTime.at(now, zone)
    val todayDays = CivilTime.daysFromCivil(today.year, today.month, today.day)
    for (ahead in 0..7) {
        val epochDay = todayDays + ahead
        if (RecordingRules.weekday(epochDay) !in days) continue
        val date = CivilTime.atUtc(epochDay * 86_400_000L)
        val at = CivilTime.toEpochMs(date.year, date.month, date.day, minuteOfDay / 60, minuteOfDay % 60, 0, zone)
        if (at > now) return at
    }
    return null
}

/** "20:30" or "8:30 pm" as a time today (or tomorrow), in the device's zone; null when it isn't one or has passed. */
internal fun parseTime(text: String, tomorrow: Boolean, now: Long): Long? {
    val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*([ap]m?)?\s*$""", RegexOption.IGNORE_CASE).find(text) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    val ampm = m.groupValues[3].lowercase()
    if (ampm.startsWith("p") && hour < 12) hour += 12
    if (ampm.startsWith("a") && hour == 12) hour = 0
    if (hour !in 0..23 || minute !in 0..59) return null
    val today = CivilTime.at(now, LiveTvTimeZones.SYSTEM)
    val dayOffset = if (tomorrow) 1 else 0
    val days = CivilTime.daysFromCivil(today.year, today.month, today.day) + dayOffset
    val date = CivilTime.atUtc(days * 86_400_000L)
    val at = CivilTime.toEpochMs(date.year, date.month, date.day, hour, minute, 0, LiveTvTimeZones.SYSTEM)
    return at.takeIf { it > now - 60_000L }
}
