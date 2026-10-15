package com.strata.tv.data.repo

import androidx.room.withTransaction
import com.strata.tv.data.db.AppDatabase
import com.strata.tv.data.db.ChannelDao
import com.strata.tv.data.db.ChannelEntity
import com.strata.tv.data.db.ContentDao
import com.strata.tv.data.db.ContentItemEntity
import com.strata.tv.data.db.EpisodeDao
import com.strata.tv.data.db.EpisodeEntity
import com.strata.tv.data.db.MovieDao
import com.strata.tv.data.db.MovieEntity
import com.strata.tv.data.db.SeriesDao
import com.strata.tv.data.db.SeriesEntity
import com.strata.tv.data.m3u.M3uEntry
import com.strata.tv.data.m3u.M3uParser
import com.strata.tv.data.m3u.ParseResult
import com.strata.tv.data.settings.SettingsRepository
import com.strata.tv.data.xtream.XtreamJsonClient
import com.strata.tv.data.xtream.XtreamSnapshot
import com.strata.tv.domain.CategoryRegion
import com.strata.tv.domain.ChannelCategorizer
import com.strata.tv.domain.ChannelDeduplicator
import com.strata.tv.domain.ContentIdHasher
import com.strata.tv.domain.ContentType
import com.strata.tv.domain.MovieDeduplicator
import com.strata.tv.domain.SkyChannelNumbers
import com.strata.tv.domain.TitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls the user's M3U playlist from the network, runs the parsed
 * entries through classification + deduplication, and writes the
 * results into Room.
 *
 * Layered like this so unit tests can drive the parser directly:
 * - [M3uParser] does the regex-heavy work and emits batches.
 * - [ChannelCategorizer] sub-categorises live channels.
 * - [ChannelDeduplicator] collapses HEVC/FHD/UKHD variants to one
 *   channel each, keeping the best quality.
 * - [SyncService.syncFromUrl] orchestrates: HTTP → parse → classify
 *   → dedup → upsert to Room.
 *
 * Progress is exposed as [progress] (a [StateFlow]) so the UI can
 * show a sync indicator without polling.
 */
