package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.customize.CustomizeScreen
import tv.own.owntv.features.settings.HomeSettingsScreen
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.settings.BackupScreen
import tv.own.owntv.features.settings.ManageProfilesScreen
import tv.own.owntv.features.settings.ManageSourcesScreen
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.VideoPlayerSettingsScreen
import tv.own.owntv.features.shell.MainSection
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.BackgroundImageChooserDialog
import tv.own.owntv.ui.components.ingestBackgroundImage
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.ALL_GLASS_SURFACES
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassConfig
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom
import kotlin.math.roundToInt
import java.io.File

private enum class TileTone { PRIMARY, SECONDARY, TERTIARY }

private enum class SettingsTab { ROOT, SOURCES, EPG, PROFILES, BACKUP, VIDEO, MINI_PLAYER, CUSTOMIZE, HOME, NETWORK, DNS, METADATA, WEATHER, NAV_MENU, CH_NAV }

/**
 * The MD3 Settings screen (shown when [MainSection.SETTINGS] is active): grouped sections, each row
 * a tonal icon tile + title/description + a trailing chip or chevron. Theme / UI Zoom are live;
 * unfinished features show a "Soon" chip.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(SettingsTab.ROOT) }
    // Deep-link from the Guide's "Add EPG" button: jump straight to EPG Sources in add mode.
    var consumeEpgAdd by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCatchupTime by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(false) }
    var showErrorLog by remember { mutableStateOf(false) }
    var showBgImageChooser by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBgRemote by remember { mutableStateOf(false) }
    var showGlassEffect by remember { mutableStateOf(false) }
    var showBrowsing by remember { mutableStateOf(false) }
    val browsingRowFocus = remember { FocusRequester() }
    // U2 — background-image ingest copies a multi-megabyte file; it runs here, off the main thread.
    val ingestScope = rememberCoroutineScope()

    // Batch 4 · Settings search + quick toggles. Empty query = normal grouped list; a non-blank
    // query swaps the list for flat results that carry their group context ("Playback › HDR").
    var searchQuery by remember { mutableStateOf("") }
    val searchFieldFocus = remember { FocusRequester() }
    // While searching, Back clears the query (and returns focus to the field) instead of leaving Settings.
    BackHandler(enabled = tab == SettingsTab.ROOT && searchQuery.isNotBlank()) {
        searchQuery = ""
        runCatching { searchFieldFocus.requestFocus() }
    }

    // Dialog-close focus return: closing a dialog/picker refocuses the row that opened it (focus
    // would otherwise fall spatially back to the sidebar).
    val folderRowFocus = remember { FocusRequester() }
    val themeRowFocus = remember { FocusRequester() }
    val accentRowFocus = remember { FocusRequester() }
    val zoomRowFocus = remember { FocusRequester() }
    val updateRowFocus = remember { FocusRequester() }
    val aboutRowFocus = remember { FocusRequester() }
    val catchupRowFocus = remember { FocusRequester() }
    val clearHistoryRowFocus = remember { FocusRequester() }
    val animationsRowFocus = remember { FocusRequester() }
    val startupRowFocus = remember { FocusRequester() }
    val errorLogRowFocus = remember { FocusRequester() }
    val glassEffectRowFocus = remember { FocusRequester() }
    // Hoisted scroll state for the root settings list. We snapshot its position the instant a row is
    // clicked (in onClick, before any recomposition) and restore it on dialog close, so the list
    // doesn't visibly jump/scroll when the dialog opens or when we refocus the opener row afterward.
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    val anyDialogOpen = showZoom || showTheme || showAccent || showFolderPicker || showUpdate || showAbout || showCatchupTime || showClearHistory || showAnimations || showStartup || showErrorLog || showBgImageChooser || showBgPicker || showGlassEffect || showBrowsing
    // When a dialog closes, restore focus to the row that opened it. NOTE: this restore crosses
    // INTO the root focus group from outside (the dialog), but onEnter does NOT fire for programmatic
    // requestsFocus (only for directional entry) — so dialogReturn must be cleared HERE, not in onEnter.
    // If it's left set, the next directional entry (e.g. sidebar→here) would re-route to a stale row.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showZoom, showTheme, showAccent, showFolderPicker, showUpdate, showAbout, showCatchupTime, showClearHistory, showAnimations, showStartup, showErrorLog, showBgImageChooser, showBgPicker, showGlassEffect, showBrowsing) {
        if (!anyDialogOpen) {
            // When a scrim dialog is torn down, Compose's focus re-search through the newly-exposed
            // scrollable Column resets its scroll to 0 and then bringIntoView-animates to wherever
            // focus lands. We counter that by waiting one frame (so the scrim is fully gone), snapping
            // the scroll back to where the user left it (scrollTo is instant — no animation), THEN
            // requesting focus on the opener row, which is now already in view — so no animation.
            withFrameNanos { }
            runCatching { scrollState.scrollTo(savedScroll) }
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
            dialogReturn = null
        }
    }
    val settingsVm: SettingsViewModel = koinViewModel()
    val downloadRoot by settingsVm.downloadRoot.collectAsStateWithLifecycle()
    val livePreview by settingsVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewAudio by settingsVm.livePreviewAudio.collectAsStateWithLifecycle()
    val hdr by settingsVm.hdrEnabled.collectAsStateWithLifecycle()
    val autoFrameRate by settingsVm.autoFrameRate.collectAsStateWithLifecycle()
    val surroundSound by settingsVm.surroundSound.collectAsStateWithLifecycle()
    val autoPlayNext by settingsVm.autoPlayNext.collectAsStateWithLifecycle()
    val updateCheckOnStart by settingsVm.updateCheckOnStart.collectAsStateWithLifecycle()
    val channelNumbers by settingsVm.directTune.collectAsStateWithLifecycle()
    val catchupTz by settingsVm.catchupTimezone.collectAsStateWithLifecycle()
    val catchupOffset by settingsVm.catchupOffsetMinutes.collectAsStateWithLifecycle()
    val catchupChannels by settingsVm.catchupChannelCount.collectAsStateWithLifecycle()
    val catchupPlayer by settingsVm.catchupPlayer.collectAsStateWithLifecycle()
    val accent by settingsVm.accent.collectAsStateWithLifecycle()
    val customAccent by settingsVm.customAccent.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val animationLevel by settingsVm.animationLevel.collectAsStateWithLifecycle()
    val weatherEnabled by settingsVm.weatherEnabled.collectAsStateWithLifecycle()
    val startupMode by settingsVm.startupMode.collectAsStateWithLifecycle()
    val navMenuMode by settingsVm.navMenuMode.collectAsStateWithLifecycle()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val rememberLastLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()
    val rememberLastMovies by settingsVm.rememberLastMovies.collectAsStateWithLifecycle()
    val rememberLastSeries by settingsVm.rememberLastSeries.collectAsStateWithLifecycle()
    val rememberCatLive by settingsVm.rememberCategoryLive.collectAsStateWithLifecycle()
    val rememberCatMovies by settingsVm.rememberCategoryMovies.collectAsStateWithLifecycle()
    val rememberCatSeries by settingsVm.rememberCategorySeries.collectAsStateWithLifecycle()

    // Restore focus to the row a sub-screen was opened from when the user navigates back.
    var lastTab by remember { mutableStateOf<SettingsTab?>(null) }
    val rowFocus = remember { mapOf(
        SettingsTab.SOURCES to FocusRequester(),
        SettingsTab.EPG to FocusRequester(),
        SettingsTab.PROFILES to FocusRequester(),
        SettingsTab.BACKUP to FocusRequester(),
        SettingsTab.VIDEO to FocusRequester(),
        SettingsTab.MINI_PLAYER to FocusRequester(),
        SettingsTab.CUSTOMIZE to FocusRequester(),
        SettingsTab.HOME to FocusRequester(),
        SettingsTab.NETWORK to FocusRequester(),
        SettingsTab.DNS to FocusRequester(),
        SettingsTab.METADATA to FocusRequester(),
        SettingsTab.WEATHER to FocusRequester(),
        SettingsTab.NAV_MENU to FocusRequester(),
        SettingsTab.CH_NAV to FocusRequester(),
    ) }
    val open: (SettingsTab) -> Unit = { lastTab = it; tab = it }
    // Restore focus to the row a sub-screen was opened from when the user navigates back. Fresh entry
    // intentionally does NOT grab focus here — every other main-menu section lets the shell/sidebar
    // own initial focus, and Settings stays consistent with them.
    LaunchedEffect(tab) {
        if (tab == SettingsTab.ROOT && lastTab != null) {
            kotlinx.coroutines.delay(60)
            runCatching { rowFocus[lastTab]?.requestFocus() }
        }
    }
    LaunchedEffect(openEpgAdd) {
        if (openEpgAdd) { consumeEpgAdd = true; open(SettingsTab.EPG); onEpgAddConsumed() }
    }

    when (tab) {
        SettingsTab.SOURCES -> { ManageSourcesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.EPG -> { tv.own.owntv.features.settings.EpgSourcesScreen(onBack = { tab = SettingsTab.ROOT; consumeEpgAdd = false }, modifier = modifier, startOnAdd = consumeEpgAdd); return }
        SettingsTab.PROFILES -> { ManageProfilesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.BACKUP -> { BackupScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.VIDEO -> { VideoPlayerSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.MINI_PLAYER -> { tv.own.owntv.features.settings.MiniPlayerSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.CUSTOMIZE -> { CustomizeScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.HOME -> { HomeSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.NETWORK -> { tv.own.owntv.features.settings.NetworkSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.DNS -> { tv.own.owntv.features.settings.DnsSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.METADATA -> { tv.own.owntv.features.settings.MetadataSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.WEATHER -> { tv.own.owntv.features.settings.WeatherSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.NAV_MENU -> { tv.own.owntv.features.settings.NavMenuSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.CH_NAV -> { tv.own.owntv.features.settings.ChNavSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.ROOT -> Unit
    }

    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel(fillColor = ContentPanelFill)
            // onEnter fires ONLY for directional entry into this group (sidebar D-pad, etc.), NOT for
            // programmatic restores — those are handled by the dialog-return LaunchedEffect above (and
            // dialogReturn is cleared there). So this only picks the entry fallback: the last-opened
            // sub-menu's row if any, else — during search — the always-bound search field (every rowFocus
            // is only attached while the search list is hidden, so PROFILES is unbound mid-search), else
            // the Profiles row.
            .focusProperties {
                onEnter = {
                    val target = rowFocus[lastTab]
                        ?: if (searchQuery.isBlank()) rowFocus.getValue(SettingsTab.PROFILES) else searchFieldFocus
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        // --- Batch 4: quick toggles (most-used settings, one-press) ---
        // NOTE: the four here are the current "most-used" set; making this list user-configurable
        // is deferred (see DESIGN_PLAN_v4.0.3 Batch 4 · B).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickToggleChip("Live preview", livePreview, OwnTVIcon.LIVE_TV) { settingsVm.setLivePreviewEnabled(!livePreview) }
            QuickToggleChip("Preview sound", previewAudio, OwnTVIcon.AUDIO) { settingsVm.setLivePreviewAudio(!previewAudio) }
            QuickToggleChip("Channel numbers", channelNumbers, OwnTVIcon.LIVE_TV) { settingsVm.setDirectTune(!channelNumbers) }
            QuickToggleChip("HDR", hdr, OwnTVIcon.VIDEO) { settingsVm.setHdrEnabled(!hdr) }
            QuickToggleChip("Auto-play", autoPlayNext, OwnTVIcon.SKIP_NEXT) { settingsVm.setAutoPlayNext(!autoPlayNext) }
            QuickToggleChip("Check for update", updateCheckOnStart, OwnTVIcon.DOWNLOADS) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) }
        }
        Spacer(Modifier.height(8.dp))

        // --- Batch 4: settings search ---
        OwnTVTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Search settings",
            placeholder = "Search settings…",
            focusRequester = searchFieldFocus,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (searchQuery.isBlank()) {
        GroupLabel("Profile")
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PERSON,
            title = "Profiles", desc = "Manage viewers, kids mode & PIN locks",
            onClick = { open(SettingsTab.PROFILES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.PROFILES)),
        )
        SectionDivider()
        GroupLabel("Content")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = "Playlists", desc = "Add, re-sync or remove M3U / Xtream playlists",
            onClick = { open(SettingsTab.SOURCES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.SOURCES)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.EPG,
            title = "EPG Sources", desc = "Add XMLTV guide feeds for the TV Guide",
            onClick = { open(SettingsTab.EPG) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.EPG)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.SORT,
            title = "Customize Categories & Items", desc = "Hide & unhide items, rename & reorder categories",
            onClick = { open(SettingsTab.CUSTOMIZE) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.CUSTOMIZE)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.MENU,
            title = "Sidebar Menu Customization", desc = "Auto-adapt side icons to your playlist, or hide specific ones",
            chip = navMenuMode.label,
            chipTone = if (navMenuMode == tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.NAV_MENU) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.NAV_MENU)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = "CH+- Key Paging", desc = "Skip up/down in the category & item lists with the CH+ / CH− keys",
            chip = if (chNavEnabled) "On" else "Off",
            chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.CH_NAV) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.CH_NAV)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = "Browsing & lists", desc = "Remember the last category and the last item in Live TV, Movies & Series",
            onClick = { savedScroll = scrollState.value; dialogReturn = browsingRowFocus; showBrowsing = true }, showChevron = true,
            modifier = Modifier.focusRequester(browsingRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HOME,
            title = "Home screen", desc = "Choose, reorder & filter the rows on Home",
            onClick = { open(SettingsTab.HOME) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.HOME)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = "Metadata (TMDB)", desc = "Posters, plots, cast & ratings for Movies and Series",
            onClick = { open(SettingsTab.METADATA) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.METADATA)),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Download folder",
            chip = downloadRoot.ifBlank { "App storage" }.let { java.io.File(it).name.ifBlank { it } },
            chipTone = TileTone.TERTIARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = folderRowFocus; showFolderPicker = true }, showChevron = true,
            modifier = Modifier.focusRequester(folderRowFocus),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Backup & Restore", desc = "Export or restore profiles & sources",
            onClick = { open(SettingsTab.BACKUP) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.BACKUP)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Clear watch history", desc = "Remove this profile's recently-watched & continue rows",
            onClick = { savedScroll = scrollState.value; dialogReturn = clearHistoryRowFocus; showClearHistory = true }, showChevron = true,
            modifier = Modifier.focusRequester(clearHistoryRowFocus),
        )
        SectionDivider()
        GroupLabel("Appearance")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.THEME,
            title = "Theme", desc = "Light, dark or follow the system",
            chip = themeLabel(themeMode), chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = themeRowFocus; showTheme = true }, showChevron = true,
            modifier = Modifier.focusRequester(themeRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PALETTE,
            title = "Accent color", desc = "Tint the interface — presets, palette or hex code",
            chip = if (customAccent.isNotBlank()) customAccent.uppercase() else accent.label,
            chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = accentRowFocus; showAccent = true }, showChevron = true,
            modifier = Modifier.focusRequester(accentRowFocus),
        )
        // One consolidated "Glass Effect" entry: opens a dialog holding the Liquid-glass on/off toggle,
        // the background-image chooser, and the transparency stepper (see GlassEffectDialog).
        val glassOn = glassConfig.enabled
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.THEME,
            title = "Glass Effect", desc = "Translucent frosted panels, background image and transparency",
            chip = if (glassOn) "On" else "Off",
            chipTone = if (glassOn) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = glassEffectRowFocus; showGlassEffect = true }, showChevron = true,
            modifier = Modifier.focusRequester(glassEffectRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.ZOOM,
            title = "UI Zoom", desc = "Scale the whole interface",
            chip = UiZoom.label(uiZoomPercent), chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = zoomRowFocus; showZoom = true }, showChevron = true,
            modifier = Modifier.focusRequester(zoomRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.THEME,
            title = "Animations", desc = "Turn interface motion on or off — Off feels snappier on lower-end TV boxes",
            chip = animationLevel.label, chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = animationsRowFocus; showAnimations = true }, showChevron = true,
            modifier = Modifier.focusRequester(animationsRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = "Weather",
            desc = "Top-bar weather chip: show or hide, custom location, and Celsius / Fahrenheit.",
            chip = if (weatherEnabled) "On" else "Off",
            chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.WEATHER) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.WEATHER)),
        )

        SectionDivider()
        GroupLabel("Playback")
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.LIVE_TV,
            title = "Live preview", desc = "Auto-play a channel when you focus it",
            chip = if (livePreview) "On" else "Off",
            chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setLivePreviewEnabled(!livePreview) },
        )
        if (livePreview) {
            SettingsRow(
                tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
                title = "Preview audio", desc = "Play sound in the Live preview",
                chip = if (previewAudio) "On" else "Off",
                chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY,
                onClick = { settingsVm.setLivePreviewAudio(!previewAudio) },
            )
        }
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.PIP,
            title = "Mini-player", desc = "Size and position of the docked mini-player",
            onClick = { open(SettingsTab.MINI_PLAYER) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.MINI_PLAYER)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = "HDR", desc = "Use HDR output when the video & TV support it",
            chip = if (hdr) "On" else "Off",
            chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setHdrEnabled(!hdr) },
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = "Auto frame rate",
            desc = "Match the TV's refresh rate to the video (24/25/50/60 fps) in full screen, for Live TV and VOD, and restore it on exit. Turn off if your TV or receiver re-handshakes HDMI noisily on every channel change.",
            chip = if (autoFrameRate) "On" else "Off",
            chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAutoFrameRate(!autoFrameRate) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
            title = "Surround sound",
            desc = "Decode Dolby/DTS to surround (5.1/7.1) for a real 5.1/7.1 receiver. Leave OFF for TV speakers or a stereo soundbar — multichannel can lag audio behind video on some TVs/soundbars. If it drifts, nudge the player's Audio menu → A/V sync.",
            chip = if (surroundSound) "On" else "Off",
            chipTone = if (surroundSound) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setSurroundSound(!surroundSound) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SKIP_NEXT,
            title = "Auto-play next episode",
            desc = "When an episode ends, automatically start the next one — and roll into the next season.",
            chip = if (autoPlayNext) "On" else "Off",
            chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAutoPlayNext(!autoPlayNext) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = "Catch-up",
            desc = if (catchupChannels > 0) "$catchupChannels channels support catch-up · timezone and player for archive playback"
                else "No catch-up channels available on this playlist",
            chip = when (catchupTz) {
                SettingsRepository.CatchupTimezone.DEVICE -> "Device"
                SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
            },
            chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = catchupRowFocus; showCatchupTime = true }, showChevron = true,
            modifier = Modifier.focusRequester(catchupRowFocus),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.VIDEO,
            title = "Video Player Settings", desc = "Decoder, subtitles, sync",
            onClick = { open(SettingsTab.VIDEO) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.VIDEO)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Playback error log", desc = "The last playback failures — details to read or report",
            onClick = { savedScroll = scrollState.value; dialogReturn = errorLogRowFocus; showErrorLog = true }, showChevron = true,
            modifier = Modifier.focusRequester(errorLogRowFocus),
        )

        SectionDivider()
        GroupLabel("Network")
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SHARE,
            title = "Proxy", desc = "Route all traffic & playback through an HTTP proxy",
            onClick = { open(SettingsTab.NETWORK) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.NETWORK)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SEARCH,
            title = "DNS", desc = "Use a custom DNS server for domain lookups",
            onClick = { open(SettingsTab.DNS) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.DNS)),
        )

        SectionDivider()
        GroupLabel("App")
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HOME,
            title = "App startup", desc = "What OwnTV opens when it starts",
            chip = startupMode.label, chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = startupRowFocus; showStartup = true }, showChevron = true,
            modifier = Modifier.focusRequester(startupRowFocus),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Check for updates", desc = "Get the latest version from GitHub Releases",
            chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}",
            onClick = { savedScroll = scrollState.value; dialogReturn = updateRowFocus; showUpdate = true }, showChevron = true,
            modifier = Modifier.focusRequester(updateRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Check updates on startup", desc = "Look for a new version when the app opens",
            chip = if (updateCheckOnStart) "On" else "Off",
            chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.MENU,
            title = "About", desc = "Version, license & project info",
            onClick = { savedScroll = scrollState.value; dialogReturn = aboutRowFocus; showAbout = true }, showChevron = true,
            modifier = Modifier.focusRequester(aboutRowFocus),
        )
        } else {
            // Batch 4 · search results — flat, group-context-prefixed rows ("Playback › HDR").
            // Dialog-opening entries return focus to the search field on close (their normal row
            // isn't composed while searching). Toggle entries keep the results visible so the chip
            // updates live.
            val entries = listOf(
                SettingsSearchEntry("Profile", "Profiles", "viewers kids mode pin lock account", OwnTVIcon.PERSON, TileTone.SECONDARY) { open(SettingsTab.PROFILES) },
                SettingsSearchEntry("Content", "Playlists", "m3u xtream source sync add remove", OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { open(SettingsTab.SOURCES) },
                SettingsSearchEntry("Content", "EPG Sources", "xmltv guide feed program", OwnTVIcon.EPG, TileTone.PRIMARY) { open(SettingsTab.EPG) },
                SettingsSearchEntry("Content", "Guide channel logos", "channel logo icon xmltv guide picon playlist", OwnTVIcon.EPG, TileTone.SECONDARY) { open(SettingsTab.EPG) },
                SettingsSearchEntry("Content", "Customize Categories & Items", "hide unhide rename reorder categories", OwnTVIcon.SORT, TileTone.PRIMARY) { open(SettingsTab.CUSTOMIZE) },
                SettingsSearchEntry("Content", "Sidebar Menu Customization", "side rail icons dynamic static hide show adapt playlist", OwnTVIcon.MENU, TileTone.PRIMARY,
                    chip = navMenuMode.label, chipTone = if (navMenuMode == tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.NAV_MENU) },
                SettingsSearchEntry("Content", "CH+- Key Paging", "channel up down skip page list category channel", OwnTVIcon.PLAYLIST, TileTone.PRIMARY,
                    chip = if (chNavEnabled) "On" else "Off", chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
                SettingsSearchEntry("Content", "Browsing & lists", "remember position scroll category last item live movies series reset top", OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { savedScroll = scrollState.value; dialogReturn = browsingRowFocus; showBrowsing = true },
                SettingsSearchEntry("Content", "Home screen", "rows hero reorder filter", OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
                SettingsSearchEntry("Content", "Metadata (TMDB)", "posters plots cast ratings", OwnTVIcon.VIDEO, TileTone.PRIMARY) { open(SettingsTab.METADATA) },
                SettingsSearchEntry("Content", "Download folder", "storage path directory", OwnTVIcon.DOWNLOADS, TileTone.TERTIARY,
                    chip = downloadRoot.ifBlank { "App storage" }.let { java.io.File(it).name.ifBlank { it } }, chipTone = TileTone.TERTIARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showFolderPicker = true },
                SettingsSearchEntry("Content", "Backup & Restore", "export import profiles sources", OwnTVIcon.DOWNLOADS, TileTone.TERTIARY) { open(SettingsTab.BACKUP) },
                SettingsSearchEntry("Content", "Clear watch history", "recently watched continue remove", OwnTVIcon.HISTORY, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showClearHistory = true },
                SettingsSearchEntry("Appearance", "Theme", "light dark system", OwnTVIcon.THEME, TileTone.PRIMARY,
                    chip = themeLabel(themeMode)) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showTheme = true },
                SettingsSearchEntry("Appearance", "Accent color", "tint palette hex preset", OwnTVIcon.PALETTE, TileTone.SECONDARY,
                    chip = if (customAccent.isNotBlank()) customAccent.uppercase() else accent.label, chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAccent = true },
                SettingsSearchEntry("Appearance", "UI Zoom", "scale interface size", OwnTVIcon.ZOOM, TileTone.SECONDARY,
                    chip = UiZoom.label(uiZoomPercent), chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showZoom = true },
                SettingsSearchEntry("Appearance", "Animations", "motion snappier performance", OwnTVIcon.THEME, TileTone.SECONDARY,
                    chip = animationLevel.label, chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAnimations = true },
                SettingsSearchEntry("Appearance", "Weather", "top bar chip location celsius fahrenheit", OwnTVIcon.EPG, TileTone.SECONDARY,
                    chip = if (weatherEnabled) "On" else "Off", chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
                SettingsSearchEntry("Playback", "Live preview", "auto play focus channel", OwnTVIcon.LIVE_TV, TileTone.TERTIARY,
                    chip = if (livePreview) "On" else "Off", chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setLivePreviewEnabled(!livePreview) },
                SettingsSearchEntry("Playback", "Preview audio", "sound live preview", OwnTVIcon.AUDIO, TileTone.SECONDARY,
                    chip = if (previewAudio) "On" else "Off", chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setLivePreviewAudio(!previewAudio) },
                SettingsSearchEntry("Playback", "Channel numbers", "channel number direct tune type digits keypad numeric zap lcn", OwnTVIcon.LIVE_TV, TileTone.PRIMARY,
                    chip = if (channelNumbers) "On" else "Off", chipTone = if (channelNumbers) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setDirectTune(!channelNumbers) },
                SettingsSearchEntry("Playback", "Mini-player", "docked pip miniplayer size position scale percent corner move", OwnTVIcon.PIP, TileTone.TERTIARY) { open(SettingsTab.MINI_PLAYER) },
                SettingsSearchEntry("Playback", "HDR", "high dynamic range output", OwnTVIcon.VIDEO, TileTone.PRIMARY,
                    chip = if (hdr) "On" else "Off", chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setHdrEnabled(!hdr) },
                SettingsSearchEntry("Playback", "Auto frame rate", "afr refresh rate hz judder 24fps 25fps 50hz 60hz display mode match", OwnTVIcon.VIDEO, TileTone.PRIMARY,
                    chip = if (autoFrameRate) "On" else "Off", chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setAutoFrameRate(!autoFrameRate) },
                SettingsSearchEntry("Playback", "Surround sound", "dolby dts 5.1 7.1 receiver audio", OwnTVIcon.AUDIO, TileTone.SECONDARY,
                    chip = if (surroundSound) "On" else "Off", chipTone = if (surroundSound) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setSurroundSound(!surroundSound) },
                SettingsSearchEntry("Playback", "Auto-play next episode", "autoplay series season", OwnTVIcon.SKIP_NEXT, TileTone.SECONDARY,
                    chip = if (autoPlayNext) "On" else "Off", chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setAutoPlayNext(!autoPlayNext) },
                SettingsSearchEntry("Playback", "Catch-up", "archive timezone offset catchup external player vlc mx", OwnTVIcon.EPG, TileTone.SECONDARY,
                    chip = when (catchupTz) {
                        SettingsRepository.CatchupTimezone.DEVICE -> "Device"
                        SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
                    }) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showCatchupTime = true },
                SettingsSearchEntry("Playback", "Video Player Settings", "decoder subtitles sync", OwnTVIcon.VIDEO, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry("Playback", "Subtitle appearance", "subtitle size color colour position transparency background opacity", OwnTVIcon.SUBTITLE, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry("Playback", "Live latency", "live buffer latency delay low latency seconds close to live edge", OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry("Playback", "Playback error log", "error crash failure diagnostics report", OwnTVIcon.HISTORY, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showErrorLog = true },
                SettingsSearchEntry("Network", "Proxy", "http traffic route", OwnTVIcon.SHARE, TileTone.SECONDARY) { open(SettingsTab.NETWORK) },
                SettingsSearchEntry("Network", "DNS", "domain lookup resolver doh dns-over-https", OwnTVIcon.SEARCH, TileTone.SECONDARY) { open(SettingsTab.DNS) },
                SettingsSearchEntry("App", "App startup", "launch open landing", OwnTVIcon.HOME, TileTone.SECONDARY,
                    chip = startupMode.label) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showStartup = true },
                SettingsSearchEntry("App", "Check for updates", "github release version", OwnTVIcon.DOWNLOADS, TileTone.PRIMARY,
                    chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}") { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showUpdate = true },
                SettingsSearchEntry("App", "Check updates on startup", "auto update new version", OwnTVIcon.HISTORY, TileTone.SECONDARY,
                    chip = if (updateCheckOnStart) "On" else "Off", chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
                SettingsSearchEntry("App", "About", "version license project info", OwnTVIcon.MENU, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAbout = true },
            )
            val tokens = searchQuery.trim().lowercase().split(" ").filter { it.isNotBlank() }
            val results = entries.filter { e -> tokens.all { t -> e.haystack.contains(t) } }
            if (results.isEmpty()) {
                Text(
                    text = "No settings match “${searchQuery.trim()}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                results.forEach { e ->
                    SettingsRow(
                        tone = e.tone, icon = e.icon,
                        title = "${e.group} › ${e.title}",
                        chip = e.chip, chipTone = e.chipTone,
                        showChevron = e.showChevron,
                        onClick = e.onClick,
                    )
                }
            }
        }
    }

    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
    }
    if (showCatchupTime) {
        CatchupTimeDialog(
            mode = catchupTz,
            offsetMinutes = catchupOffset,
            offsetRange = settingsVm.catchupOffsetRangeMinutes,
            onSetMode = settingsVm::setCatchupTimezone,
            onAdjustOffset = settingsVm::adjustCatchupOffset,
            player = catchupPlayer,
            onSetPlayer = settingsVm::setCatchupPlayer,
            onDismiss = { showCatchupTime = false },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showClearHistory) {
        ClearHistoryDialog(
            onClear = { type -> settingsVm.clearWatchHistory(type); showClearHistory = false },
            onDismiss = { showClearHistory = false },
        )
    }
    if (showTheme) {
        tv.own.owntv.features.settings.PickerDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name to themeLabel(it) },
            selected = themeMode.name,
            onSelect = { settingsVm.setThemeMode(ThemeMode.valueOf(it)); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showStartup) {
        tv.own.owntv.features.settings.PickerDialog(
            title = "App startup",
            options = tv.own.owntv.features.settings.data.StartupMode.entries.map { it.name to it.label },
            selected = startupMode.name,
            onSelect = { settingsVm.setStartupMode(tv.own.owntv.features.settings.data.StartupMode.valueOf(it)); showStartup = false },
            onDismiss = { showStartup = false },
        )
    }
    if (showAnimations) {
        tv.own.owntv.features.settings.PickerDialog(
            title = "Animations",
            options = tv.own.owntv.ui.theme.AnimationLevel.entries.map { it.name to it.label },
            selected = animationLevel.name,
            onSelect = { settingsVm.setAnimationLevel(tv.own.owntv.ui.theme.AnimationLevel.valueOf(it)); showAnimations = false },
            onDismiss = { showAnimations = false },
        )
    }
    if (showAccent) {
        AccentPaletteDialog(
            accent = accent,
            customAccent = customAccent,
            onPickPreset = { settingsVm.setAccent(it) },
            onPickCustom = { settingsVm.setCustomAccent(it) },
            onDismiss = { showAccent = false },
        )
    }
    if (showZoom) {
        ZoomDialog(current = uiZoomPercent, onSet = onSetZoom, onDismiss = { showZoom = false })
    }
    if (showBrowsing) {
        BrowsingListsDialog(
            catLive = rememberCatLive, catMovies = rememberCatMovies, catSeries = rememberCatSeries,
            itemLive = rememberLastLive, itemMovies = rememberLastMovies, itemSeries = rememberLastSeries,
            onToggleCatLive = { settingsVm.setRememberCategoryLive(!rememberCatLive) },
            onToggleCatMovies = { settingsVm.setRememberCategoryMovies(!rememberCatMovies) },
            onToggleCatSeries = { settingsVm.setRememberCategorySeries(!rememberCatSeries) },
            onToggleItemLive = { settingsVm.setRememberLastLive(!rememberLastLive) },
            onToggleItemMovies = { settingsVm.setRememberLastMovies(!rememberLastMovies) },
            onToggleItemSeries = { settingsVm.setRememberLastSeries(!rememberLastSeries) },
            onDismiss = { showBrowsing = false },
        )
    }
    if (showGlassEffect) {
        GlassEffectDialog(
            glassOn = glassConfig.enabled,
            alphaPercent = (glassConfig.alpha * 100).roundToInt(),
            bgOn = bgImagePath.isNotBlank(),
            onToggleGlass = {
                val on = glassConfig.enabled
                settingsVm.setGlassScopeBitmask(if (on) 0 else GlassConfig(ALL_GLASS_SURFACES).toBitmask())
            },
            onSetAlpha = { settingsVm.setGlassAlphaPercent(it) },
            blurPercent = (glassConfig.blurStrength * 100).roundToInt(),
            onSetBlur = { settingsVm.setGlassBlurPercent(it) },
            scope = glassConfig.scope,
            onSetScope = { settingsVm.setGlassScopeBitmask(it) },
            // Hand off to the existing background-image chooser; on close it returns to the Glass Effect row.
            onOpenBackground = { showGlassEffect = false; dialogReturn = glassEffectRowFocus; showBgImageChooser = true },
            onDismiss = { showGlassEffect = false },
        )
    }
    if (showErrorLog) {
        PlaybackErrorLogDialog(onDismiss = { showErrorLog = false })
    }
    if (showFolderPicker) {
        StorageBrowser(
            title = "Choose the download folder",
            mode = BrowseMode.FOLDER,
            onPick = { settingsVm.setDownloadRoot(it.absolutePath); showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }
    if (showBgImageChooser) {
        BackgroundImageChooserDialog(
            hasImage = bgImagePath.isNotBlank(),
            onPickLocal = { showBgImageChooser = false; showBgPicker = true },
            onPickRemote = { showBgImageChooser = false; showBgRemote = true },
            onClear = { settingsVm.setBgImagePath(""); showBgImageChooser = false },
            onDismiss = { showBgImageChooser = false },
        )
    }
    if (showBgRemote) {
        val context = LocalContext.current
        val remoteState by settingsVm.remoteState.collectAsStateWithLifecycle()
        tv.own.owntv.ui.components.RemoteBackgroundDialog(
            state = remoteState,
            images = settingsVm.remoteImages,
            onStart = settingsVm::startRemoteImageListener,
            onStop = settingsVm::stopRemoteListener,
            onImageReceived = { file ->
                // Same ingest as the local pick: copy into app-private storage, then drop the cache temp.
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                            .also { runCatching { file.delete() } }
                    }
                    if (path != null) settingsVm.setBgImagePath(path)
                }
                showBgRemote = false
            },
            onDismiss = { showBgRemote = false },
        )
    }
    if (showBgPicker) {
        val context = LocalContext.current
        StorageBrowser(
            title = "Pick a background image",
            mode = BrowseMode.FILE,
            fileExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp"),
            onPick = { file ->
                // Copy into app-private storage so USB unplug / source-folder delete can't blank it.
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                    }
                    if (path != null) settingsVm.setBgImagePath(path)
                }
                showBgPicker = false
            },
            onDismiss = { showBgPicker = false },
        )
    }
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "System"
}

/** The six quick presets shown at the top of the accent picker. */
private val AccentPresetChoices: List<tv.own.owntv.ui.theme.AccentColor> =
    tv.own.owntv.ui.theme.AccentColor.entries.take(6)

