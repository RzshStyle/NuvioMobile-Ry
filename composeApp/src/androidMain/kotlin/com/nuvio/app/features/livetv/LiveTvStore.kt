package com.nuvio.app.features.livetv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.iptv.model.PlaylistSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything Live TV keeps per profile. */
@Serializable
data class StoredIptv(
    val sources: List<PlaylistSource> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recentChannels: List<String> = emptyList(),
    val lastChannelId: String? = null,
    val playlistUpdatedAt: Map<String, Long> = emptyMap(),
    val epgUpdatedAt: Map<String, Long> = emptyMap(),
    val hiddenGroups: List<String> = emptyList(),
) {
    override fun toString(): String = "StoredIptv(sources=${sources.size}, favorites=${favorites.size})"
}

/** Stores playlists (including provider passwords) encrypted with an Android Keystore AES key. */
class LiveTvStore(context: Context) {
    private val prefs = context.getSharedPreferences("live_tv_sources", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "kind" }
    private val lock = Any()

    fun read(profileId: Int): StoredIptv = synchronized(lock) {
        val raw = prefs.getString(key(profileId), null) ?: return StoredIptv()
        runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            val plain = cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
            json.decodeFromString(StoredIptv.serializer(), plain)
        }.getOrDefault(StoredIptv())
    }

    fun write(profileId: Int, value: StoredIptv) = synchronized(lock) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(json.encodeToString(StoredIptv.serializer(), value).toByteArray())
        prefs.edit().putString(key(profileId), Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun key(profileId: Int) = "profile.$profileId"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "com.nuvio.app.livetv.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
