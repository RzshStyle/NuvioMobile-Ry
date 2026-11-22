package com.nuvio.app.features.streams.quality

import com.nuvio.app.features.debrid.DebridStreamAudioTag
import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DebridStreamQuality
import com.nuvio.app.features.debrid.DebridStreamVisualTag
import com.nuvio.app.features.livetv.sync.LiveTvDriveHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** What the Quality rules do with TRaSH's low-quality release groups. */
internal enum class LowQualityGroups { PUSH_DOWN, HIDE, NORMAL }

/** The order of the source list. */
internal enum class StreamListSort { SOURCE_ORDER, BEST_QUALITY_FIRST }

/** The Quality rules as saved on this phone. */
internal data class QualitySettingsState(
    val rules: DebridStreamPreferences = QualityRules.DEFAULT,
    val lowQuality: LowQualityGroups = LowQualityGroups.PUSH_DOWN,
    /** Trusted release groups, best first (starts with the TRaSH Guides ladder). */
    val trustedGroups: List<String> = TrashReleaseGroups.PREFERRED_LADDER,
    val listSort: StreamListSort = StreamListSort.SOURCE_ORDER,
)

/**
 * The recommended Quality rules for every source (Settings › Playback › Stream Auto-play), used by
 * "Best quality" auto-play and "Best quality first". Separate from the built-in debrid list's own
 * filters. The same recommendations as the TV app (idea from Nuvio Arc).
 */
internal object QualityRules {

    val DEFAULT: DebridStreamPreferences = DebridStreamPreferences(
        excludedQualities = listOf(
            DebridStreamQuality.CAM,
            DebridStreamQuality.TS,
            DebridStreamQuality.TC,
            DebridStreamQuality.SCR,
        ),
        excludedVisualTags = listOf(
            DebridStreamVisualTag.THREE_D,
            DebridStreamVisualTag.H_OU,
            DebridStreamVisualTag.H_SBS,
        ),
        // Lossless first, then the rest by how much they carry.
        preferredAudioTags = listOf(
            DebridStreamAudioTag.TRUEHD,
            DebridStreamAudioTag.DTS_HD_MA,
            DebridStreamAudioTag.ATMOS,
            DebridStreamAudioTag.DTS_X,
            DebridStreamAudioTag.FLAC,
            DebridStreamAudioTag.DTS_HD,
            DebridStreamAudioTag.DTS_ES,
            DebridStreamAudioTag.DD_PLUS,
            DebridStreamAudioTag.DTS,
            DebridStreamAudioTag.DD,
            DebridStreamAudioTag.OPUS,
            DebridStreamAudioTag.AAC,
            DebridStreamAudioTag.UNKNOWN,
        ),
        // AV1 after HEVC and H.264: many phones can't decode it in hardware.
        preferredEncodes = listOf(
            DebridStreamEncode.HEVC,
            DebridStreamEncode.AVC,
            DebridStreamEncode.AV1,
            DebridStreamEncode.XVID,
            DebridStreamEncode.DIVX,
            DebridStreamEncode.UNKNOWN,
        ),
    )

    private val lowQualitySet: Set<String> by lazy {
        TrashReleaseGroups.LOW_QUALITY_GROUPS.mapTo(HashSet()) { it.lowercase() }
    }

    fun isLowQualityGroup(group: String): Boolean = group.isNotEmpty() && group.lowercase() in lowQualitySet
}

/** Where the Quality rules are kept (this phone only). */
internal object QualitySettings {
    private const val KEY_RULES = "stream_quality_rules"
    private const val KEY_LOW = "stream_low_quality_groups"
    private const val KEY_TRUSTED = "stream_trusted_groups"
    private const val KEY_SORT = "stream_list_sort"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var loaded = false
    private val _state = MutableStateFlow(QualitySettingsState())
    val state: StateFlow<QualitySettingsState> get() { ensureLoaded(); return _state.asStateFlow() }

    fun current(): QualitySettingsState { ensureLoaded(); return _state.value }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val rules = LiveTvDriveHttp.getString(KEY_RULES)?.takeIf { it.isNotBlank() }
                ?.let { raw -> runCatching { json.decodeFromString(DebridStreamPreferences.serializer(), raw) }.getOrNull() }
                ?: QualityRules.DEFAULT
            val low = LiveTvDriveHttp.getString(KEY_LOW)?.let { v -> LowQualityGroups.entries.firstOrNull { it.name == v } } ?: LowQualityGroups.PUSH_DOWN
            val trusted = LiveTvDriveHttp.getString(KEY_TRUSTED)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() } ?: TrashReleaseGroups.PREFERRED_LADDER
            val sort = LiveTvDriveHttp.getString(KEY_SORT)?.let { v -> StreamListSort.entries.firstOrNull { it.name == v } } ?: StreamListSort.SOURCE_ORDER
            _state.value = QualitySettingsState(rules, low, trusted, sort)
        }
    }

    fun setRules(rules: DebridStreamPreferences) {
        ensureLoaded()
        _state.value = _state.value.copy(rules = rules)
        LiveTvDriveHttp.putString(KEY_RULES, json.encodeToString(DebridStreamPreferences.serializer(), rules))
    }

    fun setLowQuality(value: LowQualityGroups) {
        ensureLoaded()
        _state.value = _state.value.copy(lowQuality = value)
        LiveTvDriveHttp.putString(KEY_LOW, value.name)
    }

    /** An empty list gets the TRaSH Guides list back. */
    fun setTrustedGroups(groups: List<String>) {
        ensureLoaded()
        val clean = groups.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        _state.value = _state.value.copy(trustedGroups = clean.ifEmpty { TrashReleaseGroups.PREFERRED_LADDER })
        LiveTvDriveHttp.putString(KEY_TRUSTED, clean.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    fun setListSort(value: StreamListSort) {
        ensureLoaded()
        _state.value = _state.value.copy(listSort = value)
        LiveTvDriveHttp.putString(KEY_SORT, value.name)
    }

    /** Every Quality rule back to the recommended setup (the source list order stays). */
    fun reset() {
        ensureLoaded()
        _state.value = QualitySettingsState(listSort = _state.value.listSort)
        LiveTvDriveHttp.putString(KEY_RULES, null)
        LiveTvDriveHttp.putString(KEY_LOW, null)
        LiveTvDriveHttp.putString(KEY_TRUSTED, null)
    }
}
