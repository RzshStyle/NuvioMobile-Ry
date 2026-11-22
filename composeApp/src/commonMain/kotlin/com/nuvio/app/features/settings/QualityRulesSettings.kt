package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.streams.quality.LowQualityGroups
import com.nuvio.app.features.streams.quality.QualityRules
import com.nuvio.app.features.streams.quality.QualitySettings
import com.nuvio.app.features.streams.quality.StreamListSort
import com.nuvio.app.features.streams.quality.TrashReleaseGroups
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The Quality rules row (opens the rules on their own sheet) and the Sort sources row, for the
 * Stream Auto-play section (where Nuvio Arc and our TV app keep them).
 */
@Composable
internal fun QualityRulesRows(isTablet: Boolean) {
    val state by QualitySettings.state.collectAsStateWithLifecycle()
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val custom = state.rules != QualityRules.DEFAULT || state.lowQuality != LowQualityGroups.PUSH_DOWN ||
        state.trustedGroups != TrashReleaseGroups.PREFERRED_LADDER
    SettingsNavigationRow(
        title = stringResource(Res.string.quality_rules_title),
        description = stringResource(Res.string.quality_rules_subtitle) + "\n" +
            stringResource(if (custom) Res.string.quality_rules_custom else Res.string.quality_rules_recommended),
        isTablet = isTablet,
        onClick = { rulesOpen = true },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = stringResource(Res.string.stream_sort_sources),
        description = stringResource(sortLabel(state.listSort)),
        isTablet = isTablet,
        onClick = { sortOpen = true },
    )
    if (rulesOpen) QualityRulesSheet(isTablet = isTablet, onDismiss = { rulesOpen = false })
    if (sortOpen) {
        DebridSingleChoiceDialog(
            title = stringResource(Res.string.stream_sort_sources),
            selectedValue = state.listSort,
            options = StreamListSort.entries.toList(),
            label = { stringResource(sortLabel(it)) + " · " + stringResource(sortDescription(it)) },
            onSelected = QualitySettings::setListSort,
            onDismiss = { sortOpen = false },
        )
    }
}

private fun sortLabel(v: StreamListSort) = when (v) {
    StreamListSort.SOURCE_ORDER -> Res.string.stream_sort_source_order
    StreamListSort.BEST_QUALITY_FIRST -> Res.string.stream_sort_best_first
}

private fun sortDescription(v: StreamListSort) = when (v) {
    StreamListSort.SOURCE_ORDER -> Res.string.stream_sort_source_order_desc
    StreamListSort.BEST_QUALITY_FIRST -> Res.string.stream_sort_best_first_desc
}

private fun lowQualityLabel(v: LowQualityGroups) = when (v) {
    LowQualityGroups.PUSH_DOWN -> Res.string.quality_low_groups_push_down
    LowQualityGroups.HIDE -> Res.string.quality_low_groups_hide
    LowQualityGroups.NORMAL -> Res.string.quality_low_groups_normal
}

private enum class QualityDialog { LOW_GROUPS, TRUSTED_GROUPS }

/** Every Quality rule, on a sheet of its own: the same rule pickers as the built-in debrid list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityRulesSheet(isTablet: Boolean, onDismiss: () -> Unit) {
    val state by QualitySettings.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picker by rememberSaveable { mutableStateOf<DebridStreamPicker?>(null) }
    var dialog by rememberSaveable { mutableStateOf<QualityDialog?>(null) }
    val rules = state.rules
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(Res.string.quality_rules_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
            )
            Text(
                stringResource(Res.string.quality_rules_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
            )
            DebridPreferenceRow(
                isTablet = isTablet,
                title = stringResource(Res.string.quality_low_groups_title),
                description = stringResource(Res.string.quality_low_groups_subtitle),
                value = stringResource(lowQualityLabel(state.lowQuality)),
                enabled = true,
                onClick = { dialog = QualityDialog.LOW_GROUPS },
            )
            SettingsGroupDivider(isTablet = isTablet)
            DebridPreferenceRow(
                isTablet = isTablet,
                title = stringResource(Res.string.quality_trusted_groups_title),
                description = stringResource(Res.string.quality_trusted_groups_subtitle),
                value = state.trustedGroups.size.toString(),
                enabled = true,
                onClick = { dialog = QualityDialog.TRUSTED_GROUPS },
            )
            SettingsGroupDivider(isTablet = isTablet)
            DebridPreferenceRow(
                isTablet = isTablet,
                title = stringResource(Res.string.settings_debrid_size_range),
                description = stringResource(Res.string.settings_debrid_size_range_desc),
                value = sizeRangeLabel(rules),
                enabled = true,
                onClick = { picker = DebridStreamPicker.SIZE_RANGE },
            )
            debridRuleRows(rules).forEach { row ->
                SettingsGroupDivider(isTablet = isTablet)
                DebridPreferenceRow(
                    isTablet = isTablet,
                    title = row.title,
                    description = row.description,
                    value = row.value,
                    enabled = true,
                    onClick = { picker = row.picker },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            DebridPreferenceRow(
                isTablet = isTablet,
                title = stringResource(Res.string.quality_rules_reset_title),
                description = stringResource(Res.string.quality_rules_reset_subtitle),
                value = stringResource(Res.string.action_reset),
                enabled = true,
                onClick = QualitySettings::reset,
            )
            Spacer(Modifier.height(NuvioTokens.Space.s24))
        }
    }
    picker?.let { p ->
        DebridStreamPreferenceDialog(
            picker = p,
            preferences = rules,
            onPreferencesChanged = QualitySettings::setRules,
            onDismiss = { picker = null },
        )
    }
    when (dialog) {
        QualityDialog.LOW_GROUPS -> DebridSingleChoiceDialog(
            title = stringResource(Res.string.quality_low_groups_title),
            selectedValue = state.lowQuality,
            options = LowQualityGroups.entries.toList(),
            label = { stringResource(lowQualityLabel(it)) },
            onSelected = QualitySettings::setLowQuality,
            onDismiss = { dialog = null },
        )
        QualityDialog.TRUSTED_GROUPS -> DebridTextListDialog(
            title = stringResource(Res.string.quality_trusted_groups_title),
            selectedValues = state.trustedGroups,
            onSelected = QualitySettings::setTrustedGroups,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
