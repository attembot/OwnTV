package tv.own.owntv.features.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.launcher.LauncherLaunch
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.update.UpdateStatusToast
import tv.own.owntv.features.downloads.DownloadsScreen
import tv.own.owntv.features.epg.EpgScreen
import tv.own.owntv.features.home.HomeScreen
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.features.live.LiveScreen
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.search.SearchScreen
import tv.own.owntv.features.series.SeriesScreen
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.player.MiniPlayer
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlayerHud
import tv.own.owntv.features.shell.components.AvatarPickerDialog
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.ContentPane
import tv.own.owntv.features.shell.components.ExitDialog
import tv.own.owntv.features.shell.components.PlaylistPickerDialog
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.features.shell.components.SettingsScreen
import tv.own.owntv.features.shell.components.Sidebar
import tv.own.owntv.features.shell.components.TopBar
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.ThemeMode

/** Which layer currently holds focus (drives Back navigation). */
private enum class ShellLayer { SIDEBAR, RAIL, CONTENT }

/** Player presentation: hidden, fullscreen, or docked mini-player over the browse UI. */
private enum class PlayerMode { NONE, FULLSCREEN, MINI }

/** Which screen corner the PiP window sits in. [next] cycles them: top-right → top-left → bottom-left →
 *  bottom-right → top-right. A new PiP always (re)starts at [TOP_END]. */
private enum class CornerPos {
    TOP_END, TOP_START, BOTTOM_START, BOTTOM_END;

    fun next(): CornerPos = when (this) {
        TOP_END -> TOP_START
        TOP_START -> BOTTOM_START
        BOTTOM_START -> BOTTOM_END
        BOTTOM_END -> TOP_END
    }
}

/** PiP window sizing: the base 320×180 dp (the original fixed size) scaled by a user-adjustable
 *  percentage. Size +/− steps by [CORNER_SCALE_STEP]; clamped to [CORNER_SCALE_MIN]% (the base — no
 *  smaller) … [CORNER_SCALE_MAX]% (640×360 dp, which still fits every corner position with its 24 dp
 *  margin on a 960×540 dp TV canvas). Remembered for the session (unlike the position, which resets
 *  to top-right on each open). dp-based, so it additionally scales with the global UI-zoom density. */
private const val CORNER_BASE_W = 320
private const val CORNER_BASE_H = 180
private const val CORNER_SCALE_MIN = 100
private const val CORNER_SCALE_MAX = 200
private const val CORNER_SCALE_STEP = 10

/**
 * The MD3 shell: a fixed navigation panel (Layer 1) plus the active destination. Settings is a
 * single-pane sectioned screen; browse sections keep the Folder Rail → Content → Preview layout.
 */
