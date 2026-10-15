package com.strata.tv.data.backup

import android.content.Context
import android.os.Environment
import com.strata.tv.data.db.ChannelDao
import com.strata.tv.data.db.ContinueWatchingDao
import com.strata.tv.data.db.ContinueWatchingEntity
import com.strata.tv.data.db.FavouriteDao
import com.strata.tv.data.db.FavouriteEntity
import com.strata.tv.data.db.WatchHistoryDao
import com.strata.tv.data.db.WatchHistoryEntity
import com.strata.tv.data.db.WatchlistDao
import com.strata.tv.data.db.WatchlistEntity
import com.strata.tv.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * On-device backup / restore of user preferences and viewing state.
 *
 * Catalogue cache (movies/channels/EPG) is intentionally excluded —
 * it is re-downloaded from the provider after restore. Credentials,
 * filters, continue watching, favourites, watchlist and history are
 * preserved so an APK reinstall or device swap does not force a
 * full re-setup.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val continueWatchingDao: ContinueWatchingDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val favouriteDao: FavouriteDao,
    private val watchlistDao: WatchlistDao,
    private val channelDao: ChannelDao,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun defaultBackupFile(): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val backups = File(dir, "backups").also { it.mkdirs() }
        return File(backups, BACKUP_FILE_NAME)
    }

    /** Best-effort public copy under Download for file managers. */
    fun publicDownloadCopy(): File? {
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!download.exists() && !download.mkdirs()) return null
        return File(download, BACKUP_FILE_NAME)
    }

    suspend fun exportBackup(includeCredentials: Boolean = true): BackupResult {
        return runCatching {
            val payload = StreamBackup(
                schemaVersion = BACKUP_SCHEMA_VERSION,
                exportedAtEpochMs = Instant.now().toEpochMilli(),
                appVersionName = com.strata.tv.BuildConfig.VERSION_NAME,
                appVersionCode = com.strata.tv.BuildConfig.VERSION_CODE,
                preferences = settingsRepo.exportPreferencesMap(includeCredentials),
                continueWatching = continueWatchingDao.snapshotAll().map { it.toBackup() },
                watchHistory = watchHistoryDao.snapshotRecent(500).map { it.toBackup() },
                favourites = favouriteDao.snapshotAll().map { it.toBackup() },
                watchlist = watchlistDao.snapshotAll().map { it.toBackup() },
                favouriteChannelIds = channelDao.snapshotFavouriteIds(),
            )
            val text = json.encodeToString(payload)
            val primary = defaultBackupFile()
            primary.writeText(text)
            val publicCopy = publicDownloadCopy()
            runCatching { publicCopy?.writeText(text) }
            BackupResult.Success(
                path = primary.absolutePath,
                publicPath = publicCopy?.takeIf { it.exists() }?.absolutePath,
                itemCount = payload.itemCount(),
            )
        }.getOrElse { BackupResult.Failure(it.message ?: "Backup failed") }
    }

    suspend fun importBackup(file: File = defaultBackupFile()): BackupResult {
        if (!file.exists()) {
            // Fall back to public Download copy if present.
            val public = publicDownloadCopy()
            if (public == null || !public.exists()) {
                return BackupResult.Failure("No backup found at ${file.absolutePath}")
            }
            return importBackup(public)
        }
        return runCatching {
            val payload = json.decodeFromString<StreamBackup>(file.readText())
            settingsRepo.importPreferencesMap(payload.preferences)
            continueWatchingDao.upsertAll(payload.continueWatching.map { it.toEntity() })
            watchHistoryDao.insertAll(payload.watchHistory.map { it.toEntity() })
            favouriteDao.addAll(payload.favourites.map { it.toEntity() })
            watchlistDao.addAll(payload.watchlist.map { it.toEntity() })
            payload.favouriteChannelIds.forEach { id ->
                channelDao.setFavourite(id, true)
            }
            BackupResult.Success(
                path = file.absolutePath,
                publicPath = null,
                itemCount = payload.itemCount(),
            )
        }.getOrElse { BackupResult.Failure(it.message ?: "Restore failed") }
    }

    companion object {
        const val BACKUP_FILE_NAME = "stream-backup.json"
        const val BACKUP_SCHEMA_VERSION = 1
    }
}

