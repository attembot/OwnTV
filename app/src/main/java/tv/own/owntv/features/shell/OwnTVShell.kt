package tv.own.owntv.features.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.produceState
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
import tv.own.owntv.core.database.entity.ChannelEntity
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
import tv.own.owntv.features.shell.components.IncompleteRestoreDialog
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
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.ThemeMode

/** Which layer currently holds focus (drives Back navigation). */
private enum class ShellLayer { SIDEBAR, RAIL, CONTENT }

/** Player presentation: hidden, fullscreen, docked mini-player, or audio-only now-playing bar. */
private enum class PlayerMode { NONE, FULLSCREEN, MINI, AUDIO }

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
    // Docked mini-player size (% of screen width) + position, configurable in Settings and from the
    // mini-player's own controls. Read straight from settings so both entry points stay in sync.
    val settingsRepo = koinInject<tv.own.owntv.features.settings.data.SettingsRepository>()
    val miniSizePct by settingsRepo.miniPlayerSizePct.collectAsStateWithLifecycle(initialValue = tv.own.owntv.player.MiniPlayerSize.DEFAULT)
    val miniPosName by settingsRepo.miniPlayerPosition.collectAsStateWithLifecycle(initialValue = tv.own.owntv.player.MiniPlayerPosition.DEFAULT.name)
    val miniPos = tv.own.owntv.player.MiniPlayerPosition.fromName(miniPosName)
    val subtitleController = koinInject<tv.own.owntv.core.subtitles.SubtitleController>()
    val subtitleContext by subtitleController.current.collectAsStateWithLifecycle()
    var showSubtitleSearch by remember { mutableStateOf(false) }
    // Local subtitle-file picker (plan §7) — the same TV-safe in-app browser local M3U import uses.
    var showLocalSubPicker by remember { mutableStateOf(false) }
    val localSubToast = tv.own.owntv.ui.components.rememberInAppToast()
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
    // A catch-up archive programme is playing (Guide "Watch from start" or the Live TV catch-up picker)
    // rather than the live stream — the HUD swaps live-only controls for the VOD ones.
    val catchupActive by liveVm.catchupActive.collectAsStateWithLifecycle()
    val vodExoActive by player.exoActiveState.collectAsStateWithLifecycle()
    // Auto frame rate: only ever applied to the FULL-SCREEN surface (never the mini-player or the
    // in-pane Live preview) — see FrameRateController.
    val autoFrameRate by settingsRepo.autoFrameRate.collectAsStateWithLifecycle(initialValue = false)
    // Direct tune (type a channel number on the remote). Settings → Video Player → Live TV; default on.
    val directTuneEnabled by settingsRepo.directTune.collectAsStateWithLifecycle(initialValue = true)
    // "Prefer EPG logos": start following the setting once, here rather than in Application.onCreate —
    // the store queries nothing at all while the toggle is off, so cold start stays free of EPG reads.
    val epgDaoForLogos = koinInject<tv.own.owntv.core.database.dao.EpgDao>()
    val logoScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        tv.own.owntv.core.epg.EpgLogoStore.start(logoScope, settingsRepo, epgDaoForLogos)
    }
    // Live rewind / timeshift: whether the live channel supports catch-up, and how far behind live we are.
    val canRewindLive by liveVm.canRewindLive.collectAsStateWithLifecycle()
    val timeshiftOffset by liveVm.timeshiftOffsetSec.collectAsStateWithLifecycle()
    // Which section armed the current fullscreen stream — picks whose channel list CH+/CH- step through.
    var zapSource by remember { mutableStateOf<MainSection?>(null) }
    // In-player channel-list overlay (Left while controls hidden, live only).
    var showChannelList by remember { mutableStateOf(false) }
    // In-player watch-history list (Right while controls hidden, live only).
    var showHistoryList by remember { mutableStateOf(false) }
    val zapChannels by liveVm.zapChannels.collectAsStateWithLifecycle()
    val zapListTitle by liveVm.zapListTitle.collectAsStateWithLifecycle()
    val showCategoryBrowser by liveVm.showCategoryBrowser.collectAsStateWithLifecycle()
    val browserCategories by liveVm.browserCategories.collectAsStateWithLifecycle()
    val previewChannel by liveVm.previewChannel.collectAsStateWithLifecycle()
    // Favorite state for the player HUD's in-stream favorite toggle (live channel / movie / series).
    val liveFavoriteIds by liveVm.favoriteIds.collectAsStateWithLifecycle()
    val playingMovie by movieVm.playingMovie.collectAsStateWithLifecycle()
    val movieFavoriteIds by movieVm.favoriteIds.collectAsStateWithLifecycle()
    val playingSeries by seriesVm.playingSeries.collectAsStateWithLifecycle()
    val seriesFavoriteIds by seriesVm.favoriteIds.collectAsStateWithLifecycle()
    // Current programme per channel for the in-player channel list overlay (small subtitle under each row).
    // Only resolved while the overlay is actually open. Keyed on the channel set so a zap-list change re-resolves.
    val overlayNowPlaying by produceState<Map<Long, String>>(emptyMap(), showChannelList, zapChannels) {
        if (!showChannelList || zapChannels.size <= 1) { value = emptyMap(); return@produceState }
        value = runCatching { liveVm.nowPlayingFor(zapChannels) }.getOrDefault(emptyMap())
    }
    // Recently-watched channels for the right-hand history overlay — re-read each time it opens (and
    // after a zap, since tuning writes a new history row) so the newest channel is always on top.
    val historyChannels by produceState(emptyList<ChannelEntity>(), showHistoryList, previewChannel?.id) {
        if (!showHistoryList) { value = emptyList(); return@produceState }
        value = runCatching { liveVm.historyChannels() }.getOrDefault(emptyList())
    }
    val historyNowPlaying by produceState<Map<Long, String>>(emptyMap(), historyChannels) {
        if (historyChannels.isEmpty()) { value = emptyMap(); return@produceState }
        value = runCatching { liveVm.nowPlayingFor(historyChannels) }.getOrDefault(emptyMap())
    }
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
        // Only Movies/Series/Downloads carry an external-subtitle item context (set by their play
        // paths). Anything else (Live/EPG/Search-channel) clears it so ADD SUBTITLES never shows stale.
        if (source != MainSection.MOVIES && source != MainSection.SERIES && source != MainSection.DOWNLOADS) {
            subtitleController.clear()
        }
        // A new stream is opening — make sure any Audio Mode video-off state is cleared, else mpv keeps
        // `vid=no` and the new item would play with no picture.
        player.exitAudioOnly(); runCatching { liveVm.previewEngine.exitAudioOnly() }
        if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
    }
    // Restore video output on both engines (no-op unless we were in Audio Mode). mpv `vid=auto` /
    // ExoPlayer surface is re-attached by the surface remount right after.
    val resumeVideo = {
        player.exitAudioOnly()
        runCatching { liveVm.previewEngine.exitAudioOnly() }
    }
    // The mini-player's own expand button always maximizes.
    val expandPlayer = { resumeVideo(); restoreFocus = false; playerMode = PlayerMode.FULLSCREEN }
    val exitPlayer = {
        resumeVideo() // restore mpv `vid=auto` before stop so the next played item isn't left video-less
        playerMode = PlayerMode.NONE
        showChannelList = false
        showHistoryList = false
        liveVm.hideCategoryBrowser()
        liveVm.onFullscreenExited() // no longer full-screen on ExoPlayer → let the preview re-take the engine
        player.stop()
        subtitleController.clear() // leaving the player drops the OpenSubtitles item context
        if (selectedSection != MainSection.LIVE_TV) liveVm.clearLiveOnExo()
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    val dockPlayer = {
        resumeVideo()
        playerMode = PlayerMode.MINI
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    // Switch the current stream to audio-only and surface the now-playing bar in the top bar. Stop the
    // video decoder FIRST (plan §5 ordering rule), then drop the video surface by leaving FULLSCREEN/MINI.
    val toAudioMode = {
        (if (liveOnExo) liveVm.previewEngine else mpvEngine).enterAudioOnly()
        playerMode = PlayerMode.AUDIO
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

    // Liquid Glass: when a background image is active, the shell's own base paints must be transparent
    // so the full-bleed image (rendered in MainActivity behind this shell) shows through the gaps
    // between/around panels. Solid otherwise — the usual near-black base.
    val glass = LocalGlass.current
    val shellBase = if (glass.isGlassy(GlassSurface.PANELS) || glass.isGlassy(GlassSurface.SIDEBAR)) Color.Transparent else colors.background

    Box(modifier = modifier.fillMaxSize().background(shellBase)) {
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
                    // Liquid Glass: transparent here (shellBase) when a background image is active, so
                    // the image shows through the gaps between the content panels.
                    .background(shellBase),
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
                    // Audio Mode: the now-playing bar, left of the weather chip. Present only while
                    // PlayerMode.AUDIO; focusable only while the nav panel holds focus (same rule as Search).
                    audioBar = if (playerMode == PlayerMode.AUDIO) {
                        {
                            val isLiveStream = liveOnExo || player.isLiveContent
                            val zapFn: ((Int) -> Unit)? = when {
                                !isLiveStream -> null
                                zapSource == MainSection.EPG && epgCanZap -> epgVm::zap
                                zapSource == MainSection.LIVE_TV && liveCanZap -> liveVm::zap
                                else -> null
                            }
                            val audioEngine = if (liveOnExo) liveVm.previewEngine else mpvEngine
                            val vodNav by audioEngine.nav.collectAsStateWithLifecycle()
                            tv.own.owntv.player.AudioNowPlayingBar(
                                player = audioEngine,
                                isLive = isLiveStream,
                                canPrev = if (isLiveStream) zapFn != null else vodNav.hasPrev,
                                canNext = if (isLiveStream) zapFn != null else vodNav.hasNext,
                                onPrev = { if (isLiveStream) zapFn?.invoke(-1) else mpvEngine.previous() },
                                onNext = { if (isLiveStream) zapFn?.invoke(1) else mpvEngine.next() },
                                onExpand = expandPlayer,
                                onClose = exitPlayer,
                                // Always reachable while Audio Mode is active (from the Search/Continue
                                // pills on the left or the playlist chip on the right) — not gated on the
                                // nav panel like the other chips, because its own D-pad trap keeps focus
                                // inside once entered and Back is the only way out.
                                focusable = true,
                            )
                        }
                    } else null,
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
                            onPlayChannel = { ch, _ ->
                                restoreFocus = false
                                liveVm.watchFromGuide(ch)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                                // Live TV set to play externally → the channel went to another app;
                                // don't mount the fullscreen player over it.
                                if (playerMode != PlayerMode.MINI && !liveVm.externalPlayerOn.value) playerMode = PlayerMode.FULLSCREEN
                            },
                            onPlayCatchup = { ch, prog ->
                                restoreFocus = false
                                liveVm.playCatchupProgramme(ch, prog)
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
      // SurfaceView isn't recreated when docking/expanding, so playback never blips). NOT composed in
      // AUDIO mode: there's no video surface — audio plays and the top-bar now-playing bar drives it.
      if (playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI) {
        val isFull = playerMode == PlayerMode.FULLSCREEN
        Box(
            modifier = if (isFull) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                // Dynamic docked size/position: a screen-width fraction at the chosen corner/edge, so it
                // scales with the panel + UI zoom (unlike the old fixed 340×191 dp box).
                Modifier.align(miniPos.alignment).padding(24.dp)
                    .fillMaxWidth(tv.own.owntv.player.MiniPlayerSize.fraction(miniSizePct)).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
            },
        ) {
            // "Promote Preview": a Live channel playing on ExoPlayer renders the ExoPlayer surface — in BOTH
            // full-screen AND the docked mini-player (same call site = the surface persists across dock/
            // expand, so playback never blips). Everything else (mpv) renders mpv's surface.
            if (liveOnExo) {
                tv.own.owntv.player.ExoPreviewSurface(
                    engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize(),
                    keepAwake = true, autoFrameRate = isFull && autoFrameRate,
                )
            } else {
                MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize(), autoFrameRate = isFull && autoFrameRate)
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
                // ...but NOT to a catch-up archive programme. That's VOD-style playback of a past
                // programme, so it gets the VOD engine toggle (reloads the same archive URL at the same
                // position on the other engine) rather than Live TV's compatibility toggle, which would
                // re-tune the live stream and jump the user to the current programme.
                val isTunedLive = isLiveChannel && !catchupActive
                // Favorite toggle for whatever is playing: the live channel, the movie, or the series
                // (episodes favorite their parent series). Picked by the section that armed the stream.
                val favToggle: (() -> Unit)? = when {
                    isLiveChannel -> previewChannel?.let { ch -> { liveVm.toggleFavorite(ch) } }
                    zapSource == MainSection.MOVIES -> playingMovie?.let { m -> { movieVm.toggleFavorite(m) } }
                    zapSource == MainSection.SERIES -> playingSeries?.let { s -> { seriesVm.toggleFavorite(s) } }
                    else -> null
                }
                val favActive = when {
                    isLiveChannel -> previewChannel?.let { liveFavoriteIds.contains(it.id) } ?: false
                    zapSource == MainSection.MOVIES -> playingMovie?.let { movieFavoriteIds.contains(it.id) } ?: false
                    zapSource == MainSection.SERIES -> playingSeries?.let { seriesFavoriteIds.contains(it.id) } ?: false
                    else -> false
                }
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
                    onAudioMode = toAudioMode,
                    // Go inert while ANY overlay is open over the player — our channel pickers AND upstream's
                    // overlays (channel/history lists, category browser, subtitle pickers) — so the HUD's
                    // hide/error focus grabs can't yank D-pad focus off the overlay.
                    inert = pipPicking || mainPicking || cornerBrowsing ||
                        showChannelList || showHistoryList || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker,
                    onChannelUp = zap?.let { z -> { z(-1) } },
                    onChannelDown = zap?.let { z -> { z(1) } },
                    onOpenChannelList = if (isTunedLive && liveCanZap) { { showChannelList = true } } else null,
                    onOpenHistoryList = if (isTunedLive) { { showHistoryList = true } } else null,
                    onRewindLive = if (isTunedLive && canRewindLive) liveVm::rewindLive else null,
                    onForwardLive = if (isTunedLive) liveVm::forwardLive else null,
                    onGoToLive = if (isTunedLive) liveVm::goToLive else null,
                    onScrubLive = if (isTunedLive && canRewindLive) liveVm::scrubLive else null,
                    timeshiftOffsetSec = if (isTunedLive) timeshiftOffset else null,
                    // Fork: bump audioTick after the tune resolves — a number-tune retunes the main engine
                    // out-of-band, and the corner may own the sound (same rule as zap / Change main / swap).
                    onTuneToNumber = if (directTuneEnabled && isTunedLive && isLiveStream && timeshiftOffset == null && previewChannel != null) {
                        { n -> liveVm.tuneByNumber(n).also { audioTick++ } }
                    } else null,
                    directTuneContextKey = previewChannel?.id ?: 0L,
                    // Show the ACTUAL running engine (mpv when pinned OR auto-fallen-back), not just the pin —
                    // otherwise an auto-fallback to mpv still read "EXO". true = on mpv (pill shows MPV, teal).
                    compatMode = if (isTunedLive) !liveOnExo else null,
                    onToggleCompatMode = if (isTunedLive) liveVm::toggleForceMpv else null,
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
                    vodOnExo = if (!isLiveStream && !isTunedLive) vodExoActive else null,
                    onToggleVodEngine = if (!isLiveStream && !isTunedLive) player::toggleVodEngine else null,
                    // ADD SUBTITLES entry: movies/episodes only, and only when the play path set an
                    // item context (subtitle plan §4). Opens the OpenSubtitles search overlay below.
                    onSearchSubtitles = if (!isLiveStream && !isLiveChannel && subtitleContext != null) {
                        { showSubtitleSearch = true }
                    } else null,
                    // Local subtitle file (plan §7): same movie/episode gating, no account needed.
                    onSelectLocalSubtitle = if (!isLiveStream && !isLiveChannel && subtitleContext != null) {
                        { showLocalSubPicker = true }
                    } else null,
                    // In-stream favorite toggle for the current channel/movie/series.
                    favorite = favActive,
                    onToggleFavorite = favToggle,
                    // Guide card for the playing channel (nowNext follows previewChannel = what's playing).
                    liveEpgCard = if (isLiveChannel) {
                        {
                            val epg by liveVm.nowNext.collectAsStateWithLifecycle()
                            tv.own.owntv.features.shell.components.LiveEpgCard(epg = epg)
                        }
                    } else null,
                    modifier = Modifier.fillMaxSize(),
                )
                // OpenSubtitles search overlay (movies/episodes) — drawn above the HUD; the HUD is inert
                // while it's open so the D-pad stays on the overlay.
                if (showSubtitleSearch) {
                    tv.own.owntv.features.subtitles.SubtitleSearchScreen(
                        onDismiss = { showSubtitleSearch = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Local subtitle-file picker (plan §7.3) — hosted in a Dialog window, so D-pad focus
                // can't fall through to the HUD behind; picking imports a managed UTF-8 copy and
                // attaches it live to whichever engine is playing.
                if (showLocalSubPicker) {
                    tv.own.owntv.ui.components.StorageBrowser(
                        title = "Select local subtitle file",
                        mode = tv.own.owntv.ui.components.BrowseMode.FILE,
                        fileExtensions = setOf("srt", "ass", "ssa", "vtt", "webvtt"),
                        onPick = { file ->
                            showLocalSubPicker = false
                            scope.launch {
                                runCatching { subtitleController.applyLocal(file) }
                                    .onFailure { e ->
                                        localSubToast.show(e.message ?: "Couldn't load the subtitle file.")
                                    }
                            }
                        },
                        onDismiss = { showLocalSubPicker = false },
                    )
                }
                tv.own.owntv.ui.components.InAppToast(localSubToast)
                // Left — the playing channel's own provider category.
                if (showChannelList && isLiveChannel) {
                    if (showCategoryBrowser) {
                        // Second Left — every Live TV category.
                        tv.own.owntv.features.shell.components.CategoryBrowserOverlay(
                            categories = browserCategories,
                            currentCategoryId = previewChannel?.categoryId,
                            onSelect = { catId -> liveVm.loadChannelsForCategory(catId) },
                            onDismiss = { liveVm.hideCategoryBrowser() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (zapChannels.isNotEmpty()) {
                        // First Left — the channels of the current category. A browsed-to category may
                        // hold a single channel, so this renders for any non-empty list.
                        tv.own.owntv.features.shell.components.ChannelListOverlay(
                            channels = zapChannels,
                            currentId = previewChannel?.id,
                            nowPlaying = overlayNowPlaying,
                            title = zapListTitle,
                            showNumbers = directTuneEnabled,
                            onSelect = { liveVm.ensurePlaying(it); audioTick++; showChannelList = false },
                            onDismiss = { showChannelList = false },
                            onOpenCategories = { liveVm.showCategories() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Right — recently watched, to hop straight back to the previous channel.
                if (showHistoryList && isLiveChannel && historyChannels.isNotEmpty()) {
                    tv.own.owntv.features.shell.components.ChannelListOverlay(
                        channels = historyChannels,
                        currentId = previewChannel?.id,
                        nowPlaying = historyNowPlaying,
                        title = "History",
                        showNumbers = directTuneEnabled,
                        alignEnd = true,
                        onSelect = { liveVm.ensurePlaying(it); audioTick++; showHistoryList = false },
                        onDismiss = { showHistoryList = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                MiniPlayer(
                    player = if (liveOnExo) liveVm.previewEngine else mpvEngine,
                    onExpand = expandPlayer,
                    onClose = exitPlayer,
                    onCycleSize = { scope.launch { settingsRepo.setMiniPlayerSizePct(tv.own.owntv.player.MiniPlayerSize.next(miniSizePct)) } },
                    onCyclePosition = { scope.launch { settingsRepo.setMiniPlayerPosition(miniPos.next().name) } },
                    onAudioMode = toAudioMode,
                    modifier = Modifier.fillMaxSize(),
                )
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
        // Interrupted restore (B2): the marker outlives the process, so if it's still set at launch
        // the last restore didn't complete. Acknowledging clears it.
        val restoreSettings = koinInject<tv.own.owntv.features.settings.data.SettingsRepository>()
        val incompleteRestore by restoreSettings.restoreInProgress.collectAsStateWithLifecycle(initialValue = null)
        var restoreNoticeDismissed by remember { mutableStateOf(false) }
        incompleteRestore?.takeIf { !restoreNoticeDismissed }?.let { description ->
            IncompleteRestoreDialog(
                description = description,
                onDismiss = {
                    restoreNoticeDismissed = true
                    scope.launch { restoreSettings.clearRestoreMarker() }
                },
            )
        }

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
        RailCategory("Favorites", OwnTVIcon.FAVORITE),
        RailCategory("History", OwnTVIcon.HISTORY),
        RailCategory("All Channels"),
        RailCategory("United Kingdom"),
        RailCategory("United States"),
        RailCategory("Germany"),
        RailCategory("Sports"),
    )
    MainSection.MOVIES -> listOf(
        RailCategory("Favorites", OwnTVIcon.FAVORITE),
        RailCategory("History", OwnTVIcon.HISTORY),
        RailCategory("All Movies"),
        RailCategory("Action"),
        RailCategory("Drama"),
        RailCategory("Comedy"),
        RailCategory("Horror"),
    )
    MainSection.SERIES -> listOf(
        RailCategory("Favorites", OwnTVIcon.FAVORITE),
        RailCategory("History", OwnTVIcon.HISTORY),
        RailCategory("All Series"),
        RailCategory("Drama"),
        RailCategory("Action"),
        RailCategory("Animation"),
        RailCategory("Documentary"),
    )
    MainSection.DOWNLOADS -> listOf(
        RailCategory("All Downloads"),
        RailCategory("Movies"),
        RailCategory("Series"),
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
