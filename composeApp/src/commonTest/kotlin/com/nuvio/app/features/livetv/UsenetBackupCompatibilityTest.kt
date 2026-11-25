package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.data.LiveTvBackup
import com.nuvio.app.features.livetv.data.LiveTvPreferencesStore
import com.nuvio.app.features.livetv.data.PrefValue
import com.nuvio.app.features.livetv.data.exportBackupFileText
import com.nuvio.app.features.livetv.data.exportForSync
import com.nuvio.app.features.livetv.data.importBackupFile
import com.nuvio.app.features.livetv.data.importFromSync
import com.nuvio.app.features.livetv.data.parseBackupFile
import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsenetBackupCompatibilityTest {
    private val key = "usenet_sources_v1_p3"
    private val config = """{ "providers":[{"id":"provider-1","name":"Synthetic provider","host":"news.example.test","password":"synthetic-provider-secret"}],"indexers":[{"id":"indexer-1","name":"Synthetic indexer","apiUrl":"https://indexer.example.test","apiKey":"synthetic-indexer-secret"}],"future":{"preserve":true} }"""

    private fun entry(raw: String) = buildJsonObject { put("t", "str"); put("v", raw) }
    private fun data(raw: String = config, name: String = key) = buildJsonObject { put(name, entry(raw)) }
    private fun store(save: (Map<String, PrefValue>) -> Unit = {}) =
        object : LiveTvPreferencesStore() {
            init { installPersistence(load = { null }, save = save) }
        }

    @Test fun usenetOnlyFileIsUsableAndPreservesOriginalTypedPayload() = runTest {
        val incoming = data()
        assertTrue(LiveTvBackup.syncHasSources(incoming))
        assertTrue(parseBackupFile(incoming.toString()).getOrThrow().hasSources)
        val phone = store()
        importBackupFile(incoming.toString(), phone) {}.getOrThrow()
        assertEquals(incoming[key], phone.exportForSync()[key])
        val exported = exportBackupFileText(phone, deviceId = "synthetic-phone", deviceName = "Phone", nowMs = 1L)
        assertEquals(incoming, parseBackupFile(exported).getOrThrow().data)
        assertTrue(LiveTvBackup.syncHasSources(data("""{"providers":[{"host":"news.example.test"}]}""")))
        assertTrue(LiveTvBackup.syncHasSources(data("""{"indexers":[{"apiUrl":"https://indexer.example.test"}]}""")))
        assertFalse(LiveTvBackup.syncHasSources(data("{}")))
    }

    @Test fun unsupportedSourcesStayOutOfCloudButRemoteCopyAndDeviceOptionsSurvive() {
        val device = entry("""{"transparent_letterbox":true}""")
        val incoming = JsonObject(data() + ("device_settings_v1" to device))
        assertEquals(SyncPart.LOOK, SyncParts.partOf(key))
        assertEquals(SyncPart.LOOK, SyncParts.partOf("device_settings_v1"))
        assertEquals(JsonObject(emptyMap()), SyncParts.strip(incoming))
        assertEquals(incoming, SyncParts.keep(JsonObject(emptyMap()), incoming))
        val phone = store()
        phone.importFromSync(incoming)
        assertEquals(incoming, phone.exportForSync())
    }

    @Test fun malformedReservedFamilyIsRejectedBeforeReplacingExistingSettings() {
        val phone = store()
        phone.importFromSync(data())
        val before = phone.exportForSync()
        val malformed = listOf(
            data(name = "usenet_sources_v1_p0"), data(name = "usenet_sources_v1"),
            data(name = "usenet_sources_v1bad"), data(name = "usenet_sources_v1_p01"),
            data(name = "usenet_sources_v1_p999999999999999999999"),
            data("[]"), data("""{"providers":"bad"}"""), data("""{"indexers":[1]}"""),
            data("synthetic-secret-not-json"),
            buildJsonObject { put(key, buildJsonObject { put("t", "b"); put("v", true) }) },
            buildJsonObject { put(key, buildJsonObject { put("t", "str"); put("v", JsonPrimitive(1)) }) },
        )
        malformed.forEach {
            assertTrue(parseBackupFile(it.toString()).isFailure)
            assertFailsWith<IllegalArgumentException> { phone.importFromSync(it) }
            assertEquals(before, phone.exportForSync())
        }
    }

    @Test fun failedPersistenceLeavesLastSavedCredentialsExportable() {
        var fail = false
        val phone = store { if (fail) error("Synthetic protected write failure") }
        phone.importFromSync(data())
        val before = phone.exportForSync()
        fail = true
        assertFailsWith<IllegalStateException> { phone.importFromSync(data("{}")) }
        assertEquals(before, phone.exportForSync())
    }
}
