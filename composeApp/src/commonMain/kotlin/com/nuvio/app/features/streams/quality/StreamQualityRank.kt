package com.nuvio.app.features.streams.quality

import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamFacts
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DebridStreamVisualTag
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem

/**
 * What this phone and its screen can really play, for the best-quality order: an AV1 link on a
 * phone with no AV1 hardware decoder, or a Dolby Vision-only link on a screen without Dolby Vision,
 * goes to the bottom. Filled in by the Android app at start; elsewhere everything counts as playable.
 */
internal object PlaybackDeviceSupport {
    var hardwareAv1: Boolean = true
    var dolbyVisionScreen: Boolean = true
}

/**
 * Best-first order of links from every source, by the Quality rules (the TV app's rules; idea from
 * Nuvio Arc, GPL-3).
 *
 * Kept: links that pass the "Never" rules and the "Only" rules; if none do, those that pass "Never";
 * if none do, all of them (a pick is always possible). Then, best first: playable on this phone →
 * not a low-quality group (when set to move them down) → resolution → source (Remux, BluRay, WEB…)
 * → trusted release group → HDR / Dolby Vision → audio → channels → codec → bigger file → MKV.
 * Ties keep the incoming (source) order.
 */
internal object StreamQualityRank {

    private val MKV = Regex("\\.mkv\\b|\\bmkv\\b", RegexOption.IGNORE_CASE)

    data class Order(val bestFirst: List<Int>, val hidden: Set<Int>)

    /** [streams] best first, without the ones the rules leave out. */
    fun rank(streams: List<StreamItem>, settings: QualitySettingsState = QualitySettings.current()): List<StreamItem> {
        if (streams.size <= 1) return streams
        val order = order(streams, settings)
        return order.bestFirst.filter { it !in order.hidden }.map(streams::get)
    }

