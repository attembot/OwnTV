package tv.own.owntv.features.home

import androidx.compose.runtime.Immutable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ChannelWithWatchedAt
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.TrendingItemEntity
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherContinuationKind
import tv.own.owntv.core.launcher.LauncherRecommendationPlanner
import tv.own.owntv.core.launcher.LauncherWatchNextType
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.core.trending.TrendingMatcher

sealed interface HeroItem {
    val streamUrl: String
    val seekToMs: Long
    val positionMs: Long
    val durationMs: Long
    val watchNextType: LauncherWatchNextType
    val lastEngagementAt: Long

    /** Playlist this item came from — used to give the preview the same source-wide User-Agent the
     *  player uses. */
    val sourceId: Long

    /** This item's own `Key: Value` request headers (M3U), or null. Same reason as [sourceId]. */
    val httpHeaders: String?

    data class MovieHero(
        val movie: MovieEntity,
        val item: LauncherContinuationItem,
    ) : HeroItem {
        override val sourceId: Long = movie.sourceId
        override val httpHeaders: String? = movie.httpHeaders
        override val streamUrl: String = movie.streamUrl
        override val seekToMs: Long = (item.positionMs - 10_000L).coerceAtLeast(0L)
        override val positionMs: Long = item.positionMs
        override val durationMs: Long = item.durationMs
        override val watchNextType: LauncherWatchNextType = item.watchNextType
        override val lastEngagementAt: Long = item.lastEngagementAt
    }

    data class SeriesHero(
        val series: SeriesEntity,
        val episode: EpisodeEntity,
        val item: LauncherContinuationItem,
    ) : HeroItem {
        override val sourceId: Long = series.sourceId // episodes hang off the series, which owns the source
        override val httpHeaders: String? = episode.httpHeaders
        override val streamUrl: String = episode.streamUrl
        override val seekToMs: Long = if (item.watchNextType == LauncherWatchNextType.NEXT) {
            0L
        } else {
            (item.positionMs - 10_000L).coerceAtLeast(0L)
        }
        override val positionMs: Long = item.positionMs
        override val durationMs: Long = item.durationMs
        override val watchNextType: LauncherWatchNextType = item.watchNextType
        override val lastEngagementAt: Long = item.lastEngagementAt
    }

    data class LiveHero(
        val channel: ChannelEntity,
        val watchedAt: Long,
    ) : HeroItem {
        override val sourceId: Long = channel.sourceId
        override val httpHeaders: String? = channel.httpHeaders
        override val streamUrl: String = channel.streamUrl
        override val seekToMs: Long = 0L
        override val positionMs: Long = 0L
        override val durationMs: Long = 0L
        override val watchNextType: LauncherWatchNextType = LauncherWatchNextType.CONTINUE
        override val lastEngagementAt: Long = watchedAt
    }
}

@Immutable
data class HomeHeroMetadata(
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val plot: String? = null,
)

@Immutable
sealed interface TrendingHomeItem {
    val snapshot: TrendingItemEntity
    val stableKey: String get() = "${snapshot.mediaType.name}:${snapshot.tmdbId}"

    data class Movie(
        override val snapshot: TrendingItemEntity,
        val movie: MovieEntity,
    ) : TrendingHomeItem

    data class Series(
        override val snapshot: TrendingItemEntity,
        val series: SeriesEntity,
    ) : TrendingHomeItem
}

@Immutable
data class TrendingDetailsMetadata(
    val cache: MetadataCacheEntity?,
    val tmdbWins: Boolean,
)

