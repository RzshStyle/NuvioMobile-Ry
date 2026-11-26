package com.nuvio.app.features.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.plugins.AddPluginRepositoryResult
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Moves an addon and plugin setup between installs: export to a small JSON text,
 * or import that text (or any text containing links) in one go.
 */
@Serializable
internal data class NuvioSetupExport(
    val nuvioSetup: Int = 1,
    val addons: List<ExportedAddon> = emptyList(),
    val pluginRepositories: List<String> = emptyList(),
)

@Serializable
internal data class ExportedAddon(val url: String, val enabled: Boolean = true)

internal data class SetupImportReport(
    val addons: Int = 0,
    val plugins: Int = 0,
    val alreadyInstalled: Int = 0,
    val needsConfirmation: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
) {
    fun summary(): String = buildString {
        append("Added $addons addon${if (addons == 1) "" else "s"}")
        if (plugins > 0) append(" and $plugins plugin repositor${if (plugins == 1) "y" else "ies"}")
        append('.')
        if (alreadyInstalled > 0) append(" $alreadyInstalled were already installed.")
        if (needsConfirmation.isNotEmpty()) {
            append("\n\nThese need your OK, so add them one at a time above:\n")
            needsConfirmation.forEach { append("• ").append(it).append('\n') }
        }
        if (failed.isNotEmpty()) {
            append("\n\nCould not add:\n")
            failed.forEach { append("• ").append(it).append('\n') }
        }
    }.trim()
}

internal object SetupTransfer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val linkPattern = Regex("""(?:https?|stremio)://[^\s"'<>,;]+""")

    fun export(): String {
        val addons = AddonRepository.uiState.value.addons.map { ExportedAddon(it.manifestUrl, it.enabled) }
        val plugins = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.uiState.value.repositories.map { it.manifestUrl }
        } else {
            emptyList()
        }
        return json.encodeToString(NuvioSetupExport.serializer(), NuvioSetupExport(addons = addons, pluginRepositories = plugins))
    }

    suspend fun import(text: String, onProgress: (done: Int, total: Int) -> Unit): SetupImportReport {
        AddonRepository.initialize()
        if (AppFeaturePolicy.pluginsEnabled) PluginRepository.initialize()

        val setup = runCatching { json.decodeFromString(NuvioSetupExport.serializer(), text.trim()) }
            .getOrNull()
            ?.takeIf { it.addons.isNotEmpty() || it.pluginRepositories.isNotEmpty() }

        // (url, kind) where kind: 'a' addon, 'p' plugin repository, '?' unknown.
        val work: List<Pair<String, Char>> = setup?.let { s ->
            s.addons.map { it.url to 'a' } + s.pluginRepositories.map { it to 'p' }
        } ?: linkPattern.findAll(text).map { it.value.trimEnd('.', ')', ']') to '?' }.distinctBy { it.first }.toList()

        val disabled = setup?.addons?.filter { !it.enabled }?.map { it.url }?.toSet().orEmpty()
        var report = SetupImportReport()
        work.forEachIndexed { index, (url, kind) ->
            onProgress(index, work.size)
            report = when (kind) {
                'a' -> report.addAddon(url, fallbackToPlugin = false)
                'p' -> report.addPlugin(url) ?: report.copy(failed = report.failed + shorten(url))
                else -> if (looksLikePluginRepo(url)) {
                    report.addPlugin(url, quiet = true) ?: report.addAddon(url, fallbackToPlugin = false)
                } else {
                    report.addAddon(url, fallbackToPlugin = true)
                }
            }
        }
        disabled.forEach { AddonRepository.setAddonEnabled(it, false) }
        onProgress(work.size, work.size)
        return report
    }

    private suspend fun SetupImportReport.addAddon(url: String, fallbackToPlugin: Boolean): SetupImportReport =
        when (val result = AddonRepository.addAddon(url)) {
            is AddAddonResult.Success -> copy(addons = addons + 1)
            is AddAddonResult.TrackingConsentRequired -> copy(needsConfirmation = needsConfirmation + result.manifest.name)
            is AddAddonResult.Error -> when {
                result.message.contains("already", ignoreCase = true) -> copy(alreadyInstalled = alreadyInstalled + 1)
                fallbackToPlugin -> addPlugin(url, quiet = true) ?: copy(failed = failed + "${shorten(url)}: ${result.message}")
                else -> copy(failed = failed + "${shorten(url)}: ${result.message}")
            }
        }

    /** Returns null when [quiet] and the link is not a plugin repository, so the caller can try something else. */
    private suspend fun SetupImportReport.addPlugin(url: String, quiet: Boolean = false): SetupImportReport? {
        if (!AppFeaturePolicy.pluginsEnabled) return if (quiet) null else copy(failed = failed + "${shorten(url)}: plugins are not available")
        return when (val result = PluginRepository.addRepository(url)) {
            is AddPluginRepositoryResult.Success -> copy(plugins = plugins + 1)
            is AddPluginRepositoryResult.Error -> when {
                result.message.contains("already", ignoreCase = true) -> copy(alreadyInstalled = alreadyInstalled + 1)
                quiet -> null
                else -> copy(failed = failed + "${shorten(url)}: ${result.message}")
            }
        }
    }

    private fun looksLikePluginRepo(url: String): Boolean =
        url.contains("raw.githubusercontent.com") || url.contains("github.io") || url.contains("/plugins")

    /** Addon links often carry personal config or keys in the path; only show the host. */
    private fun shorten(url: String): String =
        url.substringAfter("://").substringBefore('/').ifBlank { url.take(40) }
}

@Composable
internal fun SetupTransferCard(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var copiedText by remember { mutableStateOf<String?>(null) }

    NuvioSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Move your setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste many addon and plugin links at once, or copy this app's setup to paste into another Nuvio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NuvioPrimaryButton(text = "Import links or setup", onClick = {
                resultText = null
                progress = null
                showImport = true
            })
            TextButton(onClick = {
                val text = SetupTransfer.export()
                clipboard.setText(AnnotatedString(text))
                copiedText = "Setup copied. Paste it into Import on the other device. It contains your addon links, which can include your personal addon settings, so keep it private."
            }) { Text("Copy my setup") }
            copiedText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    if (showImport) {
        val running = progress != null && resultText == null
        AlertDialog(
            onDismissRequest = { if (!running) showImport = false },
            title = { Text(if (resultText != null) "Import finished" else "Import links or setup") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        resultText != null -> Text(resultText!!)
                        running -> Text("Adding ${progress!!.first + 1} of ${progress!!.second}…")
                        else -> {
                            Text(
                                "Paste addon links, plugin repository links, or a setup copied from another Nuvio. Any text works, links are picked out automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                                placeholder = { Text("https://…/manifest.json") },
                                minLines = 5,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    resultText != null -> TextButton(onClick = { showImport = false; importText = "" }) { Text("Done") }
                    running -> Unit
                    else -> TextButton(
                        enabled = importText.isNotBlank(),
                        onClick = {
                            progress = 0 to 1
                            scope.launch {
                                val report = SetupTransfer.import(importText) { done, total -> progress = done to total }
                                resultText = if (report.addons + report.plugins + report.alreadyInstalled + report.failed.size + report.needsConfirmation.size == 0) {
                                    "No links found in that text."
                                } else {
                                    report.summary()
                                }
                            }
                        },
                    ) { Text("Import") }
                }
            },
            dismissButton = {
                if (!running && resultText == null) TextButton(onClick = { showImport = false }) { Text("Cancel") }
            },
        )
    }
}
