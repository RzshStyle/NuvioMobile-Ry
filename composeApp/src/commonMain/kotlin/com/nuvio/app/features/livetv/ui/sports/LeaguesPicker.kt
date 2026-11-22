package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.sports.League
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsSettings
import com.nuvio.app.features.livetv.sports.Sport

/** The row at the top of All Events: how many leagues are showing, and the way into Choose leagues. */
@Composable
internal fun LeaguesBar(settings: SportsSettings, follows: SportsFollows, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val count = remember(settings, follows) { repo.activeLeagues(settings, follows).size }
    val hidden = remember(settings, follows) { repo.hiddenLeagueCount(settings, follows) }
    val summary = buildString {
        append("Showing ").append(count).append(if (count == 1) " league" else " leagues")
        if (hidden > 0) append(" · ").append(hidden).append(" hidden")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Choose leagues ›", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.accent, fontWeight = FontWeight.Medium)
    }
}

/**
 * Every league the app knows, to turn on or off for All Events: the ones showing now first, then
 * the rest (other regions too). Leagues you follow show "Following" and stay on. The order stays
 * fixed while the sheet is open, so a row doesn't jump when you tick it. Typing narrows the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaguesSheet(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    val mine = remember(follows) { repo.followedLeagueIds(follows) }
    val ordered = remember(settings.region) { SportsCatalog.ordered(settings) }
    val onWhenOpened = remember { repo.activeLeagues(settings, follows).mapTo(HashSet()) { it.id } }
    val query = filter.trim()
    val found = remember(query, ordered) {
        if (query.isEmpty()) emptyList()
        else ordered.filter { it.name.contains(query, true) || it.short.contains(query, true) || it.sport.label.contains(query, true) }
    }
    val top = remember(ordered) { ordered.filter { it.id in onWhenOpened } }
    val more = remember(ordered) { ordered.filter { it.id !in onWhenOpened } }

    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, fullHeight = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            Text("Choose leagues", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            DialogButton(text = "Done", style = DialogButtonStyle.Primary, onClick = onDismiss)
        }
        NuvioInputField(value = filter, onValueChange = { filter = it }, placeholder = "Search leagues", modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            if (query.isNotEmpty()) {
                if (found.isEmpty()) item(key = "none") { Text("No leagues match", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted) }
                items(found, key = { "f/" + it.id }) { l -> LeagueChoiceRow(l, settings, mine) }
            } else {
                item(key = "h-on") { LeaguesHeading("On all events") }
                items(top, key = { "on/" + it.id }) { l -> LeagueChoiceRow(l, settings, mine) }
                if (more.isNotEmpty()) {
                    item(key = "h-more") { LeaguesHeading("More leagues") }
                    items(more, key = { "more/" + it.id }) { l -> LeagueChoiceRow(l, settings, mine) }
                }
            }
        }
    }
}

@Composable
private fun LeaguesHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.nuvio.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = NuvioTokens.Space.s8))
}

@Composable
private fun LeagueChoiceRow(league: League, settings: SportsSettings, followed: Set<String>) {
    val tokens = MaterialTheme.nuvio
    val following = league.id in followed
    val on = following || SportsCatalog.shown(league, settings)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surface)
            .clickable(enabled = !following) { SportsRepository.toggleLeagueShown(league) }
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        LeagueMark(league, 28.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(league.name, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(league.sport.label, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (following) {
            Text("Following", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
        } else {
            Checkbox(checked = on, onCheckedChange = null)
        }
    }
}

/**
 * All Events' Pick Leagues: tick the leagues to see together (from any sports), and whether to
 * show only the games on now. Only leagues showing on All Events are listed, by sport.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickLeaguesSheet(onDismiss: (Set<String>) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ticked by remember { mutableStateOf(settings.pickedLeagues) }
    var liveOnly by remember { mutableStateOf(settings.pickedLiveOnly) }
    var filter by remember { mutableStateOf("") }
    fun save() = repo.updateSettings { it.copy(pickedLeagues = ticked, pickedLiveOnly = liveOnly) }
    val leagues = remember(settings.hiddenLeagues, settings.region, follows) { repo.activeLeagues(settings, follows) }
    val query = filter.trim()
    val groups = remember(leagues, query) {
        Sport.entries.mapNotNull { s ->
            val list = leagues.filter { it.sport == s }.let { all ->
                if (query.isEmpty() || s.label.contains(query, true)) all
                else all.filter { it.name.contains(query, true) || it.short.contains(query, true) }
            }
            if (list.isEmpty()) null else s to list
        }
    }
    val done = { onDismiss(ticked) }

    NuvioModalBottomSheet(onDismissRequest = done, sheetState = sheetState, fullHeight = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            Text("Pick Leagues", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (ticked.isNotEmpty()) DialogButton(text = "Clear", style = DialogButtonStyle.Secondary, onClick = { ticked = emptySet(); save() })
            DialogButton(text = "Done", style = DialogButtonStyle.Primary, onClick = done)
        }
        Text(
            "Tick the leagues to see together on All Events, from any sports.",
            style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8)
                .clip(tokens.shapes.card)
                .background(tokens.colors.surface)
                .clickable { liveOnly = !liveOnly; save() }
                .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Live games only", style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium)
                Text("Today: only what's on now", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
            Switch(checked = liveOnly, onCheckedChange = { liveOnly = it; save() })
        }
        NuvioInputField(value = filter, onValueChange = { filter = it }, placeholder = "Search leagues", modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            if (groups.isEmpty()) item(key = "none") { Text("No leagues match", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted) }
            groups.forEach { (sport, list) ->
                item(key = "h/" + sport.name) {
                    val n = list.count { it.id in ticked }
                    LeaguesHeading(sport.icon + "  " + sport.label + if (n > 0) "  ·  $n picked" else "")
                }
                items(list, key = { "p/" + it.id }) { l ->
                    val on = l.id in ticked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(tokens.shapes.card)
                            .background(tokens.colors.surface)
                            .clickable { ticked = if (on) ticked - l.id else ticked + l.id; save() }
                            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                    ) {
                        LeagueMark(l, 28.dp)
                        Text(l.name, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Checkbox(checked = on, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
