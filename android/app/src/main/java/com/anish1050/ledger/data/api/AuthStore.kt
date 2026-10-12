package com.anish1050.ledger.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

// Keystore-backed storage for the app JWT, the device id and the backup key.
class AuthStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "auth", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** Raw AES key for row encryption, derived once from the passphrase. */
    var backupKey: ByteArray?
        get() = prefs.getString(KEY_BACKUP, null)?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
        set(value) = prefs.edit().putString(KEY_BACKUP, value?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }).apply()

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    val isSignedIn: Boolean get() = accessToken != null

    fun clear() = prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).remove(KEY_BACKUP).apply()

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_USER = "user_id"
        const val KEY_BACKUP = "backup_key"
        const val KEY_DEVICE = "device_id"
    }
}
