package com.nuvio.app.features.livetv.data

import com.nuvio.vpn.android.VpnBackup
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Local encryption only. The original typed string is what a portable backup exports. */
internal object LiveTvBackupSecretsAndroid {
    private const val PREFIX = "nuvio.backup.local.v1:"

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    fun seal(key: String, value: PrefValue, protect: (String) -> String = VpnBackup::sealCache): String {
        require(value is PrefValue.Str) { "Invalid Usenet backup value" }
        val data = buildJsonObject {
            put(key, buildJsonObject { put("t", "str"); put("v", value.value) })
        }
        UsenetBackupCompatibility.validate(data)
        return PREFIX + protect(data.toString())
    }

    fun open(key: String, stored: String, unprotect: (String) -> String = VpnBackup::openCache): PrefValue.Str {
        if (!isSealed(stored)) return PrefValue.Str(stored) // Migrated by the preference reader.
        return try {
            val data = parseObject(unprotect(stored.removePrefix(PREFIX)))
            UsenetBackupCompatibility.validate(data)
            val entry = data[key] as? JsonObject ?: error("Missing protected entry")
            PrefValue.Str(entry.optString("v"))
        } catch (_: Exception) {
            // No empty fallback: the next backup must not replace unreadable credentials.
            throw IllegalStateException("Saved TV source settings could not be opened")
        }
    }
}
