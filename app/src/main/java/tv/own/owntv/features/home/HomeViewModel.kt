package tv.own.owntv.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherContinuationKind
import tv.own.owntv.core.launcher.LauncherRecommendationPlanner
import tv.own.owntv.core.launcher.LauncherWatchNextType
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.HeroPreviewEngine

sealed interface HeroItem {
    val streamUrl: String
    val seekToMs: Long
    val positionMs: Long
    val durationMs: Long
    val watchNextType: LauncherWatchNextType
    val lastEngagementAt: Long

    data class MovieHero(
        val movie: MovieEntity,
        val item: LauncherContinuationItem,
    ) : HeroItem {
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
        override val streamUrl: String = channel.streamUrl
        override val seekToMs: Long = 0L
        override val positionMs: Long = 0L
        override val durationMs: Long = 0L
        override val watchNextType: LauncherWatchNextType = LauncherWatchNextType.CONTINUE
        override val lastEngagementAt: Long = watchedAt
    }
}

data class HomeUiState(
    val heroItems: List<HeroItem> = emptyList(),
    val activeHeroIndex: Int = 0,
    val continueMovies: List<LauncherContinuationItem> = emptyList(),
    val continueSeries: List<LauncherContinuationItem> = emptyList(),
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

private const val SLICE_WINDOW_MS = 360 * 60_000L
private const val LIVE_ROW_CHANNEL_LIMIT = 20
private const val LIVE_ROW_EPG_LIMIT = 10

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
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> = combine(_heroFocused, _previewEnabled, _uiState) { focused, enabled, state ->
        focused && enabled && state.heroItems.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    fun refresh() {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            loadHomeData(pid)
        }
    }

    private suspend fun loadHomeData(profileId: Long) {
        val state = withContext(Dispatchers.IO) {
            val config = settings.homeConfig(profileId).first()
            // Active-playlist filter: when a "Default" playlist is chosen, the home rails narrow to it too
            // (Continue Watching / Recent / Favorites). No default → activeIds == all profile ids → no-op.
            val activeIds = activeSourceIds(settings, sourceDao, profileId).toSet()
            val allIds = sourceDao.sourceIdsForProfile(profileId).toSet()
            val filtering = activeIds != allIds

            // Hidden items / hidden categories (per profile) never surface on Home either.
            val hidden = hiddenState(profileId, allIds.toList())

            val allItems = planner.buildContinuationItems(profileId)
            val items = (if (!filtering) allItems else allItems.filter { continuationSourceId(it) in activeIds })
                .filterNot { isContinuationHidden(it, hidden) }
            val movies = items.filter { it.kind == LauncherContinuationKind.MOVIE }
            val series = items.filter { it.kind == LauncherContinuationKind.EPISODE }
            val liveWithTs = recentlyWatchedLive(profileId, activeIds, filtering, LIVE_ROW_CHANNEL_LIMIT)
                .filterNot { isChannelHidden(it.channel, hidden) }
            val live = liveWithTs.map { it.channel }
            val favLive = channelDao.favoritesListAlpha(profileId, 50).first()
                .let { if (!filtering) it else it.filter { c -> c.sourceId in activeIds } }
                .filterNot { isChannelHidden(it, hidden) }
                .take(LIVE_ROW_CHANNEL_LIMIT)
            val heroItems = buildHeroItems(items, liveWithTs, config)
            val recentGuide = if (HomeRow.RECENT_CHANNELS in config.visibleOrder && config.recentLiveMode == HomeLiveRowMode.ON_NOW) {
                buildLiveGuide(profileId, activeIds, live)
            } else {
                GuideSliceState()
            }
            val favoriteGuide = if (HomeRow.FAVORITE_CHANNELS in config.visibleOrder && config.favoriteLiveMode == HomeLiveRowMode.ON_NOW) {
                buildLiveGuide(profileId, activeIds, favLive)
            } else {
                GuideSliceState()
            }

            HomeUiState(
                heroItems = heroItems,
                activeHeroIndex = 0,
                continueMovies = movies,
                continueSeries = series,
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
        suspend fun catIds(type: MediaType, hiddenKeys: Set<String>): Set<Long> {
            if (hiddenKeys.isEmpty() || sourceIds.isEmpty()) return emptySet()
            return categoryDao.observe(sourceIds, type).first()
                .filter { CustomizeKeys.category(it) in hiddenKeys }
                .map { it.id }
                .toSet()
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
        val channels = candidates.take(LIVE_ROW_CHANNEL_LIMIT)
        if (channels.isEmpty()) return@withContext GuideSliceState(now = now, windowStart = windowStart, windowEnd = windowEnd)

        val epgIds = epgSourceStore.getAll().map { it.id }
        val sourceIds = (activeIds.toList() + epgIds).distinct()
        val customizations = customize.observe(profileId, tv.own.owntv.core.model.MediaType.LIVE).first()
        val programmes = linkedMapOf<Long, List<EpgProgrammeEntity>>()

        for (channel in channels.take(LIVE_ROW_EPG_LIMIT)) {
            val key = customizations.epgMatches[CustomizeKeys.channel(channel)]
                ?: channel.epgChannelId
            val epgKey = key?.trim()?.lowercase().orEmpty()
            if (epgKey.isBlank()) continue

            val rows = epgDao.programmeSummariesForChannel(sourceIds, epgKey, windowStart, windowEnd)
            if (rows.isNotEmpty()) {
                programmes[channel.id] = rows
            }
        }

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
