@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package tv.own.owntv.features.live

import android.content.Context
import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.applyCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.throttleLatest
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.formatSystemTime

/** Layer-2 rail selection for Live TV. */
sealed interface LiveKey {
    data object Favorites : LiveKey
    data object History : LiveKey
    data object All : LiveKey
    data class Folder(val id: Long) : LiveKey
}

/** A rail entry. Favorites/History carry an [icon] (rendered instead of the abbreviation). */
@Immutable
data class LiveRailItem(val key: LiveKey, val abbr: String, val title: String, val icon: OwnTVIcon? = null)

/** Now-playing + up-next EPG for the focused channel (null entries when the guide is unavailable). */
@Immutable
data class EpgNowNext(val now: XtEpgEntry?, val next: XtEpgEntry?, val upcoming: List<XtEpgEntry> = emptyList())

class LiveViewModel(
    private val appContext: Context,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val xtreamClient: XtreamClient,
    private val customize: CustomizationStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    val player: OwnTVPlayer,
    val previewEngine: tv.own.owntv.player.LivePreviewEngine,
    private val forceMpvStore: tv.own.owntv.core.player.ForceMpvStore,
    private val contentOrderDao: ContentOrderDao,
    private val streamUrlResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
) : ViewModel() {

    data class ChannelMoveState(val items: List<ChannelEntity>, val activeIndex: Int, val contextKey: String)
    private val _moveState = MutableStateFlow<ChannelMoveState?>(null)
    val moveState: StateFlow<ChannelMoveState?> = _moveState.asStateFlow()

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Channels pinned to mpv ("compatibility mode") — opened straight on mpv, bypassing ExoPlayer. Eagerly
     *  collected so the routing decision in [ensurePlaying] always sees the current set. Keyed by stream URL. */
    val forceMpvUrls: StateFlow<Set<String>> = forceMpvStore.urls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** List ordering for this section (Playlist order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    private val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)

    // Observe the active profile's sources REACTIVELY so adding/removing a playlist refreshes Live TV
    // immediately (it used to be read once at startup, so a new playlist showed nothing until restart).
    // sourceUaMap is a lightweight side-product: sourceId → userAgent, used for synchronous play() calls
    // (playPreview, ensurePlaying) that can't do a DB lookup on the call site.
    private var sourceUaMap: Map<Long, String?> = emptyMap()
    // Full sources by id — lets the synchronous play() paths tell a Stalker source (needs play-time
    // create_link resolution) from M3U/Xtream (final URL already stored) without a DB round-trip.
    private var sourceById: Map<Long, tv.own.owntv.core.database.entity.SourceEntity> = emptyMap()
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps ->
            sourceUaMap = aps.sources.associate { it.id to it.userAgent }
            sourceById = aps.sources.associateBy { it.id }
            Ctx(aps.profileId, aps.sourceIds)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val folderContextKeys: StateFlow<Map<Long, String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyMap())
            else categoryDao.observe(c.sourceIds, MediaType.LIVE).map { cats ->
                cats.associateBy({ it.id }, { CustomizeKeys.category(it) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Contexts that actually have manual-order rows (C3): only those folders pay the
     *  unindexable content_order join-sort; everything else stays on the plain indexed query. */
    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptySet())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.LIVE).map { it.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel.asStateFlow()

    private data class CachedEpg(val at: Long, val data: EpgNowNext)
    private val epgCache = HashMap<Long, CachedEpg>()

    /** Now/next for the focused channel — fetched (debounced) from the Xtream `get_short_epg` API. */
    val nowNext: StateFlow<EpgNowNext?> = _previewChannel
        .debounce(350)
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .mapLatest { ch -> ch?.let { loadEpg(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** This profile's hide/rename/reorder customizations for Live TV. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /**
     * Category DB ids of the profile's hidden categories. Hiding a category used to only drop its rail
     * folder — its channels still showed in "All Channels", search and recently-watched (so hiding the
     * adult groups left them all visible under ALL). Resolving the hidden category keys to ids here lets
     * those lists filter the channels out, so hiding a group hides its channels everywhere.
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
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

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom, sortMode) { cats, cust, sort ->
                // A–Z also sorts the category folders; manually moved categories stay pinned first.
                val folders = cats.applyCustomizations(cust, alphaRest = sort == SettingsRepository.SortMode.ALPHA)
                defaultRail + folders.map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), abbreviate(name), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        _selected,
        ctx,
        _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        sortMode,
        custResolved,
    ) { key, c, query, sort, cs -> Args(key, c, query, sort, cs) }
        // Rebuild the pager when a folder gains/loses manual order (C3): the fast-path plain
        // PagingSource doesn't observe content_order, so the switch must recreate it.
        .combine(orderedContexts) { args, _ -> args }
        .flatMapLatest { (key, c, query, sort, cs) ->
            // Customizations are applied to each fresh PagingData inside the pager chain — a PagingData
            // that the UI already collected must never be re-transformed (Paging forbids re-collection,
            // which is why hiding a channel used to crash). A customization change re-creates the pager.
            Pager(PagingConfig(pageSize = 80, prefetchDistance = 40, initialLoadSize = 120, maxSize = 400)) {
                pagingSource(key, c, query, sort)
            }.flow.map { paging ->
                val cust = cs.cust
                val hiddenCats = cs.hiddenCats
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hiddenCats.isEmpty()) paging
                else paging
                    .filter { ch ->
                        CustomizeKeys.channel(ch) !in cust.hiddenItems &&
                            (ch.categoryId == null || ch.categoryId !in hiddenCats)
                    }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode, val cs: CustState)

    /** Hide the focused channel from all lists (undo via Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        if (_previewChannel.value?.id == channel.id) stopPreview()
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel for this profile (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.renameItem(pid, MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    /** Manually map a channel to an EPG channel id (null clears the override → auto-match). */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
        }
    }

    /** The current manual EPG id for a channel, or null if auto-matched. */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** Distinct EPG channels for the "Match EPG" picker (across the profile's playlists + EPG feeds). */
    suspend fun availableEpgChannels(query: String): List<tv.own.owntv.core.database.entity.EpgChannelEntity> {
        if (currentProfileId() == null) return emptyList()
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        if (ids.isEmpty()) return emptyList()
        return epgDao.listEpgChannels(ids, query.trim().lowercase(), 300)
    }

    /**
     * One-shot channel browse for the in-overlay switcher (PiP / MultiView): the active sources' channels,
     * filtered by [query] when it's non-blank, as a bounded list (no paging — the overlay shows a scroll of
     * the first [limit]). Lets the user retune a corner/tile from the whole playlist without leaving playback.
     */
    suspend fun browseChannels(query: String, limit: Int = 100): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val ids = ctx.value.sourceIds
        if (ids.isEmpty()) return@withContext emptyList()
        val q = query.trim()
        if (q.isEmpty()) channelDao.allForSources(ids, limit) else channelDao.searchList(q, ids, limit)
    }

    /** One-shot channels for a browse [key] (All / Favorites / History / a Folder) — backs the browse picker. */
    suspend fun channelsFor(key: LiveKey, limit: Int = 300): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val c = ctx.value
        if (c.sourceIds.isEmpty()) return@withContext emptyList()
        when (key) {
            LiveKey.All -> channelDao.allForSources(c.sourceIds, limit)
            LiveKey.Favorites -> channelDao.listFavorites(c.profileId, limit)
            LiveKey.History -> channelDao.listHistory(c.profileId, limit)
            is LiveKey.Folder -> channelDao.listByCategory(key.id, limit)
        }
    }

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden).throttleLatest() } // C2: cap live COUNT re-runs during bulk sync
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recentlyWatched: StateFlow<List<ChannelEntity>> = ctx
        .flatMapLatest { channelDao.recentlyWatched(it.profileId, 20) }
        .combine(custResolved) { list, cs ->
            list.filter {
                CustomizeKeys.channel(it) !in cs.cust.hiddenItems &&
                    (it.categoryId == null || it.categoryId !in cs.hiddenCats)
            }
                .map { ch -> cs.cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) {
        _selected.value = key
    }

    // --- Remember the last selected category (so reopening Live TV lands where you left off, #6) ---
    private fun LiveKey.serialize(): String = when (this) {
        LiveKey.Favorites -> "FAV"
        LiveKey.History -> "HIST"
        LiveKey.All -> "ALL"
        is LiveKey.Folder -> "FOLDER:$id"
    }

    private fun parseLiveKey(s: String): LiveKey? = when {
        s == "FAV" -> LiveKey.Favorites
        s == "HIST" -> LiveKey.History
        s == "ALL" -> LiveKey.All
        s.startsWith("FOLDER:") -> s.removePrefix("FOLDER:").toLongOrNull()?.let { LiveKey.Folder(it) }
        else -> null
    }

    init {
        // Persist the selected category (debounced — the rail fires select() on focus as you scroll).
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged().collect { settings.setLastLiveCategory(it.serialize()) }
        }
        // Restore it once at startup — but only while still on the default (don't yank a user who already
        // navigated). A saved folder is honoured only once it actually exists in this profile's rail.
        viewModelScope.launch {
            val saved = parseLiveKey(settings.lastLiveCategory.first()) ?: return@launch
            if (saved is LiveKey.Folder) {
                val ok = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    railItems.first { list -> list.any { it.key == saved } }
                } != null
                if (ok && _selected.value == LiveKey.All) _selected.value = saved
            } else if (_selected.value == LiveKey.All) {
                _selected.value = saved
            }
        }
        // Persist the last focused/interacted channel (debounced), and restore it once at startup so opening
        // Live TV lands focus back on it. Restore leaves the preview disarmed (no auto-preview on launch).
        viewModelScope.launch {
            _previewChannel.drop(1).filterNotNull().map { it.id }.debounce(800).distinctUntilChanged()
                .collect { settings.setLastLiveChannelId(it) }
        }
        viewModelScope.launch {
            val savedId = settings.lastLiveChannelId.first()
            if (savedId > 0 && _previewChannel.value == null) {
                ctx.first { it.profileId >= 0 }
                channelDao.getById(savedId)?.let { if (_previewChannel.value == null) _previewChannel.value = it }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _search.value = query
    }

    fun onChannelFocused(channel: ChannelEntity) {
        _previewArmed.value = true // a real user focus — the in-pane preview may now play
        _previewChannel.value = channel
    }

    // The in-pane preview only plays once the user has actually focused a channel — so restoring the last
    // focused channel on startup positions focus & the details pane WITHOUT auto-previewing on launch (#6).
    private val _previewArmed = MutableStateFlow(false)
    val previewArmed: StateFlow<Boolean> = _previewArmed.asStateFlow()


    /** In-pane preview playback (no history) — triggered by the UI after the focus settles. Runs on the
     *  lightweight ExoPlayer engine (fast HLS start), not mpv; the full/fullscreen player stays on mpv. */
    // The Stalker `cmd` whose (freshly-resolved) URL is currently loaded in previewEngine. Stalker
    // resolves cmd→URL per play, so the engine's currentUrl is the resolved link, not the cmd — this
    // tracks the cmd identity so re-focus/promote can tell "same channel" without re-resolving.
    private var stalkerPreviewCmd: String? = null
    private var stalkerPreviewJob: Job? = null

    // C-3 (§5.4.1): the live-reconnect URL provider installed on both engines while a Stalker live
    // channel plays, so a mid-session stream-death re-resolves a fresh create_link instead of looping
    // on the expired URL. Null for M3U/Xtream (their URLs are stable). The cmd it resolves is tracked
    // here so the provider always re-resolves the CURRENT channel even after a zap.
    @Volatile private var stalkerReconnectCmd: String? = null
    private val stalkerReconnectProvider = tv.own.owntv.core.stalker.ReconnectUrlProvider {
        val cmd = stalkerReconnectCmd ?: return@ReconnectUrlProvider null
        val channel = _previewChannel.value ?: return@ReconnectUrlProvider null
        val source = sourceById[channel.sourceId] ?: sourceDao.getById(channel.sourceId)
        if (!streamUrlResolver.needsResolve(source)) return@ReconnectUrlProvider null
        runCatching { streamUrlResolver.resolve(source!!, cmd) }
            .onFailure { Log.w(ENGINE_TAG, "stalker reconnect re-resolve failed '${channel.name}'", it) }
            .getOrNull()
    }

    /** Install the reconnect provider on both engines for a Stalker [cmd], or clear it (null). */
    private fun setStalkerReconnect(cmd: String?) {
        stalkerReconnectCmd = cmd
        val provider = if (cmd != null) stalkerReconnectProvider else null
        previewEngine.reconnectUrlProvider = provider
        player.reconnectUrlProvider = provider
    }

    fun playPreview(channel: ChannelEntity) {
        // Don't touch the engine while it's promoted to full-screen. Clicking OK before the in-pane preview's
        // focus-delay fires would otherwise let this late preview call re-mute the now-full-screen stream
        // (preview audio is off) — so full-screen would play with no sound. ensurePlaying() sets liveOnExo
        // the instant OK is pressed, before this can run.
        if (_liveOnExo.value) return
        val source = sourceById[channel.sourceId]
        if (streamUrlResolver.needsResolve(source)) { playPreviewStalker(channel, source!!); return }
        // Already previewing this channel (e.g. re-focus)? Just re-apply the preview mute, no reload.
        if (previewEngine.currentUrl == channel.streamUrl &&
            previewEngine.state.value != tv.own.owntv.player.LivePreviewEngine.State.ERROR
        ) {
            previewEngine.setMuted(!previewAudioOn())
            return
        }
        stalkerPreviewCmd = null
        setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
        previewEngine.play(
            channel.streamUrl, muted = !previewAudioOn(),
            meta = tv.own.owntv.player.MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
            userAgent = sourceUaMap[channel.sourceId],
        )
    }

    // While a PiP corner is up, the in-pane preview must stay silent even with the "preview audio" setting
    // on — otherwise browsing plays TWO soundtracks (the corner is the audible window in browse mode).
    private var previewAudioSuppressed = false
    private fun previewAudioOn(): Boolean = livePreviewAudio.value && !previewAudioSuppressed

    /** Shell calls this with the PiP corner's active state; applies to the current preview immediately. */
    fun setPreviewAudioSuppressed(suppressed: Boolean) {
        previewAudioSuppressed = suppressed
        if (!_liveOnExo.value && previewEngine.currentUrl != null) previewEngine.setMuted(!previewAudioOn())
    }

    /** Per-source custom user-agent — the corner/tile engines need the same UA the main engine uses. */
    fun uaFor(sourceId: Long): String? = sourceUaMap[sourceId]

    /** Stalker preview: same "already-previewing → just re-mute" shortcut keyed by the cmd, else
     *  resolve the cmd to a real URL (create_link) and load it. Async because resolution is a network call.
     *  (Fork: mute goes through previewAudioOn() so the PiP-corner suppression covers Stalker too.) */
    private fun playPreviewStalker(channel: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity) {
        if (stalkerPreviewCmd == channel.streamUrl &&
            previewEngine.state.value != tv.own.owntv.player.LivePreviewEngine.State.ERROR
        ) {
            previewEngine.setMuted(!previewAudioOn())
            return
        }
        stalkerPreviewJob?.cancel()
        stalkerPreviewJob = viewModelScope.launch {
            val url = runCatching { streamUrlResolver.resolve(source, channel.streamUrl) }
                .onFailure { Log.w(ENGINE_TAG, "stalker preview resolve failed '${channel.name}'", it) }
                .getOrNull() ?: return@launch
            if (_liveOnExo.value) return@launch // promoted to fullscreen while resolving
            stalkerPreviewCmd = channel.streamUrl
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            previewEngine.play(
                url, muted = !previewAudioOn(),
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
                userAgent = source.userAgent,
            )
        }
    }

    // The ordered channel list of the row the user opened fullscreen from, so the player HUD can
    // zap up/down with the remote without going back to the list. Snapshot of the loaded paging
    // window (enough neighbours either side of the opened channel).
    private var zapList: List<ChannelEntity> = emptyList()
    private val _canZap = MutableStateFlow(false)
    val canZap: StateFlow<Boolean> = _canZap.asStateFlow()
    // The opened channel list, exposed so the in-player channel-list overlay can show & jump within it.
    private val _zapChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val zapChannels: StateFlow<List<ChannelEntity>> = _zapChannels.asStateFlow()

    /** True when full-screen is running on the **ExoPlayer** engine (a promoted preview) rather than mpv.
     *  The shell renders the ExoPlayer surface instead of mpv's when this is set. */
    private val _liveOnExo = MutableStateFlow(false)
    val liveOnExo: StateFlow<Boolean> = _liveOnExo.asStateFlow()

    /** Called when anything OTHER than a promoted live channel takes over full-screen (a movie/episode,
     *  catch-up, an EPG/search channel — all play on mpv). Clears the ExoPlayer flag so the shell renders
     *  mpv's surface (not the leftover live channel) and stops the preview so it doesn't hold a connection. */
    /** Back out of full-screen to the Live screen: we're no longer full-screen on ExoPlayer, so the preview
     *  pane may re-take the engine (and re-apply the preview mute) on the next focus. Keeps the stream
     *  playing (no stop) — just clears the flag so [playPreview] works again. */
    fun onFullscreenExited() {
        _liveOnExo.value = false
    }

    fun clearLiveOnExo() {
        exoOutcomeJob?.cancel()
        stalkerPreviewJob?.cancel()
        stalkerPreviewCmd = null
        setStalkerReconnect(null) // catch-up/VOD-style mpv takes over — no live-reconnect re-resolve
        _liveOnExo.value = false
        previewEngine.stop()
    }

    /** The most-recently-watched live channel for the active profile (for "resume last channel"). Waits
     *  for the profile to be known, then reads the newest watch-history row. Null if there is none. */
    suspend fun lastWatchedLiveChannel(): ChannelEntity? {
        val pid = ctx.first { it.profileId >= 0 }.profileId
        return channelDao.recentlyWatched(pid, 1).first().firstOrNull()
    }

    /** Open a channel fullscreen, remembering [list] so the remote can zap up/down from here. */
    fun watchFullscreen(channel: ChannelEntity, list: List<ChannelEntity>) {
        zapList = list
        _zapChannels.value = list
        _canZap.value = list.size > 1
        ensurePlaying(channel)
    }

    /** Zap to the neighbouring channel ([delta] = +1 down / -1 up) within the opened list, wrapping
     *  around at the ends so you never dead-end (last → first, first → last). */
    fun zap(delta: Int) {
        val list = zapList
        if (list.size < 2) return
        val i = list.indexOfFirst { it.id == _previewChannel.value?.id }
        if (i < 0) return
        val next = ((i + delta) % list.size + list.size) % list.size // modulo wrap (handles negatives)
        if (next == i) return
        ensurePlaying(list[next])
    }

    /** Go full-screen on [channel]. ExoPlayer is the **primary** live engine (instant for HLS, and it plays
     *  the channels mpv struggles to open): promote the running preview if it's already this channel, else
     *  (re)start ExoPlayer on it. We fall back to the full **mpv** player ONLY if ExoPlayer **errors** (a
     *  stream it can't open) — never just because it's still loading (clicking OK before the preview is ready
     *  used to drop to mpv and stick on a black screen for HLS). */
    fun ensurePlaying(channel: ChannelEntity) {
        _previewChannel.value = channel
        timeshiftJob?.cancel(); tickJob?.cancel(); _timeshiftOffsetSec.value = null // normal live = not timeshifted
        // Self-learning routing: a channel the user pinned to mpv skips ExoPlayer entirely (no artifacts/silent
        // first), straight to the engine that plays it. Everyone else gets the fast ExoPlayer-first path.
        val pinned = channel.streamUrl in forceMpvUrls.value
        android.util.Log.i(ENGINE_TAG, "tune '${channel.name}' -> ${if (pinned) "mpv (pinned)" else "exoplayer"}")
        if (pinned) startOnMpv(channel) else startOnExo(channel)
        recordLiveHistory(channel)
    }

    private fun startOnExo(channel: ChannelEntity) {
        _liveOnExo.value = true
        player.stop() // free mpv (decoder/connection) if a previous full-screen used it
        val source = sourceById[channel.sourceId]
        if (streamUrlResolver.needsResolve(source)) { startOnExoStalker(channel, source!!); return }
        if (previewEngine.currentUrl == channel.streamUrl) {
            previewEngine.setMuted(false) // promote — instant if already PLAYING, otherwise keeps loading
        } else {
            // In-player zap to a DIFFERENT channel (CH+/-, D-pad, channel-list overlay): if we're leaving a
            // UHD channel, fully release its 4K decoder before the reuse/rebuild (no-op for SD/HD). Matches
            // the Back/exit path — so the Hisense 4K-decoder leak is avoided however you leave the channel.
            previewEngine.releaseDecoderForUhd()
            stalkerPreviewCmd = null
            setStalkerReconnect(null) // non-Stalker: URLs are stable, replay on reconnect
            previewEngine.play(
                channel.streamUrl, muted = false,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
                userAgent = sourceUaMap[channel.sourceId],
            )
        }
        watchExoOutcome(channel)
    }

    /** Fullscreen a Stalker channel on ExoPlayer: promote the preview if it already holds this cmd,
     *  else resolve the cmd (create_link) and load the fresh URL. */
    private fun startOnExoStalker(channel: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity) {
        if (stalkerPreviewCmd == channel.streamUrl) {
            previewEngine.setMuted(false) // promote the already-loaded preview
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            watchExoOutcome(channel)
            return
        }
        stalkerPreviewJob?.cancel()
        stalkerPreviewJob = viewModelScope.launch {
            previewEngine.releaseDecoderForUhd()
            val url = runCatching { streamUrlResolver.resolve(source, channel.streamUrl) }
                .onFailure { Log.w(ENGINE_TAG, "stalker fullscreen resolve failed '${channel.name}'", it) }
                .getOrNull() ?: return@launch // portal/auth failure — nothing playable to hand the engine
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return@launch // zapped away while resolving
            stalkerPreviewCmd = channel.streamUrl
            setStalkerReconnect(channel.streamUrl) // C-3: re-resolve on reconnect if the URL expires
            previewEngine.play(
                url, muted = false,
                meta = tv.own.owntv.player.MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
                userAgent = source.userAgent,
            )
            watchExoOutcome(channel)
        }
    }

    /** Start [channel] on the full mpv engine (pinned "compatibility" channel, or an ExoPlayer fallback). */
    private fun startOnMpv(channel: ChannelEntity) { viewModelScope.launch { fallbackToMpv(channel) } }

    /** HUD "compatibility mode" toggle: pin/unpin the current channel to mpv and swap engines live. */
    fun toggleForceMpv() {
        val channel = _previewChannel.value ?: return
        // Base the swap on the ACTUAL running engine, not the pin: after an auto-fallback to mpv the channel
        // runs on mpv while still unpinned, and the old pin-based logic then did nothing on click. Keying off
        // _liveOnExo makes every click flip the live engine, with the pin following the choice.
        val goToMpv = _liveOnExo.value // on Exo now → switch to mpv; on mpv now → switch to Exo
        android.util.Log.i(ENGINE_TAG, "engine toggle '${channel.name}' -> ${if (goToMpv) "mpv" else "exoplayer"} (currentlyOnExo=${_liveOnExo.value})")
        viewModelScope.launch {
            forceMpvStore.set(channel.streamUrl, goToMpv) // pin to mpv when choosing mpv; unpin when choosing Exo
            if (goToMpv) {
                fallbackToMpv(channel) // ExoPlayer → mpv now
            } else {                    // mpv → ExoPlayer now
                player.stop()
                delay(500) // let mpv's decoder/surface release before ExoPlayer takes over
                if (_previewChannel.value?.streamUrl == channel.streamUrl) startOnExo(channel)
            }
        }
    }

    fun ensurePlayingById(channelId: Long) {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId) ?: return@launch
            ensurePlaying(channel)
        }
    }

    suspend fun ensurePlayingByIdAsync(channelId: Long, zapChannels: List<ChannelEntity> = emptyList()): Boolean {
        val channel = channelDao.getById(channelId) ?: return false
        zapList = zapChannels
        // Also drive the left-arrow channel-list overlay, which reads _zapChannels. Without this, a
        // channel launched from Home (Keep Watching / Favourites) left the overlay showing the stale
        // list from the previous Live-TV session (#55). CH+/CH- already used zapList, so only the
        // overlay was wrong — keep both in sync here as watchFullscreen() does.
        _zapChannels.value = zapChannels
        _canZap.value = zapChannels.size > 1
        ensurePlaying(channel)
        return true
    }

    /** One-shot: hand [channel] to mpv if ExoPlayer can't play it fully — either it **errors** opening, or it
     *  plays but ExoPlayer can decode **none of its audio** (e.g. an AC3/E-AC3/DTS movie file added via M3U,
     *  on a device without that decoder — it'd play silently). mpv (FFmpeg) decodes everything. */
    private var exoOutcomeJob: Job? = null
    private fun watchExoOutcome(channel: ChannelEntity) {
        exoOutcomeJob?.cancel()
        exoOutcomeJob = viewModelScope.launch {
            // Runs alongside the terminal-state wait below: audio/position can be progressing fine (so
            // ExoPlayer never reaches ERROR) while a video track never renders a single frame — the "audio
            // plays, no picture" case. One-shot per tune; mpv's own outcome (success or its own error state)
            // takes it from there, same as the ERROR branch below.
            launch {
                previewEngine.noVideoDetected.first { it }
                if (isStill(channel)) fallbackToMpv(channel)
            }
            val terminal = previewEngine.state.first {
                it == tv.own.owntv.player.LivePreviewEngine.State.PLAYING ||
                    it == tv.own.owntv.player.LivePreviewEngine.State.ERROR
            }
            if (!isStill(channel)) return@launch
            if (terminal == tv.own.owntv.player.LivePreviewEngine.State.ERROR) { fallbackToMpv(channel); return@launch }
            // PLAYING: give the track list a moment to settle, then route silent (undecodable-audio) streams to mpv.
            delay(300)
            if (isStill(channel) && previewEngine.audioUnsupported.value) fallbackToMpv(channel)
        }
    }

    private fun isStill(channel: ChannelEntity) =
        _liveOnExo.value && _previewChannel.value?.streamUrl == channel.streamUrl

    private suspend fun fallbackToMpv(channel: ChannelEntity) {
        android.util.Log.i(ENGINE_TAG, "starting mpv for '${channel.name}'")
        _liveOnExo.value = false            // shell flips to mpv's surface
        stalkerPreviewCmd = null
        previewEngine.stop()
        delay(500)                          // let ExoPlayer's decoder release before mpv inits
        if (_previewChannel.value?.streamUrl == channel.streamUrl) {
            val source = sourceById[channel.sourceId] ?: sourceDao.getById(channel.sourceId)
            // Stalker stores the portal cmd — resolve it to a real URL (create_link) before mpv plays.
            val isStalker = streamUrlResolver.needsResolve(source)
            val url = if (isStalker) {
                runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }
                    .onFailure { Log.w(ENGINE_TAG, "stalker mpv resolve failed '${channel.name}'", it) }
                    .getOrNull() ?: return
            } else {
                channel.streamUrl
            }
            if (_previewChannel.value?.streamUrl != channel.streamUrl) return // zapped away while resolving
            // C-3: mpv is now the active engine — install/clear the reconnect provider to match.
            setStalkerReconnect(if (isStalker) channel.streamUrl else null)
            player.play(url, title = channel.name, logoUrl = channel.logoUrl, isLive = true, muted = false, userAgent = source?.userAgent)
        }
    }

    private var historyJob: Job? = null

    private fun recordLiveHistory(channel: ChannelEntity) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            delay(5_000L)
            val pid = currentProfileId() ?: return@launch
            Log.d(TAG, "ensurePlaying history profile=$pid channelId=${channel.id}")
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }.onFailure { t ->
                Log.w(TAG, "ensurePlaying history record failed channelId=${channel.id} profile=$pid", t)
            }
            runCatching { launcherIntegrationRepository.refreshRecentLive(pid) }
        }
    }

    // ---- Catch-up from Live TV: pick a recent programme to replay from the archive (#proposal) ----

    /** Recent (already-aired) programmes for a catch-up channel, newest first — drives the Live TV
     *  catch-up picker. Bounded to the EPG we retain (≈ 2 days) and the channel's archive window. */
    suspend fun catchupProgrammes(ch: ChannelEntity): List<tv.own.owntv.core.database.entity.EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        if (!ch.catchup) return@withContext emptyList()
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        val windowMs = (ch.catchupDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000).coerceAtMost(CATCHUP_LOOKBACK_CAP_MS)
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        epgDao.programmesForChannel(ids, epgKey, now - windowMs, now + 60 * 60 * 1000)
            .filter { it.startMs <= now }          // already started → catch-up applies
            .sortedByDescending { it.startMs }      // most recent first
            .take(80)
    }

    /** Replay a past programme from the channel's archive (seekable, like the Guide's "Watch from start"). */
    fun playCatchupProgramme(ch: ChannelEntity, programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
                // Stalker archive URLs are minted per-play via create_link (Phase E §5.6); the others
                // are pure string templates handled by CatchupUrl.
                if (source.type == SourceType.STALKER) {
                    ch.remoteId?.let { rid ->
                        runCatching { streamUrlResolver.resolveCatchup(source, rid, programme.startMs, programme.stopMs) }
                            .onFailure { Log.w(TAG, "Stalker catch-up resolve failed channelId=${ch.id}", it) }
                            .getOrNull()
                    }
                } else {
                    CatchupUrl.forSource(ch, programme, source, settings.resolveCatchupTimeZone(), xtreamClient)
                }
            } ?: return@launch
            val sourceUa = withContext(Dispatchers.IO) { sourceDao.getById(ch.sourceId)?.userAgent }
            _previewChannel.value = ch
            _timeshiftOffsetSec.value = null // guide archive isn't the live-rewind timeshift
            clearLiveOnExo() // catch-up is a VOD-style archive on mpv, not the live ExoPlayer channel
            // isLive=false → seekable archive; preferSoftware → tolerate mid-GOP archive segments.
            player.play(url, title = ch.name, subtitle = programme.title, logoUrl = ch.logoUrl, isLive = false, preferSoftware = true, userAgent = sourceUa)
        }
    }

    // ---- Live rewind / timeshift -------------------------------------------------------------------
    // Watch a catch-up-capable live channel a few minutes behind the live edge (a missed goal, etc.) using
    // the provider's rolling archive (Xtream timeshift / M3U catchup), then jump back to live. The archive
    // is a VOD-style stream on mpv (preferSoftware, mid-GOP tolerant); "Go to live" returns to ExoPlayer.
    private val _timeshiftOffsetSec = MutableStateFlow<Int?>(null) // null = at the live edge; >0 = N s behind
    val timeshiftOffsetSec: StateFlow<Int?> = _timeshiftOffsetSec.asStateFlow()

    /** True when the channel on screen records an archive — the HUD then offers "Rewind" on live. */
    val canRewindLive: StateFlow<Boolean> =
        _previewChannel.map { it?.catchup == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var timeshiftJob: Job? = null
    private var tickJob: Job? = null
    private var timeshiftStartWall = 0L // wall-clock time of the loaded archive's start (for the live counter)
    private val rewindStepSec = 30

    /** 30 s buttons. */
    fun rewindLive() = scrubLive(rewindStepSec)
    fun forwardLive() = scrubLive(-rewindStepSec)

    /** Move [deltaSec] further back (+) or toward live (−) into the archive (also drives the timeline
     *  scrubber). Coalesced so holding a key scrubs freely and loads the archive once at the final point;
     *  reaching the live edge returns to the real-time stream. */
    fun scrubLive(deltaSec: Int) {
        val ch = _previewChannel.value ?: return
        if (!ch.catchup) return
        val maxBack = (ch.catchupDays.takeIf { it > 0 } ?: 7) * 24 * 3600
        val next = ((_timeshiftOffsetSec.value ?: 0) + deltaSec).coerceIn(0, maxBack)
        if (next == 0) { goToLive(); return }
        _timeshiftOffsetSec.value = next
        scheduleTimeshiftLoad(ch, next)
    }

    /** Jump back to the real-time live edge (back on the fast ExoPlayer engine). */
    fun goToLive() {
        timeshiftJob?.cancel(); tickJob?.cancel()
        _timeshiftOffsetSec.value = null
        _previewChannel.value?.let { ensurePlaying(it) }
    }

    private fun scheduleTimeshiftLoad(ch: ChannelEntity, offsetSec: Int) {
        timeshiftJob?.cancel(); tickJob?.cancel()
        timeshiftJob = viewModelScope.launch {
            delay(350) // coalesce rapid rewind/forward presses into one archive load
            val nowMs = System.currentTimeMillis()
            val startMs = nowMs - offsetSec * 1000L
            val tz = withContext(Dispatchers.IO) { settings.resolveCatchupTimeZone() }
            val (url, sourceUa) = withContext(Dispatchers.IO) {
                val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
                buildLiveTimeshiftUrl(ch, source, startMs, offsetSec, tz)?.let { it to source.userAgent }
            } ?: return@launch
            if (_timeshiftOffsetSec.value == null) return@launch // user jumped back to live meanwhile
            // Show the clock time being watched (handy for the user; no credentials in logs).
            val localLabel = formatSystemTime(appContext, startMs)
            _previewChannel.value = ch
            clearLiveOnExo() // archive plays as a VOD-style mpv stream, not the live ExoPlayer channel
            player.play(url, title = ch.name, subtitle = "Rewind · $localLabel", logoUrl = ch.logoUrl, isLive = false, preferSoftware = true, userAgent = sourceUa)
            timeshiftStartWall = startMs
            startOffsetTick()
        }
    }

    /** Tick the "behind live" counter down as the archive plays forward (offset = realNow − watched time =
     *  realNow − (archive start + playback position)). Pausing makes it grow (you fall further behind). */
    private fun startOffsetTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (_timeshiftOffsetSec.value == null) break
                val behindSec = ((System.currentTimeMillis() - (timeshiftStartWall + player.position.value)) / 1000)
                _timeshiftOffsetSec.value = behindSec.toInt().coerceAtLeast(0)
            }
        }
    }

    private suspend fun buildLiveTimeshiftUrl(ch: ChannelEntity, source: tv.own.owntv.core.database.entity.SourceEntity, startMs: Long, offsetSec: Int, tz: java.util.TimeZone): String? {
        val durationMin = (offsetSec / 60 + 5).coerceAtLeast(1) // rewound window + buffer to play up to live
        return when (source.type) {
            SourceType.XTREAM -> ch.remoteId?.let { xtreamClient.timeshiftUrl(source, it, startMs, durationMin, tz) }
            SourceType.M3U -> CatchupUrl.forM3u(ch.streamUrl, null, ch.catchupSource, startMs, startMs + durationMin * 60_000L)
            // Stalker rewind = the same per-play archive create_link as catch-up (Phase E §5.6).
            SourceType.STALKER -> ch.remoteId?.let { rid ->
                runCatching { streamUrlResolver.resolveCatchup(source, rid, startMs, startMs + durationMin * 60_000L) }
                    .onFailure { Log.w(TAG, "Stalker rewind resolve failed channelId=${ch.id}", it) }
                    .getOrNull()
            }
            else -> null
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        setStalkerReconnect(null) // tearing down — no reconnect re-resolve should fire
        previewEngine.stop()
        player.stop()
        _previewChannel.value = null
    }

    /** Now/next for [ch], cached ~5 min. Prefers a manual EPG match / stored bulk guide, then falls
     *  back to Xtream's short EPG API. */
    private suspend fun loadEpg(ch: ChannelEntity): EpgNowNext? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        epgCache[ch.id]?.takeIf { now - it.at < 5 * 60_000 }?.let { return@withContext it.data }

        // 1) Bulk guide via the effective EPG id (manual match overrides the channel's own id).
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (epgKey != null) {
            val nowProg = epgDao.nowPlaying(epgKey, now)
            val future = epgDao.upcoming(epgKey, now, 6).first().filter { it.startMs > (nowProg?.startMs ?: 0) }
            val nextProg = future.firstOrNull()
            if (nowProg != null || nextProg != null) {
                val result = EpgNowNext(nowProg?.toXt(), nextProg?.toXt(), future.drop(1).take(4).map { it.toXt() })
                epgCache[ch.id] = CachedEpg(now, result)
                return@withContext result
            }
        }

        // 2) Provider short-EPG API fallback (Xtream get_short_epg / Stalker get_short_epg, Phase E §5.5).
        val streamId = ch.remoteId ?: return@withContext null
        val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
        val entries = when (source.type) {
            SourceType.XTREAM -> runCatching { xtreamClient.getShortEpg(source, streamId, limit = 8) }
                .getOrNull().orEmpty()
            SourceType.STALKER -> runCatching {
                streamUrlResolver.shortEpg(source, streamId)
                    .map { XtEpgEntry(title = it.title, description = it.description, startMs = it.startMs, stopMs = it.stopMs) }
            }.getOrNull().orEmpty()
            else -> return@withContext null
        }
        val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
            ?: entries.firstOrNull { it.stopMs > now }
        val future = entries.filter { it.startMs > (current?.startMs ?: now) }.sortedBy { it.startMs }
        val result = EpgNowNext(current, future.firstOrNull(), future.drop(1).take(4))
        epgCache[ch.id] = CachedEpg(now, result)
        result
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private fun tv.own.owntv.core.database.entity.EpgProgrammeEntity.toXt() =
        XtEpgEntry(title = title, description = description, startMs = startMs, stopMs = stopMs)

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        return if (query.isBlank()) {
            when (key) {
                LiveKey.All -> if (playlist) channelDao.pagingAllOriginal(ids) else channelDao.pagingAll(ids)
                LiveKey.Favorites -> channelDao.pagingFavoritesManual(c.profileId, ContentOrderEntity.FAV_CONTEXT, ids)
                LiveKey.History -> channelDao.pagingHistory(c.profileId, ids)
                is LiveKey.Folder -> {
                    val ctxKey = folderContextKeys.value[key.id] ?: ""
                    // C3 fast path: no manual order in this folder → the plain indexed query has
                    // the identical (sortOrder, name) order without the join-sort.
                    if (ctxKey !in orderedContexts.value) channelDao.pagingByCategory(key.id)
                    else channelDao.pagingByCategoryManual(key.id, c.profileId, ctxKey)
                }
            }
        } else {
            when (key) {
                LiveKey.All -> channelDao.searchAll(query, ids)
                LiveKey.Favorites -> channelDao.searchFavorites(query, c.profileId, ids)
                LiveKey.History -> channelDao.searchHistory(query, c.profileId, ids)
                is LiveKey.Folder -> channelDao.searchInCategory(query, key.id)
            }
        }
    }

    fun enterMoveMode(channel: ChannelEntity, key: LiveKey) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            val contextKey = when (key) {
                is LiveKey.Folder -> folderContextKeys.value[key.id] ?: return@launch
                LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                else -> return@launch
            }
            val items = when (key) {
                is LiveKey.Folder -> channelDao.snapshotByCategoryManual(key.id, pid, contextKey, 5000)
                LiveKey.Favorites -> channelDao.snapshotFavoritesManual(pid, contextKey, ctx.value.sourceIds.ifEmpty { listOf(-1L) }, 5000)
                else -> return@launch
            }
            val idx = items.indexOfFirst { it.id == channel.id }
            if (idx < 0) return@launch
            _moveState.value = ChannelMoveState(items, idx, contextKey)
            settings.setSortLive(SettingsRepository.SortMode.PLAYLIST)
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
                type = MediaType.LIVE,
                contextKey = s.contextKey,
                rows = s.items.mapIndexed { i, ch ->
                    ContentOrderEntity(profileId = pid, mediaType = MediaType.LIVE, contextKey = s.contextKey, itemId = ch.id, position = i)
                },
            )
        }
    }

    fun cancelMove() { _moveState.value = null }

    fun removeFromHistory(channelId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            historyDao.remove(pid, MediaType.LIVE, channelId)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> if (hiddenCats.isEmpty()) channelDao.countAll(ids) else channelDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> channelDao.countFavorites(c.profileId, ids)
            LiveKey.History -> channelDao.countHistory(c.profileId, ids)
            is LiveKey.Folder -> channelDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val ENGINE_TAG = "LiveEngine"
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Channels"),
        )
        const val CATCHUP_LOOKBACK_CAP_MS = 48L * 60 * 60 * 1000 // bounded by the EPG we retain (~2 days)
    }
}

/** Short 2–3 char rail label from a category name. */
private fun abbreviate(name: String): String {
    val cleaned = name.filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val words = cleaned.split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "•"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.first().uppercase() }
    }
}
