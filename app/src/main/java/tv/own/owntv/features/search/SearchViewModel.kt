@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.search

import android.util.Log
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
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
data class SearchResults(
    val channels: List<tv.own.owntv.core.database.dao.ChannelSearchResult> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
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
) : ViewModel() {

    /** Global "External player" toggle — the screen must NOT open the fullscreen in-app player when on. */
    val externalPlayerOn: kotlinx.coroutines.flow.StateFlow<Boolean> = settings.externalPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Search
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sourceIds) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

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

    fun setQuery(q: String) { _query.value = q }

    private suspend fun search(q: String): SearchResults {
        val pid = currentProfileId() ?: return SearchResults()
        val ids = ctx.value.sourceIds.ifEmpty { return SearchResults() }
        // Respect this profile's customizations: hidden items and hidden categories never surface,
        // renames are shown (channels only — movies/series have no per-item rename).
        val custLive = customize.observe(pid, MediaType.LIVE).first()
        val custMovie = customize.observe(pid, MediaType.MOVIE).first()
        val custSeries = customize.observe(pid, MediaType.SERIES).first()
        val hiddenLiveCats = hiddenCategoryIds(ids, MediaType.LIVE, custLive)
        val hiddenMovieCats = hiddenCategoryIds(ids, MediaType.MOVIE, custMovie)
        val hiddenSeriesCats = hiddenCategoryIds(ids, MediaType.SERIES, custSeries)
        return SearchResults(
            channels = channelDao.searchListDetailed(q, ids, LIMIT)
                .filter {
                    CustomizeKeys.channel(it.channel) !in custLive.hiddenItems &&
                        (it.channel.categoryId == null || it.channel.categoryId !in hiddenLiveCats)
                }
                .map { row -> custLive.itemNames[CustomizeKeys.channel(row.channel)]?.let { row.copy(channel = row.channel.copy(name = it)) } ?: row },
            movies = movieDao.searchList(q, ids, LIMIT)
                .filter {
                    CustomizeKeys.movie(it) !in custMovie.hiddenItems &&
                        (it.categoryId == null || it.categoryId !in hiddenMovieCats)
                },
            series = seriesDao.searchList(q, ids, LIMIT)
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
            val sourceUa = sourceDao.getById(channel.sourceId)?.userAgent
            player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true, userAgent = sourceUa)
        }
        record(MediaType.LIVE, channel.id)
    }

    fun playMovie(movie: MovieEntity) {
        viewModelScope.launch {
            // Global external-player toggle: same chokepoint behavior as MovieViewModel.play().
            if (settings.externalPlayer.first()) {
                externalPlayerLauncher.launch(movie.streamUrl, movie.name)
                return@launch
            }
            val sourceUa = sourceDao.getById(movie.sourceId)?.userAgent
            player.play(movie.streamUrl, title = movie.name, year = movie.year?.toString(), isLive = false, userAgent = sourceUa)
        }
        record(MediaType.MOVIE, movie.id)
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