/**
 * Accent picker: a handful of quick presets plus a full HSV color picker — a hue bar and a
 * saturation/brightness square (each an enter-to-edit D-pad control) with a live preview — and a
 * hex-code field for an exact color. The dialog scrolls so the on-screen keyboard never hides the
 * hex field. Presets clear the custom color; the picker/hex set it exactly (custom overrides the
 * preset in the theme).
 */
@Composable
private fun AccentPaletteDialog(
    accent: tv.own.owntv.ui.theme.AccentColor,
    customAccent: String,
    onPickPreset: (tv.own.owntv.ui.theme.AccentColor) -> Unit,
    onPickCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDark = colors.isDark
    val firstFocus = remember { FocusRequester() }

    // Live HSV state seeded from the current custom color (or a pleasant default).
    val hsv = remember {
        FloatArray(3).also { out ->
            val seed = tv.own.owntv.ui.theme.parseAccentHex(customAccent)?.toArgb() ?: 0xFF52DBC8.toInt()
            android.graphics.Color.colorToHSV(seed, out)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val pickedHex = tv.own.owntv.ui.components.hsvToHex(hue, sat, value)
    var hexInput by remember { mutableStateOf(customAccent.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    // Keep the sliders and hex field in step whenever the HSV picker moves.
    fun syncHexFromPicker() { hexInput = pickedHex.removePrefix("#") }

    // PopupFontTheme swaps in the Lora serif and applies the shared popup type scale.
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).imePadding().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // dialogPanel already applies verticalScroll; imePadding on the parent Box lifts the
            // whole panel above the on-screen keyboard so the hex field stays visible.
            modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp),
        ) {
            Text("Accent color", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            Text("Presets", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPresetChoices.forEachIndexed { i, ac ->
                    val isSel = customAccent.isBlank() && ac == accent
                    tv.own.owntv.ui.components.ColorSwatch(
                        color = ac.primary(isDark),
                        selected = isSel,
                        onClick = { onPickPreset(ac); onDismiss() },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Hex code", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // Kept above the picker on purpose: the on-screen keyboard covers the lower half of the
            // screen, so the hex field must sit high enough to stay visible while the user types.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                tv.own.owntv.ui.components.OwnTVTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(6); hexError = false },
                    label = "Hex",
                    placeholder = "52DBC8",
                    modifier = Modifier.width(200.dp),
                )
                OwnTVButton("Apply", onClick = {
                    val parsed = tv.own.owntv.ui.theme.parseAccentHex(hexInput)
                    if (parsed != null) {
                        onPickCustom("#" + hexInput.trim().removePrefix("#").uppercase())
                        onDismiss()
                    } else {
                        hexError = true
                    }
                })
            }
            if (hexError) {
                Spacer(Modifier.height(8.dp))
                Text("Enter 6 hex digits, e.g. 52DBC8", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Color picker", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    // Hue bar: OK to enter, ◀ ▶ to shift the hue, OK/Back to exit.
                    tv.own.owntv.ui.components.HueBar(hue = hue) { h -> hue = h; syncHexFromPicker(); hexError = false }
                }
                // Live preview of the currently picked color.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
                        .border(2.dp, colors.outline, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.height(14.dp))
            // Saturation / Brightness square: OK to enter, D-pad to move the dot, OK/Back to exit.
            tv.own.owntv.ui.components.SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                sat = s; value = v; syncHexFromPicker(); hexError = false
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Use this color", onClick = { onPickCustom(pickedHex); onDismiss() })
            }
        }
    }
    }
}


private const val GITHUB_REPO = "github.com/ahXN00/OwnTV"
private const val TELEGRAM_LINK = "t.me/owntvplayer"

/** About OwnTV: version, license, author and project link — all readable on screen (no TV browser). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLockup(markSize = 48, textSize = 30)
            Spacer(Modifier.height(6.dp))
            Text("Version ${tv.own.owntv.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                "Your own IPTV player for Android TV — a free, open-source, player-only app. " +
                    "It provides no channels or content; you bring your own legally accessible sources.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text("© 2026 Ashiq Hasan · GPLv3 License", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(GITHUB_REPO, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            // Community: Telegram link + a QR, side-by-side to keep the dialog compact, so TV users can
            // join from their phone — no TV browser needed.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Join us on Telegram", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(TELEGRAM_LINK, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Scan the QR to join from your phone, or open the link above.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.telegram_qr),
                        contentDescription = "Telegram group QR code",
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Contributions, bug reports & stars are welcome on GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
        }
    }
}

/**
 * Read-only viewer for the persisted playback error history (B5): the last ~10 failures with their
 * plain-English reason, media spec, raw engine text, engine, stream type and device info — so users
 * who can't pull logcat can read/report what happened after dismissing the error screen.
 */
@Composable
private fun PlaybackErrorLogDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val entries by androidx.compose.runtime.produceState<List<tv.own.owntv.player.PlaybackErrorLog.Entry>?>(initialValue = null, refresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            tv.own.owntv.player.PlaybackErrorLog.read(context)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entries) { if (entries != null) runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val timeFmt = remember { java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp)) {
            Text("Playback error log", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "The most recent playback failures (newest first). Include these details when reporting a problem.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val list = entries
            when {
                list == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                list.isEmpty() -> Text("No playback errors recorded.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                else -> list.forEach { e ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "${timeFmt.format(java.util.Date(e.atMs))}  ·  ${e.engine}  ·  ${if (e.live) "Live" else "VOD"}",
                            style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        )
                        e.reason?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        }
                        e.spec?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        e.raw?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("${e.model} · ${e.android}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!entries.isNullOrEmpty()) {
                    OwnTVButton("Clear log", onClick = {
                        tv.own.owntv.player.PlaybackErrorLog.clear(context)
                        refresh++
                    }, style = OwnTVButtonStyle.SECONDARY)
                }
                Spacer(Modifier.weight(1f))
                OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}

/**
 * Pick what watch history to clear: everything, or just Live TV / Movies / Series. Over a dimmed scrim;
 * Cancel is focused first so a stray OK doesn't wipe anything. [onClear] gets null for "all".
 */
@Composable
private fun ClearHistoryDialog(
    onClear: (tv.own.owntv.core.model.MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    // null = still choosing a scope; non-null = confirming that scope (type + label; type null = everything).
    var pending by remember { mutableStateOf<Pair<tv.own.owntv.core.model.MediaType?, String>?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pending != null) pending = null else onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = pending
            if (p == null) {
                Text("Clear watch history", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose what to remove for this profile. Playlists, favorites and downloads are not affected.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                Spacer(Modifier.height(10.dp))
                OwnTVButton("Live TV", onClick = { pending = tv.own.owntv.core.model.MediaType.LIVE to "Live TV" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("Movies", onClick = { pending = tv.own.owntv.core.model.MediaType.MOVIE to "Movies" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("Series", onClick = { pending = tv.own.owntv.core.model.MediaType.SERIES to "Series" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("All history", onClick = { pending = (null as tv.own.owntv.core.model.MediaType?) to "all" }, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Clear ${p.second} history?", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OwnTVButton("No", onClick = { pending = null }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus))
                    OwnTVButton("Yes, clear", onClick = { onClear(p.first) })
                }
            }
        }
    }
}

/** A stepper for the global UI scale. Changes apply live (the whole UI re-scales as you adjust). */
@Composable
private fun ZoomDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    // Zoom below LOW_RAM_WARN doubles the on-screen item count, which can OOM-crash 2 GB devices
    // (#51) — the first step under it is gated behind an accept-the-risk warning. Accepting once
    // arms the rest of this dialog session; if it was opened already below the line, don't nag.
    var lowZoomAccepted by remember { mutableStateOf(current < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("UI Zoom", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scale the whole interface (${UiZoom.MIN}%–${UiZoom.MAX}%).",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Initial focus lands on the DECREASE button: the dialog is most often opened to escape an
                // over-zoomed screen (where everything's too big to navigate), so "–" must be first under
                // the cursor. The buttons stay focusable at the limits (clamped + dimmed, never disabled)
                // so focus always lands inside the dialog — a disabled "+" at MAX zoom was leaving focus
                // stranded outside, trapping the user at high zoom.
                StepButton("–", dimmed = current <= UiZoom.MIN, modifier = Modifier.focusRequester(firstFocus)) {
                    val next = UiZoom.clamp(current - UiZoom.STEP)
                    if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else onSet(next)
                }
                Text(
                    UiZoom.label(current),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                    modifier = Modifier.width(120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton("+", dimmed = current >= UiZoom.MAX) {
                    onSet(UiZoom.clamp(current + UiZoom.STEP))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Reset", onClick = { onSet(UiZoom.DEFAULT) }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }

        // Accept-the-risk gate for zoom below LOW_RAM_WARN (#51). One button, focus locked (all
        // D-pad directions cancelled) — OK accepts and applies the pending step, Back cancels.
        pendingLowZoom?.let { target ->
            val acceptFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { acceptFocus.requestFocus() } }
            // Composed after the dialog's own BackHandler, so it wins while the warning is up.
            BackHandler {
                pendingLowZoom = null
                runCatching { firstFocus.requestFocus() }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⚠️ Low zoom warning", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Zoom below ${UiZoom.LOW_RAM_WARN}% shows many more items on screen at once. " +
                            "On devices with limited memory (e.g. 2 GB TV sticks) this can make the app " +
                            "unstable or crash, especially with large playlists and EPG data.\n\n" +
                            "Press OK to continue, or Back to stay at ${UiZoom.LOW_RAM_WARN}%.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OwnTVButton(
                        "I understand and accept the risk",
                        onClick = {
                            lowZoomAccepted = true
                            pendingLowZoom = null
                            onSet(target)
                            runCatching { firstFocus.requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(acceptFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
    }
}

/**
 * A stepper for the Liquid Glass fill strength — how opaque the translucent panels are over the
 * background photo. Higher = more solid (less see-through). Changes apply live. Range 20–95% in 5%
 * steps so panels can never go fully transparent (text would be unreadable) or fully solid (pointless).
 */
@Composable
private fun GlassEffectDialog(
    glassOn: Boolean,
    alphaPercent: Int,
    blurPercent: Int,
    bgOn: Boolean,
    scope: Set<GlassSurface>,
    onToggleGlass: () -> Unit,
    onSetAlpha: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetScope: (Int) -> Unit,
    onOpenBackground: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    // Per-surface scope sub-dialog (advanced). While it is open the main panel is NOT composed at all:
    // the main panel's trapAllFocusExit would otherwise keep D-pad focus locked inside itself, making
    // the sub-dialog unreachable. Re-request focus here whenever the main panel comes (back) on screen.
    var showSurfaces by remember { mutableStateOf(false) }
    LaunchedEffect(showSurfaces) { if (!showSurfaces) runCatching { firstFocus.requestFocus() } }
    val min = 20
    val max = 95
    val step = 5
    val default = (GlassConfig.DEFAULT_GLASS_ALPHA * 100).roundToInt()
    fun clamp(v: Int) = v.coerceIn(min, max)
    // Backdrop blur ("frost") stepper — 0..100 in 10% steps. 0 keeps the Tier-1 translucency-only look;
    // only has an effect when a background image is set and the device supports it (API 31+).
    val blurMin = 0
    val blurMax = 100
    val blurStep = 10
    val blurDefault = (GlassConfig.DEFAULT_BLUR_STRENGTH * 100).roundToInt()
    fun blurClamp(v: Int) = v.coerceIn(blurMin, blurMax)
    BackHandler { onDismiss() }
    if (!showSurfaces) Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // Shared Lora popup font, matching the app's other dialogs.
        tv.own.owntv.ui.theme.PopupFontTheme {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Glass Effect", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Translucent frosted panels over a background photo, or over the app's own background.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Master on/off for the liquid glass (works with or without a background image).
            OwnTVButton(
                if (glassOn) "Liquid glass: On" else "Liquid glass: Off",
                onClick = onToggleGlass,
                style = if (glassOn) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.THEME,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(12.dp))
            // Optional background photo behind everything.
            OwnTVButton(
                if (bgOn) "Background image: On" else "Background image: Off",
                onClick = onOpenBackground,
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.IMAGE,
                modifier = Modifier.fillMaxWidth(),
            )
            if (glassOn) {
                Spacer(Modifier.height(22.dp))
                Text("Transparency", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "How much shows through the panels ($min%–$max%). Higher is more solid.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("–", dimmed = alphaPercent <= min) { onSetAlpha(clamp(alphaPercent - step)) }
                    Text(
                        "$alphaPercent%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton("+", dimmed = alphaPercent >= max) { onSetAlpha(clamp(alphaPercent + step)) }
                }
                // Backdrop blur — the real "frost" behind the panels (needs a background image; API 31+).
                Spacer(Modifier.height(20.dp))
                Text("Blur / Frost", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "How frosted the backdrop is ($blurMin%–$blurMax%). Only over a background image." +
                        if (!bgOn) " Set a background image to enable." else "",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("–", dimmed = blurPercent <= blurMin) { onSetBlur(blurClamp(blurPercent - blurStep)) }
                    Text(
                        "$blurPercent%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton("+", dimmed = blurPercent >= blurMax) { onSetBlur(blurClamp(blurPercent + blurStep)) }
                }
                // Advanced: choose exactly which surfaces render as glass.
                Spacer(Modifier.height(16.dp))
                OwnTVButton(
                    if (scope == ALL_GLASS_SURFACES) "Surfaces: All" else "Surfaces: ${scope.size} of ${ALL_GLASS_SURFACES.size}",
                    onClick = { showSurfaces = true },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (glassOn) OwnTVButton("Reset", onClick = { onSetAlpha(default); onSetBlur(blurDefault); onSetScope(GlassConfig(ALL_GLASS_SURFACES).toBitmask()) }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }
        }
    }
    if (showSurfaces) {
        GlassSurfacesDialog(scope = scope, onSetScope = onSetScope, onDismiss = { showSurfaces = false })
    }
}

/**
 * Browsing & lists — six per-section toggles, two for each of Live TV / Movies / Series:
 *
 *  - "Remember last category" (on by default): reopening the section lands on the category you left
 *    rather than All. Live TV has always behaved this way; Movies/Series gained it alongside the toggle.
 *  - "Remember last item" (off by default): each category keeps its own scroll position instead of
 *    resetting to the top. The Live TV one additionally gates the last-focused-channel restore.
 *
 * The separate "App startup -> Last channel" setting is independent of all six.
 */
@Composable
private fun BrowsingListsDialog(
    catLive: Boolean,
    catMovies: Boolean,
    catSeries: Boolean,
    itemLive: Boolean,
    itemMovies: Boolean,
    itemSeries: Boolean,
    onToggleCatLive: () -> Unit,
    onToggleCatMovies: () -> Unit,
    onToggleCatSeries: () -> Unit,
    onToggleItemLive: () -> Unit,
    onToggleItemMovies: () -> Unit,
    onToggleItemSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        // Six toggles + two group headers overflow a 720p panel — dialogPanel already scrolls the body
        // (scroll = true by default), so do NOT add another verticalScroll here.
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Browsing & lists", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "What Live TV, Movies and Series return to when you come back.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            // SECONDARY chrome on every row (matching GlassSurfacesDialog): with an accent fill on each
            // "On" row the focused row becomes hard to pick out on a TV. State reads from the ": On/Off"
            // text; focus is carried by the button's own highlight.
            BrowsingGroupLabel("Remember last category", "Reopen the section on the category you left, not All.")
            OwnTVButton(
                "Live TV: ${if (catLive) "On" else "Off"}", onClick = onToggleCatLive,
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton("Movies: ${if (catMovies) "On" else "Off"}", onClick = onToggleCatMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton("Series: ${if (catSeries) "On" else "Off"}", onClick = onToggleCatSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))
            BrowsingGroupLabel(
                "Remember last item",
                "Each category keeps its own scroll position instead of starting at the top. " +
                    "Live TV also restores the last focused channel.",
            )
            OwnTVButton("Live TV: ${if (itemLive) "On" else "Off"}", onClick = onToggleItemLive,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton("Movies: ${if (itemMovies) "On" else "Off"}", onClick = onToggleItemMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton("Series: ${if (itemSeries) "On" else "Off"}", onClick = onToggleItemSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            OwnTVButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
        }
    }
}

@Composable
private fun BrowsingGroupLabel(title: String, desc: String) {
    val colors = OwnTVTheme.colors
    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(2.dp))
    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
}

/** User-facing label for a glassable surface. */
private fun glassSurfaceLabel(s: GlassSurface): String = when (s) {
    GlassSurface.PANELS -> "Content panels"
    GlassSurface.SIDEBAR -> "Sidebar"
    GlassSurface.PREVIEW -> "Preview panes"
    GlassSurface.DIALOGS -> "Dialogs & popups"
    GlassSurface.TOPBAR -> "Top bar"
    GlassSurface.CARDS -> "Cards"
    GlassSurface.MINI_PLAYER -> "Mini player"
}

/**
 * Advanced per-surface glass scope: one On/Off row per [GlassSurface] plus an "All" master.
 * Changes apply live (persisted via the scope bitmask). Unticking every surface is the same as
 * turning glass off — the helper text says so instead of blocking it.
 */
@Composable
private fun GlassSurfacesDialog(
    scope: Set<GlassSurface>,
    onSetScope: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    fun toggled(s: GlassSurface): Int = GlassConfig(if (s in scope) scope - s else scope + s).toBitmask()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        Column(
            modifier = Modifier.dialogPanel(width = 440.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Glass surfaces", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose which parts of the app render as glass. Turning every surface off turns glass off.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // All rows use the SECONDARY chrome: with the accent (PRIMARY) fill on every "On" row the
            // focused row was indistinguishable on TV. State lives in the ": On/Off" text; focus in the
            // button's own focus highlight.
            OwnTVButton(
                if (scope == ALL_GLASS_SURFACES) "All surfaces: On" else "All surfaces: Off",
                onClick = {
                    onSetScope(if (scope == ALL_GLASS_SURFACES) 0 else GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(12.dp))
            GlassSurface.entries.forEach { s ->
                val on = s in scope
                OwnTVButton(
                    "${glassSurfaceLabel(s)}: ${if (on) "On" else "Off"}",
                    onClick = { onSetScope(toggled(s)) },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            OwnTVButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
        }
    }
}

/** "UTC", "UTC+05:00", "UTC-03:30" — labels a UTC offset (in minutes) for catch-up. */
private fun utcOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "UTC"
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign%02d:%02d".format(abs / 60, abs % 60)
}

@Composable
private fun CatchupTimeDialog(
    mode: SettingsRepository.CatchupTimezone,
    offsetMinutes: Int,
    offsetRange: IntRange,
    onSetMode: (SettingsRepository.CatchupTimezone) -> Unit,
    onAdjustOffset: (Int) -> Unit,
    player: SettingsRepository.CatchupPlayer,
    onSetPlayer: (SettingsRepository.CatchupPlayer) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    val manual = mode == SettingsRepository.CatchupTimezone.MANUAL
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Catch-up", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "How catch-up timestamps are sent. Use your device timezone, or set the offset your provider's server expects.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Mode toggle: Device / Manual.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    "Device",
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.DEVICE) },
                    style = if (!manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                OwnTVButton(
                    "Manual",
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.MANUAL) },
                    style = if (manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }
            if (manual) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("–", enabled = offsetMinutes > offsetRange.first) { onAdjustOffset(-60) }
                    Text(
                        utcOffsetLabel(offsetMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.width(150.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton("+", enabled = offsetMinutes < offsetRange.last) { onAdjustOffset(60) }
                }
            }
            // Which player takes an archive programme. Archives are the streams the in-app engines
            // struggle with most, so an external app is a useful fallback — "Ask" puts the choice on
            // the "Watch from start" action itself instead of forcing one answer forever.
            Spacer(Modifier.height(24.dp))
            Text("Play catch-up in", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRepository.CatchupPlayer.entries.forEach { p ->
                    OwnTVButton(
                        when (p) {
                            SettingsRepository.CatchupPlayer.ASK -> "Always ask"
                            SettingsRepository.CatchupPlayer.INTERNAL -> "OwnTV player"
                            SettingsRepository.CatchupPlayer.EXTERNAL -> "External player"
                        },
                        onClick = { onSetPlayer(p) },
                        style = if (player == p) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                        compact = true,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OwnTVButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled && !dimmed) colors.onSurface else colors.outline)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

@Composable
private fun SettingsRow(
    tone: TileTone,
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    chipTone: TileTone = TileTone.PRIMARY,
    soon: Boolean = false,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tonal icon tile
            val (tileBg, tileOn) = tone.colors()
            Box(
                modifier = Modifier
                    .size(Dimens.IconTileSize)
                    .clip(RoundedCornerShape(Dimens.IconTileCorner))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(icon = icon, tint = tileOn, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (desc != null) {
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (soon) {
                    SoonChip()
                }
                if (chip != null) {
                    ValueChip(chip, chipTone)
                }
                if (showChevron) {
                    OwnTVIcon(icon = OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SoonChip() {
    val colors = OwnTVTheme.colors
    Text(
        text = "SOON",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHighest)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Batch 4 · one searchable settings row: its group breadcrumb, title, extra keywords, and action. */
private class SettingsSearchEntry(
    val group: String,
    val title: String,
    keywords: String,
    val icon: OwnTVIcon,
    val tone: TileTone,
    val chip: String? = null,
    val chipTone: TileTone = TileTone.PRIMARY,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
) {
    /** Lower-cased match target: group + title + keywords. */
    val haystack: String = "$group $title $keywords".lowercase()
}

/** Batch 4 · a focusable most-used toggle chip shown pinned above the settings list. */
@Composable
private fun QuickToggleChip(
    label: String,
    on: Boolean,
    icon: OwnTVIcon,
    onToggle: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val onColors = TileTone.PRIMARY.colors()
    val offColors = TileTone.SECONDARY.colors()
    val (bg, fg) = if (on) onColors else offColors
    // Always-on faint glass edge over the tonal fill (which is opaque, so it hides an outer-surface
    // rim) so these chips read as glass at rest too, matching the top-bar chips.
    val glassy = LocalGlass.current.isGlassy(GlassSurface.CARDS)
    FocusableSurface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Row(
            modifier = Modifier
                .background(bg, RoundedCornerShape(12.dp))
                .then(if (glassy) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OwnTVIcon(icon = icon, tint = fg, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (on) "On" else "Off",
                style = MaterialTheme.typography.labelMedium,
                color = if (on) fg else colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ValueChip(text: String, tone: TileTone) {
    val (bg, on) = tone.colors()
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = on,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun TileTone.colors(): Pair<Color, Color> {
    val c = OwnTVTheme.colors
    return when (this) {
        TileTone.PRIMARY -> c.primaryContainer to c.onPrimaryContainer
        TileTone.SECONDARY -> c.secondaryContainer to c.onSecondaryContainer
        TileTone.TERTIARY -> c.tertiaryContainer to c.onTertiaryContainer
    }
}
