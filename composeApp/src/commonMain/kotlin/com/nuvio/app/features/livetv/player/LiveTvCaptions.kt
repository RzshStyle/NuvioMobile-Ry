package com.nuvio.app.features.livetv.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Closed captions on live channels (as on TV): US channels carry them hidden inside the video,
 * the old kind (CEA-608, tracks CC1–CC4) and the digital kind (CEA-708). Streams rarely say which
 * they carry, so the player looks for all of them and the caption data is watched as it goes past
 * to learn which really have text. The Subtitles menu then only offers those.
 */
object LiveTvCaptions {
    const val CC1 = "cc1"
    const val CC2 = "cc2"
    const val CC3 = "cc3"
    const val CC4 = "cc4"
    const val DIGITAL = "708"

    /** Track ids of the caption tracks the player is told to look for. */
    val ids: Set<String> = setOf(CC1, CC2, CC3, CC4, DIGITAL)

    private val _found = MutableStateFlow<Set<String>>(emptySet())
    /** Caption types seen carrying text on the channel playing. */
    val found: StateFlow<Set<String>> = _found.asStateFlow()

    fun reset() {
        _found.value = emptySet()
    }

    fun mark(kind: String) {
        if (kind !in _found.value) _found.update { it + kind }
    }

    fun label(kind: String): String = when (kind) {
        CC1 -> "Closed captions"
        CC2 -> "Closed captions (CC2)"
        CC3 -> "Closed captions (CC3)"
        CC4 -> "Closed captions (CC4)"
        else -> "Closed captions (digital)"
    }

    /**
     * The caption type to offer first: CC1, or the digital copy when only that one has text.
     * Null when the channel has no caption tracks.
     */
    fun primary(available: Collection<String>): String? {
        if (available.isEmpty()) return null
        val found = _found.value
        return available.firstOrNull { it == CC1 && (found.isEmpty() || CC1 in found) }
            ?: available.firstOrNull { it == DIGITAL && DIGITAL in found }
            ?: available.firstOrNull { it in found }
            ?: available.firstOrNull { it == CC1 }
            ?: available.first()
    }
}
