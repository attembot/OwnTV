@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.MediaMeta
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaylistItem
import tv.own.owntv.ui.components.OwnTVIcon

class SeriesViewModel(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val contentOrderDao: ContentOrderDao,
    private val metadata: tv.own.owntv.core.metadata.MetadataRepository,
    private val externalPlayerLauncher: tv.own.owntv.core.player.ExternalPlayerLauncher,
) : ViewModel() {

    data class SeriesMoveState(val items: List<SeriesEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<SeriesMoveState?>(null)
    val moveState: StateFlow<SeriesMoveState?> = _moveState.asStateFlow()

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Series
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sourceIds) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.SERIES).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** This profile's hide/rename/reorder customizations for Series. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.SERIES)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /**
     * Category DB ids of this profile's hidden Series categories — so hiding a category hides its
     * series everywhere (All, search, Home rails), not just the rail folder (mirrors Live TV).
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.SERIES), custom) { cats, cust ->
                    if (cust.hiddenCategories.isEmpty()) emptySet()
                    else cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories }.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CustState(SectionCustomizations(), emptySet()))

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortSeries
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.ALPHA)

    fun toggleSort() {
        viewModelScope.launch {
            // Cycle Provider → A–Z → Rating → Provider.
            settings.setSortSeries(
                when (sortMode.value) {
                    SettingsRepository.SortMode.PLAYLIST -> SettingsRepository.SortMode.ALPHA
                    SettingsRepository.SortMode.ALPHA -> SettingsRepository.SortMode.RATING
                    SettingsRepository.SortMode.RATING -> SettingsRepository.SortMode.PLAYLIST
                },
            )
        }
    }

    val viewMode: StateFlow<SettingsRepository.VodViewMode> = settings.vodViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.VodViewMode.GRID)

    fun toggleViewMode() {
        viewModelScope.launch {
            settings.setVodViewMode(
                if (viewMode.value == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                else SettingsRepository.VodViewMode.GRID,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    // Bumped after a favourite/history mutation so the pager rebuilds its (manual, non-reactive)
    // PagingSource. Without this, unfavouriting on the Favorites category leaves the removed series in
    // the paged snapshot, which breaks focus restore (the stale row disposes under focus).
    private val _listRefresh = MutableStateFlow(0)
    private fun refreshList() { _listRefresh.value++ }

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries.asStateFlow()

    /** On-demand TMDB enrichment for the focused series (show-level), tagged with the series id to avoid
     *  stale meta during the debounce. Null when off or no confident match. */
    /** Bumped by [refetchSeriesMeta] to force the focused series' TMDB resolve to re-run after clearing its cache. */
    private val _seriesMetaTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedSeriesMeta: StateFlow<SeriesMeta?> = combine(_selectedSeries, _seriesMetaTick) { s, tick -> s to tick }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .debounce(350)
        .mapLatest { (s, _) ->
            if (s == null) null
            else SeriesMeta(s.id, runCatching { metadata.resolveSeries(s) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    data class SeriesMeta(val seriesId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    /** Source mode (plan §4.1) — the pane/details use it to flip provider/TMDB precedence. */
    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> = settings.metadataMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)

    private val _openedSeries = MutableStateFlow<SeriesEntity?>(null)
    val openedSeries: StateFlow<SeriesEntity?> = _openedSeries.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _lastPlayedEpisodeId = MutableStateFlow<Long?>(null)
    val lastPlayedEpisodeId: StateFlow<Long?> = _lastPlayedEpisodeId.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    init {
        // Periodically persist the playing episode's resume position (same cadence as movies).
        // Episodes were previously *read* on play but never saved — resume never actually worked.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveEpisodeProgressNow()
            }
        }
        // Auto-play continuation across seasons: the player advances within a season itself, then signals
        // here when a season's last episode finishes so we can start the next season's first episode.
        viewModelScope.launch {
            player.queueEnded.collect { continueToNextSeason() }
        }
    }

    /** A season's last episode finished with auto-play on — start the next season's first episode, if any.
     *  Matches the just-finished episode by its stream URL (robust to in-season auto-advance). */
    private fun continueToNextSeason() {
        val url = player.currentMediaUrl ?: return
        val all = episodes.value
        val finished = all.firstOrNull { it.streamUrl == url } ?: return // not one of this series' episodes
        val nextEpisode = all
            .filter { it.seasonNumber == finished.seasonNumber + 1 }
            .minByOrNull { it.episodeNumber } ?: return // no next season — series finished
        playEpisode(nextEpisode)
    }

    /** Saves the currently playing episode's position (matched by stream URL, so prev/next in the
     *  player queue is tracked correctly even though the VM didn't initiate the switch). */
    fun saveEpisodeProgressNow() {
        if (player.isLiveContent || !player.isPlaying.value) return
        val url = player.currentMediaUrl ?: return
        val ep = episodes.value.firstOrNull { it.streamUrl == url } ?: return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                val pid = currentProfileId() ?: return@launch
                Log.d(TAG, "saveEpisodeProgressNow episodeId=${ep.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = ep.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveEpisodeProgressNow progress save failed episodeId=${ep.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishEpisodeProgress(pid, ep.id, pos, dur)
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.SERIES),
                customize.observe(c.profileId, MediaType.SERIES),
            ) { cats, cust ->
                defaultRail + cats.applyCustomizations(cust).map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), name.take(3).uppercase(), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val series: Flow<PagingData<SeriesEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(), sortMode, _listRefresh,
    ) { key, c, query, sort, _ -> Args(key, c, query, sort) }
        .combine(custResolved) { args, cs -> args to cs }
        .flatMapLatest { (args, cs) ->
            // Hidden items/categories are filtered on each fresh PagingData inside the pager chain —
            // a customization change re-creates the pager (same pattern as Live TV).
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                pagingSource(args.key, args.ctx, args.query, args.sort)
            }.flow.map { paging ->
                if (cs.cust.hiddenItems.isEmpty() && cs.hiddenCats.isEmpty()) paging
                else paging.filter { s ->
                    CustomizeKeys.series(s) !in cs.cust.hiddenItems &&
                        (s.categoryId == null || s.categoryId !in cs.hiddenCats)
                }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode)

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.SERIES) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val episodes: StateFlow<List<EpisodeEntity>> = _openedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else seriesDao.episodesBySeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onSeriesFocused(s: SeriesEntity) { _selectedSeries.value = s }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this series' cached match/details (incl. a 7-day
     * negative cache) and re-trigger [metadata.resolveSeries] for the focused series via the series meta tick.
     */
    fun refetchSeriesMeta(series: SeriesEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearSeries(series) }
            _seriesMetaTick.value++
        }
    }

    /**
     * Prefill for the "Set TMDB name" dialog (plan §11.2 U5b): the saved override if any, else the cleaned
     * provider title. [hasOverride] drives the dialog's Clear button. Episodes inherit the series match, so
     * the override lives at the series level (no separate episode override).
     */
    data class TmdbNamePrefill(val title: String, val year: Int?, val hasOverride: Boolean)

    suspend fun seriesTmdbNamePrefill(series: SeriesEntity): TmdbNamePrefill {
        metadata.seriesOverride(series)?.let { return TmdbNamePrefill(it.title, it.year, hasOverride = true) }
        val norm = tv.own.owntv.core.metadata.TitleNormalizer.normalize(series.name)
        return TmdbNamePrefill(norm.query, series.year ?: norm.year, hasOverride = false)
    }

    /** Save the hand-typed override and force a re-resolve under the new query (plan §11.2 U5b). */
    fun setSeriesTmdbName(series: SeriesEntity, title: String, year: Int?) {
        viewModelScope.launch {
            runCatching { metadata.setSeriesOverride(series, title, year) }
            _seriesMetaTick.value++
        }
    }

    /** Remove the override and re-resolve with the cleaned provider title (plan §11.2 U5b). */
    fun clearSeriesTmdbName(series: SeriesEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearSeriesOverride(series) }
            _seriesMetaTick.value++
        }
    }

    fun selectSeason(season: Int) { _selectedSeason.value = season }

    // --- Episode enrichment (U3): the focused episode's TMDB still/plot/rating for the right detail pane ---
    private val _selectedEpisode = MutableStateFlow<EpisodeEntity?>(null)
    val selectedEpisode: StateFlow<EpisodeEntity?> = _selectedEpisode.asStateFlow()
    fun onEpisodeFocused(ep: EpisodeEntity) { _selectedEpisode.value = ep }

    /**
     * Manual "Refetch TMDB details" (plan §11.2 U5a): clear this episode's cache AND its show's match (so an
     * episode whose show was negative-cached also recovers), then re-trigger [metadata.resolveEpisode] for the
     * focused episode via the episode meta tick.
     */
    fun refetchEpisodeMeta(series: SeriesEntity, episode: EpisodeEntity) {
        viewModelScope.launch {
            runCatching { metadata.clearEpisode(series, episode) }
            _episodeMetaTick.value++
        }
    }

    /** TMDB metadata for the focused episode, tagged with its id to avoid stale meta during the debounce.
     *  Resolved lazily against the currently opened show. */
    /** Bumped by [refetchEpisodeMeta] to force the focused episode's TMDB resolve to re-run after clearing its cache. */
    private val _episodeMetaTick = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedEpisodeMeta: StateFlow<EpisodeMeta?> = combine(_selectedEpisode, _episodeMetaTick) { ep, tick -> ep to tick }
        .distinctUntilChanged { a, b -> a.first?.id == b.first?.id && a.second == b.second }
        .debounce(350)
        .mapLatest { (ep, _) ->
            val show = _openedSeries.value
            if (ep == null || show == null) null
            else EpisodeMeta(ep.id, runCatching { metadata.resolveEpisode(show, ep) }.getOrNull())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    data class EpisodeMeta(val episodeId: Long, val cache: tv.own.owntv.core.database.entity.MetadataCacheEntity?)

    fun openSeries(s: SeriesEntity) {
        _openedSeries.value = s
        _selectedSeason.value = 1 // reset season when opening a different show
        _lastPlayedEpisodeId.value = null
        Log.d(TAG, "openSeries seriesId=${s.id} profile=${ctx.value.profileId}")
        viewModelScope.launch {
            _episodesLoading.value = true
            seriesRepository.loadEpisodes(s)
            // Jump to where you left off: seed the last-watched episode (and its season) from saved progress
            // BEFORE clearing loading, so the screen's focus effect lands on it instead of episode 1 (#22).
            val eps = seriesDao.episodesBySeries(s.id).first()
            val lastEp = progressDao.lastWatchedEpisodeId(ctx.value.profileId, s.id)
                ?.let { id -> eps.firstOrNull { it.id == id } }
            if (lastEp != null) {
                _selectedSeason.value = lastEp.seasonNumber
                _lastPlayedEpisodeId.value = lastEp.id
            }
            _episodesLoading.value = false
        }
    }

    fun openSeriesById(seriesId: Long) {
        viewModelScope.launch {
            val show = seriesDao.getSeriesById(seriesId) ?: return@launch
            openSeries(show)
        }
    }

    fun playFromHome(seriesId: Long, episodeId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            playFromHomeAsync(seriesId, episodeId, startPositionMs)
        }
    }

    suspend fun playFromHomeAsync(seriesId: Long, episodeId: Long, startPositionMs: Long = 0): Boolean {
        val episode = seriesDao.getEpisodeById(episodeId) ?: return false
        val showId = if (seriesId > 0) seriesId else episode.seriesId
        val show = seriesDao.getSeriesById(showId) ?: return false
        if (episode.seriesId != show.id) return false
        seriesRepository.loadEpisodes(show)
        val queue = seriesDao.episodesBySeries(show.id).first()
        if (queue.isEmpty()) return false
        playEpisodeQueue(show, queue, episode, startPositionMs)
        return true
    }

    fun closeSeries() {
        _openedSeries.value = null
    }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [episode] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(episode: EpisodeEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.EPISODE, episode.id)?.positionMs ?: 0 } ?: 0

    /** Global "External player" toggle — screens must NOT open the fullscreen in-app player when on
     *  (mounting it spins up an mpv instance even though playback branched to the external app). */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Phase B: long-press "Play with external player" — always external, regardless of the global toggle. */
    fun playEpisodeExternal(episode: EpisodeEntity) {
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = currentProfileId()
            Log.d(TAG, "playEpisodeExternal episodeId=${episode.id}")
            externalPlayerLauncher.launch(episode.streamUrl, episode.name)
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                }.onFailure { t -> Log.w(TAG, "external play episode history record failed episodeId=${episode.id} profile=$pid", t) }
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                }.onFailure { t -> Log.w(TAG, "external play series history record failed seriesId=${episode.seriesId} profile=$pid", t) }
            }
        }
    }

    fun playEpisode(episode: EpisodeEntity, startPositionMs: Long = 0) {
        val show = _openedSeries.value ?: return
        val seasonEpisodes = episodes.value
            .filter { it.seasonNumber == episode.seasonNumber }
            .sortedBy { it.episodeNumber }
        playEpisodeQueue(show, seasonEpisodes, episode, startPositionMs)
    }

    fun playEpisodeQueue(show: SeriesEntity, queue: List<EpisodeEntity>, episode: EpisodeEntity, startPositionMs: Long = 0) {
        _openedSeries.value = show
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = currentProfileId()
            // External player (global toggle): launch only the selected episode (external players are
            // single-item — no prev/next queue). History is still recorded; resume position and the
            // in-app HUD/progress tick are not, since OwnTV can't observe the external app.
            if (settings.externalPlayer.first()) {
                Log.d(TAG, "playEpisodeQueue seriesId=${show.id} episodeId=${episode.id} -> external player")
                externalPlayerLauncher.launch(episode.streamUrl, episode.name)
                if (pid != null) {
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                    }.onFailure { t -> Log.w(TAG, "external play episode history record failed episodeId=${episode.id} profile=$pid", t) }
                    runCatching {
                        historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                    }.onFailure { t -> Log.w(TAG, "external play series history record failed seriesId=${episode.seriesId} profile=$pid", t) }
                }
                return@launch
            }
            val startIndex = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
            Log.d(TAG, "playEpisodeQueue seriesId=${show.id} episodeId=${episode.id} profile=$pid queue=${queue.size} startIndex=$startIndex startPositionMs=$startPositionMs")
            val sourceUa = sourceDao.getById(show.sourceId)?.userAgent
            player.playEpisodes(
                items = queue.map { ep ->
                    PlaylistItem(
                        url = ep.streamUrl,
                        meta = MediaMeta(
                            title = ep.name,
                            subtitle = listOfNotNull(show.name, "Season ${ep.seasonNumber}").joinToString(" · "),
                        ),
                    )
                },
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                userAgent = sourceUa,
            )
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue episode history record failed episodeId=${episode.id} profile=$pid", t)
                }
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue series history record failed seriesId=${episode.seriesId} profile=$pid", t)
                }
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    /** Download states for the open show's episodes, keyed by episode id. */
    val episodeDownloads: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.EPISODE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun downloadEpisode(episode: EpisodeEntity) {
        val show = _openedSeries.value
        val showDir = StorageAccess.sanitize(show?.name ?: "Series")
        val ext = episode.containerExt ?: StorageAccess.extOf(episode.streamUrl)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.EPISODE,
                itemId = episode.id,
                title = episode.name,
                posterUrl = show?.posterUrl,
                streamUrl = episode.streamUrl,
                relativeDir = "Series/$showDir/Season ${episode.seasonNumber}",
                fileName = "${StorageAccess.sanitize(episode.name)}.$ext",
            )
        }
    }

    fun downloadSeries(series: SeriesEntity) {
        val showDir = StorageAccess.sanitize(series.name)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            seriesDao.episodesBySeries(series.id).first().forEach { ep ->
                val ext = ep.containerExt ?: StorageAccess.extOf(ep.streamUrl)
                downloadManager.enqueue(
                    profileId = pid,
                    mediaType = MediaType.EPISODE,
                    itemId = ep.id,
                    title = ep.name,
                    posterUrl = series.posterUrl,
                    streamUrl = ep.streamUrl,
                    relativeDir = "Series/$showDir/Season ${ep.seasonNumber}",
                    fileName = "${StorageAccess.sanitize(ep.name)}.$ext",
                )
            }
        }
    }

    fun toggleFavorite(s: SeriesEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(s.id)) favoriteDao.remove(pid, MediaType.SERIES, s.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = s.id))
            refreshList() // the Favorites category uses a manual PagingSource — force a rebuild
        }
    }

    fun enterMoveMode(series: SeriesEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> seriesDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                LiveKey.Favorites -> seriesDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                else -> return@launch
            }
            val idx = items.indexOfFirst { it.id == series.id }
            if (idx < 0) return@launch
            _moveState.value = SeriesMoveState(items, idx, contextKey)
            settings.setSortSeries(SettingsRepository.SortMode.PLAYLIST)
        }
    }

    fun moveUp() {
        val s = _moveState.value ?: return
        if (s.activeIndex == 0) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i - 1] = s.items[i]; list[i] = s.items[i - 1]
        _moveState.value = s.copy(items = list, activeIndex = i - 1)
    }

    fun moveDown() {
        val s = _moveState.value ?: return
        if (s.activeIndex == s.items.size - 1) return
        val list = s.items.toMutableList()
        val i = s.activeIndex
        list[i + 1] = s.items[i]; list[i] = s.items[i + 1]
        _moveState.value = s.copy(items = list, activeIndex = i + 1)
    }

    fun commitMove() {
        val s = _moveState.value ?: return
        _moveState.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            contentOrderDao.replaceContext(
                profileId = pid,
                type = MediaType.SERIES,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, ser ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.SERIES, contextKey = s.contextKey, itemId = ser.id, position = i)
                },
            )
        }
    }

    fun cancelMove() { _moveState.value = null }

    /** Hide the series from all lists (undo via Settings → Customize Category → Hidden items). */
    fun hideSeries(series: SeriesEntity) {
        if (_selectedSeries.value?.id == series.id) _selectedSeries.value = null
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.SERIES, CustomizeKeys.series(series), series.name, true)
        }
    }

    fun removeFromHistory(seriesId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.SERIES, seriesId)
            refreshList() // the History category uses a manual PagingSource — force a rebuild
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, SeriesEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        val rating = sort == SettingsRepository.SortMode.RATING
        return if (query.isBlank()) when (key) {
            LiveKey.All -> when {
                rating -> seriesDao.pagingAllRating(ids)
                playlist -> seriesDao.pagingAllOriginal(ids)
                else -> seriesDao.pagingAll(ids)
            }
            LiveKey.Favorites -> seriesDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
            LiveKey.History -> seriesDao.pagingHistory(c.profileId, ids)
            is LiveKey.Folder ->
                if (rating) seriesDao.pagingByCategoryRating(key.id)
                else seriesDao.pagingByCategoryManual(key.id, c.profileId, folderContextKeys.value[key.id] ?: "")
        } else when (key) {
            LiveKey.All -> seriesDao.searchAll(query, ids)
            LiveKey.Favorites -> seriesDao.searchFavorites(query, c.profileId, ids)
            LiveKey.History -> seriesDao.searchHistory(query, c.profileId, ids)
            is LiveKey.Folder -> seriesDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All ->
                if (hiddenCats.isEmpty()) seriesDao.countAll(ids)
                else seriesDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> seriesDao.countFavorites(c.profileId, ids)
            LiveKey.History -> seriesDao.countHistory(c.profileId, ids)
            is LiveKey.Folder -> seriesDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Series"),
        )
    }
}
