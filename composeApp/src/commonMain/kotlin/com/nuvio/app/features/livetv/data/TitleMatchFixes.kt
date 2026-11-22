package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * "Fix match", as on the TV: the title you picked by hand for a recorded show (every recording
 * with that name, now and later) or for one On Demand title. Saved with the Live TV settings in
 * the TV app's format, so picks go both ways with sync.
 */
object TitleMatchFixes {
    /** A picked title. */
    data class Pick(
        val name: String,
        val year: Int?,
        val series: Boolean,
        val poster: String?,
        val backdrop: String?,
        val tmdbId: String?,
        val imdbId: String?,
    )

    /** One search result: a show or movie on TMDB. */
    data class Candidate(
        val tmdbId: Int,
        val series: Boolean,
        val name: String,
        val year: Int?,
        val poster: String?,
        val backdrop: String?,
        val overview: String?,
        val countries: List<String>,
        val votes: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Key (see [recordingKey] / [vodKey]) -> the picked title. */
    val fixes: StateFlow<Map<String, Pick>> = LiveTvPreferences.titleMatchFixes
        .map { decode(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Recordings: one pick per show name. */
    fun recordingKey(title: String): String = "rec:" + title.trim().lowercase().replace(Regex("\\s+"), " ")

    /** On Demand: one pick per title. */
    fun vodKey(uid: String): String = "vod:$uid"

    /** The pick for [key], straight from the saved settings (a pick made a moment ago included). */
    fun get(key: String): Pick? = decode(LiveTvPreferences.titleMatchFixesNow())[key]

    /** [set], carried on in the background (the sheet that asked can close straight away). */
    fun pick(key: String, picked: Candidate?) { scope.launch { set(key, picked) } }

    /** Saves [picked] for [key]; null goes back to automatic. */
    suspend fun set(key: String, picked: Candidate?) {
        val encoded = picked?.let { c ->
            val imdb = runCatching { imdbOf(c) }.getOrNull()
            buildJsonObject {
                put("v", 1)
                put("a", "tmdb")
                put("t", if (c.series) "series" else "movie")
                put("rt", if (c.series) "series" else "movie")
                put("id", imdb ?: "tmdb:${c.tmdbId}")
                put("n", c.name)
                c.poster?.let { put("p", it) }
                c.backdrop?.let { put("b", it) }
                c.overview?.let { put("d", it) }
                c.year?.let { put("r", it.toString()) }
                imdb?.let { put("im", it) }
                put("tm", c.tmdbId)
            }.toString()
        }
        LiveTvPreferences.editTitleMatchFixes { raw ->
            val m = (runCatching { liveTvJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())).toMutableMap()
            if (encoded == null) m.remove(key) else m[key] = JsonPrimitive(encoded)
            JsonObject(m).toString()
        }
    }

    /**
     * Shows and movies on TMDB for [text] ([series]: true = shows only, false = movies only,
     * null = both), best known first. An IMDb id, a TMDB link or "tmdb:123" finds that exact title.
     */
    suspend fun search(text: String, series: Boolean?, year: Int?): List<Candidate> = withContext(Dispatchers.Default) {
        val key = TmdbSettingsRepository.effectiveApiKey()
        val q = text.trim()
        if (key.isBlank() || q.isEmpty()) return@withContext emptyList()
        TMDB_LINK.find(q)?.let { m -> return@withContext listOfNotNull(details(m.groupValues[2].toInt(), m.groupValues[1] == "tv", key)) }
        TMDB_PREFIX.matchEntire(q)?.let { m -> return@withContext listOfNotNull(details(m.groupValues[1].toInt(), series ?: true, key)) }
        if (series != null) DIGITS.matchEntire(q)?.let { return@withContext listOfNotNull(details(it.value.toInt(), series, key)) }
        IMDB_ID.find(q)?.value?.let { imdb ->
            val o = getJson(buildTmdbUrl("find/$imdb", key, mapOf("external_source" to "imdb_id"))) ?: return@withContext emptyList()
            return@withContext parse(o.optJSONArray("tv_results"), true) + parse(o.optJSONArray("movie_results"), false)
        }
        val out = ArrayList<Candidate>()
        val kinds = when (series) { true -> listOf(true); false -> listOf(false); null -> listOf(true, false) }
        for (isSeries in kinds) {
            val query = linkedMapOf("query" to q.encodeURLParameter(), "include_adult" to "false")
            year?.let { query[if (isSeries) "first_air_date_year" else "primary_release_year"] = it.toString() }
            val o = getJson(buildTmdbUrl("search/" + (if (isSeries) "tv" else "movie"), key, query)) ?: continue
            out += parse(o.optJSONArray("results"), isSeries)
        }
        val wanted = norm(q)
        out.sortedWith(compareByDescending<Candidate> { norm(it.name) == wanted }.thenByDescending { it.votes }).take(30)
    }

    private val countryCache = HashMap<String, Any>()
    private object NoPick

    /**
     * For shows with the same name in several countries ("Big Brother" in the UK and the US): when
     * the channel's name says which country it's from and exactly one show with that very name is
     * from there, its poster. Null when it can't tell.
     */
    suspend fun posterByCountry(title: String, channelName: String?): String? {
        val country = countryOf(channelName) ?: return null
        val cacheKey = recordingKey(title) + "|" + country
        countryCache[cacheKey]?.let { return it as? String }
        val wanted = norm(title)
        val same = if (wanted.length < 2) emptyList() else runCatching { search(title, series = true, year = null) }.getOrDefault(emptyList())
            .filter { norm(it.name) == wanted }
        val poster = if (same.size < 2) null else same.filter { country in it.countries }.singleOrNull()?.poster
        countryCache[cacheKey] = poster ?: NoPick
        return poster
    }

    /** The country a channel's name says it's from ("UK: ITV1", "ITV 1 UK HD"), as a country code. */
    fun countryOf(channelName: String?): String? {
        val name = channelName?.uppercase() ?: return null
        for ((tag, code) in COUNTRY_TAGS) {
            if (Regex("""(^|[^A-Z])$tag([^A-Z]|$)""").containsMatchIn(name)) return code
        }
        return null
    }

    private suspend fun imdbOf(c: Candidate): String? {
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) return null
        val o = getJson(buildTmdbUrl((if (c.series) "tv" else "movie") + "/${c.tmdbId}/external_ids", key)) ?: return null
        return o.optString("imdb_id").takeIf { it.startsWith("tt") }
    }

    private suspend fun details(id: Int, series: Boolean, key: String): Candidate? {
        val o = getJson(buildTmdbUrl((if (series) "tv" else "movie") + "/$id", key)) ?: return null
        return candidate(o, series)
    }

    private fun parse(arr: JsonArray?, series: Boolean): List<Candidate> =
        arr?.mapNotNull { (it as? JsonObject)?.let { o -> candidate(o, series) } }.orEmpty()

    private fun candidate(o: JsonObject, series: Boolean): Candidate? {
        val id = o.optInt("id", 0).takeIf { it > 0 } ?: return null
        val name = o.optString(if (series) "name" else "title").ifBlank { o.optString(if (series) "original_name" else "original_title") }
        if (name.isBlank()) return null
        val countries = buildList {
            o.optJSONArray("origin_country")?.forEach { e -> (e as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { add(it) } }
            o.optJSONArray("production_countries")?.forEach { e -> (e as? JsonObject)?.optString("iso_3166_1")?.takeIf { it.isNotBlank() }?.let { add(it) } }
        }.distinct()
        return Candidate(
            tmdbId = id,
            series = series,
            name = name,
            year = o.optString(if (series) "first_air_date" else "release_date").take(4).toIntOrNull(),
            poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" },
            backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w780$it" },
            overview = o.optString("overview").takeIf { it.isNotBlank() },
            countries = countries,
            votes = o.optInt("vote_count", 0),
        )
    }

    private suspend fun getJson(url: String): JsonObject? =
        runCatching { liveTvJson.parseToJsonElement(httpGetText(url)) as? JsonObject }.getOrNull()

    private fun decode(raw: String): Map<String, Pick> {
        if (raw.isBlank()) return emptyMap()
        val o = runCatching { liveTvJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Pick>()
        for ((k, v) in o) {
            val text = (v as? JsonPrimitive)?.content ?: continue
            val m = runCatching { liveTvJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: continue
            val id = m.optString("id")
            out[k] = Pick(
                name = m.optString("n"),
                year = m.optString("r").take(4).toIntOrNull(),
                series = m.optString("t").equals("series", ignoreCase = true),
                poster = m.optString("p").ifBlank { null },
                backdrop = m.optString("b").ifBlank { null },
                tmdbId = m.optString("tm").ifBlank { null } ?: id.removePrefix("tmdb:").takeIf { id.startsWith("tmdb:") },
                imdbId = m.optString("im").ifBlank { null },
            )
        }
        return out
    }

    private fun norm(s: String): String = s.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

    private val IMDB_ID = Regex("""\btt\d{5,10}\b""")
    private val TMDB_LINK = Regex("""themoviedb\.org/(tv|movie)/(\d+)""")
    private val TMDB_PREFIX = Regex("""(?i)tmdb[:\s]*(\d+)""")
    private val DIGITS = Regex("""\d{1,9}""")
    private val COUNTRY_TAGS = listOf(
        "UK" to "GB", "GB" to "GB", "USA" to "US", "US" to "US", "CA" to "CA", "AU" to "AU", "IE" to "IE", "NZ" to "NZ",
    )
}
