package com.nuvio.vpn.android

import com.nuvio.app.features.livetv.data.LiveTvBackupSecretsAndroid
import com.nuvio.app.features.livetv.data.PrefValue
import com.nuvio.app.features.livetv.LiveTvPreferencesAndroid
import com.nuvio.app.features.livetv.data.LiveTvPreferencesStore
import com.nuvio.app.features.livetv.data.exportForSync
import android.content.Context
import android.content.SharedPreferences
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/** Uses an injected synthetic AES key: real device Keystore availability remains untested. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiveTvBackupSecretsAndroidTest {
    private val key = SecretKeySpec(ByteArray(32), "AES")
    private val profile = "usenet_sources_v1_p3"
    private val original = PrefValue.Str("""{ "providers":[{"password":"synthetic-provider-secret"}],"indexers":[{"apiKey":"synthetic-indexer-secret"}],"future":[1,2] }""")

    @Test fun preferencesKeepOnlyCiphertextAndReopenTheExactPortableString() {
        val sealed = LiveTvBackupSecretsAndroid.seal(profile, original) { VpnBackup.sealCache(it, key) }
        assertFalse(sealed.contains("synthetic-provider-secret"))
        assertFalse(sealed.contains("synthetic-indexer-secret"))
        assertEquals(sealed, LiveTvBackupSecretsAndroid.seal(profile, original, sealed) {
            error("Unchanged source settings must reuse their ciphertext")
        })
        assertEquals(original, LiveTvBackupSecretsAndroid.open(profile, sealed) { VpnBackup.openCache(it, key) })
        assertEquals(original, LiveTvBackupSecretsAndroid.open(profile, original.value))
    }

    @Test fun unreadableOrSwappedProtectedPreferencesRetainOnlyTheOriginalCiphertext() {
        val sealed = LiveTvBackupSecretsAndroid.seal(profile, original) { VpnBackup.sealCache(it, key) }
        assertEquals(PrefValue.UnavailableBackupSecret(sealed),
            LiveTvBackupSecretsAndroid.open("usenet_sources_v1_p4", sealed) { VpnBackup.openCache(it, key) })
        assertEquals(PrefValue.UnavailableBackupSecret(sealed),
            LiveTvBackupSecretsAndroid.open(profile, sealed) { error("Synthetic unreadable key") })
        val damaged = sealed.replace("\"iv\":", "\"missingIv\":")
        assertEquals(PrefValue.UnavailableBackupSecret(damaged),
            LiveTvBackupSecretsAndroid.open(profile, damaged) { VpnBackup.openCache(it, key) })
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun unavailableSecretsDoNotBlockLoadingOrOrdinaryIptvWritesAndCannotBeExported() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("synthetic-unavailable-backup", Context.MODE_PRIVATE)
        val damaged = "nuvio.backup.local.v1:synthetic-unreadable-ciphertext"
        val playlists = """[{"id":"existing","name":"Local","url":"https://example.test/list.m3u"}]"""
        preferences.edit().clear().putString(profile, damaged).putString("playlists", playlists).commit()
        val read = LiveTvPreferencesAndroid::class.java.getDeclaredMethod("read", SharedPreferences::class.java).apply { isAccessible = true }
        val write = LiveTvPreferencesAndroid::class.java.getDeclaredMethod("write", SharedPreferences::class.java, Map::class.java).apply { isAccessible = true }
        val phone = object : LiveTvPreferencesStore() {
            init {
                installPersistence(
                    load = { read.invoke(LiveTvPreferencesAndroid, preferences) as Map<String, PrefValue> },
                    save = { write.invoke(LiveTvPreferencesAndroid, preferences, it); Unit },
                )
            }
        }
        assertEquals("existing", phone.currentPlaylists().single().id)
        phone.recordWatched("synthetic-channel")
        phone.edit { it.remove(profile) }
        assertEquals(damaged, preferences.getString(profile, null))
        assertEquals(PrefValue.UnavailableBackupSecret(damaged), phone.rawData.value[profile])
        assertEquals("existing", phone.currentPlaylists().single().id)
        assertEquals("Saved TV source settings could not be opened",
            assertFailsWith<IllegalStateException> { phone.exportForSync() }.message)
        assertEquals("UnavailableBackupSecret", phone.rawData.value[profile].toString())
    }
}