@Immutable
data class HomeUiState(
    val trendingItems: List<TrendingHomeItem> = emptyList(),
    val activeTrendingIndex: Int = 0,
    val trendingPreferredLanguage: String = "EN",
    val trendingSeasonCounts: Map<Long, Int> = emptyMap(),
    val heroItems: List<HeroItem> = emptyList(),
    val activeHeroIndex: Int = 0,
    val continueMovies: List<LauncherContinuationItem> = emptyList(),
    val continueSeries: List<LauncherContinuationItem> = emptyList(),
    val heroMetadata: Map<String, HomeHeroMetadata> = emptyMap(),
    val continuationArtwork: Map<String, String> = emptyMap(),
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val config: HomeConfig = HomeConfig(),
    val recentGuide: GuideSliceState = GuideSliceState(),
    val favoriteGuide: GuideSliceState = GuideSliceState(),
    /**
     * True until the first [HomeViewModel.loadHomeData] completes. Home's queries are profile-scoped and
     * already indexed, but on a cold boot their first reads come off slow eMMC (pages not yet in the OS
     * page cache) — that's the ~half-second gap between the shell painting and `home-data`. While that
     * runs we render a skeleton so the landing screen paints its *structure* instantly instead of flashing
     * the empty state (which looks wrong for a user who does have history). Flips to false the moment real
     * data publishes, and stays false thereafter (refreshes don't re-skeleton).
     */
    val isLoading: Boolean = true,
)

@Immutable
data class GuideSliceState(
    val channels: List<ChannelEntity> = emptyList(),
    val programmes: Map<Long, List<EpgProgrammeEntity>> = emptyMap(),
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val now: Long = 0L,
) {
    val hasContent: Boolean
        get() = channels.isNotEmpty()
}

/** What the shared top-bar Continue chip points at (Batch 7). */
enum class ContinueKind { LIVE, MOVIE, EPISODE }
enum class ContinueAction { RESUME, PLAY, NEXT_UP, LAST_CHANNEL }

/** A single resumable target: semantic action and display name. */
@Immutable
data class ContinueTarget(
    val kind: ContinueKind,
    /** Display name (movie/series/channel). */
    val name: String,
    val action: ContinueAction,
    val channelId: Long = -1L,
    val movieId: Long = -1L,
    val seriesId: Long = -1L,
    val episodeId: Long = -1L,
    val positionMs: Long = 0L,
)

private const val SLICE_WINDOW_MS = 360 * 60_000L
private const val RECENT_LIVE_ROW_LIMIT = 20