sealed interface BackupResult {
    data class Success(
        val path: String,
        val publicPath: String?,
        val itemCount: Int,
    ) : BackupResult

    data class Failure(val message: String) : BackupResult
}

@Serializable
data class StreamBackup(
    val schemaVersion: Int,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val preferences: Map<String, String> = emptyMap(),
    val continueWatching: List<CwBackup> = emptyList(),
    val watchHistory: List<HistoryBackup> = emptyList(),
    val favourites: List<FavouriteBackup> = emptyList(),
    val watchlist: List<WatchlistBackup> = emptyList(),
    val favouriteChannelIds: List<String> = emptyList(),
) {
    fun itemCount(): Int =
        continueWatching.size + watchHistory.size + favourites.size +
            watchlist.size + favouriteChannelIds.size + if (preferences.isNotEmpty()) 1 else 0
}

@Serializable
data class CwBackup(
    val contentId: String,
    val contentType: String,
    val streamUrl: String = "",
    val artworkUrl: String = "",
    val resumePositionMs: Long,
    val totalDurationMs: Long = 0,
    val lastUpdatedEpochMs: Long,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val title: String = "",
)

@Serializable
data class HistoryBackup(
    val contentId: String,
    val contentType: String,
    val durationWatchedMs: Long,
    val watchedAtEpochMs: Long,
)

@Serializable
data class FavouriteBackup(
    val contentId: String,
    val contentType: String,
    val addedAtEpochMs: Long,
)

@Serializable
data class WatchlistBackup(
    val contentId: String,
    val contentType: String,
    val title: String,
    val artworkUrl: String = "",
    val addedAtEpochMs: Long,
)

private fun ContinueWatchingEntity.toBackup() = CwBackup(
    contentId = contentId,
    contentType = contentType,
    streamUrl = streamUrl,
    artworkUrl = artworkUrl,
    resumePositionMs = resumePositionMs,
    totalDurationMs = totalDurationMs,
    lastUpdatedEpochMs = lastUpdated.toEpochMilli(),
    seriesTitle = seriesTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
)

private fun CwBackup.toEntity() = ContinueWatchingEntity(
    contentId = contentId,
    contentType = contentType,
    streamUrl = streamUrl,
    artworkUrl = artworkUrl,
    resumePositionMs = resumePositionMs,
    totalDurationMs = totalDurationMs,
    lastUpdated = Instant.ofEpochMilli(lastUpdatedEpochMs),
    seriesTitle = seriesTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
)

private fun WatchHistoryEntity.toBackup() = HistoryBackup(
    contentId = contentId,
    contentType = contentType,
    durationWatchedMs = durationWatchedMs,
    watchedAtEpochMs = watchedAt.toEpochMilli(),
)

private fun HistoryBackup.toEntity() = WatchHistoryEntity(
    contentId = contentId,
    contentType = contentType,
    durationWatchedMs = durationWatchedMs,
    watchedAt = Instant.ofEpochMilli(watchedAtEpochMs),
)

private fun FavouriteEntity.toBackup() = FavouriteBackup(
    contentId = contentId,
    contentType = contentType,
    addedAtEpochMs = addedAt.toEpochMilli(),
)

private fun FavouriteBackup.toEntity() = FavouriteEntity(
    contentId = contentId,
    contentType = contentType,
    addedAt = Instant.ofEpochMilli(addedAtEpochMs),
)

private fun WatchlistEntity.toBackup() = WatchlistBackup(
    contentId = contentId,
    contentType = contentType,
    title = title,
    artworkUrl = artworkUrl,
    addedAtEpochMs = addedAt.toEpochMilli(),
)

private fun WatchlistBackup.toEntity() = WatchlistEntity(
    contentId = contentId,
    contentType = contentType,
    title = title,
    artworkUrl = artworkUrl,
    addedAt = Instant.ofEpochMilli(addedAtEpochMs),
)
