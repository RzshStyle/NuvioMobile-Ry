package com.nuvio.app.features.livetv.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.ondemand.OnDemandPlayback
import com.nuvio.app.features.livetv.ondemand.VodItem
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsWatchController
import com.nuvio.app.features.livetv.sports.SportsWatchGame
import com.nuvio.app.features.livetv.ui.ChannelLogo
import com.nuvio.app.features.livetv.ui.OnDemandSearchBridge
import com.nuvio.app.features.livetv.ui.OnDemandTitleSheet
import com.nuvio.app.features.livetv.ui.formatClock
import com.nuvio.app.features.livetv.ui.rememberVodMatch
import com.nuvio.app.features.livetv.ui.sports.LeagueMark
import com.nuvio.app.features.livetv.ui.sports.SportsBadge
import com.nuvio.app.features.livetv.ui.sports.SportsLiveRed
import com.nuvio.app.features.livetv.ui.sports.TeamLogo
import com.nuvio.app.features.livetv.ui.sports.eventHeadline
import com.nuvio.app.features.livetv.ui.sports.progressOf
import com.nuvio.app.features.livetv.ui.sports.teamBandBrush
import com.nuvio.app.features.livetv.ui.sports.whenLabel
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_hero_min_left
import nuvio.composeapp.generated.resources.home_rows_hold_edit
import nuvio.composeapp.generated.resources.home_rows_hold_fav_add
import nuvio.composeapp.generated.resources.home_rows_hold_fav_remove
import nuvio.composeapp.generated.resources.home_rows_hold_game_page
import nuvio.composeapp.generated.resources.home_rows_hold_guide
import nuvio.composeapp.generated.resources.home_rows_hold_multiview
import nuvio.composeapp.generated.resources.home_rows_hold_other_streams
import nuvio.composeapp.generated.resources.home_rows_hold_play
import nuvio.composeapp.generated.resources.home_rows_hold_watch_on
import nuvio.composeapp.generated.resources.home_rows_more_streams
import nuvio.composeapp.generated.resources.home_rows_next
import org.jetbrains.compose.resources.stringResource

/** What Home's live rows can open; set by the app's navigation (MainAppContent). */
internal object HomeLiveRowActions {
    /** Plays a channel full screen (the channel, what's on now). */
    var playChannel: ((LiveChannel, String?) -> Unit)? = null
    var openGuide: (() -> Unit)? = null
    var openGame: ((SportsEvent) -> Unit)? = null
    /** Opens Multiview's Sports mode with this channel in the first pane (null when Multiview isn't offered). */
    var openMultiview: ((String?) -> Unit)? = null
    var openVodSeeAll: ((VodKind, String?, String) -> Unit)? = null
}

/** A Live TV, Sports or On Demand row on Home, by its row key ("livetv_<id>"). Draws nothing when the row is empty. */
@Composable
internal fun HomeLiveRowSection(rowKey: String, sectionPadding: Dp, modifier: Modifier = Modifier) {
    val rows by HomeLiveRows.shownRows.collectAsStateWithLifecycle()
    val names by HomeLiveRows.names.collectAsStateWithLifecycle()
    val empty by HomeLiveRows.emptyRowIds.collectAsStateWithLifecycle()
    val id = HomeLiveRow.idOf(rowKey) ?: return
    val row = rows.firstOrNull { it.id == id } ?: return
    if (row.id in empty) return
    var editing by remember { mutableStateOf(false) }
    val title = names[row.id].orEmpty()
    val onEdit = { editing = true }
    when (row.kind) {
        HomeLiveRow.KIND_CHANNELS -> ChannelsRow(row, title, sectionPadding, modifier, onEdit)
        HomeLiveRow.KIND_SPORTS -> SportsRow(row, title, sectionPadding, modifier, onEdit)
        else -> VodRow(row, title, sectionPadding, modifier)
    }
    if (editing) HomeRowEditSheet(row = row, onDismiss = { editing = false })
}

// ------------------------------------------------------------------ channels