@Composable
fun OwnTVShell(
    selectedSection: MainSection,
    visibleSections: Set<MainSection>,
    onSelectSection: (MainSection) -> Unit,
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    avatarId: Int,
    onSetAvatar: (Int) -> Unit,
    profileName: String,
    sourceSummary: String,
    playlists: List<tv.own.owntv.core.database.entity.SourceEntity> = emptyList(),
    activePlaylistId: Long = -1L,
    onSelectPlaylist: (Long) -> Unit = {},
    weatherInfo: tv.own.owntv.core.weather.WeatherInfo? = null, // Phase 7
    weatherFahrenheit: Boolean = false,
    activeProfileId: Long?,
    pendingDeepLink: LauncherDeepLink?,
    onDeepLinkConsumed: () -> Unit,
    isOffline: Boolean = false,
    onExitApp: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val railSelection = remember { mutableStateMapOf<MainSection, Int>() }
    val selectedRail = railSelection[selectedSection] ?: 0
    val categories = railCategoriesFor(selectedSection)

    val scope = rememberCoroutineScope()
    val sidebarFocus = remember { FocusRequester() }
    var focusedLayer by remember { mutableStateOf(ShellLayer.SIDEBAR) }
    var showExit by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    // Deep-link: the Guide's "Add EPG" button switches to Settings and opens EPG Sources → add.
    var openEpgAdd by remember { mutableStateOf(false) }
    // One-shot: set when leaving the player so the returning browse screen re-focuses the item you played.
    var restoreFocus by remember { mutableStateOf(false) }
    val player = koinInject<OwnTVPlayer>()
    val mpvEngine = remember(player) { tv.own.owntv.player.MpvPlaybackEngine(player) }
    val launcherIntegrationRepository = koinInject<LauncherIntegrationRepository>()
    val homeVm = org.koin.androidx.compose.koinViewModel<HomeViewModel>()
    val movieVm = org.koin.androidx.compose.koinViewModel<MovieViewModel>()
    val seriesVm = org.koin.androidx.compose.koinViewModel<SeriesViewModel>()
    // True picture-in-picture: a second, independent stream in a corner window, mounted at the shell's top
    // level so it persists across the browse UI <-> full-screen. Audio belongs to one window at a time —
    // by default the main stream, until the user hands sound to the corner (audioOnCorner).
    val pip = koinInject<tv.own.owntv.features.multiview.PipController>()
    val cornerActive by pip.active.collectAsStateWithLifecycle()
    val cornerChannel by pip.channel.collectAsStateWithLifecycle()
    var audioOnCorner by remember { mutableStateOf(false) }
    // True while the channel switcher for the PiP corner is open (retune the corner without closing it).
    var cornerBrowsing by remember { mutableStateOf(false) }
    // True while picking the SECOND stream to open in the corner from the full-screen player (true PiP entry).
    var pipPicking by remember { mutableStateOf(false) }
    // True while picking a new channel for the FULL-SCREEN window from the PiP row (corner keeps playing).
    var mainPicking by remember { mutableStateOf(false) }
    // Which screen corner the PiP window sits in. Resets to TOP_END each time a corner is (re)opened.
    var cornerPos by remember { mutableStateOf(CornerPos.TOP_END) }
    // PiP window scale (percent of the 320×180 base) — Size +/− steps it, remembered for the session.
    var cornerScalePct by remember { androidx.compose.runtime.mutableIntStateOf(CORNER_SCALE_MIN) }
    val cornerGrow = { cornerScalePct = (cornerScalePct + CORNER_SCALE_STEP).coerceAtMost(CORNER_SCALE_MAX) }
    val cornerShrink = { cornerScalePct = (cornerScalePct - CORNER_SCALE_STEP).coerceAtLeast(CORNER_SCALE_MIN) }
    // Bumped whenever either window is retuned out-of-band (zap, Change main/PiP, swap) so the audio
    // arbitration below re-applies its plan — retuning unmutes/mutes engines without changing any of the
    // arbitration's other keys, which previously left both windows audible (or both silent).
    var audioTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Same activity-scoped instances the Live/Guide screens use — lets the fullscreen HUD zap channels
    // up/down (CH+/CH-) through whichever section's list opened the stream.
    val liveVm = org.koin.androidx.compose.koinViewModel<LiveViewModel>()
    val epgVm = org.koin.androidx.compose.koinViewModel<tv.own.owntv.features.epg.EpgViewModel>()
    val liveCanZap by liveVm.canZap.collectAsStateWithLifecycle()
    val epgCanZap by epgVm.canZap.collectAsStateWithLifecycle()
    // Full-screen is running on the ExoPlayer engine (a promoted Live preview) rather than mpv.
    val liveOnExo by liveVm.liveOnExo.collectAsStateWithLifecycle()
    val vodExoActive by player.exoActiveState.collectAsStateWithLifecycle()
    // Live rewind / timeshift: whether the live channel supports catch-up, and how far behind live we are.
    val canRewindLive by liveVm.canRewindLive.collectAsStateWithLifecycle()
    val timeshiftOffset by liveVm.timeshiftOffsetSec.collectAsStateWithLifecycle()
    // Which section armed the current fullscreen stream — picks whose channel list CH+/CH- step through.
    var zapSource by remember { mutableStateOf<MainSection?>(null) }
    // In-player channel-list overlay (Left while controls hidden, live only).
    var showChannelList by remember { mutableStateOf(false) }
    val zapChannels by liveVm.zapChannels.collectAsStateWithLifecycle()
    val previewChannel by liveVm.previewChannel.collectAsStateWithLifecycle()
    // Batch 7 — the single most-recent resumable item, surfaced as a shared top-bar "Continue" chip.
    val continueTarget by homeVm.continueTarget.collectAsStateWithLifecycle()

    // "Resume last channel on startup" (opt-in, default off): once when the shell first appears, if enabled
    // and nothing is playing, jump straight back into the last live channel watched. Reads the setting once
    // (via first()) so toggling it later in Settings never yanks the user into a channel.
    val resumeSettings = koinInject<tv.own.owntv.features.settings.data.SettingsRepository>()
    LaunchedEffect(Unit) {
        if (playerMode != PlayerMode.NONE) return@LaunchedEffect
        val pid = resumeSettings.activeProfileId.first()
        when (resumeSettings.startupMode(pid).first()) {
            tv.own.owntv.features.settings.data.StartupMode.LAST_CHANNEL -> {
                val ch = liveVm.lastWatchedLiveChannel()
                if (ch != null && playerMode == PlayerMode.NONE) {
                    zapSource = MainSection.LIVE_TV
                    liveVm.watchFullscreen(ch, listOf(ch))
                    playerMode = PlayerMode.FULLSCREEN
                }
            }
            // Open straight to Live TV on the Favorites folder, with focus landing inside the channel list
            // (restoreFocus drives LiveScreen to focus the first/last channel, not the nav panel).
            tv.own.owntv.features.settings.data.StartupMode.FAVORITES -> {
                onSelectSection(MainSection.LIVE_TV)
                liveVm.select(tv.own.owntv.features.live.LiveKey.Favorites)
                restoreFocus = true
            }
            tv.own.owntv.features.settings.data.StartupMode.HOME -> Unit
        }
    }

    // Movies/Series/Live load on first open via their reactive Paging flows — their indexed first page is
    // cheap, so they need NO preloading (a Live-TV-only user pays nothing for them). The TV Guide is the ONE
    // exception: load() pulls every guide channel + a programme window, which is heavy enough that doing it on
    // open felt slow. So warm EPG in the background shortly after the shell renders — opening the Guide is then
    // instant, matching how it behaved before. (EpgScreen also calls load() on mount, so this is a pure pre-warm
    // and is skipped if the user is already on EPG.)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_200)
        if (selectedSection != MainSection.EPG) { tv.own.owntv.Perf.stamp("epg-preload"); epgVm.load() }
    }

    // MultiView (PR #2): up to four live tiles. Channels to add come from the recently-watched list.
    val mv = koinInject<tv.own.owntv.features.multiview.MultiViewController>()
    val mvActive by mv.active.collectAsStateWithLifecycle()
    val recentChannels by liveVm.recentlyWatched.collectAsStateWithLifecycle()
    // Browse categories for the PiP/MultiView channel pickers: "Recent" + the live rail's All and folder
    // categories (each loads its channels on demand). Favorites/History stay in the main guide.
    val railItems by liveVm.railItems.collectAsStateWithLifecycle()
    val browseCategories = remember(railItems, recentChannels) {
        buildList {
            add(tv.own.owntv.ui.components.ChannelCategory("Recent") { recentChannels })
            // Every live-rail category (Favorites, History, All, and each folder) in the order the guide shows.
            railItems.forEach { item ->
                add(tv.own.owntv.ui.components.ChannelCategory(item.title) { liveVm.channelsFor(item.key) })
            }
        }
    }

    // Opening content from a browse screen goes fullscreen — UNLESS the player is already docked as a
    // mini-player, in which case it stays docked and just swaps to the newly-selected stream (the VM
    // already started it), so picking a channel updates the PiP window in place (#6).
    fun openFullscreen(source: MainSection = selectedSection) {
        restoreFocus = false
        zapSource = source
        homeVm.stopPreview()
        // Only Live TV promotes a channel to the ExoPlayer engine. Movies/Series/Search/EPG/Downloads all
        // play on mpv — clear any stale live-on-ExoPlayer flag so the shell renders mpv, not the old channel.
        if (source != MainSection.LIVE_TV) liveVm.clearLiveOnExo()
        if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
    }
    // The mini-player's own expand button always maximizes.
    val expandPlayer = { restoreFocus = false; playerMode = PlayerMode.FULLSCREEN }
    val exitPlayer = {
        playerMode = PlayerMode.NONE
        showChannelList = false
        liveVm.onFullscreenExited() // no longer full-screen on ExoPlayer → let the preview re-take the engine
        player.stop()
        if (selectedSection != MainSection.LIVE_TV) liveVm.clearLiveOnExo()
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    val dockPlayer = {
        playerMode = PlayerMode.MINI
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }

    LaunchedEffect(Unit) { tv.own.owntv.Perf.stamp("shell-composed"); runCatching { sidebarFocus.requestFocus() } }

    LaunchedEffect(pendingDeepLink, activeProfileId) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        val pid = activeProfileId ?: return@LaunchedEffect
        if (pid < 0) return@LaunchedEffect
        when (deepLink) {
            LauncherDeepLink.OpenLiveSection -> {
                onSelectSection(MainSection.LIVE_TV)
                onDeepLinkConsumed()
            }
            else -> when (val launch = launcherIntegrationRepository.resolveLaunch(pid, deepLink)) {
                is LauncherLaunch.Movie -> {
                    onSelectSection(MainSection.MOVIES)
                    movieVm.play(launch.movie, launch.startPositionMs)
                    openFullscreen(MainSection.MOVIES)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Episode -> {
                    onSelectSection(MainSection.SERIES)
                    seriesVm.playEpisodeQueue(launch.show, launch.queue, launch.episode, launch.startPositionMs)
                    openFullscreen(MainSection.SERIES)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Live -> {
                    onSelectSection(MainSection.LIVE_TV)
                    liveVm.ensurePlaying(launch.channel)
                    openFullscreen(MainSection.LIVE_TV)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Series -> {
                    onSelectSection(MainSection.SERIES)
                    seriesVm.openSeries(launch.show)
                    onDeepLinkConsumed()
                }
                null -> {
                    onDeepLinkConsumed()
                }
            }
        }
    }
    // --- True PiP corner: audio arbitration + window gestures -------------------------------------
    // Mute/unmute the main stream regardless of which engine owns it (promoted-live ExoPlayer vs mpv).
    val setMainMuted: (Boolean) -> Unit = { muted ->
        if (liveOnExo) liveVm.previewEngine.setMuted(muted) else player.setMuted(muted)
    }
    val closeCorner = {
        setMainMuted(false) // hand the sound back to the main window
        audioOnCorner = false
        pip.closeCorner()
        Unit
    }
    val toggleCornerAudio = { audioOnCorner = !audioOnCorner }
    // Full-screen swap (live main only): exchange the corner stream with the main one.
    val swapCorner = {
        val cornerCh = cornerChannel
        val mainCh = liveVm.previewChannel.value
        if (cornerCh != null && mainCh != null) {
            pip.openCorner(mainCh)         // old main → corner (starts muted)
            zapSource = MainSection.LIVE_TV
            liveVm.ensurePlaying(cornerCh) // old corner → main window
            audioOnCorner = false          // sound follows the (new) main window
            audioTick++                    // retune changed engine mutes out-of-band — re-apply the plan
        }
        Unit
    }
    // Enter MultiView seeded with [channel] (plus the PiP corner channel, if one is up). Stops the single-
    // stream players so ONLY MultiView's tile engines run — no main decoder competing with the tiles.
    val enterMultiView: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit = { channel ->
        val seed = buildList {
            add(channel)
            cornerChannel?.let { if (it.id != channel.id) add(it) }
        }
        pip.closeCorner(); audioOnCorner = false
        liveVm.clearLiveOnExo() // stop the live ExoPlayer (preview/main)
        player.stop()           // stop mpv (VOD), if it was the main
        playerMode = PlayerMode.NONE
        mv.enter(seed)
    }
    val exitMultiView = {
        mv.exit()
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    // Browse: promote the corner channel to full-screen, closing the corner.
    val expandCorner = {
        cornerChannel?.let { ch ->
            audioOnCorner = false
            pip.closeCorner()
            zapSource = MainSection.LIVE_TV
            onSelectSection(MainSection.LIVE_TV)
            liveVm.watchFullscreen(ch, listOf(ch))
            playerMode = PlayerMode.FULLSCREEN
        }
        Unit
    }
    // Only one window is audible at a time. The decision is a pure function (PipAudio) so it's unit-tested;
    // a null plan (no corner) leaves both engines untouched, so normal playback never has its mute changed.
    // audioTick re-runs the plan after any retune (zap / Change main / Change PiP / swap) — those calls set
    // engine mutes out-of-band (ensurePlaying unmutes the main, openCorner re-mutes the corner) without
    // changing the other keys, which used to leave both windows audible or both silent.
    LaunchedEffect(cornerActive, audioOnCorner, playerMode, liveOnExo, audioTick) {
        val plan = tv.own.owntv.features.multiview.PipAudio.plan(
            cornerActive = cornerActive,
            mainPresent = playerMode != PlayerMode.NONE,
            audioOnCorner = audioOnCorner,
        ) ?: return@LaunchedEffect
        plan.muteMain?.let { setMainMuted(it) }
        pip.engine.setMuted(plan.muteCorner)
    }

    // Per-source user-agents for the corner/tile engines (providers with a custom UA otherwise 403 in
    // PiP/MultiView while playing fine full-screen).
    LaunchedEffect(Unit) {
        pip.uaResolver = { sourceId -> liveVm.uaFor(sourceId) }
        mv.uaResolver = { sourceId -> liveVm.uaFor(sourceId) }
    }

    // If the corner closes by any path (close, swap-to-fullscreen, entering MultiView), drop its switcher too.
    // Also silence the in-pane preview while a corner is up — in browse mode the corner is the audible
    // window, and with the "preview audio" setting on the preview would otherwise play a second soundtrack.
    LaunchedEffect(cornerActive) {
        liveVm.setPreviewAudioSuppressed(cornerActive)
        if (cornerActive) cornerPos = CornerPos.TOP_END // every new PiP starts in the top-right
        else cornerBrowsing = false
    }

    LaunchedEffect(Unit) { runCatching { sidebarFocus.requestFocus() } }

    // Stop a leftover live preview when you leave the Live section (but never while fullscreen/mini plays).
    LaunchedEffect(selectedSection, playerMode) {
        if (selectedSection != MainSection.LIVE_TV && playerMode == PlayerMode.NONE) player.stop()
        if (selectedSection != MainSection.HOME || playerMode != PlayerMode.NONE) homeVm.stopPreview()
    }

    LaunchedEffect(selectedSection, playerMode, activeProfileId, activePlaylistId) {
        if (selectedSection == MainSection.HOME && playerMode == PlayerMode.NONE && (activeProfileId?.let { it >= 0 } == true)) {
            homeVm.refresh()
        }
    }

    BackHandler {
        when {
            playerMode == PlayerMode.FULLSCREEN -> exitPlayer()
            showAvatarPicker -> showAvatarPicker = false
            showPlaylistPicker -> showPlaylistPicker = false
            showExit -> showExit = false
            focusedLayer == ShellLayer.SIDEBAR -> showExit = true
            else -> runCatching { sidebarFocus.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
      // Browse UI — hidden while the player is fullscreen (stays visible behind the docked mini-player) and
      // while MultiView owns the screen (so its hidden Live preview decoder doesn't run behind the tiles).
      if (playerMode != PlayerMode.FULLSCREEN && !mvActive) {
        Column(modifier = Modifier.fillMaxSize()) {
          if (isOffline) OfflineBanner()
          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                selected = selectedSection,
                onSelect = onSelectSection,
                visibleSections = visibleSections,
                avatarId = avatarId,
                onPickAvatar = { showAvatarPicker = true },
                profileName = profileName,
                sourceSummary = sourceSummary,
                onSwitchProfile = onSwitchProfile,
                selectedItemFocusRequester = sidebarFocus,
                onFocused = { focusedLayer = ShellLayer.SIDEBAR },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    // Phase 6 — unified panel surface: panels and content area share #102520 so the
                    // rounded borders define regions on one continuous dark-green surface.
                    .background(colors.background),
            ) {
                // Phase 5 — top bar above the content (active section + Search pill + clock + playlist).
                // Shown on EVERY section now, including Settings ("top bar same for all").
                TopBar(
                    sectionLabel = selectedSection.label,
                    onSearchClick = { onSelectSection(MainSection.SEARCH) },
                    // The chip reflects the active filter: "All playlists" when none is chosen (id <= 0),
                    // the chosen playlist's name otherwise. With a single playlist there's nothing to switch,
                    // so just show its name.
                    playlistName = when {
                        playlists.size <= 1 -> sourceSummary
                        activePlaylistId <= 0L -> "All playlists"
                        else -> playlists.firstOrNull { it.id == activePlaylistId }?.name ?: sourceSummary
                    },
                    weatherInfo = weatherInfo,
                    weatherFahrenheit = weatherFahrenheit,
                    // The Search pill only exists while focus sits on the nav panel — inside a
                    // section it fades out and turns unfocusable, so focus can never jump to it.
                    searchVisible = focusedLayer == ShellLayer.SIDEBAR,
                    // The playlist chip becomes a quick-switcher only when there's more than one to pick.
                    playlistInteractive = playlists.size > 1,
                    onPlaylistClick = { showPlaylistPicker = true },
                    // Batch 7 — shared "Continue" chip: one-press resume of the most-recent item.
                    continueLabel = continueTarget?.let { "${it.actionLabel} · ${it.name}" },
                    continueIcon = when (continueTarget?.kind) {
                        tv.own.owntv.features.home.ContinueKind.LIVE -> OwnTVIcon.LIVE_TV
                        tv.own.owntv.features.home.ContinueKind.MOVIE -> OwnTVIcon.MOVIES
                        tv.own.owntv.features.home.ContinueKind.EPISODE -> OwnTVIcon.SERIES
                        null -> OwnTVIcon.PLAY
                    },
                    onContinueClick = {
                        continueTarget?.let { t ->
                            scope.launch {
                                when (t.kind) {
                                    tv.own.owntv.features.home.ContinueKind.LIVE ->
                                        if (liveVm.ensurePlayingByIdAsync(t.channelId)) openFullscreen(MainSection.LIVE_TV)
                                    tv.own.owntv.features.home.ContinueKind.MOVIE ->
                                        if (movieVm.playByIdAsync(t.movieId, t.positionMs) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES)
                                    tv.own.owntv.features.home.ContinueKind.EPISODE ->
                                        if (seriesVm.playFromHomeAsync(t.seriesId, t.episodeId, t.positionMs) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES)
                                }
                            }
                        }
                    },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 0.dp, end = 6.dp, bottom = 6.dp)) {
                    when {
                        selectedSection == MainSection.SETTINGS -> SettingsScreen(
                            themeMode = themeMode,
                            uiZoomPercent = uiZoomPercent,
                            onSetZoom = onSetZoom,
                            onOpenPlaylist = { /* Phase 6: open setup/playlist */ },
                            openEpgAdd = openEpgAdd,
                            onEpgAddConsumed = { openEpgAdd = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.HOME -> HomeScreen(
                            vm = homeVm,
                            // Skip the fullscreen player when the global external-player toggle is on
                            // (mounting it spins up mpv even though playback went to the external app).
                            onPlayMovie = { id, pos -> scope.launch { if (movieVm.playByIdAsync(id, pos) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) } },
                            onPlayEpisode = { seriesId, epId, pos -> scope.launch { if (seriesVm.playFromHomeAsync(seriesId, epId, pos) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES) } },
                            onPlayChannel = { id, zap -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id, zap)) openFullscreen(MainSection.LIVE_TV) } },
                            onOpenGuide = { onSelectSection(MainSection.EPG) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SEARCH -> SearchScreen(
                            onFullscreen = { openFullscreen() },
                            // Open the actual series (its episode list), then switch to the Series section —
                            // the screen shares this SeriesViewModel, so it shows the opened show.
                            onOpenSeries = { series -> seriesVm.openSeries(series); onSelectSection(MainSection.SERIES) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.LIVE_TV -> LiveScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            onOpenMultiView = enterMultiView, // fork: enter MultiView seeded with this channel
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.MOVIES -> MoviesScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SERIES -> SeriesScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.DOWNLOADS -> DownloadsScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.EPG -> EpgScreen(
                            onBack = { runCatching { sidebarFocus.requestFocus() } },
                            onFullscreen = { openFullscreen() },
                            onPlayChannel = { ch, list ->
                                restoreFocus = false
                                liveVm.watchFullscreen(ch, list)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                                if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
                            },
                            onAddEpg = { openEpgAdd = true; onSelectSection(MainSection.SETTINGS) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        else -> Row(modifier = Modifier.fillMaxSize()) {
                            CategoryRail(
                                categories = categories,
                                selectedIndex = selectedRail,
                                onSelect = { railSelection[selectedSection] = it },
                                onFocused = { focusedLayer = ShellLayer.RAIL },
                            )

                            ContentPane(
                                sectionTitle = selectedSection.label,
                                categoryName = categories.getOrNull(selectedRail)?.fullName ?: "All",
                                categoryAbbr = categories.getOrNull(selectedRail)?.abbr ?: "ALL",
                                countLabel = placeholderCount(selectedSection),
                                emptyIcon = selectedSection.emptyIcon,
                                emptyMessage = "Content for ${selectedSection.label} arrives in a later phase. Add an M3U or Xtream source to populate this list.",
                                onAddSource = { onSelectSection(MainSection.SETTINGS) },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                    .focusGroup(),
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(Dimens.GapLarge),
                            ) {
                                PreviewPane(hint = "Select a channel to preview it here.")
                            }
                        }
                    }
                }
            }
          }
        }
      }

      // Unobtrusive background-sync pill (bottom middle): visible while any catalog sync runs —
      // backgrounded first import, remainder worker, auto refresh — but never over fullscreen video.
      if (playerMode != PlayerMode.FULLSCREEN) {
          tv.own.owntv.features.shell.components.SyncStatusPill(modifier = Modifier.align(Alignment.BottomCenter))
      }

      // Player surface — hoisted so it persists across fullscreen <-> mini (same call site = the
      // SurfaceView isn't recreated when docking/expanding, so playback never blips).
      if (playerMode != PlayerMode.NONE) {
        val isFull = playerMode == PlayerMode.FULLSCREEN
        Box(
            modifier = if (isFull) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier.align(Alignment.BottomEnd).padding(24.dp).size(width = 340.dp, height = 191.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
            },
        ) {
            // "Promote Preview": a Live channel playing on ExoPlayer renders the ExoPlayer surface — in BOTH
            // full-screen AND the docked mini-player (same call site = the surface persists across dock/
            // expand, so playback never blips). Everything else (mpv) renders mpv's surface.
            if (liveOnExo) {
                tv.own.owntv.player.ExoPreviewSurface(engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize(), keepAwake = true)
            } else {
                MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize())
            }
            // Direct render mode: mpv can't draw subtitles on the decoder-owned surface — the app does.
            if (isFull && !liveOnExo) tv.own.owntv.player.SubtitleOverlay(player = player, modifier = Modifier.fillMaxSize())
            if (isFull) {
                // CH+/CH- zap through the channel list of whichever section opened the current stream
                // (Live TV or the Guide); never for VOD. When live plays on ExoPlayer (liveOnExo=true) the
                // mpv `player` is stopped so player.isLiveContent is false — the ExoPlayer engine is the one
                // playing live, so we must check liveOnExo too (otherwise zap breaks for the common case).
                val isLiveStream = liveOnExo || player.isLiveContent
                val zap: ((Int) -> Unit)? = when {
                    !isLiveStream -> null
                    // Zapping retunes the main out-of-band (ensurePlaying unmutes it); bump audioTick so the
                    // audio arbitration re-applies its plan while a PiP corner holds the sound.
                    zapSource == MainSection.EPG && epgCanZap -> ({ d -> epgVm.zap(d); audioTick++ })
                    zapSource == MainSection.LIVE_TV && liveCanZap -> ({ d -> liveVm.zap(d); audioTick++ })
                    else -> null
                }
                // Live rewind controls apply to a Live-TV channel (live OR its timeshift archive).
                val isLiveChannel = zapSource == MainSection.LIVE_TV
                PlayerHud(
                    player = if (liveOnExo) liveVm.previewEngine else mpvEngine, // HUD drives the active engine
                    onBack = exitPlayer,
                    // PiP button: for LIVE, true PiP — pick a SECOND stream for the corner while this one
                    // stays full-screen (hidden once a corner is up; the PiP row takes over). For VOD there
                    // is no live corner equivalent, so it keeps upstream's behavior: dock to the mini-player
                    // and browse while the movie keeps playing.
                    onPip = when {
                        cornerActive -> null
                        isLiveChannel -> ({ pipPicking = true })
                        else -> dockPlayer
                    },
                    // MultiView: drop into the grid seeded with this channel (live only); add more from inside.
                    onMultiView = if (isLiveChannel) ({ liveVm.previewChannel.value?.let { enterMultiView(it) } }) else null,
                    // Go inert while ANY overlay is open over the player — our channel pickers AND upstream's
                    // channel-list overlay (PR #41) — so the HUD can't steal focus from it.
                    inert = pipPicking || mainPicking || cornerBrowsing || showChannelList,
                    onChannelUp = zap?.let { z -> { z(-1) } },
                    onChannelDown = zap?.let { z -> { z(1) } },
                    onOpenChannelList = if (isLiveChannel && liveCanZap) { { showChannelList = true } } else null,
                    onRewindLive = if (isLiveChannel && canRewindLive) liveVm::rewindLive else null,
                    onForwardLive = if (isLiveChannel) liveVm::forwardLive else null,
                    onGoToLive = if (isLiveChannel) liveVm::goToLive else null,
                    onScrubLive = if (isLiveChannel && canRewindLive) liveVm::scrubLive else null,
                    timeshiftOffsetSec = if (isLiveChannel) timeshiftOffset else null,
                    // Show the ACTUAL running engine (mpv when pinned OR auto-fallen-back), not just the pin —
                    // otherwise an auto-fallback to mpv still read "EXO". true = on mpv (pill shows MPV, teal).
                    compatMode = if (isLiveChannel) !liveOnExo else null,
                    onToggleCompatMode = if (isLiveChannel) liveVm::toggleForceMpv else null,
                    // True PiP corner controls — present only while a second stream is in the corner.
                    // Swap is offered only when the main stream is a promoted live channel (both ExoPlayer),
                    // so the exchange is clean; audio/close are always available with a corner up.
                    onCornerSwap = if (cornerActive && liveOnExo) swapCorner else null,
                    onCornerAudio = if (cornerActive) toggleCornerAudio else null,
                    onCornerMove = if (cornerActive) ({ cornerPos = cornerPos.next() }) else null,
                    onCornerGrow = if (cornerActive) cornerGrow else null,
                    onCornerShrink = if (cornerActive) cornerShrink else null,
                    onCornerClose = if (cornerActive) closeCorner else null,
                    // Explicit per-window channel pickers, so it's never ambiguous which window retunes:
                    // "Change main" = the full-screen stream, "Change PiP" = the corner. Both keep the
                    // other window playing untouched.
                    onChangeMain = if (cornerActive && isLiveChannel) ({ mainPicking = true }) else null,
                    onChangeCorner = if (cornerActive) ({ cornerBrowsing = true }) else null,
                    cornerAudioOn = audioOnCorner,
                    // VOD engine toggle (movies/series only — live and catch-up channels keep their own
                    // engine handling above): flip the current item between mpv and ExoPlayer.
                    vodOnExo = if (!isLiveStream && !isLiveChannel) vodExoActive else null,
                    onToggleVodEngine = if (!isLiveStream && !isLiveChannel) player::toggleVodEngine else null,
                    // Guide card for the playing channel (nowNext follows previewChannel = what's playing).
                    liveEpgCard = if (isLiveChannel) {
                        {
                            val epg by liveVm.nowNext.collectAsStateWithLifecycle()
                            tv.own.owntv.features.shell.components.LiveEpgCard(epg = epg)
                        }
                    } else null,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showChannelList && isLiveChannel && zapChannels.size > 1) {
                    tv.own.owntv.features.shell.components.ChannelListOverlay(
                        channels = zapChannels,
                        currentId = previewChannel?.id,
                        onSelect = { liveVm.ensurePlaying(it); showChannelList = false },
                        onDismiss = { showChannelList = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                MiniPlayer(player = if (liveOnExo) liveVm.previewEngine else mpvEngine, onExpand = expandPlayer, onClose = exitPlayer, modifier = Modifier.fillMaxSize())
            }
        }
      }

      // MultiView — up to four live tiles, full-screen above everything. The single-stream players were
      // stopped on entry, so only the tile engines run; exiting releases them all.
      if (mvActive) {
        tv.own.owntv.features.multiview.MultiViewScreen(
            controller = mv,
            categories = browseCategories,
            searchChannels = { q -> liveVm.browseChannels(q) },
            onExit = exitMultiView,
            modifier = Modifier.fillMaxSize(),
        )
      }

      // True picture-in-picture corner — a second, independent stream drawn over both the browse UI and the
      // full-screen player (its SurfaceView is z-ordered above the main surface). The user can cycle it through
      // the four screen corners (cornerPos); it always (re)opens top-right. While full-screen the player HUD
      // owns the corner's controls, so the window itself is video-only then.
      if (cornerActive) {
        val cornerAlign = when (cornerPos) {
            CornerPos.TOP_END -> Alignment.TopEnd
            CornerPos.TOP_START -> Alignment.TopStart
            CornerPos.BOTTOM_START -> Alignment.BottomStart
            CornerPos.BOTTOM_END -> Alignment.BottomEnd
        }
        Box(
            modifier = Modifier.align(cornerAlign).padding(24.dp)
                .size(width = (CORNER_BASE_W * cornerScalePct / 100).dp, height = (CORNER_BASE_H * cornerScalePct / 100).dp),
        ) {
            tv.own.owntv.player.PipCornerWindow(
                engine = pip.engine,
                showControls = playerMode != PlayerMode.FULLSCREEN,
                audioOnCorner = audioOnCorner,
                onToggleAudio = toggleCornerAudio,
                onBrowse = { cornerBrowsing = true }, // retune the corner from the playlist, live
                onMove = { cornerPos = cornerPos.next() }, // cycle through the four corners
                onGrow = cornerGrow,     // +10% (capped at 200% of the base size)
                onShrink = cornerShrink, // −10% (never below the 320×180 base)
                onSwap = expandCorner, // window's expand button promotes the corner channel to full-screen
                onClose = closeCorner,
                modifier = Modifier.fillMaxSize(),
            )
        }
      }

      // Channel switcher for the PiP corner — pick from the playlist (search included) and the corner retunes
      // in place, without closing or stealing the main window's sound.
      if (cornerActive && cornerBrowsing) {
        tv.own.owntv.ui.components.ChannelSwitcher(
            title = "Change the PiP channel — the small window",
            categories = browseCategories,
            search = { q -> liveVm.browseChannels(q) },
            onPick = { ch -> pip.openCorner(ch); audioTick++; cornerBrowsing = false },
            onDismiss = { cornerBrowsing = false },
            modifier = Modifier.fillMaxSize(),
        )
      }

      // Channel switcher for the MAIN window while PiP is up — retunes the full-screen stream; the corner
      // keeps playing untouched (its engine is independent).
      if (mainPicking) {
        tv.own.owntv.ui.components.ChannelSwitcher(
            title = "Change the main channel — the full screen",
            categories = browseCategories,
            search = { q -> liveVm.browseChannels(q) },
            onPick = { ch ->
                zapSource = MainSection.LIVE_TV
                liveVm.ensurePlaying(ch)
                audioTick++ // ensurePlaying unmuted the main — re-apply the audio plan (corner may own sound)
                mainPicking = false
            },
            onDismiss = { mainPicking = false },
            modifier = Modifier.fillMaxSize(),
        )
      }

      // True PiP entry from the full-screen player: pick a SECOND stream to open in the corner. The current
      // full-screen stream keeps playing as the main; the corner opens muted. (Two streams = two provider
      // connections — a provider that allows only one will 509 the corner; that's a plan limit, not a bug.)
      if (pipPicking) {
        tv.own.owntv.ui.components.ChannelSwitcher(
            title = "Add a stream to the corner",
            categories = browseCategories,
            search = { q -> liveVm.browseChannels(q) },
            onPick = { ch -> pip.openCorner(ch); audioTick++; pipPicking = false },
            onDismiss = { pipPicking = false },
            modifier = Modifier.fillMaxSize(),
        )
      }

        if (showExit) {
            ExitDialog(onConfirm = onExitApp, onDismiss = { showExit = false })
        }
        if (showAvatarPicker) {
            AvatarPickerDialog(
                selectedId = avatarId,
                onSelect = onSetAvatar,
                onDismiss = { showAvatarPicker = false },
            )
        }
        if (showPlaylistPicker) {
            PlaylistPickerDialog(
                playlists = playlists,
                activeId = activePlaylistId,
                onSelect = onSelectPlaylist,
                onDismiss = { showPlaylistPicker = false },
            )
        }

        // Automatic update check (GitHub Releases) shortly after launch, once per session: a small
        // top-right status card shows "Checking… / up to date" (auto-hides) or stays with
        // Update now / Later when a release is newer. Hidden while in Settings (its manual
        // "Check for updates" dialog drives the same state machine) and during playback.
        val updateManager = koinInject<UpdateManager>()
        var showStartupToast by remember { mutableStateOf(false) }
        var showChangelog by remember { mutableStateOf(false) }
        val settingsRepo = koinInject<tv.own.owntv.features.settings.data.SettingsRepository>()
        val updateCheckOnStart by settingsRepo.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
        LaunchedEffect(updateCheckOnStart) {
            if (updateCheckOnStart && !showStartupToast) {
                kotlinx.coroutines.delay(5_000)
                showStartupToast = true
                updateManager.check()
            }
        }
        if (showChangelog) {
            // Full "What's New" changelog (same dialog the manual Settings check uses), shown when
            // the startup card's "What's New" is pressed. No re-check — the release is already loaded.
            UpdateDialog(onDismiss = { showChangelog = false; showStartupToast = false; updateManager.reset() }, checkOnOpen = false)
        } else if (showStartupToast && selectedSection != MainSection.SETTINGS && playerMode == PlayerMode.NONE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                UpdateStatusToast(
                    onDone = { showStartupToast = false; updateManager.reset() },
                    onViewChangelog = { showChangelog = true },
                )
            }
        }
    }
}

/** A thin bar shown above the browse UI when the device loses internet. */
@Composable
private fun OfflineBanner() {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "You're offline — playback and updates won't work until you reconnect.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onTertiaryContainer,
        )
    }
}

private val MainSection.emptyIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SETTINGS -> OwnTVIcon.SETTINGS
    }

private fun railCategoriesFor(section: MainSection): List<RailCategory> = when (section) {
    MainSection.SEARCH -> emptyList()
    MainSection.HOME -> emptyList()
    MainSection.EPG -> emptyList()
    MainSection.LIVE_TV -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Channels"),
        RailCategory("UK", "United Kingdom"),
        RailCategory("US", "United States"),
        RailCategory("DE", "Germany"),
        RailCategory("SPO", "Sports"),
    )
    MainSection.MOVIES -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Movies"),
        RailCategory("ACT", "Action"),
        RailCategory("DRA", "Drama"),
        RailCategory("COM", "Comedy"),
        RailCategory("HOR", "Horror"),
    )
    MainSection.SERIES -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Series"),
        RailCategory("DRA", "Drama"),
        RailCategory("ACT", "Action"),
        RailCategory("ANI", "Animation"),
        RailCategory("DOC", "Documentary"),
    )
    MainSection.DOWNLOADS -> listOf(
        RailCategory("ALL", "All Downloads"),
        RailCategory("MOV", "Movies"),
        RailCategory("SER", "Series"),
    )
    MainSection.SETTINGS -> emptyList()
}

private fun placeholderCount(section: MainSection): String = when (section) {
    MainSection.SEARCH -> ""
    MainSection.HOME -> ""
    MainSection.LIVE_TV -> "0 channels"
    MainSection.MOVIES -> "0 movies"
    MainSection.SERIES -> "0 series"
    MainSection.DOWNLOADS -> "0 downloads"
    MainSection.EPG -> ""
    MainSection.SETTINGS -> ""
}
