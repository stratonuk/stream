package com.strata.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("strata_settings")

/**
 * Persistent app settings backed by [androidx.datastore.preferences].
 *
 * Credentials, provider host, and sync preferences live here — never in
 * source.  The repository exposes [settings] as a hot [Flow] so the
 * Settings screen and SyncWorker react to changes immediately.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val secureCredentials: SecureCredentialStore,
) {

    private object Keys {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
        val CONNECTION_NAME = stringPreferencesKey("connection_name")
        val PROVIDER_HOST = stringPreferencesKey("provider_host")
        val PROVIDER_USERNAME = stringPreferencesKey("provider_username")
        /** Legacy plaintext password — migrated once into [SecureCredentialStore]. */
        val PROVIDER_PASSWORD = stringPreferencesKey("provider_password")
        val PROVIDER_CUSTOM_M3U = stringPreferencesKey("provider_custom_m3u")
        val PROVIDER_USE_FILTERED = booleanPreferencesKey("provider_use_filtered_playlist")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val LAST_SYNC_EPOCH_DAY = longPreferencesKey("last_sync_epoch_day")
        val COUNTRY_WHITELIST = stringSetPreferencesKey("country_whitelist")
        val EXCLUDED_CATEGORIES = stringSetPreferencesKey("excluded_categories")
        val WANTED_LANGUAGES = stringSetPreferencesKey("wanted_languages")
        val EXCLUDED_GENRES = stringSetPreferencesKey("excluded_genres")
        val EXCLUDED_LANGUAGES = stringSetPreferencesKey("excluded_languages")
        val MINIMUM_YEAR = androidx.datastore.preferences.core.intPreferencesKey("minimum_year")
        val BLACKLIST = stringSetPreferencesKey("blacklist")
        val STOP_STREAM_IN_MENUS = booleanPreferencesKey("stop_stream_in_menus")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }

    @Volatile
    private var credentialMigrationDone = false

    /** Hot [Flow] of the full settings state. */
    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val password = resolvePassword(prefs)

        AppSettings(
            provider = ProviderConfig(
                providerId = prefs[Keys.PROVIDER_ID].orEmpty(),
                connectionName = prefs[Keys.CONNECTION_NAME].orEmpty(),
                host = prefs[Keys.PROVIDER_HOST].orEmpty(),
                username = prefs[Keys.PROVIDER_USERNAME].orEmpty(),
                password = password,
                customM3uUrl = prefs[Keys.PROVIDER_CUSTOM_M3U].orEmpty(),
                useFilteredPlaylist = prefs[Keys.PROVIDER_USE_FILTERED] ?: false,
            ),
            syncFrequency = prefs[Keys.SYNC_FREQUENCY]
                ?.let { runCatching { SyncFrequency.valueOf(it) }.getOrNull() }
                ?: SyncFrequency.Daily,
            countryWhitelist = prefs[Keys.COUNTRY_WHITELIST]
                ?: AppSettings.DEFAULT_COUNTRY_WHITELIST,
            excludedCategories = prefs[Keys.EXCLUDED_CATEGORIES]
                ?: AppSettings.DEFAULT_EXCLUDED_CATEGORIES,
            wantedLanguages = prefs[Keys.WANTED_LANGUAGES]
                ?: AppSettings.DEFAULT_WANTED_LANGUAGES,
            excludedGenres = prefs[Keys.EXCLUDED_GENRES] ?: emptySet(),
            excludedLanguages = prefs[Keys.EXCLUDED_LANGUAGES] ?: emptySet(),
            minimumYear = prefs[Keys.MINIMUM_YEAR] ?: AppSettings.DEFAULT_MINIMUM_YEAR,
            blacklistedContentIds = prefs[Keys.BLACKLIST] ?: emptySet(),
            stopStreamInMenus = prefs[Keys.STOP_STREAM_IN_MENUS] ?: false,
        )
    }

    /**
     * Prefer Keystore-backed password. If a legacy plaintext copy still
     * exists in DataStore (pre-1.0.1), migrate it once then strip it.
     */
    private fun resolvePassword(prefs: Preferences): String {
        val secure = secureCredentials.getPassword()
        if (secure.isNotEmpty()) return secure
        val legacy = prefs[Keys.PROVIDER_PASSWORD].orEmpty()
        if (legacy.isNotEmpty() && !credentialMigrationDone) {
            secureCredentials.setPassword(legacy)
            credentialMigrationDone = true
            return legacy
        }
        return legacy
    }

    /** Call once after first settings read to scrub plaintext password. */
    suspend fun finalizeCredentialMigration() {
        val legacy = context.settingsStore.data.first()[Keys.PROVIDER_PASSWORD].orEmpty()
        if (legacy.isEmpty()) return
        if (secureCredentials.getPassword().isEmpty()) {
            secureCredentials.setPassword(legacy)
        }
        context.settingsStore.edit { it.remove(Keys.PROVIDER_PASSWORD) }
        credentialMigrationDone = true
    }

    /** One-shot snapshot. */
    suspend fun current(): AppSettings {
        finalizeCredentialMigration()
        return settings.first()
    }

    suspend fun setProvider(config: ProviderConfig) {
        secureCredentials.setPassword(config.password)
        context.settingsStore.edit { prefs ->
            prefs[Keys.PROVIDER_ID] = config.providerId
            prefs[Keys.CONNECTION_NAME] = config.connectionName
            prefs[Keys.PROVIDER_HOST] = config.host
            prefs[Keys.PROVIDER_USERNAME] = config.username
            // Never persist password in plaintext DataStore going forward.
            prefs.remove(Keys.PROVIDER_PASSWORD)
            prefs[Keys.PROVIDER_CUSTOM_M3U] = config.customM3uUrl
            prefs[Keys.PROVIDER_USE_FILTERED] = config.useFilteredPlaylist
        }
    }

    suspend fun clearProvider() {
        secureCredentials.clearPassword()
        context.settingsStore.edit { prefs ->
            prefs.remove(Keys.PROVIDER_ID)
            prefs.remove(Keys.CONNECTION_NAME)
            prefs.remove(Keys.PROVIDER_HOST)
            prefs.remove(Keys.PROVIDER_USERNAME)
            prefs.remove(Keys.PROVIDER_PASSWORD)
            prefs.remove(Keys.PROVIDER_CUSTOM_M3U)
            prefs.remove(Keys.PROVIDER_USE_FILTERED)
        }
    }

    suspend fun activeProfileId(): String =
        context.settingsStore.data.first()[Keys.ACTIVE_PROFILE_ID] ?: "profile_1"

    suspend fun setActiveProfileId(id: String) {
        context.settingsStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    /**
     * Snapshot of non-secret preferences for backup export.
     * Password is included only when [includeCredentials] is true
     * (user-initiated backup — file stays on-device).
     */
    suspend fun exportPreferencesMap(includeCredentials: Boolean): Map<String, String> {
        val s = current()
        val map = linkedMapOf(
            "provider_id" to s.provider.providerId,
            "provider_host" to s.provider.host,
            "provider_username" to s.provider.username,
            "provider_custom_m3u" to s.provider.customM3uUrl,
            "provider_use_filtered" to s.provider.useFilteredPlaylist.toString(),
            "sync_frequency" to s.syncFrequency.name,
            "country_whitelist" to s.countryWhitelist.joinToString(","),
            "excluded_categories" to s.excludedCategories.joinToString(","),
            "wanted_languages" to s.wantedLanguages.joinToString(","),
            "excluded_genres" to s.excludedGenres.joinToString(","),
            "excluded_languages" to s.excludedLanguages.joinToString(","),
            "minimum_year" to s.minimumYear.toString(),
            "blacklist" to s.blacklistedContentIds.joinToString(","),
            "stop_stream_in_menus" to s.stopStreamInMenus.toString(),
            "active_profile_id" to activeProfileId(),
        )
        if (includeCredentials) {
            map["provider_password"] = s.provider.password
        }
        return map
    }

    suspend fun importPreferencesMap(map: Map<String, String>) {
        val password = map["provider_password"].orEmpty()
        if (password.isNotEmpty()) {
            secureCredentials.setPassword(password)
        }
        context.settingsStore.edit { prefs ->
            map["provider_id"]?.let { prefs[Keys.PROVIDER_ID] = it }
            map["provider_host"]?.let { prefs[Keys.PROVIDER_HOST] = it }
            map["provider_username"]?.let { prefs[Keys.PROVIDER_USERNAME] = it }
            prefs.remove(Keys.PROVIDER_PASSWORD)
            map["provider_custom_m3u"]?.let { prefs[Keys.PROVIDER_CUSTOM_M3U] = it }
            map["provider_use_filtered"]?.let {
                prefs[Keys.PROVIDER_USE_FILTERED] = it.equals("true", ignoreCase = true)
            }
            map["sync_frequency"]?.let { prefs[Keys.SYNC_FREQUENCY] = it }
            map["country_whitelist"]?.let {
                prefs[Keys.COUNTRY_WHITELIST] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["excluded_categories"]?.let {
                prefs[Keys.EXCLUDED_CATEGORIES] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["wanted_languages"]?.let {
                prefs[Keys.WANTED_LANGUAGES] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["excluded_genres"]?.let {
                prefs[Keys.EXCLUDED_GENRES] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["excluded_languages"]?.let {
                prefs[Keys.EXCLUDED_LANGUAGES] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["minimum_year"]?.toIntOrNull()?.let { prefs[Keys.MINIMUM_YEAR] = it }
            map["blacklist"]?.let {
                prefs[Keys.BLACKLIST] = it.split(',').filter { s -> s.isNotBlank() }.toSet()
            }
            map["stop_stream_in_menus"]?.let {
                prefs[Keys.STOP_STREAM_IN_MENUS] = it.equals("true", ignoreCase = true)
            }
            map["active_profile_id"]?.let { prefs[Keys.ACTIVE_PROFILE_ID] = it }
        }
    }

    suspend fun setSyncFrequency(value: SyncFrequency) {
        context.settingsStore.edit { it[Keys.SYNC_FREQUENCY] = value.name }
    }

    suspend fun setLastSyncEpochDay(epochDay: Long) {
        context.settingsStore.edit { it[Keys.LAST_SYNC_EPOCH_DAY] = epochDay }
    }

    suspend fun lastSyncEpochDay(): Long =
        context.settingsStore.data.first()[Keys.LAST_SYNC_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setCountryWhitelist(values: Set<String>) {
        context.settingsStore.edit { it[Keys.COUNTRY_WHITELIST] = values }
    }

    suspend fun setExcludedCategories(values: Set<String>) {
        context.settingsStore.edit { it[Keys.EXCLUDED_CATEGORIES] = values }
    }

    suspend fun setWantedLanguages(values: Set<String>) {
        context.settingsStore.edit { it[Keys.WANTED_LANGUAGES] = values }
    }

    suspend fun addExcludedGenre(genre: String) {
        val trimmed = genre.trim()
        if (trimmed.isEmpty()) return
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_GENRES] ?: emptySet()
            prefs[Keys.EXCLUDED_GENRES] = current + trimmed
        }
    }

    suspend fun removeExcludedGenre(genre: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_GENRES] ?: emptySet()
            prefs[Keys.EXCLUDED_GENRES] = current - genre
        }
    }

    suspend fun addExcludedLanguage(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_LANGUAGES] ?: emptySet()
            prefs[Keys.EXCLUDED_LANGUAGES] = current + trimmed
        }
    }

    suspend fun removeExcludedLanguage(code: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_LANGUAGES] ?: emptySet()
            prefs[Keys.EXCLUDED_LANGUAGES] = current - code
        }
    }

    suspend fun setMinimumYear(year: Int) {
        context.settingsStore.edit { it[Keys.MINIMUM_YEAR] = year }
    }

    suspend fun addToBlacklist(contentId: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.BLACKLIST] ?: emptySet()
            prefs[Keys.BLACKLIST] = current + contentId
        }
    }

    suspend fun removeFromBlacklist(contentId: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.BLACKLIST] ?: emptySet()
            prefs[Keys.BLACKLIST] = current - contentId
        }
    }

    suspend fun setStopStreamInMenus(value: Boolean) {
        context.settingsStore.edit { it[Keys.STOP_STREAM_IN_MENUS] = value }
    }
}