class HomeViewModel(
    private val planner: LauncherRecommendationPlanner,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val heroPreviewEngine: HeroPreviewEngine,
    private val epgSourceStore: EpgSourceStore,
    private val epgDao: EpgDao,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val progressDao: tv.own.owntv.core.database.dao.ProgressDao,
    private val metadata: MetadataRepository,
    private val trendingDao: TrendingDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Room invalidates this after the worker atomically replaces a snapshot, so Home updates even
        // when the user stays on the screen throughout a background post-sync refresh.
        viewModelScope.launch {
            trendingDao.observeAllItems().collect {
                currentProfileId()?.let { profileId -> loadHomeData(profileId) }
            }
        }
    }

    // --- Batch 7: the single most-recent resumable item, for the shared top-bar Continue chip. ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val continueTarget: StateFlow<ContinueTarget?> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(null)
            else historyDao.observeMostRecent(pid).map { h -> h?.let { resolveContinue(pid, it) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun resolveContinue(
        pid: Long,
        h: tv.own.owntv.core.database.entity.WatchHistoryEntity,
    ): ContinueTarget? = when (h.mediaType) {
        MediaType.MOVIE -> movieDao.getById(h.itemId)?.let { m ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, m.categoryId, profileDao, categoryDao)) return@let null
            val pos = progressDao.get(pid, MediaType.MOVIE, m.id)?.positionMs ?: 0L
            ContinueTarget(ContinueKind.MOVIE, m.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.PLAY, movieId = m.id, positionMs = pos)
        }
        MediaType.EPISODE -> seriesDao.getEpisodeById(h.itemId)?.let { ep ->
            seriesDao.getSeriesById(ep.seriesId)?.let { s ->
                if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, s.categoryId, profileDao, categoryDao)) return@let null
                val pos = progressDao.get(pid, MediaType.EPISODE, ep.id)?.positionMs ?: 0L
                ContinueTarget(ContinueKind.EPISODE, s.name, if (pos > 0) ContinueAction.RESUME else ContinueAction.NEXT_UP, seriesId = ep.seriesId, episodeId = ep.id, positionMs = pos)
            }
        }
        MediaType.LIVE -> channelDao.getById(h.itemId)?.let { c ->
            if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(pid, c.categoryId, profileDao, categoryDao)) return@let null
            ContinueTarget(ContinueKind.LIVE, c.name, ContinueAction.LAST_CHANNEL, channelId = c.id)
        }
        else -> null
    }

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)
    private val resolvingHeroKeys = mutableSetOf<String>()
    private val resolvingContinuationArtworkKeys = mutableSetOf<String>()

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> = combine(_heroFocused, _previewEnabled, _uiState) { focused, enabled, state ->
        focused && enabled && state.heroItems.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreviewEnabled(enabled: Boolean) {
        _previewEnabled.value = enabled
    }

    fun setHeroFocused(focused: Boolean) {
        _heroFocused.value = focused
    }

    fun navigateHero(index: Int) {
        val items = _uiState.value.heroItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeHeroIndex = index)
    }

    fun navigateTrending(index: Int) {
        val items = _uiState.value.trendingItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeTrendingIndex = index)
    }

    /** Re-resolve the exact saved provider row immediately before an action in case a sync replaced it. */
    suspend fun revalidateTrendingItem(item: TrendingHomeItem): TrendingHomeItem? = withContext(Dispatchers.IO) {
        when (item) {
            is TrendingHomeItem.Movie -> movieDao.getById(item.movie.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(movie = it) }
            is TrendingHomeItem.Series -> seriesDao.getSeriesById(item.series.id)
                ?.takeIf { it.sourceId == item.snapshot.sourceId }
                ?.let { item.copy(series = it) }
        }
    }

    /** Load the same full cached TMDB payload used by Movies/Series details, using Trending's exact id. */
    suspend fun resolveTrendingDetails(item: TrendingHomeItem): TrendingDetailsMetadata = withContext(Dispatchers.IO) {
        val config = settings.metadataConfig()
        val cache = when (item) {
            is TrendingHomeItem.Movie -> metadata.resolveKnownMovie(item.movie, item.snapshot.tmdbId)
            is TrendingHomeItem.Series -> metadata.resolveKnownSeries(item.series, item.snapshot.tmdbId)
        }
        TrendingDetailsMetadata(cache = cache, tmdbWins = config.mode.tmdbWins)
    }

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
        resolveHeroMetadata(index)
    }

    fun resolveSeriesContinuationArtwork(item: LauncherContinuationItem) {
        if (item.kind != LauncherContinuationKind.EPISODE) return
        if (_uiState.value.continuationArtwork.containsKey(item.stableKey)) return
        if (!resolvingContinuationArtworkKeys.add(item.stableKey)) return

        viewModelScope.launch {
            try {
                delay(250)
                val art = withContext(Dispatchers.IO) { seriesContinuationArtwork(item) } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    continuationArtwork = _uiState.value.continuationArtwork + (item.stableKey to art),
                )
            } finally {
                resolvingContinuationArtworkKeys.remove(item.stableKey)
            }
        }
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    /**
     * Start the hero preview for [hero] with the SAME request identity the player would use: the
     * playlist's User-Agent plus the item's own headers. Without them a source that needs a custom UA or
     * a Referer had a home screen that 403'd on every preview while the item itself played fine.
     */
    suspend fun startPreview(hero: HeroItem) {
        val ua = withContext(Dispatchers.IO) {
            runCatching { sourceDao.getById(hero.sourceId)?.userAgent }.getOrNull()
        }
        heroPreviewEngine.play(hero.streamUrl, hero.seekToMs, ua, hero.httpHeaders)
    }

    fun refresh() {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            loadHomeData(pid)
        }
    }

    private suspend fun loadHomeData(profileId: Long) {
        val previous = _uiState.value
        val state = withContext(Dispatchers.IO) {
            val config = settings.homeConfig(profileId).first()
            val metadataConfig = settings.metadataConfig()
            val preferredLanguage = metadataConfig.resolvedLanguage.substringBefore('-').ifBlank { "en" }.uppercase()
            // Active-playlist filter + per-section enabledScope: Home rails never show Off sections.
            val liveIds = activeSourceIds(settings, sourceDao, profileId, MediaType.LIVE).toSet()
            val movieIds = activeSourceIds(settings, sourceDao, profileId, MediaType.MOVIE).toSet()
            val seriesIds = activeSourceIds(settings, sourceDao, profileId, MediaType.SERIES).toSet()
            val allIds = sourceDao.sourceIdsForProfile(profileId).toSet()

            // Hidden items / hidden categories (per profile) never surface on Home either.
            val hidden = hiddenState(profileId, allIds.toList())
            val trending = buildTrendingItems(
                movieSourceIds = movieIds,
                seriesSourceIds = seriesIds,
                hidden = hidden,
                sourceOrder = (movieIds + seriesIds).distinct().toList(),
                metadataEnabled = metadataConfig.enabled,
                preferredLanguage = preferredLanguage,
            )
            val trendingSeriesIds = trending.filterIsInstance<TrendingHomeItem.Series>().map { it.series.id }
            val trendingSeasonCounts = if (trendingSeriesIds.isEmpty()) emptyMap() else {
                seriesDao.storedSeasonCounts(trendingSeriesIds).associate { it.seriesId to it.seasonCount }
            }

            val allItems = planner.buildContinuationItems(profileId)
            val items = allItems
                .filter { item ->
                    val sid = continuationSourceId(item) ?: return@filter false
                    when (item.kind) {
                        LauncherContinuationKind.MOVIE -> sid in movieIds
                        LauncherContinuationKind.EPISODE -> sid in seriesIds
                        LauncherContinuationKind.LIVE -> sid in liveIds
                    }
                }
                .filterNot { isContinuationHidden(it, hidden) }
            val movies = items.filter { it.kind == LauncherContinuationKind.MOVIE }
            val series = items.filter { it.kind == LauncherContinuationKind.EPISODE }
            val liveWithTs = recentlyWatchedLive(profileId, liveIds, filtering = true, RECENT_LIVE_ROW_LIMIT)
                .filterNot { isChannelHidden(it.channel, hidden) }
            val live = liveWithTs.map { it.channel }
            val favLive = channelDao.favoritesListAlpha(profileId).first()
                .filter { c -> c.sourceId in liveIds }
                .filterNot { isChannelHidden(it, hidden) }
            val heroItems = buildHeroItems(items, liveWithTs, config)
            val recentGuide = if (HomeRow.RECENT_CHANNELS in config.visibleOrder && config.recentLiveMode == HomeLiveRowMode.ON_NOW) {
                buildLiveGuide(profileId, liveIds, live)
            } else {
                GuideSliceState()
            }
            val favoriteGuide = if (HomeRow.FAVORITE_CHANNELS in config.visibleOrder && config.favoriteLiveMode == HomeLiveRowMode.ON_NOW) {
                buildLiveGuide(profileId, liveIds, favLive)
            } else {
                GuideSliceState()
            }

            HomeUiState(
                trendingItems = trending,
                activeTrendingIndex = previous.activeTrendingIndex.coerceIn(0, (trending.size - 1).coerceAtLeast(0)),
                trendingPreferredLanguage = preferredLanguage,
                trendingSeasonCounts = trendingSeasonCounts,
                heroItems = heroItems,
                activeHeroIndex = 0,
                continueMovies = movies,
                continueSeries = series,
                heroMetadata = previous.heroMetadata.filterKeys { key -> heroItems.any { homeHeroKey(it) == key } },
                continuationArtwork = previous.continuationArtwork.filterKeys { key -> series.any { it.stableKey == key } },
                recentLive = live,
                favoriteLive = favLive,
                config = config,
                recentGuide = recentGuide,
                favoriteGuide = favoriteGuide,
                isLoading = false,
            )
        }
        _uiState.value = state
        tv.own.owntv.Perf.stamp("home-data")
    }

    private fun resolveHeroMetadata(index: Int) {
        val item = _uiState.value.heroItems.getOrNull(index) ?: return
        if (item is HeroItem.LiveHero) return

        val key = homeHeroKey(item)
        if (_uiState.value.heroMetadata.containsKey(key)) return
        if (!resolvingHeroKeys.add(key)) return

        viewModelScope.launch {
            try {
                delay(250)
                val current = _uiState.value.heroItems.getOrNull(_uiState.value.activeHeroIndex)
                if (current == null || homeHeroKey(current) != key) return@launch

                val resolved = withContext(Dispatchers.IO) { heroMetadata(item) } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    heroMetadata = _uiState.value.heroMetadata + (key to resolved),
                )
            } finally {
                resolvingHeroKeys.remove(key)
            }
        }
    }

    private suspend fun heroMetadata(item: HeroItem): HomeHeroMetadata? = when (item) {
        is HeroItem.MovieHero -> metadata.resolveMovie(item.movie)?.let { cache ->
            HomeHeroMetadata(
                backdropUrl = MetadataImages.backdrop(cache.backdropPath, size = "w1280"),
                logoUrl = MetadataImages.logo(cache.logoPath),
                plot = cache.overview?.takeIf { it.isNotBlank() },
            )
        }
        is HeroItem.SeriesHero -> {
            val show = metadata.resolveSeries(item.series)
            val episode = if (show?.overview.isNullOrBlank()) metadata.resolveEpisode(item.series, item.episode) else null
            when {
                show != null || episode != null -> HomeHeroMetadata(
                    backdropUrl = MetadataImages.backdrop(show?.backdropPath, size = "w1280")
                        ?: MetadataImages.backdrop(episode?.backdropPath ?: episode?.posterPath, size = "w1280"),
                    logoUrl = MetadataImages.logo(show?.logoPath),
                    plot = show?.overview?.takeIf { it.isNotBlank() } ?: episode?.overview?.takeIf { it.isNotBlank() },
                )
                else -> null
            }
        }
        is HeroItem.LiveHero -> null
    }

    private suspend fun seriesContinuationArtwork(item: LauncherContinuationItem): String? {
        val episode = seriesDao.getEpisodeById(item.targetItemId) ?: return null
        val series = seriesDao.getSeriesById(episode.seriesId) ?: return null
        val episodeMeta = metadata.resolveEpisode(series, episode)
        val showMeta = if (episodeMeta == null) metadata.resolveSeries(series) else null
        return MetadataImages.backdrop(episodeMeta?.backdropPath ?: episodeMeta?.posterPath, size = "w780")
            ?: MetadataImages.backdrop(showMeta?.backdropPath, size = "w780")
            ?: series.backdropUrl?.takeIf { it.isNotBlank() }
            ?: series.posterUrl?.takeIf { it.isNotBlank() }
    }

    /** This profile's hide customizations across all three sections, with category keys resolved to ids. */
    private data class HiddenState(
        val live: SectionCustomizations,
        val movie: SectionCustomizations,
        val series: SectionCustomizations,
        val liveCats: Set<Long>,
        val movieCats: Set<Long>,
        val seriesCats: Set<Long>,
    ) {
        val isEmpty: Boolean
            get() = live.hiddenItems.isEmpty() && movie.hiddenItems.isEmpty() && series.hiddenItems.isEmpty() &&
                liveCats.isEmpty() && movieCats.isEmpty() && seriesCats.isEmpty()
    }

    private suspend fun hiddenState(profileId: Long, sourceIds: List<Long>): HiddenState {
        val live = customize.observe(profileId, MediaType.LIVE).first()
        val movie = customize.observe(profileId, MediaType.MOVIE).first()
        val series = customize.observe(profileId, MediaType.SERIES).first()
        val isKidsProfile = profileDao.getById(profileId)?.isKids == true
        suspend fun catIds(type: MediaType, hiddenKeys: Set<String>): Set<Long> {
            if ((hiddenKeys.isEmpty() && !isKidsProfile) || sourceIds.isEmpty()) return emptySet()
            return tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
                categoryDao.observe(sourceIds, type).first(),
                hiddenKeys,
                isKidsProfile,
            )
        }
        return HiddenState(
            live = live, movie = movie, series = series,
            liveCats = catIds(MediaType.LIVE, live.hiddenCategories),
            movieCats = catIds(MediaType.MOVIE, movie.hiddenCategories),
            seriesCats = catIds(MediaType.SERIES, series.hiddenCategories),
        )
    }

    private fun isChannelHidden(ch: ChannelEntity, h: HiddenState): Boolean =
        CustomizeKeys.channel(ch) in h.live.hiddenItems || (ch.categoryId != null && ch.categoryId in h.liveCats)

    private suspend fun buildTrendingItems(
        movieSourceIds: Set<Long>,
        seriesSourceIds: Set<Long>,
        hidden: HiddenState,
        sourceOrder: List<Long>,
        metadataEnabled: Boolean,
        preferredLanguage: String,
    ): List<TrendingHomeItem> {
        if (!metadataEnabled) return emptyList()
        val sourceIds = (movieSourceIds + seriesSourceIds).toList()
        if (sourceIds.isEmpty()) return emptyList()
        val snapshots = trendingDao.getItemsForSources(sourceIds)
        if (snapshots.isEmpty()) return emptyList()

        val movieRows = movieDao.getByIds(snapshots.filter { it.mediaType == MediaType.MOVIE }.map { it.providerItemId })
            .associateBy { it.id }
        val seriesRows = seriesDao.getSeriesByIds(snapshots.filter { it.mediaType == MediaType.SERIES }.map { it.providerItemId })
            .associateBy { it.id }
        val resolved = snapshots.mapNotNull { snapshot ->
            when (snapshot.mediaType) {
                MediaType.MOVIE -> movieRows[snapshot.providerItemId]
                    ?.takeIf { it.sourceId in movieSourceIds }
                    ?.takeUnless { CustomizeKeys.movie(it) in hidden.movie.hiddenItems || (it.categoryId != null && it.categoryId in hidden.movieCats) }
                    ?.let { TrendingHomeItem.Movie(snapshot, it) }
                MediaType.SERIES -> seriesRows[snapshot.providerItemId]
                    ?.takeIf { it.sourceId in seriesSourceIds }
                    ?.takeUnless { CustomizeKeys.series(it) in hidden.series.hiddenItems || (it.categoryId != null && it.categoryId in hidden.seriesCats) }
                    ?.let { TrendingHomeItem.Series(snapshot, it) }
                else -> null
            }
        }
        val sourceRanks = sourceOrder.withIndex().associate { it.value to it.index }
        val deduplicated = resolved
            .groupBy { it.snapshot.mediaType to it.snapshot.tmdbId }
            .values
            .map { variants ->
                variants.minWith(
                    compareBy<TrendingHomeItem> { homeLanguageRank(it.snapshot.providerLanguage, preferredLanguage) }
                        .thenByDescending { homeQualityRank(it.snapshot.advertisedQuality) }
                        .thenBy { sourceRanks[it.snapshot.sourceId] ?: Int.MAX_VALUE }
                        .thenBy { it.snapshot.position },
                )
            }
        val movies = deduplicated.filter { it.snapshot.mediaType == MediaType.MOVIE }
            .sortedBy { it.snapshot.trendingRank }
            .take(TrendingMatcher.MAX_PER_MEDIA_TYPE)
        val series = deduplicated.filter { it.snapshot.mediaType == MediaType.SERIES }
            .sortedBy { it.snapshot.trendingRank }
            .take(TrendingMatcher.MAX_PER_MEDIA_TYPE)
        val seriesTarget = if (movies.size >= 5) 5 else TrendingMatcher.MAX_TOTAL - movies.size
        val selectedSeries = series.take(seriesTarget)
        val selectedMovies = movies.take(TrendingMatcher.MAX_TOTAL - selectedSeries.size)
        val interleaved = buildList {
            for (index in 0 until maxOf(selectedMovies.size, selectedSeries.size)) {
                selectedMovies.getOrNull(index)?.let(::add)
                selectedSeries.getOrNull(index)?.let(::add)
            }
        }.take(TrendingMatcher.MAX_TOTAL)
        return interleaved.takeIf { it.size >= TrendingDao.MIN_ELIGIBLE_ITEMS }.orEmpty()
    }

    private fun homeLanguageRank(language: String?, preferred: String): Int = when {
        language == preferred -> 0
        language == "EN" -> 1
        language == null -> 2
        else -> 3
    }

    private fun homeQualityRank(quality: String?): Int = when (quality) {
        "8K" -> 5
        "4K", "4K UHD" -> 4
        "FHD", "1080p FHD" -> 3
        "HD", "720p HD" -> 2
        "SD" -> 1
        else -> 0
    }

    private suspend fun isContinuationHidden(item: LauncherContinuationItem, h: HiddenState): Boolean {
        if (h.isEmpty) return false
        return when (item.kind) {
            LauncherContinuationKind.MOVIE -> movieDao.getById(item.sourceItemId)?.let {
                CustomizeKeys.movie(it) in h.movie.hiddenItems || (it.categoryId != null && it.categoryId in h.movieCats)
            } ?: false
            LauncherContinuationKind.EPISODE -> seriesDao.getEpisodeById(item.targetItemId)?.let { ep ->
                seriesDao.getSeriesById(ep.seriesId)?.let { s ->
                    CustomizeKeys.series(s) in h.series.hiddenItems || (s.categoryId != null && s.categoryId in h.seriesCats)
                }
            } ?: false
            LauncherContinuationKind.LIVE -> channelDao.getById(item.sourceItemId)?.let { isChannelHidden(it, h) } ?: false
        }
    }

    /** The playlist a continuation item belongs to, for the active-playlist filter (null if it's gone). */
    private suspend fun continuationSourceId(item: LauncherContinuationItem): Long? = when (item.kind) {
        LauncherContinuationKind.MOVIE -> movieDao.getById(item.sourceItemId)?.sourceId
        LauncherContinuationKind.EPISODE ->
            seriesDao.getEpisodeById(item.targetItemId)?.let { seriesDao.getSeriesById(it.seriesId)?.sourceId }
        LauncherContinuationKind.LIVE -> channelDao.getById(item.sourceItemId)?.sourceId
    }

    private suspend fun buildHeroItems(
        continuationItems: List<LauncherContinuationItem>,
        liveChannels: List<ChannelWithWatchedAt>,
        config: HomeConfig,
    ): List<HeroItem> {
        data class Candidate(val engagedAt: Long, val resolve: suspend () -> HeroItem?)

        val candidates = mutableListOf<Candidate>()

        continuationItems.forEach { item ->
            when (item.kind) {
                LauncherContinuationKind.MOVIE -> if (config.heroIncludeMovies) {
                    candidates += Candidate(item.lastEngagementAt) {
                        val movie = movieDao.getById(item.sourceItemId) ?: return@Candidate null
                        HeroItem.MovieHero(movie, item)
                    }
                }
                LauncherContinuationKind.EPISODE -> if (config.heroIncludeSeries) {
                    candidates += Candidate(item.lastEngagementAt) {
                        val episode = seriesDao.getEpisodeById(item.targetItemId) ?: return@Candidate null
                        val series = seriesDao.getSeriesById(episode.seriesId) ?: return@Candidate null
                        HeroItem.SeriesHero(series, episode, item)
                    }
                }
                LauncherContinuationKind.LIVE -> Unit
            }
        }

        if (config.heroIncludeLive) {
            liveChannels.forEach { watched ->
                candidates += Candidate(watched.watchedAt) {
                    HeroItem.LiveHero(watched.channel, watched.watchedAt)
                }
            }
        }

        val result = mutableListOf<HeroItem>()
        for (candidate in candidates.sortedByDescending { it.engagedAt }) {
            if (result.size >= 10) break
            candidate.resolve()?.let { result += it }
        }
        return result
    }

    private fun homeHeroKey(item: HeroItem): String = when (item) {
        is HeroItem.MovieHero -> "movie:${item.movie.id}"
        is HeroItem.SeriesHero -> "episode:${item.episode.id}"
        is HeroItem.LiveHero -> "live:${item.channel.id}"
    }

    private suspend fun recentlyWatchedLive(
        profileId: Long,
        activeIds: Set<Long>,
        filtering: Boolean,
        limit: Int,
    ): List<ChannelWithWatchedAt> =
        if (filtering) {
            channelDao.recentlyWatchedWithTimestampFiltered(profileId, activeIds.toList(), limit).first()
        } else {
            channelDao.recentlyWatchedWithTimestamp(profileId, limit).first()
        }

    private suspend fun buildLiveGuide(
        profileId: Long,
        activeIds: Set<Long>,
        candidates: List<ChannelEntity>,
    ): GuideSliceState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val windowStart = floorToHalfHour(now)
        val windowEnd = windowStart + SLICE_WINDOW_MS
        val channels = candidates
        if (channels.isEmpty()) return@withContext GuideSliceState(now = now, windowStart = windowStart, windowEnd = windowEnd)

        val epgIds = epgSourceStore.getAll().map { it.id }
        val sourceIds = (activeIds.toList() + epgIds).distinct()
        val customizations = customize.observe(profileId, tv.own.owntv.core.model.MediaType.LIVE).first()
        val programmes = linkedMapOf<Long, List<EpgProgrammeEntity>>()
        // Channels with a guide shift query a moved window and get moved rows back, so the rail's
        // progress bars line up with what's actually on. Grouped, so no shift = one query as before.
        val globalShift = settings.epgOffsetMinutes.first()
        val channelKeys = channels.mapNotNull { channel ->
            val key = customizations.epgMatches[CustomizeKeys.channel(channel)] ?: channel.epgChannelId
            key?.trim()?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?.let { Triple(channel.id, it, tv.own.owntv.core.epg.EpgShift.minutesFor(customizations, channel, globalShift)) }
        }
        val collected = HashMap<Long, List<EpgProgrammeEntity>>()
        for ((shift, group) in channelKeys.groupBy { it.third }) {
            val from = tv.own.owntv.core.epg.EpgShift.toStored(windowStart, shift)
            val to = tv.own.owntv.core.epg.EpgShift.toStored(windowEnd, shift)
            val rowsByKey = group
                .map { it.second }
                .distinct()
                .chunked(400)
                .flatMap { epgKeys -> epgDao.programmeSummariesForChannels(sourceIds, epgKeys, from, to) }
                .groupBy { it.epgChannelId }
            for ((channelId, epgKey, _) in group) {
                rowsByKey[epgKey]?.takeIf { it.isNotEmpty() }
                    ?.let { collected[channelId] = tv.own.owntv.core.epg.EpgShift.apply(it, shift) }
            }
        }
        // Back into channel order — the rail renders straight off this map's iteration order.
        for (channel in channels) collected[channel.id]?.let { programmes[channel.id] = it }

        GuideSliceState(
            channels = channels,
            programmes = programmes,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
        )
    }

    private fun floorToHalfHour(ms: Long): Long {
        val halfHourMs = 30 * 60_000L
        return ms / halfHourMs * halfHourMs
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

}
