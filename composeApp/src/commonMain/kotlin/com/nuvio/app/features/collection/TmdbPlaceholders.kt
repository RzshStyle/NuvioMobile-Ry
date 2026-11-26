package com.nuvio.app.features.collection

import com.nuvio.app.features.livetv.sports.SportsPlatform
import com.nuvio.app.features.watchprogress.CurrentDateProvider

/**
 * Placeholders allowed in TMDB filter text: {today}, {d-N} / {d+N} (days from today) and
 * {region}. Plain string handling on purpose: Android's ICU regex rejects an unescaped "}".
 */
internal object TmdbPlaceholders {
    fun region(): String =
        runCatching { SportsPlatform.countryCode() }.getOrNull()
            ?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: "GB"

    fun fill(value: String, region: String = region(), todayIso: String = CurrentDateProvider.todayIsoDate()): String {
        if (!value.contains('{')) return value
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val open = value.indexOf('{', i)
            if (open < 0) { out.append(value, i, value.length); break }
            val close = value.indexOf('}', open + 1)
            if (close < 0) { out.append(value, i, value.length); break }
            out.append(value, i, open)
            val token = value.substring(open + 1, close).trim().lowercase()
            out.append(resolve(token, todayIso, region) ?: value.substring(open, close + 1))
            i = close + 1
        }
        return out.toString()
    }

    private fun resolve(token: String, todayIso: String, region: String): String? = when {
        token == "today" -> todayIso
        token == "region" -> region
        token.startsWith("d-") -> token.drop(2).toLongOrNull()?.let { shift(todayIso, -it) }
        token.startsWith("d+") -> token.drop(2).toLongOrNull()?.let { shift(todayIso, it) }
        else -> null
    }

    /** "2026-11-26" moved by [days], using days-from-civil arithmetic (no date library needed). */
    internal fun shift(iso: String, days: Long): String? {
        val parts = iso.take(10).split('-')
        if (parts.size != 3) return null
        val y = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val d = parts[2].toLongOrNull() ?: return null
        val (ny, nm, nd) = civilFromDays(daysFromCivil(y, m, d) + days)
        return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}-${nd.toString().padStart(2, '0')}"
    }

    private fun daysFromCivil(y0: Long, m: Long, d: Long): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civilFromDays(z0: Long): Triple<Long, Long, Long> {
        val z = z0 + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple(if (m <= 2) y + 1 else y, m, d)
    }
}
