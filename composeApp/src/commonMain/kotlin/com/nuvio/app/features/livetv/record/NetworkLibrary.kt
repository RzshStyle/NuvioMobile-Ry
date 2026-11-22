package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

/**
 * A shared folder on a computer or NAS (Windows file sharing, "SMB"), set up on a TV for its
 * recordings. Same fields and JSON as the TV app's; synced (Live TV sync, Logins part) so the
 * phone can watch what the TVs record there without typing the address again.
 */
data class NetworkFolderConfig(
    val host: String,
    val share: String,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
) {
    /** How it's shown: \\192.168.1.20\Recordings\TV */
    val label: String get() = "\\\\" + host + "\\" + share + (if (path.isEmpty()) "" else "\\" + path.replace('/', '\\'))

    companion object {
        /** The synced value: null for none (never set up, or removed: an empty value). */
        fun fromJson(text: String?): NetworkFolderConfig? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val o = Json.parseToJsonElement(text).jsonObject
                NetworkFolderConfig(
                    host = o.optString("host"), share = o.optString("share"), path = o.optString("path"),
                    username = o.optString("username"), password = o.optString("password"), domain = o.optString("domain"),
                ).takeIf { it.host.isNotBlank() && it.share.isNotBlank() }
            }.getOrNull()
        }
    }
}

/** What the phone needs from the device to reach the network folder (Android only; iOS has none yet). */
internal expect object NetworkLibraryPlatform {
    /** The device can open network folders. */
    val available: Boolean

    /**
     * Every recording on the folder: its id and its info.json text. Throws when the folder
     * can't be reached (away from home, the computer asleep, a wrong password).
     */
    suspend fun list(config: NetworkFolderConfig): Map<String, String>

    /** A link the player opens for recording [id] (an HLS playlist served from this phone). */
    fun playUrl(config: NetworkFolderConfig, id: String): String

    /** Deletes recording [id] from the folder: its show file(s) and its details. Throws when it can't. */
    suspend fun delete(config: NetworkFolderConfig, id: String)

    /** The folder set up changed (or went): drop the old connection; the VPN lets the new address through. */
    fun configChanged(config: NetworkFolderConfig?)

    /** A problem reaching the folder, in plain words. */
    fun describe(e: Throwable, config: NetworkFolderConfig): String
}

/**
 * The TVs' recordings on the network folder, shown in the phone's Recordings (watch only: the
 * phone doesn't record there). Read when Recordings opens and every few minutes while it's
 * open; the last list is kept, so away from home the recordings still show, marked as
 * available at home.
 */
internal object NetworkLibrary {
    /** A network recording's [LiveTvRecording.dir] starts with this, then its id. */
    const val DIR_PREFIX = "net:"
    /** The TV app's folder for recordings' details on the network folder. */
    const val META_DIR = ".nuvio/recordings"
    private const val CACHE = "network_library.json"

    data class State(
        /** Null: no network folder is set up (on any TV). */
        val folderLabel: String? = null,
        val recordings: List<LiveTvRecording> = emptyList(),
        /** Null: not checked yet; false: couldn't be reached last time. */
        val reachable: Boolean? = null,
        val problem: String? = null,
        val checking: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var config: NetworkFolderConfig? = null
    @Volatile private var started = false
    @Volatile private var lastRefreshAt = 0L

    fun isNetwork(rec: LiveTvRecording): Boolean = rec.dir.startsWith(DIR_PREFIX)
    private fun idOf(rec: LiveTvRecording): String = rec.dir.removePrefix(DIR_PREFIX)

    /** Follows the synced folder (once). */
    fun start() {
        if (started || !NetworkLibraryPlatform.available) return
        started = true
        scope.launch {
            LiveTvPreferences.networkFolderJson.collect { json ->
                val c = NetworkFolderConfig.fromJson(json)
                if (c == config && _state.value.folderLabel == c?.label) return@collect
                config = c
                runCatching { NetworkLibraryPlatform.configChanged(c) }
                if (c == null) {
                    _state.value = State()
                    runCatching { LiveTvFiles.delete(LiveTvDirs.STATE, CACHE) }
                } else {
                    _state.value = State(folderLabel = c.label, recordings = readCache(c.label))
                    refresh(force = true)
                }
            }
        }
    }

    /** Reads the folder again (Recordings opened, or every few minutes while it's open). */
    fun refresh(force: Boolean = false) {
        val c = config ?: return
        val now = LiveTvClock.nowMs()
        if (!force && now - lastRefreshAt < 20_000L) return
        lastRefreshAt = now
        scope.launch {
            refreshLock.withLock {
                if (config != c) return@withLock
                _state.update { it.copy(checking = true) }
                try {
                    val found = NetworkLibraryPlatform.list(c)
                    val list = found.mapNotNull { (id, text) -> parse(id, text) }.sortedByDescending { it.startedAtMs }
                    if (config != c) return@withLock
                    _state.value = State(folderLabel = c.label, recordings = list, reachable = true)
                    writeCache(c.label, found)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (config != c) return@withLock
                    _state.update { it.copy(reachable = false, problem = runCatching { NetworkLibraryPlatform.describe(e, c) }.getOrNull(), checking = false) }
                }
            }
        }
    }

    /** The link the player opens. */
    fun playUrl(rec: LiveTvRecording): String {
        val c = config ?: return ""
        return NetworkLibraryPlatform.playUrl(c, idOf(rec))
    }

    /** Deletes a finished recording from the network folder (so from every TV). Null when it worked, else why not. */
    suspend fun delete(rec: LiveTvRecording): String? {
        val c = config ?: return "No network folder is set up"
        if (rec.isRecording) return "It's still recording on a TV. Delete it once it has finished"
        return try {
            NetworkLibraryPlatform.delete(c, idOf(rec))
            _state.update { s -> s.copy(recordings = s.recordings.filter { it.id != rec.id }) }
            refresh(force = true)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching { NetworkLibraryPlatform.describe(e, c) }.getOrDefault("The network folder couldn't be reached")
        }
    }

    /** [delete] in the background (the sheet asking closes straight away), saying how it went. */
    fun deleteAndTell(rec: LiveTvRecording) {
        scope.launch {
            val problem = delete(rec)
            com.nuvio.app.core.ui.NuvioToastController.show(problem ?: "Deleted from the network folder")
        }
    }

    private fun parse(id: String, text: String): LiveTvRecording? = runCatching {
        LiveTvRecording.fromJson(Json.parseToJsonElement(text).jsonObject, DIR_PREFIX + id)
            ?.copy(id = id, fileName = "index.m3u8")
    }.getOrNull()

    // ---- the last list read, for when the folder can't be reached

    private fun readCache(label: String): List<LiveTvRecording> = runCatching {
        val o = Json.parseToJsonElement(LiveTvFiles.readSmall(LiveTvDirs.STATE, CACHE) ?: return emptyList()).jsonObject
        if (o.optString("folder") != label) return emptyList()
        o["items"]!!.jsonArray.mapNotNull { e ->
            val item = e.jsonObject
            parse(item.optString("id"), item.optString("info"))
        }.sortedByDescending { it.startedAtMs }
    }.getOrDefault(emptyList())

    private fun writeCache(label: String, found: Map<String, String>) {
        runCatching {
            val o: JsonObject = buildJsonObject {
                put("folder", label)
                put("items", buildJsonArray {
                    found.forEach { (id, info) -> add(buildJsonObject { put("id", id); put("info", info) }) }
                })
            }
            LiveTvFiles.writeSmall(LiveTvDirs.STATE, CACHE, o.toString())
        }
    }
}
