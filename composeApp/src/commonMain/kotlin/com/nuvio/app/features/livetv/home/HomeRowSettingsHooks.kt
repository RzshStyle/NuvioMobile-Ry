package com.nuvio.app.features.livetv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.SettingsGroup
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_rows_add_button
import nuvio.composeapp.generated.resources.home_rows_add_button_hint
import nuvio.composeapp.generated.resources.home_rows_edit
import nuvio.composeapp.generated.resources.home_rows_hidden_empty
import org.jetbrains.compose.resources.stringResource

/** Settings › Homescreen: the Add a row card above the catalog list. */
@Composable
internal fun HomeRowAddButton(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    var adding by remember { mutableStateOf(false) }
    // The rows only show while Live TV is on.
    val liveTvOn = com.nuvio.app.features.livetv.data.LiveTvPreferences.settings.collectAsStateWithLifecycle().value.showInSidebar
    if (!liveTvOn) return
    SettingsGroup(isTablet = isTablet) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { adding = true }
                .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tokens.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
                Text(stringResource(Res.string.home_rows_add_button), style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(stringResource(Res.string.home_rows_add_button_hint), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
        }
    }
    if (adding) HomeRowAddSheet(onDismiss = { adding = false })
}

/** Under a row in Settings › Homescreen: "Nothing to show right now – hidden on Home" while it's empty. */
@Composable
internal fun HomeLiveRowEmptyNote(rowKey: String) {
    val empty by HomeLiveRows.emptyRowIds.collectAsStateWithLifecycle()
    if (HomeLiveRow.idOf(rowKey) !in empty) return
    Text(
        stringResource(Res.string.home_rows_hidden_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textSecondary,
    )
}

/** The Edit button beside a row in Settings › Homescreen; opens the row's options. */
@Composable
internal fun HomeLiveRowEditButton(rowKey: String) {
    val rows by HomeLiveRows.rows.collectAsStateWithLifecycle()
    val row = rows.firstOrNull { it.key == rowKey } ?: return
    var editing by remember { mutableStateOf(false) }
    TextButton(onClick = { editing = true }) {
        Text(stringResource(Res.string.home_rows_edit), color = MaterialTheme.nuvio.colors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
    if (editing) HomeRowEditSheet(row = row, onDismiss = { editing = false })
}
