package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.livetv.model.CatchupInfo
import com.nuvio.app.features.livetv.model.DrmInfo
import com.nuvio.app.features.livetv.model.LiveChannel

/**
 * The channel list as it was last made, saved so the app can show it the moment it opens
 * (like the TV app) instead of reading every playlist again. Each playlist's part is labelled
 * with what it was read from (see LiveTvRepository.playlistSegment): at start only playlists
 * whose file or settings changed since are read again. Provider portals (Stalker) aren't saved:
 * they're read from their own saved copy right after, as before.
 *
 * Plain text, one line per entry, fields split by tabs:
 * - `V <version>`
 * - `S <playlist id> <segment>`: what that playlist's channels were read from
 * - `E <playlist id> <guide link>…`: guide links named inside that playlist
 * - `C <fields…>`: one channel
 * A file of another version, or one that can't be read, is simply not used.
 */
internal object SavedChannels {
    const val FILE = "channels_saved.txt"
    private const val VERSION = "1"
    /** Between a header's name and value, and between headers. */
    private const val KV = '\u001F'
    private const val ITEM = '\u001E'
    private const val NULL = "\\0"

    class Saved(
        val segments: Map<String, String>,
        val epgUrls: Map<String, List<String>>,
        val channels: List<LiveChannel>,
    )

    private fun esc(v: String?): String {
        if (v == null) return NULL
        if (v.none { it == '\\' || it == '\t' || it == '\n' || it == '\r' }) return v
        val sb = StringBuilder(v.length + 8)
        for (ch in v) when (ch) {
            '\\' -> sb.append("\\\\")
            '\t' -> sb.append("\\t")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    private fun unesc(v: String): String? {
        if (v == NULL) return null
        if ('\\' !in v) return v
        val sb = StringBuilder(v.length)
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(v[i + 1])
                }
                i += 2
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }

    private fun encodeMap(m: Map<String, String>): String =
        if (m.isEmpty()) "" else m.entries.joinToString(ITEM.toString()) { it.key + KV + it.value }

    private fun decodeMap(v: String): Map<String, String> {
        if (v.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        v.split(ITEM).forEach { p -> val i = p.indexOf(KV); if (i > 0) out[p.substring(0, i)] = p.substring(i + 1) }
        return out
    }

    /** The lines to write, given to [write] one piece at a time. */
    fun write(segments: Map<String, String>, epgUrls: Map<String, List<String>>, channels: List<LiveChannel>, write: (String) -> Unit) {
        write("V\t$VERSION\n")
        segments.forEach { (id, seg) -> write("S\t${esc(id)}\t${esc(seg)}\n") }
        epgUrls.forEach { (id, urls) -> if (id in segments && urls.isNotEmpty()) write("E\t${esc(id)}\t" + urls.joinToString("\t") { esc(it)!! } + "\n") }
        val sb = StringBuilder(64 * 1024)
        for (c in channels) {
            sb.append('C')
            for (f in fieldsOf(c)) sb.append('\t').append(esc(f))
            sb.append('\n')
            if (sb.length > 48 * 1024) { write(sb.toString()); sb.setLength(0) }
        }
        if (sb.isNotEmpty()) write(sb.toString())
    }

    private fun fieldsOf(c: LiveChannel): List<String?> = listOf(
        c.key, c.sourceId, c.sourceName, c.name, c.tvgId, c.tvgName, c.logo, c.groupId, c.group,
        c.number.toString(), c.url, encodeMap(c.headers),
        c.catchup?.let { listOf(it.type, it.source.orEmpty(), it.days.toString(), it.correctionHours.toString(), if (it.source == null) "0" else "1").joinToString(ITEM.toString()) },
        c.drm?.let { listOf(it.scheme, it.license, encodeMap(it.licenseHeaders).replace(ITEM, '\u001D').replace(KV, '\u001C'), it.manifestType ?: NULL).joinToString(ITEM.toString()) },
        c.tvgShiftMinutes.toString(), if (c.autoNumber) "1" else "0",
    )

    private const val FIELDS = 16

    /** Reads a saved list; null when it can't be used (another version, broken, empty). */
    fun read(lines: Sequence<String>): Saved? {
        val segments = LinkedHashMap<String, String>()
        val epg = HashMap<String, List<String>>()
        val out = ArrayList<LiveChannel>()
        // Text shared by many channels (playlist, group, header sets) kept once in memory.
        val shared = HashMap<String, String>()
        fun one(v: String?): String? = v?.let { shared.getOrPut(it) { it } }
        val headerSets = HashMap<String, Map<String, String>>()
        var versionOk = false
        for (line in lines) {
            if (line.isEmpty()) continue
            val f = line.split('\t')
            when (f[0]) {
                "V" -> versionOk = f.getOrNull(1) == VERSION
                "S" -> if (f.size >= 3) { val id = unesc(f[1]); val seg = unesc(f[2]); if (id != null && seg != null) segments[id] = seg }
                "E" -> if (f.size >= 2) unesc(f[1])?.let { id -> epg[id] = f.drop(2).mapNotNull(::unesc) }
                "C" -> {
                    if (!versionOk) return null
                    if (f.size != FIELDS + 1) return null
                    val v = f.drop(1).map(::unesc)
                    val number = v[9]?.toIntOrNull() ?: return null
                    val headersRaw = v[11].orEmpty()
                    val headers = headerSets.getOrPut(headersRaw) { decodeMap(headersRaw) }
                    val catchup = v[12]?.split(ITEM)?.takeIf { it.size >= 5 }?.let {
                        CatchupInfo(it[0], if (it[4] == "1") it[1] else null, it[2].toIntOrNull() ?: 0, it[3].toDoubleOrNull() ?: 0.0)
                    }
                    val drm = v[13]?.split(ITEM)?.takeIf { it.size >= 4 }?.let {
                        DrmInfo(it[0], it[1], decodeMap(it[2].replace('\u001D', ITEM).replace('\u001C', KV)), it[3].takeIf { t -> t != NULL })
                    }
                    out += LiveChannel(
                        key = v[0] ?: return null,
                        sourceId = one(v[1]) ?: return null,
                        sourceName = one(v[2]).orEmpty(),
                        name = v[3].orEmpty(),
                        tvgId = v[4], tvgName = v[5], logo = v[6],
                        groupId = one(v[7]).orEmpty(),
                        group = one(v[8]).orEmpty(),
                        number = number,
                        url = v[10].orEmpty(),
                        headers = headers,
                        catchup = catchup,
                        drm = drm,
                        tvgShiftMinutes = v[14]?.toIntOrNull() ?: 0,
                        autoNumber = v[15] == "1",
                    )
                }
                else -> return null
            }
        }
        if (!versionOk || out.isEmpty()) return null
        return Saved(segments, epg, out)
    }
}
