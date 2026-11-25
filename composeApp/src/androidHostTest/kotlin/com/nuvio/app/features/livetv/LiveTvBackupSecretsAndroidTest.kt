package com.nuvio.vpn.android

import com.nuvio.app.features.livetv.data.LiveTvBackupSecretsAndroid
import com.nuvio.app.features.livetv.data.PrefValue
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test fun unreadableOrSwappedProtectedPreferencesFailWithoutPlaintextFallback() {
        val sealed = LiveTvBackupSecretsAndroid.seal(profile, original) { VpnBackup.sealCache(it, key) }
        assertFailsWith<IllegalStateException> {
            LiveTvBackupSecretsAndroid.open("usenet_sources_v1_p4", sealed) { VpnBackup.openCache(it, key) }
        }
        assertFailsWith<IllegalStateException> {
            LiveTvBackupSecretsAndroid.open(profile, sealed) { error("Synthetic unreadable key") }
        }
        assertFailsWith<IllegalStateException> {
            LiveTvBackupSecretsAndroid.open(profile, sealed.replace("\"iv\":", "\"missingIv\":")) { VpnBackup.openCache(it, key) }
        }
    }
}