@Singleton
class SyncService @Inject constructor(
    private val http: OkHttpClient,
    private val parser: M3uParser,
    private val db: AppDatabase,
    private val contentDao: ContentDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val progressTracker: com.strata.tv.data.tmdb.EnrichmentProgressTracker,
    private val settingsRepo: SettingsRepository,
    private val xtreamJson: XtreamJsonClient,
    private val libraryFilter: LibraryFilterRepository,
    private val diagnosticsStore: SyncDiagnosticsStore,
) {

    sealed interface Progress {
        data object Idle : Progress
        data class Downloading(val bytesRead: Long) : Progress
        data class Parsing(val parsed: Int, val skipped: Int) : Progress
        data object PostProcessing : Progress
        data class Done(val totalParsed: Int, val totalSkipped: Int) : Progress
        data class Error(val message: String) : Progress
    }

    companion object {
        /**
         * Stream-persist thresholds.  Movies + episodes are flushed to
         * Room every N parsed entries instead of buffered to end-of-file.
         * 5000 × ~400-byte M3uEntry ≈ 2 MB peak per buffer — vs. ~120 MB
         * for an unbounded 300k-entry library that was blowing the
         * Fire Stick's 128 MB foreground heap.
         */
        private const val MOVIES_FLUSH_THRESHOLD = 5000
        private const val EPISODES_FLUSH_THRESHOLD = 5000
    }

    /**
     * Persist work unit drained by the background worker so the parser
     * doesn't block on DB writes.  Channel capacity is 2 so the parser
     * can run ~2 batches ahead before backpressure kicks in.
     */
    private sealed interface PersistJob {
        data class Movies(val entries: List<M3uEntry>) : PersistJob
        data class Shows(val entries: List<M3uEntry>) : PersistJob
    }

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    /**
     * Called by [com.strata.tv.ui.home.HomeViewModel] when it decides
     * no fresh sync is needed (returning user, sync frequency cap not
     * yet reached).  Without this, [progress] would stay [Progress.Idle]
     * and the gate that's now wired to wait for sync + enrichment would
     * never transition to Main.
     */
    fun markSkipped() {
        _progress.value = Progress.Done(totalParsed = 0, totalSkipped = 0)
    }

    /**
     * Fetch catalogue and write to Room.
     *
     * [scope] lets the user sync Live / Movies / Shows separately so a
     * huge VOD JSON payload cannot OOM the Fire Stick mid-parse.
     * [SyncScope.All] runs the three Xtream slices **one after another**
     * (persist + free memory between each) instead of loading everything
     * at once.
     */
    suspend fun syncFromUrl(
        playlistUrl: String,
        sourceId: Int,
        scope: SyncScope = SyncScope.All,
    ) {
        try {
            _progress.value = Progress.Downloading(0)
            val settings = settingsRepo.current()
            val provider = settings.provider
            val isXtream = provider.providerId != "custom_m3u" &&
                provider.host.isNotBlank() &&
                provider.username.isNotBlank() &&
                provider.password.isNotBlank()

            var totalParsed = 0
            var syncPath = "m3u"
            var diag = SyncDiagnostics.Empty

            if (isXtream) {
                val slices: List<SyncScope> = when (scope) {
                    SyncScope.All -> listOf(SyncScope.Live, SyncScope.Movies, SyncScope.Shows)
                    else -> listOf(scope)
                }
                var jsonOk = false
                for (slice in slices) {
                    try {
                        android.util.Log.i("SyncService", "Xtream JSON sync scope=${slice.name}")
                        val snapshot = xtreamJson.fetchScope(
                            host = provider.host,
                            user = provider.username,
                            pass = provider.password,
                            scope = slice,
                            userAgent = provider.apiUserAgent(),
                            onProgress = { status ->
                                _progress.value = Progress.Parsing(totalParsed, 0)
                                progressTracker.setLabel("${slice.label}: $status")
                            },
                        )
                        _progress.value = Progress.PostProcessing
                        val parsed = persistSnapshot(snapshot, sourceId, playlistUrl, slice)
                        totalParsed += parsed
                        diag = mergeDiagnostics(diag, snapshot, slice)
                        syncPath = "xtream_json/${slice.name.lowercase()}"
                        jsonOk = true
                        // Drop large lists before the next slice.
                        System.gc()
                    } catch (e: Throwable) {
                        android.util.Log.w(
                            "SyncService",
                            "Xtream JSON ${slice.name} failed: ${e.message}",
                        )
                        if (scope != SyncScope.All) throw e
                        // All-mode: continue other slices; fall back to M3U
                        // only if every JSON slice failed.
                    }
                }
                if (!jsonOk && scope == SyncScope.All) {
                    android.util.Log.w("SyncService", "All JSON slices failed — M3U fallback")
                    totalParsed = syncM3uScoped(playlistUrl, sourceId, provider, SyncScope.All)
                    syncPath = "m3u_fallback"
                } else if (!jsonOk) {
                    error("Could not sync ${scope.label} from provider API")
                }
            } else {
                totalParsed = syncM3uScoped(playlistUrl, sourceId, provider, scope)
                syncPath = "m3u/${scope.name.lowercase()}"
            }

            runCatching { libraryFilter.recomputeHiddenFlags() }
                .onFailure { e ->
                    android.util.Log.w("SyncService", "recomputeHiddenFlags failed: ${e.message}", e)
                }

            publishDiagnostics(syncPath, diag, totalParsed)
            _progress.value = Progress.Done(totalParsed = totalParsed, totalSkipped = 0)
        } catch (e: Throwable) {
            val rootCause = generateSequence(e) { it.cause }.last()
            val msg = buildString {
                append(e::class.simpleName ?: "Throwable")
                e.message?.let { append(": ").append(it) }
                if (rootCause !== e) {
                    append(" (cause: ")
                    append(rootCause::class.simpleName ?: "Throwable")
                    rootCause.message?.let { append(": ").append(it) }
                    append(")")
                }
            }
            diagnosticsStore.update(
                diagnosticsStore.snapshot.value.copy(
                    lastSyncAt = Instant.now(),
                    lastError = msg,
                ),
            )
            _progress.value = Progress.Error(msg)
            throw e
        }
    }

    private suspend fun persistSnapshot(
        snapshot: XtreamSnapshot,
        sourceId: Int,
        sourceKey: String,
        scope: SyncScope,
    ): Int {
        var n = 0
        when (scope) {
            SyncScope.Live -> {
                persistLive(snapshot.live, sourceId, sourceKey)
                n = snapshot.live.size
            }
            SyncScope.Movies -> {
                persistMovies(snapshot.movies, sourceId, sourceKey)
                n = snapshot.movies.size
            }
            SyncScope.Shows -> {
                persistSeriesCatalogue(snapshot.seriesMeta)
                n = snapshot.seriesMeta.size
            }
            SyncScope.All -> error("persistSnapshot expects a single slice")
        }
        progressTracker.syncComplete(n)
        return n
    }

    private fun mergeDiagnostics(
        base: SyncDiagnostics,
        snapshot: XtreamSnapshot,
        scope: SyncScope,
    ): SyncDiagnostics = when (scope) {
        SyncScope.Live -> base.copy(
            liveCategories = snapshot.liveCategories,
            liveProvider = snapshot.liveProviderCount,
            liveParsed = snapshot.live.size,
        )
        SyncScope.Movies -> base.copy(
            vodCategories = snapshot.vodCategories,
            moviesProvider = snapshot.moviesProviderCount,
            moviesParsed = snapshot.movies.size,
        )
        SyncScope.Shows -> base.copy(
            seriesCategories = snapshot.seriesCategories,
            seriesProvider = snapshot.seriesProviderCount,
            seriesParsed = snapshot.seriesMeta.size,
        )
        SyncScope.All -> base
    }

    private suspend fun publishDiagnostics(
        syncPath: String,
        partial: SyncDiagnostics,
        totalParsed: Int,
    ) {
        val liveStored = channelDao.watchCount().first()
        val moviesStored = movieDao.countAll()
        val seriesStored = seriesDao.countAll()
        val moviesVisible = movieDao.countVisible()
        val seriesVisible = seriesDao.countVisible()
        val settingsNow = settingsRepo.current()
        val liveContent = contentDao.byType("live")
        val liveVisible = liveContent.count { item ->
            CategoryRegion.matchesRegion(item.groupTitle, settingsNow.countryWhitelist) &&
                !CategoryRegion.matchesExcludedCategory(
                    item.groupTitle,
                    settingsNow.excludedCategories,
                )
        }
        diagnosticsStore.update(
            SyncDiagnostics(
                lastSyncAt = Instant.now(),
                lastError = null,
                syncPath = syncPath,
                liveCategories = partial.liveCategories,
                vodCategories = partial.vodCategories,
                seriesCategories = partial.seriesCategories,
                totalCategories = partial.liveCategories + partial.vodCategories +
                    partial.seriesCategories,
                liveProvider = partial.liveProvider,
                liveParsed = partial.liveParsed,
                liveStored = liveStored,
                liveVisible = liveVisible,
                moviesProvider = partial.moviesProvider,
                moviesParsed = partial.moviesParsed,
                moviesStored = moviesStored,
                moviesVisible = moviesVisible,
                seriesProvider = partial.seriesProvider,
                seriesParsed = partial.seriesParsed,
                seriesStored = seriesStored,
                seriesVisible = seriesVisible,
                hiddenByRegion = (liveStored - liveVisible).coerceAtLeast(0),
                hiddenByLanguage = (moviesStored - moviesVisible).coerceAtLeast(0),
                hiddenCategoriesEstimate = settingsNow.excludedCategories.size,
            ),
        )
        android.util.Log.i(
            "SyncService",
            "Sync done path=$syncPath totalParsed=$totalParsed " +
                "stored live=$liveStored movies=$moviesStored series=$seriesStored",
        )
    }

    /**
     * M3U path with optional content-type filter for [scope].
     * @return number of entries persisted for this scope.
     */
    private suspend fun syncM3uScoped(
        playlistUrl: String,
        sourceId: Int,
        provider: com.strata.tv.data.settings.ProviderConfig,
        scope: SyncScope,
    ): Int {
        val live = mutableListOf<M3uEntry>()
        var moviesPersisted = 0
        var showsPersisted = 0
        val wantLive = scope == SyncScope.All || scope == SyncScope.Live
        val wantMovies = scope == SyncScope.All || scope == SyncScope.Movies
        val wantShows = scope == SyncScope.All || scope == SyncScope.Shows

        withContext(Dispatchers.IO) {
            fetchPlaylistWithRetry(playlistUrl, provider.apiUserAgent())
                .use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} fetching playlist")
                    }
                    val body = response.body ?: error("Empty playlist body")
                    val contentType = response.header("Content-Type").orEmpty()
                    val source = body.source()
                    source.request(512)
                    val peek = source.peek().readUtf8(
                        minOf(512L, source.buffer.size),
                    ).trimStart()
                    val isJsonResponse = contentType.contains("json", ignoreCase = true) ||
                        peek.startsWith("{") || peek.startsWith("[")
                    if (isJsonResponse) {
                        error("Playlist URL returned JSON; configure Xtream credentials")
                    }

                    body.charStream().buffered().useLines { lines ->
                        coroutineScope {
                            val persistChannel = kotlinx.coroutines.channels
                                .Channel<PersistJob>(capacity = 2)
                            val persistJob = launch(Dispatchers.IO) {
                                for (job in persistChannel) {
                                    when (job) {
                                        is PersistJob.Movies -> {
                                            persistMovies(job.entries, sourceId, playlistUrl)
                                            moviesPersisted += job.entries.size
                                        }
                                        is PersistJob.Shows -> {
                                            persistShows(job.entries, sourceId, playlistUrl)
                                            showsPersisted += job.entries.size
                                        }
                                    }
                                }
                            }
                            val moviesBuf = ArrayList<M3uEntry>(MOVIES_FLUSH_THRESHOLD)
                            val episodesBuf = ArrayList<M3uEntry>(EPISODES_FLUSH_THRESHOLD)
                            try {
                                parser.parseLines(lines).collect { result ->
                                    when (result) {
                                        is ParseResult.Batch -> {
                                            for (entry in result.entries) {
                                                when (entry.contentType) {
                                                    ContentType.Live ->
                                                        if (wantLive) live.add(entry)
                                                    ContentType.Movie -> if (wantMovies) {
                                                        moviesBuf.add(entry)
                                                        if (moviesBuf.size >= MOVIES_FLUSH_THRESHOLD) {
                                                            persistChannel.send(
                                                                PersistJob.Movies(moviesBuf.toList()),
                                                            )
                                                            moviesBuf.clear()
                                                        }
                                                    }
                                                    ContentType.Show -> if (wantShows) {
                                                        episodesBuf.add(entry)
                                                        if (episodesBuf.size >= EPISODES_FLUSH_THRESHOLD) {
                                                            persistChannel.send(
                                                                PersistJob.Shows(episodesBuf.toList()),
                                                            )
                                                            episodesBuf.clear()
                                                        }
                                                    }
                                                }
                                            }
                                            progressTracker.syncAdvance(result.entries.size)
                                        }
                                        is ParseResult.Progress -> {
                                            _progress.value = Progress.Parsing(
                                                result.parsed,
                                                result.skipped,
                                            )
                                        }
                                        is ParseResult.Complete -> {
                                            progressTracker.syncComplete(result.totalParsed)
                                        }
                                    }
                                }
                                if (moviesBuf.isNotEmpty()) {
                                    persistChannel.send(PersistJob.Movies(moviesBuf.toList()))
                                    moviesBuf.clear()
                                }
                                if (episodesBuf.isNotEmpty()) {
                                    persistChannel.send(PersistJob.Shows(episodesBuf.toList()))
                                    episodesBuf.clear()
                                }
                            } finally {
                                persistChannel.close()
                                persistJob.join()
                            }
                        }
                    }
                }
        }

        _progress.value = Progress.PostProcessing
        if (wantLive) {
            persistLive(live, sourceId, playlistUrl)
        }
        return live.size + moviesPersisted + showsPersisted
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    /**
     * Fetch the playlist URL, retrying with a different User-Agent on
     * each failure.  Some IPTV panels (lecyvision.com — Me2u Ultra Sky's
     * Ultra portal — is a known offender) reject the default
     * `okhttp/4.x` UA with HTTP 5xx, but accept a stripped UA, VLC, or
     * a browser UA.  This walks a small list of common UAs until one
     * returns 2xx.
     *
     * Returns the open [okhttp3.Response] — the caller is responsible
     * for closing it (via `.use { ... }` etc).
     */
    /**
     * Per-request OkHttp client that forces HTTP/1.1 (some Cloudflare-
     * protected panels — lecyvision.com — return HTTP 512 to OkHttp's
     * HTTP/2 connections but accept HTTP/1.1 with a VLC-style header
     * set fine).  Built lazily from the shared client so connection
     * pool + timeouts are inherited.
     */
    private val playlistHttp: OkHttpClient by lazy {
        http.newBuilder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
    }

    private fun fetchPlaylistWithRetry(
        url: String,
        preferredUserAgent: String?,
    ): okhttp3.Response {
        // Order matters: try the provider's preferred UA first (works
        // for the majority of panels), then fall back to alternatives
        // that have been observed to bypass anti-bot filters.
        val strategies: List<String?> = buildList {
            preferredUserAgent?.takeIf { it.isNotBlank() }?.let { add(it) }
            // VLC — almost-universal fallback.  Verified to work on
            // lecyvision.com, le.thund.re, MyBunny.TV, Cloudflare panels.
            if ("VLC" !in preferredUserAgent.orEmpty()) {
                add("VLC/3.0.20 LibVLC/3.0.20")
            }
            // okhttp default — works on le.thund.re and most older
            // panels.  Skipped if it was already the preferred UA.
            if (preferredUserAgent != "okhttp/4.12.0") {
                add("okhttp/4.12.0")
            }
            // Fire Stick browser UA — last resort, mimics Silk.
            add("Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36")
            // Empty UA last — known to 403 on lecyvision/le.thund.re,
            // but occasionally needed for the strictest Cloudflare rules.
            add(null)
        }

        var lastError: Throwable? = null
        for (ua in strategies) {
            val label = ua ?: "(none)"
            try {
                val builder = Request.Builder()
                    .url(url)
                    // Mimic what VLC + browsers send so Cloudflare-WAF
                    // panels don't reject us on "missing Accept" rules.
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .header("Connection", "close")
                if (ua != null) {
                    builder.header("User-Agent", ua)
                } else {
                    builder.removeHeader("User-Agent")
                }
                val response = playlistHttp.newCall(builder.build()).execute()
                if (response.isSuccessful) {
                    android.util.Log.i(
                        "SyncService",
                        "Playlist fetched OK with UA='$label'",
                    )
                    return response
                }
                val code = response.code
                response.close()
                lastError = IllegalStateException("HTTP $code (UA='$label')")
                android.util.Log.w(
                    "SyncService",
                    "Playlist UA='$label' got HTTP $code — trying next",
                )
            } catch (t: Throwable) {
                lastError = t
                android.util.Log.w(
                    "SyncService",
                    "Playlist UA='$label' threw ${t.message} — trying next",
                )
            }
        }
        throw IllegalStateException(
            "Playlist fetch failed for all User-Agent strategies " +
                "(${strategies.size} tried). Last error: ${lastError?.message}",
            lastError,
        )
    }

    // -------------------------------------------------------------------------
    // Persisters
    // -------------------------------------------------------------------------

    private suspend fun persistLive(
        entries: List<M3uEntry>,
        sourceId: Int,
        sourceKey: String,
    ) {
        // Dedup in-memory first — keeps the per-row writes to one per
        // logical channel.  dedupeWithAlternates also hands back the
        // lower-quality variant URLs so the player can fall back to a
        // playable feed when the best one can't be decoded.
        val deduped = ChannelDeduplicator.dedupeWithAlternates(
            channels = entries,
            displayName = M3uEntry::displayName,
            tvgId = M3uEntry::tvgId,
            streamUrl = M3uEntry::streamUrl,
            withTvgId = { e, id -> e.copy(tvgId = id) },
        )

        // Chunked persist — see persistMovies for rationale.
        for (chunk in deduped.chunked(2000)) {
            val contentRows = ArrayList<ContentItemEntity>(chunk.size)
            val channelRows = ArrayList<ChannelEntity>(chunk.size)

            for (withAlts in chunk) {
                val entry = withAlts.channel
                val contentId = ContentIdHasher.hash(
                    sourceKey = sourceKey,
                    normalisedTitle = TitleParser.normalise(
                        ChannelDeduplicator.cleanChannelName(entry.displayName),
                    ),
                    groupTitle = entry.groupTitle,
                    streamUrl = entry.streamUrl,
                )

                contentRows.add(
                    ContentItemEntity(
                        contentId = contentId,
                        sourceId = sourceId,
                        displayName = entry.displayName,
                        streamUrl = entry.streamUrl,
                        groupTitle = entry.groupTitle,
                        contentType = "live",
                        tvgId = entry.tvgId,
                        tvgName = entry.tvgName,
                        tvgLogo = entry.tvgLogo,
                        tvgType = entry.tvgType,
                        altStreamUrls = withAlts.altStreamUrls.joinToString("\n"),
                    ),
                )

                channelRows.add(
                    ChannelEntity(
                        contentId = contentId,
                        category = ChannelCategorizer.categorise(entry.displayName, entry.groupTitle),
                        logoUrl = entry.tvgLogo,
                        channelNumber = SkyChannelNumbers.numberFor(
                            ChannelDeduplicator.cleanChannelName(entry.displayName),
                        ).takeIf { it != SkyChannelNumbers.UNKNOWN },
                    ),
                )
            }

            // One transaction per chunk so a kill between the two upserts
            // can't leave `content_items` rows with no matching `channels`.
            db.withTransaction {
                contentDao.upsertAll(contentRows)
                channelDao.upsertAll(channelRows)
            }
        }
    }

    private suspend fun persistMovies(
        entries: List<M3uEntry>,
        sourceId: Int,
        sourceKey: String,
    ) {
        // Collapse exact duplicates up front: the same movie is listed
        // once per category by Xtream (e.g. a film in "Action" + "Drama"),
        // all with the same stream URL.  Without this we'd mint a separate
        // content_id per category (group_title is in the hash) and show the
        // film twice.  distinctBy keeps the first listing per stream URL.
        val deduped = entries.distinctBy { it.streamUrl }

        // Chunk the persist so peak memory stays bounded even on very
        // large catalogues (300k+ entries seen on premium IPTV panels).
        for (chunk in deduped.chunked(2000)) {
            val contentRows = ArrayList<ContentItemEntity>(chunk.size)
            val movieRows = ArrayList<MovieEntity>(chunk.size)

            for (entry in chunk) {
                val title = entry.movieTitle ?: TitleParser.stripHdPrefix(entry.displayName)
                val contentId = ContentIdHasher.hash(
                    sourceKey = sourceKey,
                    normalisedTitle = TitleParser.normalise(title),
                    groupTitle = entry.groupTitle,
                    streamUrl = entry.streamUrl,
                )

                contentRows.add(
                    ContentItemEntity(
                        contentId = contentId,
                        sourceId = sourceId,
                        displayName = entry.displayName,
                        streamUrl = entry.streamUrl,
                        groupTitle = entry.groupTitle,
                        contentType = "movie",
                        tvgId = entry.tvgId,
                        tvgName = entry.tvgName,
                        tvgLogo = entry.tvgLogo,
                        tvgType = entry.tvgType,
                        title = title,
                    ),
                )
                movieRows.add(
                    MovieEntity(
                        contentId = contentId,
                        movieTitle = title,
                        year = entry.movieYear,
                        // Provider poster + rating filled instantly so the
                        // library isn't blank pre-TMDB.  TMDB later upgrades
                        // these if it finds a (better) match.
                        posterUrl = entry.posterUrl,
                        rating = entry.rating ?: 0.0,
                    ),
                )
            }

            db.withTransaction {
                // content_items is 100% parse-derived (stream URL, group,
                // tvg-*), so a full upsert is correct — it refreshes those
                // from the latest playlist.
                contentDao.upsertAll(contentRows)
                // movies carries TMDB enrichment + user flags, so we must
                // NOT @Upsert (that resets every column to defaults on each
                // sync).  insertNewOnly leaves existing rows untouched;
                // brand-new movies start with the provider poster/rating.
                movieDao.insertNewOnly(movieRows)
                // Backfill the provider poster/rating onto pre-existing rows
                // that don't yet have a poster (TMDB not run, or older sync
                // before this feature).  Gated on poster_url = '' so a real
                // TMDB poster is never overwritten.
                for (row in movieRows) {
                    if (row.posterUrl.isNotEmpty()) {
                        movieDao.fillProviderArtworkIfMissing(
                            contentId = row.contentId,
                            poster = row.posterUrl,
                            rating = row.rating,
                            year = row.year,
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistShows(
        entries: List<M3uEntry>,
        sourceId: Int,
        sourceKey: String,
    ) {
        // For shows we have one entity per *episode* (in episodes
        // table) plus one summary row per *series* in series table.
        // Group by series title first.
        val grouped = entries
            .filter { it.seriesTitle != null }
            .groupBy { TitleParser.normalise(it.seriesTitle!!) }

        // Process series-groups in chunks of 100 so the intermediate
        // contentRows / seriesRows / episodeRows lists never hold more
        // than ~10k objects at a time (100 series × ~100 episodes/series).
        // Keeps peak memory bounded even for 5000+ series libraries.
        val seriesChunks = grouped.entries.chunked(100)
        for (seriesChunk in seriesChunks) {
            persistShowsChunk(seriesChunk, sourceId, sourceKey)
        }
    }

    private suspend fun persistShowsChunk(
        seriesChunk: List<Map.Entry<String, List<M3uEntry>>>,
        sourceId: Int,
        sourceKey: String,
    ) {
        val contentRows = ArrayList<ContentItemEntity>(seriesChunk.size * 50)
        val seriesRows = ArrayList<SeriesEntity>(seriesChunk.size)
        val episodeRows = ArrayList<EpisodeEntity>(seriesChunk.size * 50)

        for ((normalisedTitle, episodesForSeries) in seriesChunk) {
            val first = episodesForSeries.first()
            val titleForSeries = first.seriesTitle!!.trim()

            // Deduplicate quality variants: group by (season, episode),
            // keep only the best quality variant per episode slot.
            val dedupedEpisodes = episodesForSeries
                .groupBy { (it.seasonNumber ?: 0) to (it.episodeNumber ?: 0) }
                .values
                .map { variants ->
                    variants.maxByOrNull {
                        MovieDeduplicator.detectQuality(it.displayName, it.streamUrl).ordinal
                    } ?: variants.first()
                }

            // "NEW" badge bookkeeping: preserve the user's seen-state
            // across the Upsert.  Room's @Upsert overwrites every
            // column, so without this read-modify-write the badge would
            // either (a) reset to 0 on every sync and falsely flag the
            // whole library as new, or (b) get bumped past `total` and
            // suppress legitimate badges.  Rules:
            //   - Series didn't exist before -> seen = newTotal so a
            //     fresh import doesn't flood the home screen with NEW.
            //   - Episode count grew -> seen = previousTotal so the
            //     badge fires on the delta.
            //   - Otherwise -> seen = previousSeen (preserve as-is).
            val newTotalEpisodes = dedupedEpisodes.size
            val newTotalSeasons = dedupedEpisodes.mapNotNull { it.seasonNumber }.maxOrNull() ?: 0
            // Read-modify-write so a re-sync NEVER wipes the series'
            // TMDB enrichment (poster/plot/genre/tmdb_id) or user flags
            // (hidden, user_hidden, is_favourite).  Only the parse-derived
            // counts + NEW-badge bookkeeping are refreshed.  Mirrors the
            // pattern already used in persistSeriesCatalogue.
            val existing = seriesDao.byTitle(titleForSeries)
            val previousTotal = existing?.totalEpisodes
            val previousSeen = existing?.lastSeenTotalEpisodes
            val nextLastSeen = when {
                previousTotal == null -> newTotalEpisodes
                newTotalEpisodes > previousTotal -> previousTotal
                else -> previousSeen ?: newTotalEpisodes
            }

            seriesRows.add(
                existing?.copy(
                    totalSeasons = newTotalSeasons,
                    totalEpisodes = newTotalEpisodes,
                    lastSeenTotalEpisodes = nextLastSeen,
                    groupTitle = first.groupTitle,
                ) ?: SeriesEntity(
                    seriesTitle = titleForSeries,
                    totalSeasons = newTotalSeasons,
                    totalEpisodes = newTotalEpisodes,
                    lastSeenTotalEpisodes = nextLastSeen,
                    groupTitle = first.groupTitle,
                ),
            )

            for (entry in dedupedEpisodes) {
                val episodeContentId = ContentIdHasher.hash(
                    sourceKey = sourceKey,
                    normalisedTitle = "$normalisedTitle s${entry.seasonNumber}e${entry.episodeNumber}",
                    groupTitle = entry.groupTitle,
                    streamUrl = entry.streamUrl,
                )

                contentRows.add(
                    ContentItemEntity(
                        contentId = episodeContentId,
                        sourceId = sourceId,
                        displayName = entry.displayName,
                        streamUrl = entry.streamUrl,
                        groupTitle = entry.groupTitle,
                        contentType = "show",
                        tvgId = entry.tvgId,
                        tvgName = entry.tvgName,
                        tvgLogo = entry.tvgLogo,
                        tvgType = entry.tvgType,
                        title = titleForSeries,
                    ),
                )

                episodeRows.add(
                    EpisodeEntity(
                        contentId = episodeContentId,
                        seriesTitle = titleForSeries,
                        seasonNumber = entry.seasonNumber ?: 0,
                        episodeNumber = entry.episodeNumber ?: 0,
                        streamUrl = entry.streamUrl,
                    ),
                )
            }
        }

        db.withTransaction {
            contentDao.upsertAll(contentRows)
            // seriesRows were built via read-modify-write above, so they
            // already carry the existing enrichment — upsert is safe here.
            seriesDao.upsertAll(seriesRows)
            // episodes carry per-episode watched/resume — insertNewOnly so
            // a re-sync doesn't reset the user's progress.
            episodeDao.insertNewOnly(episodeRows)
        }
    }

    /**
     * Persist a Xtream series catalogue (metadata only — episodes will
     * be lazy-loaded by the show detail screen).  We don't blow away
     * the user's existing per-series progress columns when we re-sync;
     * `upsertAll` preserves them via Room's `@Upsert` matching on the
     * unique `series_title` index, except for `xtream_series_id` which
     * we always overwrite so the lazy fetcher has a current id.
     */
    private suspend fun persistSeriesCatalogue(
        snapshot: List<com.strata.tv.data.xtream.XtreamSeriesMeta>,
    ) {
        if (snapshot.isEmpty()) return
        val rows = snapshot.map { meta ->
            // Preserve totals/last-seen counters across re-sync.  Fill the
            // provider `cover` as the poster instantly (like movies) so the
            // series isn't blank pre-TMDB — but only when we don't already
            // have a (TMDB) poster, so enrichment can still upgrade it.
            val existing = seriesDao.byTitle(meta.title)
            existing?.copy(
                xtreamSeriesId = meta.xtreamSeriesId,
                groupTitle = meta.groupTitle,
                posterUrl = existing.posterUrl.ifBlank { meta.cover },
            )
                ?: SeriesEntity(
                    seriesTitle = meta.title,
                    xtreamSeriesId = meta.xtreamSeriesId,
                    groupTitle = meta.groupTitle,
                    posterUrl = meta.cover,
                )
        }
        seriesDao.upsertAll(rows)
    }

}
