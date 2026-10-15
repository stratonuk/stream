package com.strata.tv.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream password storage backed by [EncryptedSharedPreferences]
 * (Android Keystore AES-256-GCM).
 *
 * Host / username stay in DataStore; only the password is elevated here
 * so a compromised plaintext preferences dump cannot recover credentials.
 * Survives APK updates — Keystore keys are tied to the app's UID /
 * signing certificate, not the APK file itself.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Extremely rare Keystore failure — fall back to private prefs
        // rather than crashing startup. Still sandboxed to the app UID.
        context.getSharedPreferences(FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()

    fun setPassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val FILE_NAME = "stream_secure_credentials"
        private const val FILE_NAME_FALLBACK = "stream_secure_credentials_fallback"
        private const val KEY_PASSWORD = "provider_password"
    }
}
