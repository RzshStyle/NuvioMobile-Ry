package com.nuvio.app.features.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.iptv.model.LiveChannel
import com.nuvio.iptv.model.PlaylistSource
import com.nuvio.iptv.model.XtreamOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val GROUP_FAVOURITES = "\u0000favourites"
private const val GROUP_RECENT = "\u0000recent"
private const val GROUP_ALL = "\u0000all"

@Composable
actual fun LiveTvTab(modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit) {
    val repository = LiveTvAndroid.repository
    val state by repository.state.collectAsState()
    var managing by rememberSaveable { mutableStateOf(false) }
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current

    Box(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        if (!state.hasSources) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Live TV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Add your own IPTV provider with an Xtream Codes login or an M3U link. Nothing is included with the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AddPlaylistForm(repository, onAdded = {})
            }
        } else {
            ChannelBrowser(
                state = state,
                bottomPadding = bottomPadding.value,
                onManage = { managing = true },
                onToggleFavourite = repository::toggleFavorite,
                onPlay = { channel, nowTitle ->
                    repository.markWatched(channel.id)
                    onPlay(
                        LiveTvPlayRequest(
                            channelId = channel.id,
                            title = channel.name,
                            url = channel.streamUrl,
                            headers = channel.headers,
                            logo = channel.logoUrl,
                            subtitle = nowTitle,
                        )
                    )
                },
            )
        }
    }

    if (managing) ManagePlaylistsDialog(repository, state, onDismiss = { managing = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelBrowser(
    state: IptvState,
    bottomPadding: Float,
    onManage: () -> Unit,
    onToggleFavourite: (String) -> Unit,
    onPlay: (LiveChannel, String?) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var selectedGroup by rememberSaveable {
        mutableStateOf(if (state.favorites.isNotEmpty()) GROUP_FAVOURITES else GROUP_ALL)
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val groupChips = remember(state.revision) {
        buildList {
            add(GROUP_FAVOURITES to "Favourites")
            if (state.recentChannels.isNotEmpty()) add(GROUP_RECENT to "Recent")
            add(GROUP_ALL to "All channels")
            state.groups.filter { it.name !in state.hiddenGroups }.forEach { add(it.name to it.name) }
        }
    }
    val visible = remember(state.revision, selectedGroup, query) {
        val base: List<LiveChannel> = when (selectedGroup) {
            GROUP_FAVOURITES -> state.favorites.mapNotNull(state::channel)
            GROUP_RECENT -> state.recentChannels.mapNotNull(state::channel)
            GROUP_ALL -> state.channels.filter { it.group !in state.hiddenGroups }
            else -> state.groups.firstOrNull { it.name == selectedGroup }?.channelIds?.mapNotNull(state::channel).orEmpty()
        }
        val q = query.trim()
        if (q.isEmpty()) base
        else state.channels.filter { it.name.contains(q, ignoreCase = true) }
    }
    val loading = state.status.values.any { it.loadingPlaylist }
    val errors = state.status.values.mapNotNull { it.error }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search all channels") },
                )
                IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.Close, "Close search") }
            } else {
                Text("Live TV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                IconButton(onClick = onManage) { Icon(Icons.Filled.Settings, "Playlists") }
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        errors.firstOrNull()?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (query.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groupChips, key = { it.first }) { (key, label) ->
                    FilterChip(selected = key == selectedGroup, onClick = { selectedGroup = key }, label = { Text(label, maxLines = 1) })
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(selectedGroup) { listState.scrollToItem(0) }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        query.isNotBlank() -> "No channels match \"$query\"."
                        selectedGroup == GROUP_FAVOURITES -> "No favourites yet. Long-press a channel to add it."
                        loading -> "Loading channels…"
                        else -> "No channels here."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.dp + 16.dp),
        ) {
            items(visible, key = { it.id }) { channel ->
                val (current, next) = state.nowAndNext(channel, now)
                val favourite = channel.id in state.favorites
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPlay(channel, current?.title) },
                            onLongClick = { onToggleFavourite(channel.id) },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (channel.logoUrl != null) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                            )
                        } else {
                            Text(channel.name.take(3).uppercase(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            channel.number?.let {
                                Text("$it  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (current != null) {
                            Text(current.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(
                                progress = { ((now - current.startMs).toFloat() / current.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                            )
                        }
                        if (next != null) {
                            Text(
                                "Next: ${timeOf(next.startMs)}  ${next.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onToggleFavourite(channel.id) }) {
                        Icon(
                            if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                            tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 92.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

private val timeFormat: DateFormat by lazy { DateFormat.getTimeInstance(DateFormat.SHORT) }
private fun timeOf(ms: Long): String = timeFormat.format(Date(ms))

@Composable
private fun ManagePlaylistsDialog(repository: IptvRepository, state: IptvState, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(if (adding) "Add a playlist" else "Your playlists") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (adding) {
                    AddPlaylistForm(repository, onAdded = { adding = false })
                    TextButton(onClick = { adding = false }) { Text("Back") }
                } else {
                    state.sources.forEach { source ->
                        val status = state.status[source.id]
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            val kind = if (source is PlaylistSource.Xtream) "Xtream Codes" else "M3U"
                            val detail = when {
                                status == null -> kind
                                status.loadingPlaylist -> "$kind · loading channels…"
                                status.error != null -> "$kind · ${status.error}"
                                else -> buildString {
                                    append("$kind · ${status.channelCount} channels")
                                    if (status.loadingEpg) append(" · loading guide…")
                                    else if (status.epgChannelCount > 0) append(" · guide for ${status.epgChannelCount}")
                                }
                            }
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            status?.expiresAtMs?.let { Text("Expires ${df.format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                            status?.epgErrors?.forEach { (url, err) ->
                                Text("Guide $url failed: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { repository.refresh(source.id) }) { Text("Refresh") }
                                OutlinedButton(onClick = {
                                    if (confirmRemove == source.id) { repository.removeSource(source.id); confirmRemove = null }
                                    else confirmRemove = source.id
                                }) { Text(if (confirmRemove == source.id) "Tap again to remove" else "Remove") }
                            }
                        }
                        HorizontalDivider()
                    }
                    Button(onClick = { adding = true }) { Text("Add another playlist") }
                }
            }
        },
    )
}

@Composable
private fun AddPlaylistForm(repository: IptvRepository, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var xtream by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var userAgent by rememberSaveable { mutableStateOf("") }
    var hls by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canAdd = !busy && if (xtream) server.isNotBlank() && username.isNotBlank() && password.isNotBlank() else m3uUrl.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = xtream, onClick = { xtream = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Xtream Codes") }
            SegmentedButton(selected = !xtream, onClick = { xtream = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("M3U link") }
        }
        Field(name, { name = it }, "Name (optional)")
        if (xtream) {
            Field(server, { server = it }, "Server address", "http://example.com:8080", KeyboardType.Uri)
            Field(username, { username = it }, "Username")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Field(epg, { epg = it }, "EPG guide URL (optional)", "Leave empty to use your provider's guide", KeyboardType.Uri)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use HLS streams", style = MaterialTheme.typography.bodyMedium)
                    Text("Try this if channels buffer or fail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hls, onCheckedChange = { hls = it })
            }
        } else {
            Field(m3uUrl, { m3uUrl = it }, "Playlist URL", "http://example.com/get.php?…", KeyboardType.Uri)
            Field(epg, { epg = it }, "EPG guide URL (optional)", "Several allowed, comma separated", KeyboardType.Uri)
            Field(userAgent, { userAgent = it }, "User agent (optional)")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = null
                val epgUrls = epg.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                val source = if (xtream) PlaylistSource.Xtream(
                    id = "",
                    name = name.trim().ifEmpty { hostOf(server) },
                    serverUrl = server.trim(),
                    username = username.trim(),
                    password = password,
                    epgUrls = epgUrls,
                    outputFormat = if (hls) XtreamOutput.HLS else XtreamOutput.TS,
                ) else PlaylistSource.M3u(
                    id = "",
                    name = name.trim().ifEmpty { hostOf(m3uUrl) },
                    url = m3uUrl.trim().let { if ("://" in it) it else "http://$it" },
                    epgUrls = epgUrls,
                    userAgent = userAgent.trim().ifEmpty { null },
                )
                scope.launch {
                    repository.addSource(source)
                        .onSuccess { password = ""; onAdded() }
                        .onFailure { error = it.message ?: "Could not add the playlist" }
                    busy = false
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…")
            } else {
                Text("Add playlist")
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String? = null, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun hostOf(url: String) = url.trim().substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { "Playlist" }
