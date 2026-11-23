package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    /** What's typed in the Address box. */
    val address: String get() = label

    /** The synced value (the TV app's JSON). */
    fun toJson(): String = buildJsonObject {
        put("host", host); put("share", share); put("path", path)
        put("username", username); put("password", password); put("domain", domain)
    }.toString()

    companion object {
        /**
         * Reads an address typed any common way: \\host\share\folder, //host/share/folder,
         * smb://host/share/folder or host/share/folder. Null when there's no shared folder name.
         */
        fun parseAddress(text: String): Triple<String, String, String>? {
            var t = text.trim()
            if (t.startsWith("smb://", ignoreCase = true)) t = t.substring(6)
            val parts = t.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
        }

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

    /**
     * Checks [config] works (connects, logs in, saves and removes a small test file). Null when
     * it does, else why not in plain words. A Full app VPN lets the address through first
     * ([onVpnWait] is called while it reconnects).
     */
    suspend fun test(config: NetworkFolderConfig, onVpnWait: () -> Unit): String?

    // ── Find on network ──

    /** Looks for computers and NAS boxes with file sharing on the home network (about ten seconds). */
    fun search(): Flow<NetworkSearchState>

    /** In Full app VPN mode, lets [host] past the VPN first ([onVpnWait] is called while it reconnects). */
    suspend fun letPastVpn(host: String, onVpnWait: () -> Unit)

    /** [host]'s shared folders (guest when [username] is blank). Throws (see [describe]) when it can't. */
    suspend fun shares(host: String, username: String, password: String): List<SmbShare>

    /** The folders inside [config]'s folder, by name. Throws when it can't. */
    suspend fun folders(config: NetworkFolderConfig): List<String>

    /** Makes folder [name] inside [config]'s folder. Throws when it can't. */
    suspend fun makeFolder(config: NetworkFolderConfig, name: String)
}

/** A computer or NAS found on the home network that has file sharing. */
data class FoundComputer(
    /** Its IP address (what's saved in the folder's address). */
    val host: String,
    /** Its name, when it says one (like BRIAN-PC or DS920); null shows the address only. */
    val name: String? = null,
)

/** What a search has found so far. */
data class NetworkSearchState(
    val found: List<FoundComputer> = emptyList(),
    /** Still looking. */
    val searching: Boolean = true,
    /** A VPN carries this app's connections, so only devices that announce themselves can be found. */
    val vpnInTheWay: Boolean = false,
    /** The phone isn't on a home network (no Wi-Fi or Ethernet address). */
    val noNetwork: Boolean = false,
)

/** A shared folder a computer or NAS offers. */
data class SmbShare(val name: String, val remark: String = "")

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

    /**
     * Saves the folder set up on this phone (null removes it). It goes to the TVs with Live TV
     * sync, like a folder set up on a TV; each TV still picks where its own recordings go.
     */
    fun save(c: NetworkFolderConfig?) {
        LiveTvPreferences.setNetworkFolderJson(c?.toJson() ?: "")
        start()
    }

    /** The set-up screen closed without saving: the VPN goes back to the saved folder's address. */
    fun restoreVpn() {
        runCatching { NetworkLibraryPlatform.configChanged(NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson())) }
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
