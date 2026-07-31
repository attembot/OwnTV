@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.search

import tv.own.owntv.core.epg.displayLogoUrl
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer

/** Combined results of a global query (each list bounded). */
@Immutable
data class SearchResults(
    val channels: List<tv.own.owntv.core.database.dao.ChannelSearchResult> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** Batch 5 — empty-state launcher intents. All bounded (favourites / recent history). */
enum class SearchIntent(val label: String) {
    CONTINUE("Continue watching"),
    UNWATCHED("Unwatched"),
    CHANNELS("Channels"),
}

/** Phase 11 — cross-section search over a profile's channels, movies and series. */
class SearchViewModel(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val favoriteDao: FavoriteDao,
    val player: OwnTVPlayer,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
) : ViewModel() {

    /** Global "External player" toggle — the screen must NOT open the fullscreen in-app player when on. */
    val externalPlayerOn: kotlinx.coroutines.flow.StateFlow<Boolean> = settings.externalPlayerMovies
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private data class Ctx(
        val profileId: Long,
        val liveSourceIds: List<Long>,
        val movieSourceIds: List<Long>,
        val seriesSourceIds: List<Long>,
    ) {
        val hasAny: Boolean get() = liveSourceIds.isNotEmpty() || movieSourceIds.isNotEmpty() || seriesSourceIds.isNotEmpty()
    }
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Search
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    // Per-section Off flags split the id sets so an Off section never surfaces in results.
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.liveSourceIds, aps.movieSourceIds, aps.seriesSourceIds) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList(), emptyList(), emptyList()))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flowOf(search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) {
        _query.value = q
        if (q.isNotBlank()) _intent.value = null // typing overrides an active launcher intent
    }

    // --- Batch 5: empty-state launcher (recent search terms + Continue / Unwatched / Channels) ---

    /** Persisted recent search terms (most-recent first). */
    val recentSearches: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearRecentSearches() { viewModelScope.launch { settings.clearRecentSearches() } }

    /** Save the current query into recents — called when the user actually opens a result. */
    fun rememberCurrentQuery() { viewModelScope.launch { settings.addRecentSearch(_query.value) } }

    private val _intent = MutableStateFlow<SearchIntent?>(null)
    val intent: StateFlow<SearchIntent?> = _intent.asStateFlow()

    /** Selecting an intent clears any typed query so the curated list shows; null returns to the launcher. */
    fun setIntent(i: SearchIntent?) {
        _intent.value = i
        if (i != null) _query.value = ""
    }

    /** Curated results for the active empty-state intent (bounded; reuses favourites/history queries). */
    val curatedResults: StateFlow<SearchResults> = combine(_intent, ctx) { i, c -> i to c }
        .flatMapLatest { (i, c) ->
            if (i == null || c.profileId < 0 || !c.hasAny) flowOf(SearchResults())
            else flowOf(loadIntent(i, c))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private suspend fun loadIntent(intent: SearchIntent, c: Ctx): SearchResults = when (intent) {
        SearchIntent.CONTINUE -> SearchResults(
            channels = channelDao.recentlyWatched(c.profileId, LIMIT).first()
                .filter { it.sourceId in c.liveSourceIds }
                .map { ChannelSearchResult(it, null) },
            movies = if (c.movieSourceIds.isEmpty()) emptyList()
            else movieDao.recentlyWatchedSnapshot(c.profileId, c.movieSourceIds, LIMIT),
            series = if (c.seriesSourceIds.isEmpty()) emptyList()
            else seriesDao.recentlyWatchedSnapshot(c.profileId, c.seriesSourceIds, LIMIT),
        )
        SearchIntent.UNWATCHED -> SearchResults(
            movies = if (c.movieSourceIds.isEmpty()) emptyList()
            else movieDao.unwatchedFavorites(c.profileId, c.movieSourceIds, LIMIT),
            series = if (c.seriesSourceIds.isEmpty()) emptyList()
            else seriesDao.unwatchedFavorites(c.profileId, c.seriesSourceIds, LIMIT),
        )
        SearchIntent.CHANNELS -> SearchResults(
            channels = channelDao.favoritesListAlpha(c.profileId).first()
                .filter { it.sourceId in c.liveSourceIds }
                .take(LIMIT)
                .map { ChannelSearchResult(it, null) },
        )
    }

    /**
     * Builds a sanitized FTS4 MATCH expression from user text: each whitespace-separated token is
     * stripped to letters/digits and turned into a prefix term ("harry pot" → "harry* pot*", implicit
     * AND). Returns null when nothing tokenizable remains (symbols-only input) — caller falls back to
     * the substring LIKE queries. Prefix-token semantics differ slightly from substring LIKE
     * (matches word starts, not mid-word substrings), which is the accepted trade-off for an
     * index-served search over ~220k rows per keystroke (A3).
     */
    private fun ftsQueryFor(q: String): String? {
        val tokens = q.split(Regex("\\s+"))
            .map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    private suspend fun search(q: String): SearchResults {
        val pid = currentProfileId() ?: return SearchResults()
        val c = ctx.value
        if (!c.hasAny) return SearchResults()
        val fts = ftsQueryFor(q)
        // Respect this profile's customizations: hidden items and hidden categories never surface,
        // renames are shown (channels only — movies/series have no per-item rename).
        val custLive = customize.observe(pid, MediaType.LIVE).first()
        val custMovie = customize.observe(pid, MediaType.MOVIE).first()
        val custSeries = customize.observe(pid, MediaType.SERIES).first()
        val hiddenLiveCats = hiddenCategoryIds(c.liveSourceIds, MediaType.LIVE, custLive)
        val hiddenMovieCats = hiddenCategoryIds(c.movieSourceIds, MediaType.MOVIE, custMovie)
        val hiddenSeriesCats = hiddenCategoryIds(c.seriesSourceIds, MediaType.SERIES, custSeries)
        return SearchResults(
            channels = if (c.liveSourceIds.isEmpty()) emptyList()
            else (if (fts != null) channelDao.searchListDetailedFts(fts, c.liveSourceIds, LIMIT) else channelDao.searchListDetailed(q, c.liveSourceIds, LIMIT))
                .filter {
                    CustomizeKeys.channel(it.channel) !in custLive.hiddenItems &&
                        (it.channel.categoryId == null || it.channel.categoryId !in hiddenLiveCats)
                }
                .map { row -> custLive.itemNames[CustomizeKeys.channel(row.channel)]?.let { row.copy(channel = row.channel.copy(name = it)) } ?: row },
            movies = if (c.movieSourceIds.isEmpty()) emptyList()
            else (if (fts != null) movieDao.searchListFts(fts, c.movieSourceIds, LIMIT) else movieDao.searchList(q, c.movieSourceIds, LIMIT))
                .filter {
                    CustomizeKeys.movie(it) !in custMovie.hiddenItems &&
                        (it.categoryId == null || it.categoryId !in hiddenMovieCats)
                },
            series = if (c.seriesSourceIds.isEmpty()) emptyList()
            else (if (fts != null) seriesDao.searchListFts(fts, c.seriesSourceIds, LIMIT) else seriesDao.searchList(q, c.seriesSourceIds, LIMIT))
                .filter {
                    CustomizeKeys.series(it) !in custSeries.hiddenItems &&
                        (it.categoryId == null || it.categoryId !in hiddenSeriesCats)
                },
        )
    }

    /** DB ids of this profile's hidden categories for [type] (so hidden groups drop out of search too). */
    private suspend fun hiddenCategoryIds(
        sourceIds: List<Long>,
        type: MediaType,
        cust: tv.own.owntv.core.customize.SectionCustomizations,
    ): Set<Long> {
        if (cust.hiddenCategories.isEmpty()) return emptySet()
        return categoryDao.observe(sourceIds, type).first()
            .filter { CustomizeKeys.category(it) in cust.hiddenCategories }
            .map { it.id }
            .toSet()
    }

    /** Live channels this profile has favourited — so a search result can show a star and toggle it. */
    val favoriteChannelIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Long-press a channel result to add/remove it from Favorites (no need to open Live TV first). */
    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (pid < 0) return@launch
            if (favoriteChannelIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun playChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val source = sourceDao.getById(channel.sourceId)
            // Stalker stores the portal cmd, not a playable URL — mint one (create_link) before playing.
            val url = if (streamUrlResolver.needsResolve(source)) {
                runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }
                    .onFailure { Log.w(TAG, "stalker resolve failed channelId=${channel.id}", it) }
                    .getOrNull() ?: return@launch
            } else {
                channel.streamUrl
            }
            player.play(url, title = channel.name, logoUrl = channel.displayLogoUrl, isLive = true, userAgent = source?.userAgent)
        }
        record(MediaType.LIVE, channel.id)
        rememberCurrentQuery()
    }

    fun playMovie(movie: MovieEntity) {
        viewModelScope.launch {
            val source = sourceDao.getById(movie.sourceId)
            // Stalker: resolve the stored cmd before EITHER branch — the external player must never
            // receive a raw portal cmd (plan §7), and neither should the in-app engines.
            val url = if (streamUrlResolver.needsResolve(source)) {
                runCatching { streamUrlResolver.resolve(source!!, movie.streamUrl, vod = true) }
                    .onFailure { Log.w(TAG, "stalker resolve failed movieId=${movie.id}", it) }
                    .getOrNull() ?: return@launch
            } else {
                movie.streamUrl
            }
            // Global external-player toggle: same chokepoint behavior as MovieViewModel.play().
            if (settings.externalPlayerMovies.first()) {
                externalPlayerLauncher.launch(url, movie.name)
                return@launch
            }
            player.play(
                url, title = movie.name, year = movie.year?.toString(), isLive = false,
                userAgent = source?.userAgent,
                // P6 — same stable engine-pin identity MovieViewModel uses, so a pin made in one
                // screen applies in the other.
                contentKey = tv.own.owntv.core.player.enginePinKey(movie.sourceId, "MOVIE", movie.remoteId),
            )
        }
        record(MediaType.MOVIE, movie.id)
        rememberCurrentQuery()
    }

    private fun record(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = type, itemId = itemId))
            }.onFailure { t ->
                Log.w(TAG, "record history failed profile=$pid type=$type itemId=$itemId", t)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private companion object {
        const val TAG = "OwnTVHome"
        const val LIMIT = 40
    }
}