@Composable
private fun ChannelsRow(row: HomeLiveRow, title: String, sectionPadding: Dp, modifier: Modifier, onEdit: () -> Unit) {
    val entries by HomeLiveRowsData.channelEntries.collectAsStateWithLifecycle()
    val now by HomeLiveRowsData.now.collectAsStateWithLifecycle()
    val user by LiveTvPreferences.userState.collectAsStateWithLifecycle()
    val list = entries[row.id].orEmpty()
    if (list.isEmpty()) return
    var held by remember { mutableStateOf<HomeChannelEntry?>(null) }
    fun tap(e: HomeChannelEntry) {
        if (row.opensGuide) HomeLiveRowActions.openGuide?.invoke()
        else HomeLiveRowActions.playChannel?.invoke(e.channel, e.now?.title)
    }
    NuvioShelfSection(
        title = title,
        entries = list,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = HomeLiveRowActions.openGuide,
        key = { it.channel.key },
    ) { e ->
        ChannelCard(e, row, now, onClick = { tap(e) }, onLongClick = { held = e })
    }
    held?.let { e ->
        ChannelHoldSheet(
            entry = e,
            favorite = e.channel.key in user.favorites,
            onDismiss = { held = null },
            onEdit = onEdit,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(e: HomeChannelEntry, row: HomeLiveRow, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val p = e.now
    val minutesLeft = p?.let { ((it.stopMs - now) / 60_000L).toInt() }?.takeIf { it > 0 }
    val progress = p?.let { ((now - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f) }
    val next = e.next?.takeIf { row.showNext && it.title.isNotBlank() }
    val portrait = row.portrait
    Column(
        Modifier.width(if (portrait) 128.dp else 210.dp)
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surfaceElevated)
            .border(tokens.borders.thin, tokens.colors.borderDefault, tokens.shapes.compactCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier.fillMaxWidth().height(if (portrait) 104.dp else 92.dp).background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(e.channel, width = if (portrait) 96.dp else 150.dp, height = if (portrait) 72.dp else 64.dp, inset = NuvioTokens.Space.s2)
            if (e.channel.number > 0) {
                Text(
                    e.channel.number.toString(),
                    modifier = Modifier.align(Alignment.TopStart).padding(NuvioTokens.Space.s6)
                        .clip(RoundedCornerShape(NuvioTokens.Space.s6)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = NuvioTokens.Space.s6, vertical = NuvioTokens.Space.s2),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Column(Modifier.padding(NuvioTokens.Space.s10), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(
                p?.title?.takeIf { it.isNotBlank() } ?: e.channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (portrait) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) NuvioProgressBar(progress = progress, height = NuvioTokens.Space.s3, modifier = Modifier.fillMaxWidth())
            val parts = listOfNotNull(
                minutesLeft?.let { stringResource(Res.string.home_hero_min_left, it) },
                next?.let { stringResource(Res.string.home_rows_next, formatClock(it.startMs), it.title) },
            )
            Text(
                parts.joinToString(" · ").ifBlank { e.channel.name },
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = if (portrait) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelHoldSheet(entry: HomeChannelEntry, favorite: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    val tokens = MaterialTheme.nuvio
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s10)) {
            Text(entry.channel.name, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.now?.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_play), icon = Icons.Filled.PlayArrow,
            onClick = { close { HomeLiveRowActions.playChannel?.invoke(entry.channel, entry.now?.title) } })
        NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_guide), icon = Icons.Filled.LiveTv,
            onClick = { close { HomeLiveRowActions.openGuide?.invoke() } })
        NuvioBottomSheetActionRow(
            stringResource(if (favorite) Res.string.home_rows_hold_fav_remove else Res.string.home_rows_hold_fav_add),
            icon = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            onClick = { close { LiveTvPreferences.toggleFavorite(entry.channel.key) } },
        )
        NuvioBottomSheetDivider()
        NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_edit), icon = Icons.Filled.Edit, onClick = { close(onEdit) })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

// ------------------------------------------------------------------ sports

@Composable
private fun SportsRow(row: HomeLiveRow, title: String, sectionPadding: Dp, modifier: Modifier, onEdit: () -> Unit) {
    // Games with your channels, worked out while the row is on screen (the Sports page's own rules).
    val controller = remember {
        SportsWatchController(ProfileRepository.state.value.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId)
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val games by controller.games.collectAsStateWithLifecycle()
    val fallback by HomeLiveRowsData.sportsFallback.collectAsStateWithLifecycle()
    val sportsSettings by SportsRepository.settings.collectAsStateWithLifecycle()
    val now by HomeLiveRowsData.now.collectAsStateWithLifecycle()
    val use24h = LiveTvPreferences.settings.collectAsStateWithLifecycle().value.use24HourClock
    val withChannels = remember(games, row) { HomeLiveRowsData.rowGames(games, row) }
    // Nothing with a channel on now or soon: the row's live games and the next week's games instead.
    val cards: List<SportsWatchGame> = if (withChannels.isNotEmpty()) withChannels
        else fallback[row.id].orEmpty().map { SportsWatchGame(it, emptyList()) }
    if (cards.isEmpty()) return
    var held by remember { mutableStateOf<SportsWatchGame?>(null) }
    fun play(game: SportsWatchGame, match: ChannelMatch? = game.channels.firstOrNull()) {
        if (match == null) HomeLiveRowActions.openGame?.invoke(game.event)
        else HomeLiveRowActions.playChannel?.invoke(match.channel, null)
    }
    NuvioShelfSection(
        title = title,
        entries = cards,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        key = { it.event.id },
    ) { game ->
        GameCard(
            game = game,
            portrait = row.portrait,
            now = now,
            use24h = use24h,
            hideScores = sportsSettings.hideScores,
            onClick = { if (row.playsGame && game.channels.isNotEmpty()) play(game) else HomeLiveRowActions.openGame?.invoke(game.event) },
            onLongClick = { held = game },
        )
    }
    held?.let { game ->
        GameHoldSheet(game = game, onDismiss = { held = null }, onPlay = { m -> play(game, m) }, onEdit = onEdit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(game: SportsWatchGame, portrait: Boolean, now: Long, use24h: Boolean, hideScores: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val event = game.event
    val league = SportsCatalog.league(event.leagueId)
    val sides = event.ordered(league?.sport ?: Sport.OTHER)
    val live = event.state == GameState.LIVE
    val streamCount = game.channels.distinctBy { it.channel.key }.size
    Column(
        Modifier.width(if (portrait) 150.dp else 228.dp)
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surfaceElevated)
            .border(tokens.borders.thin, tokens.colors.borderDefault, tokens.shapes.compactCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            LeagueMark(league, 16.dp)
            Text(league?.short.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
            Text(
                whenLabel(event, now, use24h, hideScores),
                style = MaterialTheme.typography.labelSmall,
                color = if (live) SportsLiveRed else tokens.colors.textMuted,
                maxLines = 1,
            )
        }
        if (sides.size == 2) sides.forEach { side ->
            Row(
                Modifier.fillMaxWidth().background(teamBandBrush(side.team))
                    .padding(horizontal = NuvioTokens.Space.s8, vertical = if (portrait) NuvioTokens.Space.s8 else NuvioTokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                TeamLogo(side.team, if (portrait) 28.dp else 20.dp)
                Text(side.team.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (event.hasScore && !hideScores) Text(side.score.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, fontWeight = FontWeight.Black)
            }
        } else Text(
            eventHeadline(event, league),
            Modifier.padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
            style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary,
            maxLines = if (portrait) 3 else 2, overflow = TextOverflow.Ellipsis,
        )
        if (live) NuvioProgressBar(
            progress = progressOf(event, now), height = NuvioTokens.Space.s3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
        )
        Row(
            Modifier.fillMaxWidth().padding(NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            Text(
                game.channels.firstOrNull()?.channel?.name ?: event.broadcasts.take(2).joinToString(", "),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (streamCount > 1 && !portrait) SportsBadge(stringResource(Res.string.home_rows_more_streams, streamCount - 1), tokens.colors.surface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHoldSheet(game: SportsWatchGame, onDismiss: () -> Unit, onPlay: (ChannelMatch) -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    val tokens = MaterialTheme.nuvio
    val event = game.event
    val league = SportsCatalog.league(event.leagueId)
    val channels = game.channels.distinctBy { it.channel.key }
    var showStreams by remember { mutableStateOf(false) }
    val now by HomeLiveRowsData.now.collectAsStateWithLifecycle()
    val use24h = LiveTvPreferences.settings.collectAsStateWithLifecycle().value.use24HourClock
    val hideScores = SportsRepository.settings.collectAsStateWithLifecycle().value.hideScores
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s10)) {
            Text(eventHeadline(event, league), style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(whenLabel(event, now, use24h, hideScores), style = MaterialTheme.typography.bodySmall,
                color = if (event.state == GameState.LIVE) SportsLiveRed else tokens.colors.textMuted)
        }
        if (showStreams) {
            channels.forEach { m ->
                NuvioBottomSheetActionRow(m.channel.name, icon = Icons.Filled.PlayArrow, onClick = { close { onPlay(m) } })
            }
        } else {
            channels.firstOrNull()?.let { first ->
                NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_watch_on, first.channel.name), icon = Icons.Filled.PlayArrow,
                    onClick = { close { onPlay(first) } })
            }
            if (channels.size > 1) {
                NuvioBottomSheetActionRow("${stringResource(Res.string.home_rows_hold_other_streams)} (${channels.size - 1})", icon = Icons.AutoMirrored.Filled.ViewList,
                    onClick = { showStreams = true })
            }
            NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_game_page), icon = Icons.Filled.Info,
                onClick = { close { HomeLiveRowActions.openGame?.invoke(event) } })
            val multiview = HomeLiveRowActions.openMultiview
            if (multiview != null && channels.isNotEmpty()) {
                NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_multiview), icon = Icons.Filled.GridView,
                    onClick = { close { multiview(channels.first().channel.key) } })
            }
            NuvioBottomSheetDivider()
            NuvioBottomSheetActionRow(stringResource(Res.string.home_rows_hold_edit), icon = Icons.Filled.Edit, onClick = { close(onEdit) })
        }
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

// ------------------------------------------------------------------ On Demand

@Composable
private fun VodRow(row: HomeLiveRow, title: String, sectionPadding: Dp, modifier: Modifier) {
    val all by HomeLiveRowsData.vodItems.collectAsStateWithLifecycle()
    val items = all[row.id].orEmpty()
    if (items.isEmpty()) return
    val watchedState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<VodItem?>(null) }
    val seeAll = HomeLiveRowActions.openVodSeeAll?.let { go ->
        {
            when (row.source) {
                HomeLiveRow.VOD_RECENT_MOVIES -> go(VodKind.MOVIE, null, title)
                HomeLiveRow.VOD_RECENT_SERIES -> go(VodKind.SERIES, null, title)
                else -> {
                    val uid = row.source.removePrefix(HomeLiveRow.VOD_GROUP_PREFIX)
                    go(VodKind.of(uid.substringBefore(':')), uid, title)
                }
            }
        }
    }
    NuvioShelfSection(
        title = title,
        entries = items,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = seeAll,
        key = { it.uid },
    ) { item ->
        val match = rememberVodMatch(item)
        val watched = remember(item.uid, match, watchedState.watchedKeys) { OnDemandPlayback.isWatched(item, match) }
        NuvioPosterCard(
            title = match?.title ?: item.displayTitle,
            imageUrl = match?.poster?.takeIf { it.isNotBlank() } ?: item.icon,
            fallbackImageUrl = item.icon,
            detailLine = item.year?.toString(),
            isWatched = watched,
            onClick = { selected = item },
            onLongClick = { selected = item },
        )
    }
    val actions = OnDemandSearchBridge.actions
    selected?.let { item ->
        if (actions != null) OnDemandTitleSheet(item = item, actions = actions, onDismiss = { selected = null })
        else selected = null
    }
}