    /** Every link's place, with the ones the rules leave out reported in [Order.hidden]. */
    fun order(streams: List<StreamItem>, settings: QualitySettingsState = QualitySettings.current()): Order {
        val rules = settings.rules
        val facts = streams.map { DebridStreamMetadata.facts(it, rules) }
        val groups = streams.indices.map { i -> facts[i].releaseGroup.ifBlank { ReleaseGroupText.of(streams[i]) } }
        val lq = BooleanArray(streams.size) { QualityRules.isLowQualityGroup(groups[it]) }
        val trusted = settings.trustedGroups
        val groupRank = IntArray(streams.size) { i ->
            val g = groups[i]
            if (g.isEmpty()) Int.MAX_VALUE else trusted.indexOfFirst { it.equals(g, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it }
        }
        val passNever = streams.indices.filter {
            passesExclusions(facts[it], groups[it], rules) && !(settings.lowQuality == LowQualityGroups.HIDE && lq[it])
        }
        val passOnly = passNever.filter { passesRequirements(facts[it], groups[it], rules) }
        val kept = passOnly.ifEmpty { passNever }.ifEmpty { streams.indices.toList() }.toHashSet()
        val sinks = IntArray(streams.size) { i ->
            var s = 0
            if (!playableHere(facts[i])) s += 2
            if (settings.lowQuality == LowQualityGroups.PUSH_DOWN && lq[i]) s += 1
            s
        }
        val mkv = IntArray(streams.size) { if (isMkv(streams[it])) 1 else 0 }
        val sorted = streams.indices.sortedWith(
            compareBy<Int> { sinks[it] }
                .thenBy { facts[it].resolutionRank }
                .thenBy { facts[it].qualityRank }
                .thenBy { groupRank[it] }
                .thenBy { facts[it].visualRank }
                .thenBy { facts[it].audioRank }
                .thenBy { facts[it].channelRank }
                .thenBy { facts[it].encodeRank }
                .thenByDescending { facts[it].size ?: -1L }
                .thenByDescending { mkv[it] }
        )
        return Order(sorted, streams.indices.filterTo(HashSet()) { it !in kept })
    }

    /**
     * "Best quality first" for the source list: everything that arrived, ranked as one list (all
     * sources), or each source's links ranked (one source picked). A view made only of links the
     * rules leave out shows them all, ranked.
     */
    fun sortGroups(groups: List<AddonStreamGroup>, combined: Boolean, label: String, settings: QualitySettingsState = QualitySettings.current()): List<AddonStreamGroup> {
        if (!combined) return groups.map { g -> if (g.streams.size <= 1) g else g.copy(streams = rankOrAll(g.streams, settings)) }
        val all = groups.flatMap { it.streams }
        if (all.isEmpty()) return groups
        return listOf(AddonStreamGroup(addonName = label, addonId = BEST_FIRST_ID, streams = rankOrAll(all, settings), isLoading = groups.any { it.isLoading }))
    }

    private fun rankOrAll(streams: List<StreamItem>, settings: QualitySettingsState): List<StreamItem> {
        val order = order(streams, settings)
        val kept = order.bestFirst.filter { it !in order.hidden }
        return (kept.ifEmpty { order.bestFirst }).map(streams::get)
    }

    const val BEST_FIRST_ID = "quality:best-first"

    private fun passesExclusions(f: DebridStreamFacts, group: String, p: DebridStreamPreferences): Boolean {
        if (f.resolution in p.excludedResolutions) return false
        if (f.quality in p.excludedQualities) return false
        if (f.visualTags.any { it in p.excludedVisualTags }) return false
        if (f.audioTags.any { it in p.excludedAudioTags }) return false
        if (f.audioChannels.any { it in p.excludedAudioChannels }) return false
        if (f.encode in p.excludedEncodes) return false
        if (f.languages.isNotEmpty() && f.languages.all { it in p.excludedLanguages }) return false
        if (p.excludedReleaseGroups.any { group.equals(it, ignoreCase = true) }) return false
        if (p.sizeMinGb > 0 && f.size != null && f.size < p.sizeMinGb * GB) return false
        if (p.sizeMaxGb > 0 && f.size != null && f.size > p.sizeMaxGb * GB) return false
        return true
    }

    private fun passesRequirements(f: DebridStreamFacts, group: String, p: DebridStreamPreferences): Boolean {
        if (p.requiredResolutions.isNotEmpty() && f.resolution !in p.requiredResolutions) return false
        if (p.requiredQualities.isNotEmpty() && f.quality !in p.requiredQualities) return false
        if (p.requiredVisualTags.isNotEmpty() && f.visualTags.none { it in p.requiredVisualTags }) return false
        if (p.requiredAudioTags.isNotEmpty() && f.audioTags.none { it in p.requiredAudioTags }) return false
        if (p.requiredAudioChannels.isNotEmpty() && f.audioChannels.none { it in p.requiredAudioChannels }) return false
        if (p.requiredEncodes.isNotEmpty() && f.encode !in p.requiredEncodes) return false
        if (p.requiredLanguages.isNotEmpty() && f.languages.none { it in p.requiredLanguages }) return false
        if (p.requiredReleaseGroups.isNotEmpty() && p.requiredReleaseGroups.none { group.equals(it, ignoreCase = true) }) return false
        return true
    }

    private fun playableHere(f: DebridStreamFacts): Boolean {
        if (f.encode == DebridStreamEncode.AV1 && !PlaybackDeviceSupport.hardwareAv1) return false
        if (DebridStreamVisualTag.DV_ONLY in f.visualTags && !PlaybackDeviceSupport.dolbyVisionScreen) return false
        return true
    }

    private fun isMkv(stream: StreamItem): Boolean {
        val text = listOfNotNull(stream.behaviorHints.filename, stream.playableDirectUrl, stream.name, stream.title).joinToString(" ")
        return MKV.containsMatchIn(text)
    }

    private const val GB = 1_000_000_000L
}

/**
 * The release group from a file or release name: the word after the last hyphen, with codec,
 * source and resolution words ruled out ("WEB-DL" has no group "DL"). Idea from Nuvio Arc (GPL-3).
 */
internal object ReleaseGroupText {
    private val containerExtension = Regex("\\.(mkv|mp4|m4v|avi|ts|m2ts|webm|mov)$", RegexOption.IGNORE_CASE)
    private val leadingToken = Regex("^[A-Za-z0-9][A-Za-z0-9._]{0,23}")
    private val bracketGroup = Regex("^\\[([A-Za-z0-9][A-Za-z0-9._-]{1,23})\\]")
    private val channelToken = Regex("^\\d\\.\\d$")
    private val nonGroupTokens = setOf(
        "dl", "rip", "hd", "uhd", "sd", "web", "bluray", "remux", "hdr", "hdr10", "sdr",
        "dv", "dovi", "ma", "es", "x", "atmos", "truehd", "dts", "aac", "ac3", "eac3",
        "flac", "opus", "avc", "hevc", "av1", "x264", "x265", "h264", "h265", "vc1",
        "10bit", "8bit", "hi10p", "imax", "proper", "repack", "extended", "remastered",
        "unrated", "multi", "dual", "sub", "subs", "dubbed", "hc", "cam", "ts", "tc", "scr",
        "2160p", "1440p", "1080p", "720p", "576p", "480p", "360p", "4k", "2k",
    )
    // Group names that contain '-' or '.', which the last-hyphen rule would cut.
    private val compoundGroup = Regex(
        "(?<![A-Za-z0-9])(VISIONPLUSHDR\\-X|BR\\-GuyZo|Pahe\\.in|Pahe\\.ph|D\\-Z0N3|YTS\\.LT|YTS\\.MX|YTS\\.AG|C\\.A\\.A)(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )

    fun of(stream: StreamItem): String {
        val resolve = stream.clientResolve
        val raw = resolve?.stream?.raw
        val candidates = listOfNotNull(
            stream.behaviorHints.filename, resolve?.filename, raw?.filename, resolve?.torrentName, raw?.torrentName,
            stream.debridCacheStatus?.cachedName, stream.name, stream.title, stream.description,
        )
        for (c in candidates) {
            val g = fromText(c)
            if (g.isNotEmpty()) return g
        }
        return ""
    }

    fun fromText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        compoundGroup.find(trimmed)?.let { return it.value }
        bracketGroup.find(trimmed)?.let { m -> val c = m.groupValues[1]; if (c.lowercase() !in nonGroupTokens) return c }
        for (line in trimmed.lineSequence()) {
            val stripped = containerExtension.replace(line.trim(), "")
            val hyphen = stripped.lastIndexOf('-')
            if (hyphen <= 0 || hyphen >= stripped.length - 1) continue
            val tail = stripped.substring(hyphen + 1).trim()
            val token = leadingToken.find(tail)?.value ?: continue
            if (token.lowercase() in nonGroupTokens) continue
            if (token.all { it.isDigit() }) continue
            if (channelToken.matches(token)) continue
            if (token.length == 1 && tail != token) continue
            return token
        }
        return ""
    }
}
