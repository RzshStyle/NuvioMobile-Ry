package com.nuvio.app.features.livetv.ondemand

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.livetv.data.VideoPlaying
import com.nuvio.app.features.livetv.data.liveTvJson
import com.nuvio.app.features.livetv.data.optInt
import com.nuvio.app.features.livetv.data.optJSONArray
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * "Hide Non-English Titles": each title's original language, from TMDB (one small request per
 * title: by the provider's TMDB id when it sends one, else by name and year), saved on the phone
 * so it's only ever asked once. Titles in another language are marked in the catalog
 * ([OnDemandStore.saveLanguages]) and lists leave them out from then on. The TV app's way.
 *
 * Two ways in: titles about to be shown are checked first ([check], a short wait), and the rest
 * of the catalog is worked through in the background ([startSweep]), while nothing is playing.
 */
internal object OnDemandLanguages {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Semaphore(AT_ONCE)
    private var sweepJob: Job? = null

    private fun retryBefore() = LiveTvClock.nowMs() - RETRY_MS

    /**
     * Checks the titles of a list about to be shown that have no answer yet, waiting at most
     * [waitMs]. True when some turned out to be in another language (read the list again).
     */
    suspend fun check(items: List<VodItem>, waitMs: Long): Boolean = withContext(Dispatchers.Default) {
        if (items.isEmpty()) return@withContext false
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return@withContext false
        val byKey = items.associateBy { VodTitles.languageKey(it) }
        val known = runCatching { OnDemandStore.knownLanguages(byKey.keys, retryBefore()) }.getOrDefault(byKey.keys)
        val todo = byKey.filterKeys { it !in known }
        if (todo.isEmpty()) return@withContext false
        val found = HashMap<String, String>()
        val lock = kotlinx.atomicfu.locks.SynchronizedObject()
        withTimeoutOrNull(waitMs) {
            coroutineScope {
                todo.map { (k, item) -> async { lookup(item, apiKey)?.let { l -> kotlinx.atomicfu.locks.synchronized(lock) { found[k] = l } } } }.awaitAll()
            }
        }
        // Whatever came back in time is saved (the rest is left to the background check).
        val copy = kotlinx.atomicfu.locks.synchronized(lock) { HashMap(found) }
        runCatching { OnDemandStore.saveLanguages(copy) }.getOrDefault(0) > 0
    }

    /** Works through every title not checked yet, a batch at a time, while nothing plays. [onMarked] when some were set aside. */
    fun startSweep(enabled: () -> Boolean, onMarked: () -> Unit) {
        if (sweepJob?.isActive == true) return
        sweepJob = scope.launch {
            var marked = 0
            var firstBatch = true
            try {
                while (isActive && enabled()) {
                    val apiKey = TmdbSettingsRepository.effectiveApiKey()
                    if (apiKey.isBlank()) break
                    awaitIdle()
                    val batch = runCatching { OnDemandStore.needingLanguage(BATCH, retryBefore()) }.getOrDefault(emptyList())
                    if (batch.isEmpty()) break
                    for (part in batch.chunked(SAVE_EVERY)) {
                        if (!enabled()) break
                        awaitIdle()
                        val found = coroutineScope {
                            part.map { item -> async { lookup(item, apiKey)?.let { VodTitles.languageKey(item) to it } } }.awaitAll()
                        }.filterNotNull().toMap()
                        // No answers at all (no connection): left for next time.
                        if (found.isEmpty()) return@launch
                        marked += runCatching { OnDemandStore.saveLanguages(found) }.getOrDefault(0)
                    }
                    // The first batch holds the titles set aside on a guess: lists reload right
                    // away, so an English one among them is back quickly.
                    if (firstBatch && marked > 0) { onMarked(); marked = 0 }
                    firstBatch = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (marked > 0) onMarked()
            }
        }
    }

    fun stopSweep() { sweepJob?.cancel() }

    /** The language a poster lookup already found (no extra request): saved for "Hide Non-English Titles". */
    fun remember(item: VodItem, lang: String?) {
        if (lang == null) return
        scope.launch { runCatching { OnDemandStore.saveLanguages(mapOf(VodTitles.languageKey(item) to lang)) } }
    }

    private suspend fun awaitIdle() {
        while (VideoPlaying.open.value > 0) VideoPlaying.open.first { it == 0 }
        delay(50)
    }

    /** The title's original language ("en", "fr"…), "" when TMDB doesn't know it, null when TMDB couldn't be reached. */
    private suspend fun lookup(item: VodItem, apiKey: String): String? = gate.withPermit {
        val series = item.kind == VodKind.SERIES
        val path = if (series) "tv" else "movie"
        try {
            item.tmdbId?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let { id ->
                val o = parse(httpGetText(buildTmdbUrl("$path/$id", apiKey)))
                if (o != null && o.optInt("id") == id) return@withPermit o.optString("original_language")
                // A wrong id from the provider: try the name instead.
            }
            val clean = VodTitles.displayTitle(item.name).trim()
            if (clean.length < 2) return@withPermit ""
            val titleKey = if (series) "name" else "title"
            for (title in listOfNotNull(clean, VodTitles.withoutTrailingYear(clean)).distinct()) {
                val query = linkedMapOf("query" to title.encodeURLParameter(), "include_adult" to "false")
                val results = parse(httpGetText(buildTmdbUrl("search/$path", apiKey, query)))?.optJSONArray("results")?.toList().orEmpty()
                val want = VodTitles.normalize(title)
                val best = results.mapNotNull { it as? JsonObject }.firstOrNull { r ->
                    val names = listOf(r.optString(titleKey), r.optString("original_$titleKey")).filter { it.isNotBlank() }.map { VodTitles.normalize(it) }
                    val y = VodTitles.yearOf(r.optString(if (series) "first_air_date" else "release_date"))
                    want in names && (item.year == null || y == null || kotlin.math.abs(y - item.year) <= 1)
                }
                if (best != null) return@withPermit best.optString("original_language")
            }
            ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(text: String): JsonObject? = runCatching { liveTvJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private const val AT_ONCE = 6
    private const val BATCH = 600
    private const val SAVE_EVERY = 30
    /** A title TMDB didn't know is asked about again after a month. */
    private const val RETRY_MS = 30L * 24 * 60 * 60 * 1000
}
