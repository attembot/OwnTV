@file:OptIn(ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.features.settings.data.EpgAutoRefresh
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Phase 13 — manage IPTV sources (list / add / re-sync / delete) for the active profile. */
class SettingsViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
    private val channelDao: tv.own.owntv.core.database.dao.ChannelDao,
    private val historyDao: tv.own.owntv.core.database.dao.HistoryDao,
    private val progressDao: tv.own.owntv.core.database.dao.ProgressDao,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val metadataProvider: tv.own.owntv.core.metadata.MetadataProvider,
) : ViewModel() {
    companion object {
        private const val TAG = "OwnTVHome"
    }

    // Semi-auto EPG: after a playlist import, if the playlist has a guide URL we offer to sync the EPG now
    // (instead of the old slow auto-sync). "Sync now" shows a live programme count, just like the import.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<EpgSyncUi>(EpgSyncUi.Hidden)
    val epgSync: StateFlow<EpgSyncUi> = _epgSync.asStateFlow()

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch { runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it } }
    }

    /** Skip (from the prompt) or acknowledge (after Done) — either way, close the EPG flow. */
    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = EpgSyncUi.Hidden }

    /** Clear the active profile's watch history (the "recently watched" / continue rows). #26
     *  [type] null = everything; otherwise just LIVE / MOVIE / SERIES. */
    fun clearWatchHistory(type: tv.own.owntv.core.model.MediaType? = null) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            if (type == null) {
                historyDao.clear(pid)
                progressDao.clearProfile(pid) // also wipe resume positions → empties Home's continue-watching
            } else {
                historyDao.clearType(pid, type)
                // Home's Movies/Series continue-watching comes from the resume (progress) table, not history;
                // series progress is stored under EPISODE. Live has no resume progress to clear.
                when (type) {
                    tv.own.owntv.core.model.MediaType.MOVIE ->
                        progressDao.clearProfileType(pid, tv.own.owntv.core.model.MediaType.MOVIE)
                    tv.own.owntv.core.model.MediaType.SERIES ->
                        progressDao.clearProfileType(pid, tv.own.owntv.core.model.MediaType.EPISODE)
                    else -> Unit
                }
            }
            // Rebuild the Android TV home cards so the cleared items also leave the system Continue Watching row.
            runCatching { launcherIntegrationRepository.refreshProfile(pid) }
        }
    }

    /** Stored EPG programme count for a source — the row shows it as the EPG status. */
    fun epgCount(sourceId: Long): kotlinx.coroutines.flow.Flow<Int> = epgDao.countForSource(sourceId)

    /** Content counts (channels/movies/series) for a source — shown on each Playlists row. */
    fun contentCounts(sourceId: Long): kotlinx.coroutines.flow.Flow<SyncCounts> =
        catalogSyncScheduler.observeSync(sourceId)
            .onStart { emit(CatalogSyncState.Idle) }
            .filter { !it.isActive }
            .map { importFinalizer.contentCounts(sourceId) }

    fun syncState(sourceId: Long): kotlinx.coroutines.flow.Flow<CatalogSyncState> =
        catalogSyncScheduler.observeSync(sourceId)

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** [summary] is the per-type breakdown, e.g. "40K channels · 100K movies · 30K series synced". */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    val sources: StateFlow<List<SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many of the active profile's channels advertise catch-up — for the Catch-up settings note. */
    val catchupChannelCount: StateFlow<Int> = sources
        .flatMapLatest { srcs ->
            val ids = srcs.map { it.id }
            kotlinx.coroutines.flow.flow { emit(if (ids.isEmpty()) 0 else channelDao.countCatchup(ids)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Configured download folder ("" = app-specific storage). */
    val downloadRoot: StateFlow<String> = settings.downloadRoot
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setDownloadRoot(path: String) {
        viewModelScope.launch { settings.setDownloadRoot(path) }
    }

    /** The source marked as default/active (shown in the sidebar). */
    val defaultSourceId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    fun setDefaultSource(id: Long) {
        viewModelScope.launch { settings.setDefaultSource(id) }
    }

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLivePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewEnabled(enabled) }
    }

    val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLivePreviewAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewAudio(enabled) }
    }

    val hdrEnabled: StateFlow<Boolean> = settings.hdrEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setHdrEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHdrEnabled(enabled) }
    }

    val surroundSound: StateFlow<Boolean> = settings.surroundSound
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSurroundSound(enabled: Boolean) {
        viewModelScope.launch { settings.setSurroundSound(enabled) }
    }

    val autoPlayNext: StateFlow<Boolean> = settings.autoPlayNext
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoPlayNext(enabled) }
    }

    val catchupTimezone: StateFlow<SettingsRepository.CatchupTimezone> = settings.catchupTimezone
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.CatchupTimezone.MANUAL)

    val catchupOffsetMinutes: StateFlow<Int> = settings.catchupOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val catchupOffsetRangeMinutes: IntRange = settings.catchupOffsetRangeMinutes

    fun setCatchupTimezone(mode: SettingsRepository.CatchupTimezone) {
        viewModelScope.launch { settings.setCatchupTimezone(mode) }
    }

    /** Nudge the manual UTC offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustCatchupOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setCatchupOffsetMinutes(catchupOffsetMinutes.value + deltaMinutes) }
    }

    val androidTvHomeEnabled: StateFlow<Boolean> = settings.androidTvHomeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAndroidTvHomeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "setAndroidTvHomeEnabled enabled=$enabled")
            settings.setAndroidTvHomeEnabled(enabled)
            if (enabled) {
                refreshActiveTvHome(allowBrowsableRequest = true)
            } else {
                profileDao.getAllOnce().forEach { profile -> launcherIntegrationRepository.clearProfile(profile.id) }
            }
        }
    }

    /** Status of the manual "Refresh now" so the UI can show Rebuilding… → Done. */
    enum class TvHomeRefresh { IDLE, REFRESHING, DONE }
    private val _tvHomeRefresh = MutableStateFlow(TvHomeRefresh.IDLE)
    val tvHomeRefresh: StateFlow<TvHomeRefresh> = _tvHomeRefresh.asStateFlow()

    fun refreshAndroidTvHome() {
        if (_tvHomeRefresh.value == TvHomeRefresh.REFRESHING) return
        viewModelScope.launch {
            _tvHomeRefresh.value = TvHomeRefresh.REFRESHING
            runCatching { refreshActiveTvHome(allowBrowsableRequest = true) }
            _tvHomeRefresh.value = TvHomeRefresh.DONE
            kotlinx.coroutines.delay(1_800)
            if (_tvHomeRefresh.value == TvHomeRefresh.DONE) _tvHomeRefresh.value = TvHomeRefresh.IDLE
        }
    }

    // --- Video Player Settings ---
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }

    val vodPreferExo: StateFlow<Boolean> = settings.vodPreferExo.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setVodPreferExo(enabled: Boolean) { viewModelScope.launch { settings.setVodPreferExo(enabled) } }

    val externalPlayer: StateFlow<Boolean> = settings.externalPlayer.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setExternalPlayer(enabled: Boolean) { viewModelScope.launch { settings.setExternalPlayer(enabled) } }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

    val resumeLastChannel: StateFlow<Boolean> =
        settings.resumeLastChannel.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setResumeLastChannel(enabled: Boolean) { viewModelScope.launch { settings.setResumeLastChannel(enabled) } }

    // Per-profile startup landing (v4.0.0): Home / Last channel / Live·Favorites.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val startupMode: StateFlow<tv.own.owntv.features.settings.data.StartupMode> =
        settings.activeProfileId
            .flatMapLatest { settings.startupMode(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.features.settings.data.StartupMode.HOME)
    fun setStartupMode(mode: tv.own.owntv.features.settings.data.StartupMode) {
        viewModelScope.launch { settings.setStartupMode(settings.activeProfileId.first(), mode) }
    }

    val resumeMode: StateFlow<SettingsRepository.ResumeMode> =
        settings.resumeMode.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)
    fun setResumeMode(name: String) {
        viewModelScope.launch {
            settings.setResumeMode(runCatching { SettingsRepository.ResumeMode.valueOf(name) }.getOrDefault(SettingsRepository.ResumeMode.ASK))
        }
    }

    val defaultZoom: StateFlow<String> = settings.defaultZoom.stateIn(viewModelScope, SharingStarted.Eagerly, "FIT")
    fun setDefaultZoom(name: String) { viewModelScope.launch { settings.setDefaultZoom(name) } }

    val subtitleScale: StateFlow<Float> = settings.subtitleScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    fun setSubtitleScale(scale: Float) { viewModelScope.launch { settings.setSubtitleScale(scale) } }

    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setAudioDelayMs(ms: Int) { viewModelScope.launch { settings.setAudioDelayMs(ms) } }

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    val animationLevel: StateFlow<tv.own.owntv.ui.theme.AnimationLevel> =
        settings.animationLevel.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.ui.theme.AnimationLevel.FULL)
    fun setAnimationLevel(level: tv.own.owntv.ui.theme.AnimationLevel) { viewModelScope.launch { settings.setAnimationLevel(level) } }

    // Weather chip: visibility toggle + manual location override (for VPN users).
    val weatherEnabled: StateFlow<Boolean> =
        settings.weatherEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setWeatherEnabled(enabled: Boolean) { viewModelScope.launch { settings.setWeatherEnabled(enabled) } }
    val weatherLocation: StateFlow<String> =
        settings.weatherLocation.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setWeatherLocation(location: String) { viewModelScope.launch { settings.setWeatherLocation(location) } }
    val weatherFahrenheit: StateFlow<Boolean> =
        settings.weatherFahrenheit.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setWeatherFahrenheit(fahrenheit: Boolean) { viewModelScope.launch { settings.setWeatherFahrenheit(fahrenheit) } }

    /** Per-source playlist auto-refresh selection (Off / Startup / staleness threshold). */
    val playlistAutoRefresh: StateFlow<Map<Long, PlaylistAutoRefresh>> = settings.playlistAutoRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Per-source EPG auto-refresh selection (Off / Startup / staleness threshold). */
    val epgAutoRefresh: StateFlow<Map<Long, EpgAutoRefresh>> = settings.epgAutoRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setPlaylistAutoRefresh(sourceId: Long, mode: PlaylistAutoRefresh) {
        viewModelScope.launch { settings.setPlaylistAutoRefresh(sourceId, mode) }
    }

    fun setEpgAutoRefresh(sourceId: Long, mode: EpgAutoRefresh) {
        viewModelScope.launch { settings.setEpgAutoRefresh(sourceId, mode) }
    }

    /** Edit an existing source's settings (no re-import unless the user re-syncs). */
    fun updateSource(id: Long, name: String, urlOrServer: String, user: String, pass: String, userAgent: String, epgUrl: String, autoRefresh: PlaylistAutoRefresh, isDefault: Boolean = false) {
        viewModelScope.launch {
            val existing = sourceDao.getById(id) ?: return@launch
            sourceRepository.updateSource(
                existing.copy(
                    name = name.ifBlank { existing.name },
                    url = urlOrServer.trim().ifBlank { existing.url },
                    username = user.trim().takeIf { it.isNotBlank() } ?: existing.username,
                    password = pass.takeIf { it.isNotBlank() } ?: existing.password,
                    userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                    epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                ),
            )
            settings.setPlaylistAutoRefresh(id, autoRefresh)
            // Apply the "Default playlist" toggle: on → this becomes the active playlist; off → if this was
            // the default, clear it back to All. Leaves another playlist's default untouched.
            when {
                isDefault -> settings.setDefaultSource(id)
                settings.defaultSourceId.first() == id -> settings.setDefaultSource(-1L)
            }
        }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** The last source whose sync failed — persisted so AddSourceScreen can pre-fill the form
     *  instead of making the user re-type everything on the remote after a typo. */
    private var _lastFailedSource: SourceEntity? = null
    val lastFailedSource: SourceEntity? get() = _lastFailedSource

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    private var importJob: Job? = null

    fun addXtream(
        name: String,
        server: String,
        user: String,
        pass: String,
        userAgent: String = "",
        epgUrl: String = "",
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        syncLive: Boolean = true,
        syncMovies: Boolean = true,
        syncSeries: Boolean = true,
        isDefault: Boolean = false,
    ) {
        val priority = SyncContentTypes(syncLive, syncMovies, syncSeries)
        runImport(autoRefresh, priority, enqueueRemainder = true, requiresNetwork = true, makeDefault = isDefault) { pid ->
            sourceRepository.addXtreamSource(
                pid, name.ifBlank { "My IPTV" }, server.trim(), user.trim(), pass,
                userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }
    }

    fun addM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF, isDefault: Boolean = false) = runImport(
        autoRefresh,
        requiresNetwork = !url.isLocalPlaylistPath(),
        makeDefault = isDefault,
    ) { pid ->
        sourceRepository.addM3uSource(
            pid, name.ifBlank { "My Playlist" }, url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun runImport(
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        enqueueRemainder: Boolean = false,
        requiresNetwork: Boolean = true,
        makeDefault: Boolean = false,
        addSource: suspend (Long) -> SourceEntity,
    ) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            var source: SourceEntity? = null
            try {
                if (requiresNetwork && !connectivity.isOnlineNow()) {
                    _importState.value = ImportState.Failed(friendlySyncError(null, online = false))
                    return@launch
                }
                val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                Log.d(TAG, "runImport profile=$pid autoRefresh=$autoRefresh")
                source = addSource(pid)
                val freshSync = source.lastSyncAt == null
                val remainder = if (enqueueRemainder) SyncContentTypes().remainderAfter(contentTypes) else SyncContentTypes(live = false, movies = false, series = false)
                settings.setPlaylistAutoRefresh(source.id, autoRefresh)
                when (val r = sourceRepository.sync(source, onProgress = { _progress.value = it }, contentTypes = contentTypes)) {
                    is SyncResult.Success -> {
                        // Settings playlist add: content breakdown only (EPG syncs silently and is
                        // shown on the EPG Sources screen, per the separated-EPG design).
                        val counts = importFinalizer.finalize(source, deferIndexes = freshSync)
                        if (makeDefault) settings.setDefaultSource(source.id)
                        val syncedSource = sourceDao.getById(source.id) ?: source
                        Log.d(TAG, "runImport sync success sourceId=${source.id} profile=$pid")
                        if (enqueueRemainder) enqueueRemainderSync(source, contentTypes)
                        if (freshSync && !remainder.hasAny) catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_add")
                        _lastFailedSource = null
                        _importState.value = ImportState.Success(counts.summary(includeEpg = false).withWarnings(r))
                        // Offer a one-tap EPG sync if this playlist actually has a guide feed.
                        if (epgRepository.guideUrl(syncedSource) != null) {
                            pendingEpgSource = syncedSource
                            _epgSync.value = EpgSyncUi.Ask(syncedSource.name)
                        }
                        viewModelScope.launch { runCatching { refreshActiveTvHome(allowBrowsableRequest = true) } }
                    }
                    is SyncResult.Failed -> {
                        cleanupFailedAdd(source)
                        _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                    }
                    SyncResult.Cancelled -> {
                        cleanupFailedAdd(source)
                        _importState.value = ImportState.Idle
                    }
                }
            } catch (c: CancellationException) {
                cleanupFailedAdd(source)
                _importState.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                cleanupFailedAdd(source)
                _importState.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    private fun String.isLocalPlaylistPath(): Boolean =
        startsWith("/") || startsWith("file://") || startsWith("content://")

    /** Re-sync an existing source through WorkManager so it can continue after leaving this screen. */
    fun resync(source: SourceEntity) {
        Log.d(TAG, "resync enqueue sourceId=${source.id}")
        viewModelScope.launch {
            val counts = importFinalizer.contentCounts(source.id)
            catalogSyncScheduler.enqueueSync(
                source.id,
                reason = "manual_resync",
                baseItemCount = counts.channels + counts.movies + counts.series,
            )
        }
    }

    fun cancelResync(source: SourceEntity) {
        Log.d(TAG, "resync cancel sourceId=${source.id}")
        catalogSyncScheduler.cancelSync(source.id)
    }

    fun delete(source: SourceEntity) {
        viewModelScope.launch {
            Log.d(TAG, "delete sourceId=${source.id}")
            catalogSyncScheduler.cancelSync(source.id)
            sourceRepository.deleteSource(source)
            if (defaultSourceId.value == source.id) settings.setDefaultSource(-1L)
            refreshActiveTvHome(allowBrowsableRequest = true)
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
        _progress.value = null
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importState.value = ImportState.Idle
        _progress.value = null
    }

    private suspend fun refreshActiveTvHome(allowBrowsableRequest: Boolean = true) {
        val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return
        Log.d(TAG, "refreshActiveTvHome profile=$pid allowBrowsable=$allowBrowsableRequest")
        launcherIntegrationRepository.refreshProfile(pid, allowBrowsableRequest)
    }

    private fun enqueueRemainderSync(source: SourceEntity, priority: SyncContentTypes) {
        val remainder = SyncContentTypes().remainderAfter(priority)
        if (remainder.hasAny) {
            // The priority pass + this remainder cover all content types, so a successful remainder
            // run must mark the source synced (SyncManager only does that for single full syncs).
            catalogSyncScheduler.enqueueSync(source.id, reason = "add_remainder", contentTypes = remainder, completesInitialSync = true)
        }
    }

    private suspend fun cleanupFailedAdd(source: SourceEntity?) {
        if (source == null) return
        withContext(NonCancellable) {
            catalogSyncScheduler.cancelSync(source.id)
            runCatching { sourceRepository.deleteSource(source) }
            runCatching { settings.setPlaylistAutoRefresh(source.id, PlaylistAutoRefresh.OFF) }
        }
    }

    private fun String.withWarnings(result: SyncResult.Success): String =
        result.warningSummary()?.let { "$this\n$it" } ?: this

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---

    val proxyConfig: StateFlow<tv.own.owntv.core.network.ProxyConfig> = settings.proxyConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.network.ProxyConfig())

    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        viewModelScope.launch { settings.saveProxy(enabled, host, port, username, password) }
    }

    sealed interface ProxyTestState {
        data object Idle : ProxyTestState
        data object Testing : ProxyTestState
        data class Ok(val millis: Long) : ProxyTestState
        data class Fail(val message: String) : ProxyTestState
    }

    private val _proxyTest = MutableStateFlow<ProxyTestState>(ProxyTestState.Idle)
    val proxyTest: StateFlow<ProxyTestState> = _proxyTest.asStateFlow()

    fun resetProxyTest() { _proxyTest.value = ProxyTestState.Idle }

    fun testProxy(host: String, port: Int, username: String, password: String) {
        if (_proxyTest.value == ProxyTestState.Testing) return
        val h = host.trim()
        if (h.isBlank() || port !in 1..65535) {
            _proxyTest.value = ProxyTestState.Fail("Enter a valid host and port (1–65535).")
            return
        }
        _proxyTest.value = ProxyTestState.Testing
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val proxy = java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        java.net.InetSocketAddress.createUnresolved(h, port),
                    )
                    val builder = okHttpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    if (username.trim().isNotBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(username.trim(), password))
                                .build()
                        }
                    } else {
                        builder.proxyAuthenticator(okhttp3.Authenticator.NONE)
                    }
                    val client = builder.build()
                    val request = okhttp3.Request.Builder()
                        .url("https://www.gstatic.com/generate_204")
                        .head()
                        .build()
                    val start = System.currentTimeMillis()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 204) {
                            throw java.io.IOException("Proxy returned HTTP ${resp.code}")
                        }
                    }
                    System.currentTimeMillis() - start
                }
            }
            _proxyTest.value = result.fold(
                onSuccess = { ProxyTestState.Ok(it) },
                onFailure = { ProxyTestState.Fail(friendlyProxyError(it)) },
            )
        }
    }

    // --- TMDB metadata enrichment (plan §4) — Phase M1 config + manual "look up title" test ---

    val metadataMode: StateFlow<tv.own.owntv.core.metadata.MetadataMode> =
        settings.metadataMode.stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)
    fun setMetadataMode(mode: tv.own.owntv.core.metadata.MetadataMode) { viewModelScope.launch { settings.setMetadataMode(mode) } }

    val tmdbApiKey: StateFlow<String> =
        settings.tmdbApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setTmdbApiKey(key: String) { viewModelScope.launch { settings.setTmdbApiKey(key) } }

    val metadataServerUrl: StateFlow<String> =
        settings.metadataServerUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setMetadataServerUrl(url: String) { viewModelScope.launch { settings.setMetadataServerUrl(url) } }

    /** Which access tier the current config resolves to — shown as the Metadata screen's status chip. */
    val metadataTier: StateFlow<tv.own.owntv.core.metadata.MetadataConfig.Tier> =
        settings.metadataConfigFlow
            .map { it.tier }
            .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER)

    sealed interface MetadataTestState {
        data object Idle : MetadataTestState
        data object Testing : MetadataTestState
        /** Top match summary, e.g. "Oppenheimer (2023) · tmdb #872585". */
        data class Ok(val summary: String) : MetadataTestState
        data class Fail(val message: String) : MetadataTestState
    }

    private val _metadataTest = MutableStateFlow<MetadataTestState>(MetadataTestState.Idle)
    val metadataTest: StateFlow<MetadataTestState> = _metadataTest.asStateFlow()

    fun resetMetadataTest() { _metadataTest.value = MetadataTestState.Idle }

    /** Manual "look up title" through the configured tier — proves the plumbing end-to-end (M1 deliverable). */
    fun testMetadataLookup(title: String) {
        if (_metadataTest.value == MetadataTestState.Testing) return
        val q = title.trim()
        if (q.isEmpty()) {
            _metadataTest.value = MetadataTestState.Fail("Enter a title to look up.")
            return
        }
        _metadataTest.value = MetadataTestState.Testing
        viewModelScope.launch {
            val result = runCatching { metadataProvider.searchMovie(q) }
            _metadataTest.value = result.fold(
                onSuccess = { hits ->
                    val top = hits?.firstOrNull()
                    if (hits == null) {
                        MetadataTestState.Fail("Couldn't reach the metadata server — check network / key / URL.")
                    } else if (top == null) {
                        MetadataTestState.Fail("No TMDB match for \"$q\".")
                    } else {
                        val yr = top.year?.let { " ($it)" } ?: ""
                        MetadataTestState.Ok("${top.title}$yr · tmdb #${top.tmdbId}")
                    }
                },
                onFailure = { MetadataTestState.Fail(it.message?.takeIf { m -> m.isNotBlank() } ?: "Lookup failed.") },
            )
        }
    }

    private fun friendlyProxyError(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Can't reach the proxy host."
        is java.net.SocketTimeoutException -> "Connection to the proxy timed out."
        is java.net.ConnectException -> "Couldn't connect to the proxy (check host/port)."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Proxy test failed."
    }
}
