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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.customize.CustomizeScreen
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.player.SurroundMode
import tv.own.owntv.features.settings.HomeSettingsScreen
import tv.own.owntv.features.settings.LanguageSettingsScreen
import tv.own.owntv.features.settings.LanguageSettingsViewModel
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
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.BackgroundImageChooserDialog
import tv.own.owntv.ui.components.ingestBackgroundImage
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.format.formatBestDateTime
import tv.own.owntv.ui.theme.ALL_GLASS_SURFACES
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassConfig
import tv.own.owntv.ui.theme.GlassInteraction
import tv.own.owntv.ui.theme.GlassPreset
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.glass
import tv.own.owntv.ui.theme.AppFontFamily
import tv.own.owntv.ui.theme.FontCustomization
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.player.displayText
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiFontScale
import tv.own.owntv.ui.theme.UiZoom
import tv.own.owntv.ui.theme.asComposeFamily
import kotlin.math.roundToInt
import java.io.File
import java.util.Locale

private enum class TileTone { PRIMARY, SECONDARY, TERTIARY }

private enum class SettingsTab { ROOT, LANGUAGE, SOURCES, EPG, PROFILES, BACKUP, VIDEO, MINI_PLAYER, CUSTOMIZE, HOME, NETWORK, DNS, METADATA, WEATHER, NAV_MENU, CH_NAV, PANEL_WIDTH }

@Composable
private fun surroundModeLabel(mode: SurroundMode): String = stringResource(
    when (mode) {
        SurroundMode.AUTO -> R.string.settings_auto
        SurroundMode.STEREO -> R.string.settings_surround_stereo
        SurroundMode.SURROUND -> R.string.settings_surround_sound
    },
)

@Composable
private fun epgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}

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
    fontCustomization: FontCustomization,
    onSetFontCustomization: (FontCustomization) -> Unit,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
) {
    // A cross-script language change recreates the Activity so Android can apply the new script's
    // shaping and font fallback. Keep the open settings sub-screen across that configuration change
    // instead of dropping back to the Settings root/sidebar.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.ROOT) }
    // Deep-link from the Guide's "Add EPG" button: jump straight to EPG Sources in add mode.
    var consumeEpgAdd by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showFontCustomization by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCatchupTime by remember { mutableStateOf(false) }
    var showEpgOffset by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(false) }
    var showErrorLog by remember { mutableStateOf(false) }
    var showAfrWarning by remember { mutableStateOf(false) }
    var showLivePreviewPanelWarning by remember { mutableStateOf(false) }
    var showBgImageChooser by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBgRemote by remember { mutableStateOf(false) }
    var showGlassEffect by remember { mutableStateOf(false) }
    var showAmbientGlow by remember { mutableStateOf(false) }
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
    val fontCustomizationRowFocus = remember { FocusRequester() }
    val updateRowFocus = remember { FocusRequester() }
    val aboutRowFocus = remember { FocusRequester() }
    val catchupRowFocus = remember { FocusRequester() }
    val epgOffsetRowFocus = remember { FocusRequester() }
    val clearHistoryRowFocus = remember { FocusRequester() }
    val animationsRowFocus = remember { FocusRequester() }
    val startupRowFocus = remember { FocusRequester() }
    val errorLogRowFocus = remember { FocusRequester() }
    val afrRowFocus = remember { FocusRequester() }
    val livePreviewQuickFocus = remember { FocusRequester() }
    val livePreviewRowFocus = remember { FocusRequester() }
    val glassEffectRowFocus = remember { FocusRequester() }
    val ambientGlowRowFocus = remember { FocusRequester() }
    // Hoisted scroll state for the root settings list. We snapshot its position the instant a row is
    // clicked (in onClick, before any recomposition) and restore it on dialog close, so the list
    // doesn't visibly jump/scroll when the dialog opens or when we refocus the opener row afterward.
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    val anyDialogOpen = showZoom || showFontCustomization || showTheme || showAccent || showFolderPicker || showUpdate || showAbout || showCatchupTime || showEpgOffset || showClearHistory || showAnimations || showStartup || showErrorLog || showAfrWarning || showLivePreviewPanelWarning || showBgImageChooser || showBgPicker || showGlassEffect || showAmbientGlow || showBrowsing
    // When a dialog closes, restore focus to the row that opened it. NOTE: this restore crosses
    // INTO the root focus group from outside (the dialog), but onEnter does NOT fire for programmatic
    // requestsFocus (only for directional entry) — so dialogReturn must be cleared HERE, not in onEnter.
    // If it's left set, the next directional entry (e.g. sidebar→here) would re-route to a stale row.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showZoom, showFontCustomization, showTheme, showAccent, showFolderPicker, showUpdate, showAbout, showCatchupTime, showEpgOffset, showClearHistory, showAnimations, showStartup, showErrorLog, showAfrWarning, showLivePreviewPanelWarning, showBgImageChooser, showBgPicker, showGlassEffect, showAmbientGlow, showBrowsing) {
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
    val languageVm: LanguageSettingsViewModel = koinViewModel()
    val currentLocaleTag by languageVm.currentTag.collectAsStateWithLifecycle()
    val languageChip = languageChipText(currentLocaleTag)
    val downloadRoot by settingsVm.downloadRoot.collectAsStateWithLifecycle()
    val livePreview by settingsVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val livePreviewPanelActive by settingsVm.livePreviewPanelActive.collectAsStateWithLifecycle()
    val previewAudio by settingsVm.livePreviewAudio.collectAsStateWithLifecycle()
    val hdr by settingsVm.hdrEnabled.collectAsStateWithLifecycle()
    val autoFrameRate by settingsVm.autoFrameRate.collectAsStateWithLifecycle()
    val surroundMode by settingsVm.surroundMode.collectAsStateWithLifecycle()
    val autoPlayNext by settingsVm.autoPlayNext.collectAsStateWithLifecycle()
    val updateCheckOnStart by settingsVm.updateCheckOnStart.collectAsStateWithLifecycle()
    val channelNumbers by settingsVm.directTune.collectAsStateWithLifecycle()
    val catchupTz by settingsVm.catchupTimezone.collectAsStateWithLifecycle()
    val catchupOffset by settingsVm.catchupOffsetMinutes.collectAsStateWithLifecycle()
    val epgOffset by settingsVm.epgOffsetMinutes.collectAsStateWithLifecycle()
    val catchupChannels by settingsVm.catchupChannelCount.collectAsStateWithLifecycle()
    val catchupPlayer by settingsVm.catchupPlayer.collectAsStateWithLifecycle()
    val accent by settingsVm.accent.collectAsStateWithLifecycle()
    val customAccent by settingsVm.customAccent.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val glassOn = glassConfig.enabled
    val animationLevel by settingsVm.animationLevel.collectAsStateWithLifecycle()
    val ambientGlowEnabled by settingsVm.ambientGlowEnabled.collectAsStateWithLifecycle()
    val ambientGlowPulse by settingsVm.ambientGlowPulse.collectAsStateWithLifecycle()
    LaunchedEffect(glassOn) {
        if (glassOn) showAmbientGlow = false
    }
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
    // "Custom" on the Panel Width row as soon as any one of the three sections is switched on.
    val panelWidthLive by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.features.settings.data.PanelSection.LIVE).collectAsStateWithLifecycle()
    val panelWidthMovies by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.features.settings.data.PanelSection.MOVIES).collectAsStateWithLifecycle()
    val panelWidthSeries by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.features.settings.data.PanelSection.SERIES).collectAsStateWithLifecycle()
    val panelWidthCustom = panelWidthLive || panelWidthMovies || panelWidthSeries

    // Auto frame rate is the one toggle that can make the picture visibly worse on the wrong hardware:
    // below Android 12 there is no way to ask the display which refresh rates it can reach without
    // blanking. Turning it on there therefore asks first; turning it off remains immediate.
    val afrNeedsWarning = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
    val toggleAutoFrameRate: (FocusRequester) -> Unit = { returnFocus ->
        if (!autoFrameRate && afrNeedsWarning) {
            savedScroll = scrollState.value
            dialogReturn = returnFocus
            showAfrWarning = true
        } else {
            settingsVm.setAutoFrameRate(!autoFrameRate)
        }
    }
    val toggleLivePreview: (FocusRequester) -> Unit = { returnFocus ->
        if (livePreview) {
            settingsVm.setLivePreviewEnabled(false)
        } else if (!livePreviewPanelActive) {
            savedScroll = scrollState.value
            dialogReturn = returnFocus
            showLivePreviewPanelWarning = true
        } else {
            settingsVm.setLivePreviewEnabled(true)
        }
    }

    // Restore focus to the row a sub-screen was opened from when the user navigates back.
    var lastTab by rememberSaveable { mutableStateOf<SettingsTab?>(null) }
    val rowFocus = remember { mapOf(
        SettingsTab.LANGUAGE to FocusRequester(),
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
        SettingsTab.PANEL_WIDTH to FocusRequester(),
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
        SettingsTab.LANGUAGE -> { LanguageSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
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
        SettingsTab.PANEL_WIDTH -> { tv.own.owntv.features.settings.PanelWidthSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
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
            text = stringResource(R.string.settings_title),
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
            QuickToggleChip(
                stringResource(R.string.settings_quick_live_preview),
                livePreview,
                OwnTVIcon.LIVE_TV,
                modifier = Modifier.focusRequester(livePreviewQuickFocus),
            ) { toggleLivePreview(livePreviewQuickFocus) }
            QuickToggleChip(stringResource(R.string.settings_quick_preview_sound), previewAudio, OwnTVIcon.AUDIO) { settingsVm.setLivePreviewAudio(!previewAudio) }
            QuickToggleChip(stringResource(R.string.settings_quick_channel_numbers), channelNumbers, OwnTVIcon.LIVE_TV) { settingsVm.setDirectTune(!channelNumbers) }
            QuickToggleChip(stringResource(R.string.settings_quick_hdr), hdr, OwnTVIcon.VIDEO) { settingsVm.setHdrEnabled(!hdr) }
            QuickToggleChip(stringResource(R.string.settings_quick_autoplay), autoPlayNext, OwnTVIcon.SKIP_NEXT) { settingsVm.setAutoPlayNext(!autoPlayNext) }
            QuickToggleChip(stringResource(R.string.settings_quick_check_update), updateCheckOnStart, OwnTVIcon.DOWNLOADS) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) }
        }
        Spacer(Modifier.height(8.dp))

        // --- Batch 4: settings search ---
        OwnTVTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = stringResource(R.string.settings_search_label),
            placeholder = stringResource(R.string.settings_search_hint),
            focusRequester = searchFieldFocus,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (searchQuery.isBlank()) {
        GroupLabel(stringResource(R.string.settings_profile_group))
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PERSON,
            title = stringResource(R.string.profiles_title), desc = stringResource(R.string.settings_profiles_description),
            onClick = { open(SettingsTab.PROFILES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.PROFILES)),
        )
        SectionDivider()
        GroupLabel(stringResource(R.string.settings_content_group))
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = stringResource(R.string.settings_playlists), desc = stringResource(R.string.settings_playlists_description),
            onClick = { open(SettingsTab.SOURCES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.SOURCES)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.EPG,
            title = stringResource(R.string.settings_epg_sources), desc = stringResource(R.string.settings_epg_sources_nav_description),
            onClick = { open(SettingsTab.EPG) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.EPG)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = stringResource(R.string.content_epg_time_offset),
            desc = stringResource(R.string.settings_epg_offset_root_description),
            chip = epgShiftLabel(epgOffset),
            chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = epgOffsetRowFocus; showEpgOffset = true }, showChevron = true,
            modifier = Modifier.focusRequester(epgOffsetRowFocus),
        )
        // Sits with the EPG offset, not with Playback: both answer "the guide/archive clock is wrong",
        // and a user fixing one almost always looks at the other next.
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = stringResource(R.string.settings_catchup),
            desc = if (catchupChannels > 0) pluralStringResource(R.plurals.settings_catchup_supported, catchupChannels, catchupChannels)
                else stringResource(R.string.settings_catchup_unavailable),
            chip = when (catchupTz) {
                SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
            },
            chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = catchupRowFocus; showCatchupTime = true }, showChevron = true,
            modifier = Modifier.focusRequester(catchupRowFocus),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.SORT,
            title = stringResource(R.string.settings_customize), desc = stringResource(R.string.settings_customize_nav_description),
            onClick = { open(SettingsTab.CUSTOMIZE) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.CUSTOMIZE)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.MENU,
            title = stringResource(R.string.settings_sidebar_customization), desc = stringResource(R.string.settings_sidebar_description_root),
            chip = navModeLabel(navMenuMode),
            chipTone = if (navMenuMode == tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.NAV_MENU) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.NAV_MENU)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = stringResource(R.string.settings_ch_paging), desc = stringResource(R.string.settings_ch_paging_description),
            chip = if (chNavEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.CH_NAV) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.CH_NAV)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.ZOOM,
            title = stringResource(R.string.settings_panel_width),
            desc = stringResource(R.string.settings_panel_width_description),
            chip = if (panelWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default),
            chipTone = if (panelWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.PANEL_WIDTH) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.PANEL_WIDTH)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = stringResource(R.string.settings_browsing_lists), desc = stringResource(R.string.settings_browsing_description),
            onClick = { savedScroll = scrollState.value; dialogReturn = browsingRowFocus; showBrowsing = true }, showChevron = true,
            modifier = Modifier.focusRequester(browsingRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HOME,
            title = stringResource(R.string.settings_home_root), desc = stringResource(R.string.settings_home_root_description),
            onClick = { open(SettingsTab.HOME) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.HOME)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_metadata), desc = stringResource(R.string.settings_metadata_root_description),
            onClick = { open(SettingsTab.METADATA) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.METADATA)),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.settings_download_folder),
            chip = downloadRoot.ifBlank { stringResource(R.string.settings_app_storage) }.let { java.io.File(it).name.ifBlank { it } },
            chipTone = TileTone.TERTIARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = folderRowFocus; showFolderPicker = true }, showChevron = true,
            modifier = Modifier.focusRequester(folderRowFocus),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.settings_backup_restore), desc = stringResource(R.string.settings_backup_restore_description),
            onClick = { open(SettingsTab.BACKUP) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.BACKUP)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_clear_history), desc = stringResource(R.string.settings_clear_history_description),
            onClick = { savedScroll = scrollState.value; dialogReturn = clearHistoryRowFocus; showClearHistory = true }, showChevron = true,
            modifier = Modifier.focusRequester(clearHistoryRowFocus),
        )
        SectionDivider()
        GroupLabel(stringResource(R.string.settings_appearance_group))
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.LANGUAGE,
            title = stringResource(R.string.settings_language),
            desc = stringResource(R.string.settings_language_description),
            chip = languageChip,
            chipTone = TileTone.PRIMARY,
            onClick = { open(SettingsTab.LANGUAGE) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.LANGUAGE)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.THEME,
            title = stringResource(R.string.settings_theme), desc = stringResource(R.string.settings_theme_description),
            chip = themeLabel(themeMode), chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = themeRowFocus; showTheme = true }, showChevron = true,
            modifier = Modifier.focusRequester(themeRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PALETTE,
            title = stringResource(R.string.settings_accent), desc = stringResource(R.string.settings_accent_description),
            chip = if (customAccent.isNotBlank()) customAccent.uppercase() else stringResource(accent.labelRes),
            chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = accentRowFocus; showAccent = true }, showChevron = true,
            modifier = Modifier.focusRequester(accentRowFocus),
        )
        // One consolidated "Glass Effect" entry: opens a dialog holding the glass on/off toggle,
        // the background-image chooser, and the transparency stepper (see GlassEffectDialog).
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.THEME,
            title = stringResource(R.string.settings_glass_effect), desc = stringResource(R.string.settings_glass_description),
            chip = if (glassOn) glassPresetLabel(glassConfig.preset) else stringResource(R.string.common_off),
            chipTone = if (glassOn) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = glassEffectRowFocus; showGlassEffect = true }, showChevron = true,
            modifier = Modifier.focusRequester(glassEffectRowFocus),
        )
        if (!glassOn) {
            SettingsRow(
                tone = TileTone.PRIMARY, icon = OwnTVIcon.PALETTE,
                title = stringResource(R.string.settings_ambient_glow),
                desc = stringResource(R.string.settings_ambient_glow_description),
                chip = stringResource(if (ambientGlowEnabled) R.string.common_on else R.string.common_off),
                chipTone = if (ambientGlowEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
                onClick = { savedScroll = scrollState.value; dialogReturn = ambientGlowRowFocus; showAmbientGlow = true },
                showChevron = true,
                modifier = Modifier.focusRequester(ambientGlowRowFocus),
            )
        }
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PALETTE,
            title = stringResource(R.string.settings_font_customization),
            desc = stringResource(R.string.settings_font_customization_description),
            chip = stringResource(R.string.common_percent, fontCustomization.sizePercent),
            chipTone = TileTone.SECONDARY,
            onClick = {
                savedScroll = scrollState.value
                dialogReturn = fontCustomizationRowFocus
                showFontCustomization = true
            },
            showChevron = true,
            modifier = Modifier.focusRequester(fontCustomizationRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.ZOOM,
            title = stringResource(R.string.settings_ui_zoom), desc = stringResource(R.string.settings_ui_zoom_description),
            chip = stringResource(R.string.common_percent, uiZoomPercent), chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = zoomRowFocus; showZoom = true }, showChevron = true,
            modifier = Modifier.focusRequester(zoomRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.THEME,
            title = stringResource(R.string.settings_animations), desc = stringResource(R.string.settings_animations_description),
            chip = stringResource(animationLevel.labelRes), chipTone = TileTone.SECONDARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = animationsRowFocus; showAnimations = true }, showChevron = true,
            modifier = Modifier.focusRequester(animationsRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = stringResource(R.string.settings_weather),
            desc = stringResource(R.string.settings_weather_description_root),
            chip = if (weatherEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { open(SettingsTab.WEATHER) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.WEATHER)),
        )

        SectionDivider()
        GroupLabel(stringResource(R.string.settings_playback_group))
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_quick_live_preview), desc = stringResource(R.string.settings_live_preview_description),
            chip = if (livePreview) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { toggleLivePreview(livePreviewRowFocus) },
            modifier = Modifier.focusRequester(livePreviewRowFocus),
        )
        if (livePreview) {
            SettingsRow(
                tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
                title = stringResource(R.string.settings_preview_audio), desc = stringResource(R.string.settings_preview_audio_description),
                chip = if (previewAudio) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY,
                onClick = { settingsVm.setLivePreviewAudio(!previewAudio) },
            )
        }
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.PIP,
            title = stringResource(R.string.settings_mini_player_root), desc = stringResource(R.string.settings_mini_player_root_description),
            onClick = { open(SettingsTab.MINI_PLAYER) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.MINI_PLAYER)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_quick_hdr), desc = stringResource(R.string.settings_hdr_description),
            chip = if (hdr) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setHdrEnabled(!hdr) },
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_auto_frame_rate),
            desc = stringResource(R.string.settings_auto_frame_rate_description) +
                if (afrNeedsWarning) " " + stringResource(R.string.settings_auto_frame_rate_warning_suffix) else "",
            chip = if (autoFrameRate) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY,
            modifier = Modifier.focusRequester(afrRowFocus),
            onClick = { toggleAutoFrameRate(afrRowFocus) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
            title = stringResource(R.string.settings_surround_sound),
            desc = when (surroundMode) {
                SurroundMode.AUTO -> stringResource(R.string.settings_surround_auto_description)
                SurroundMode.STEREO -> stringResource(R.string.settings_surround_stereo_description)
                SurroundMode.SURROUND -> stringResource(R.string.settings_surround_forced_description)
            },
            chip = when (surroundMode) {
                SurroundMode.AUTO -> stringResource(R.string.settings_auto)
                SurroundMode.STEREO -> stringResource(R.string.settings_surround_stereo)
                SurroundMode.SURROUND -> stringResource(R.string.settings_surround_sound)
            },
            chipTone = if (surroundMode == SurroundMode.STEREO) TileTone.SECONDARY else TileTone.PRIMARY,
            onClick = { settingsVm.cycleSurroundMode() },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SKIP_NEXT,
            title = stringResource(R.string.settings_autoplay_next),
            desc = stringResource(R.string.settings_autoplay_next_description),
            chip = if (autoPlayNext) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAutoPlayNext(!autoPlayNext) },
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_video_player), desc = stringResource(R.string.settings_video_player_description),
            onClick = { open(SettingsTab.VIDEO) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.VIDEO)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_playback_error_log), desc = stringResource(R.string.settings_playback_error_description),
            onClick = { savedScroll = scrollState.value; dialogReturn = errorLogRowFocus; showErrorLog = true }, showChevron = true,
            modifier = Modifier.focusRequester(errorLogRowFocus),
        )

        SectionDivider()
        GroupLabel(stringResource(R.string.settings_network_group))
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SHARE,
            title = stringResource(R.string.common_proxy), desc = stringResource(R.string.settings_proxy_description),
            onClick = { open(SettingsTab.NETWORK) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.NETWORK)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SEARCH,
            title = stringResource(R.string.settings_dns),
            desc = stringResource(R.string.settings_dns_description),
            onClick = { open(SettingsTab.DNS) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.DNS)),
        )

        SectionDivider()
        GroupLabel(stringResource(R.string.settings_app_group))
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HOME,
            title = stringResource(R.string.settings_app_startup), desc = stringResource(R.string.settings_app_startup_description),
            chip = startupLabel(startupMode), chipTone = TileTone.PRIMARY,
            onClick = { savedScroll = scrollState.value; dialogReturn = startupRowFocus; showStartup = true }, showChevron = true,
            modifier = Modifier.focusRequester(startupRowFocus),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.settings_check_updates), desc = stringResource(R.string.settings_check_updates_description),
            chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}",
            onClick = { savedScroll = scrollState.value; dialogReturn = updateRowFocus; showUpdate = true }, showChevron = true,
            modifier = Modifier.focusRequester(updateRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_update_startup), desc = stringResource(R.string.settings_update_startup_description),
            chip = if (updateCheckOnStart) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.MENU,
            title = stringResource(R.string.settings_about), desc = stringResource(R.string.settings_about_description),
            onClick = { savedScroll = scrollState.value; dialogReturn = aboutRowFocus; showAbout = true }, showChevron = true,
            modifier = Modifier.focusRequester(aboutRowFocus),
        )
        } else {
            // Batch 4 · search results — flat, group-context-prefixed rows ("Playback › HDR").
            // Dialog-opening entries return focus to the search field on close (their normal row
            // isn't composed while searching). Toggle entries keep the results visible so the chip
            // updates live.
            val entries = listOfNotNull(
                SettingsSearchEntry(stringResource(R.string.settings_appearance_group), stringResource(R.string.settings_language), stringResource(R.string.settings_search_keywords_language), OwnTVIcon.LANGUAGE, TileTone.PRIMARY,
                    chip = languageChip, chipTone = TileTone.PRIMARY) { open(SettingsTab.LANGUAGE) },
                SettingsSearchEntry(stringResource(R.string.settings_group_profile), stringResource(R.string.profiles_title), stringResource(R.string.settings_search_keywords_profiles), OwnTVIcon.PERSON, TileTone.SECONDARY) { open(SettingsTab.PROFILES) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_playlists), stringResource(R.string.settings_search_keywords_playlists), OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { open(SettingsTab.SOURCES) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_epg_sources), stringResource(R.string.settings_search_keywords_epg), OwnTVIcon.EPG, TileTone.PRIMARY) { open(SettingsTab.EPG) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.content_epg_time_offset), stringResource(R.string.settings_search_keywords_epg_offset), OwnTVIcon.EPG, TileTone.SECONDARY,
                    chip = epgShiftLabel(epgOffset), chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showEpgOffset = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_search_guide_logos), stringResource(R.string.settings_search_keywords_logos), OwnTVIcon.EPG, TileTone.SECONDARY) { open(SettingsTab.EPG) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_customize), stringResource(R.string.settings_search_keywords_customize), OwnTVIcon.SORT, TileTone.PRIMARY) { open(SettingsTab.CUSTOMIZE) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_sidebar_customization), stringResource(R.string.settings_search_keywords_sidebar), OwnTVIcon.MENU, TileTone.PRIMARY,
                    chip = navModeLabel(navMenuMode), chipTone = if (navMenuMode == tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.NAV_MENU) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_ch_paging), stringResource(R.string.settings_search_keywords_ch), OwnTVIcon.PLAYLIST, TileTone.PRIMARY,
                    chip = if (chNavEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_panel_width), stringResource(R.string.settings_search_keywords_panel_width), OwnTVIcon.ZOOM, TileTone.PRIMARY,
                    chip = if (panelWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default), chipTone = if (panelWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.PANEL_WIDTH) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_browsing_lists), stringResource(R.string.settings_search_keywords_browsing), OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { savedScroll = scrollState.value; dialogReturn = browsingRowFocus; showBrowsing = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_home_root), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_metadata), stringResource(R.string.settings_search_keywords_metadata), OwnTVIcon.VIDEO, TileTone.PRIMARY) { open(SettingsTab.METADATA) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_download_folder), stringResource(R.string.settings_search_keywords_download), OwnTVIcon.DOWNLOADS, TileTone.TERTIARY,
                    chip = downloadRoot.ifBlank { stringResource(R.string.settings_app_storage) }.let { java.io.File(it).name.ifBlank { it } }, chipTone = TileTone.TERTIARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showFolderPicker = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_backup_restore), stringResource(R.string.settings_search_keywords_backup), OwnTVIcon.DOWNLOADS, TileTone.TERTIARY) { open(SettingsTab.BACKUP) },
                SettingsSearchEntry(stringResource(R.string.settings_group_content), stringResource(R.string.settings_clear_history), stringResource(R.string.settings_search_keywords_history), OwnTVIcon.HISTORY, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showClearHistory = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_theme), stringResource(R.string.settings_search_keywords_theme), OwnTVIcon.THEME, TileTone.PRIMARY,
                    chip = themeLabel(themeMode)) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showTheme = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_accent), stringResource(R.string.settings_search_keywords_accent), OwnTVIcon.PALETTE, TileTone.SECONDARY,
                    chip = if (customAccent.isNotBlank()) customAccent.uppercase() else stringResource(accent.labelRes), chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAccent = true },
                if (!glassOn) SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_ambient_glow), stringResource(R.string.settings_ambient_glow_description), OwnTVIcon.PALETTE, TileTone.PRIMARY,
                    chip = stringResource(if (ambientGlowEnabled) R.string.common_on else R.string.common_off), chipTone = if (ambientGlowEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAmbientGlow = true } else null,
                SettingsSearchEntry(
                    stringResource(R.string.settings_group_appearance),
                    stringResource(R.string.settings_font_customization),
                    stringResource(R.string.settings_search_keywords_fonts),
                    OwnTVIcon.PALETTE,
                    TileTone.SECONDARY,
                    chip = stringResource(R.string.common_percent, fontCustomization.sizePercent),
                    chipTone = TileTone.SECONDARY,
                ) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showFontCustomization = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_ui_zoom), stringResource(R.string.settings_search_keywords_zoom), OwnTVIcon.ZOOM, TileTone.SECONDARY,
                    chip = stringResource(R.string.common_percent, uiZoomPercent), chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showZoom = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_animations), stringResource(R.string.settings_search_keywords_animation), OwnTVIcon.THEME, TileTone.SECONDARY,
                    chip = stringResource(animationLevel.labelRes), chipTone = TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAnimations = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_weather), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.EPG, TileTone.SECONDARY,
                    chip = if (weatherEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_quick_live_preview), stringResource(R.string.settings_search_keywords_live_preview), OwnTVIcon.LIVE_TV, TileTone.TERTIARY,
                    chip = if (livePreview) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleLivePreview(searchFieldFocus) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_preview_audio), stringResource(R.string.settings_search_keywords_sound), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                    chip = if (previewAudio) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setLivePreviewAudio(!previewAudio) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_quick_channel_numbers), stringResource(R.string.settings_search_keywords_channel_numbers), OwnTVIcon.LIVE_TV, TileTone.PRIMARY,
                    chip = if (channelNumbers) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (channelNumbers) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setDirectTune(!channelNumbers) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_mini_player_root), stringResource(R.string.settings_search_keywords_mini), OwnTVIcon.PIP, TileTone.TERTIARY) { open(SettingsTab.MINI_PLAYER) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_quick_hdr), stringResource(R.string.settings_search_keywords_hdr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                    chip = if (hdr) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setHdrEnabled(!hdr) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_auto_frame_rate), stringResource(R.string.settings_search_keywords_afr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                    chip = if (autoFrameRate) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleAutoFrameRate(searchFieldFocus) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_surround_sound), stringResource(R.string.settings_search_keywords_surround), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                    chip = surroundModeLabel(surroundMode), chipTone = if (surroundMode == SurroundMode.STEREO) TileTone.SECONDARY else TileTone.PRIMARY, showChevron = false) { settingsVm.cycleSurroundMode() },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_autoplay_next), stringResource(R.string.settings_search_keywords_autoplay), OwnTVIcon.SKIP_NEXT, TileTone.SECONDARY,
                    chip = if (autoPlayNext) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setAutoPlayNext(!autoPlayNext) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_catchup), stringResource(R.string.settings_search_keywords_catchup), OwnTVIcon.EPG, TileTone.SECONDARY,
                    chip = when (catchupTz) {
                        SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                        SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
                    }) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showCatchupTime = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_video_player), stringResource(R.string.settings_search_keywords_video), OwnTVIcon.VIDEO, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_subtitle_appearance), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_live_latency), stringResource(R.string.settings_search_keywords_latency), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_live_preroll), stringResource(R.string.settings_search_keywords_live_preroll), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_playback_error_log), stringResource(R.string.settings_search_keywords_errors), OwnTVIcon.HISTORY, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showErrorLog = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_detailed_playback_logging), stringResource(R.string.settings_search_keywords_detailed_logging), OwnTVIcon.INFO, TileTone.SECONDARY) { open(SettingsTab.VIDEO) },
                SettingsSearchEntry(stringResource(R.string.settings_group_network), stringResource(R.string.common_proxy), stringResource(R.string.settings_search_keywords_proxy), OwnTVIcon.SHARE, TileTone.SECONDARY) { open(SettingsTab.NETWORK) },
                SettingsSearchEntry(stringResource(R.string.settings_group_network), stringResource(R.string.settings_dns), stringResource(R.string.settings_search_keywords_dns), OwnTVIcon.SEARCH, TileTone.SECONDARY) { open(SettingsTab.DNS) },
                SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_app_startup), stringResource(R.string.settings_search_keywords_startup), OwnTVIcon.HOME, TileTone.SECONDARY,
                    chip = startupLabel(startupMode)) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showStartup = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_check_updates), stringResource(R.string.settings_search_keywords_updates), OwnTVIcon.DOWNLOADS, TileTone.PRIMARY,
                    chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}") { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showUpdate = true },
                SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_update_startup), stringResource(R.string.settings_search_keywords_update_auto), OwnTVIcon.HISTORY, TileTone.SECONDARY,
                    chip = if (updateCheckOnStart) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
                SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_about), stringResource(R.string.settings_search_keywords_about), OwnTVIcon.MENU, TileTone.SECONDARY) { savedScroll = scrollState.value; dialogReturn = searchFieldFocus; showAbout = true },
            )
            val tokens = searchQuery.trim().lowercase().split(" ").filter { it.isNotBlank() }
            val results = entries.filter { e -> tokens.all { t -> e.haystack.contains(t) } }
            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_settings_match, searchQuery.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                results.forEach { e ->
                    SettingsRow(
                        tone = e.tone, icon = e.icon,
                        title = stringResource(R.string.settings_breadcrumb, e.group, e.title),
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
    if (showEpgOffset) {
        EpgOffsetSettingDialog(
            offsetMinutes = epgOffset,
            offsetRange = settingsVm.epgOffsetRangeMinutes,
            onAdjust = settingsVm::adjustEpgOffset,
            onReset = { settingsVm.setEpgOffsetMinutes(0) },
            onDismiss = { showEpgOffset = false },
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
            title = stringResource(R.string.settings_theme_dialog),
            options = ThemeMode.entries.map { it.name to themeLabel(it) },
            selected = themeMode.name,
            onSelect = { settingsVm.setThemeMode(ThemeMode.valueOf(it)); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showStartup) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.settings_app_startup_dialog),
            options = tv.own.owntv.features.settings.data.StartupMode.entries.map { it.name to startupLabel(it) },
            selected = startupMode.name,
            onSelect = { settingsVm.setStartupMode(tv.own.owntv.features.settings.data.StartupMode.valueOf(it)); showStartup = false },
            onDismiss = { showStartup = false },
        )
    }
    if (showAnimations) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.settings_animations_dialog),
            options = tv.own.owntv.ui.theme.AnimationLevel.entries.map { it.name to stringResource(it.labelRes) },
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
    if (showFontCustomization) {
        FontCustomizationDialog(
            current = fontCustomization,
            onApply = {
                onSetFontCustomization(it)
                showFontCustomization = false
            },
            onDismiss = { showFontCustomization = false },
        )
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
            preset = glassConfig.preset,
            alphaPercent = (glassConfig.alpha * 100).roundToInt(),
            highlightPercent = (glassConfig.highlightStrength * 100).roundToInt(),
            allowFullTransparency = glassConfig.allowFullTransparency,
            depthEffects = glassConfig.depthEffects,
            bgOn = bgImagePath.isNotBlank(),
            onToggleGlass = {
                val on = glassConfig.enabled
                settingsVm.setGlassScopeBitmask(if (on) 0 else GlassConfig(ALL_GLASS_SURFACES).toBitmask())
            },
            onSetPreset = settingsVm::setGlassPreset,
            onSetAlpha = { settingsVm.setGlassAlphaPercent(it, (glassConfig.blurStrength * 100).roundToInt()) },
            blurPercent = (glassConfig.blurStrength * 100).roundToInt(),
            onSetBlur = { settingsVm.setGlassBlurPercent(it, (glassConfig.alpha * 100).roundToInt()) },
            onSetHighlight = { settingsVm.setGlassHighlightPercent(it) },
            onSetAllowFullTransparency = settingsVm::setGlassAllowFullTransparency,
            onSetDepthEffects = settingsVm::setGlassDepthEffects,
            scope = glassConfig.scope,
            onSetScope = { settingsVm.setGlassScopeBitmask(it) },
            // Hand off to the existing background-image chooser; on close it returns to the Glass Effect row.
            onOpenBackground = { showGlassEffect = false; dialogReturn = glassEffectRowFocus; showBgImageChooser = true },
            onDismiss = { showGlassEffect = false },
        )
    }
    if (showAmbientGlow) {
        AmbientGlowDialog(
            glowEnabled = ambientGlowEnabled,
            pulseEnabled = ambientGlowPulse,
            onToggleGlow = { settingsVm.setAmbientGlowEnabled(!ambientGlowEnabled) },
            onTogglePulse = { settingsVm.setAmbientGlowPulse(!ambientGlowPulse) },
            onDismiss = { showAmbientGlow = false },
        )
    }
    if (showErrorLog) {
        PlaybackErrorLogDialog(onDismiss = { showErrorLog = false })
    }
    if (showAfrWarning) {
        AutoFrameRateWarningDialog(
            onEnable = { settingsVm.setAutoFrameRate(true); showAfrWarning = false },
            onDismiss = { showAfrWarning = false },
        )
    }
    if (showLivePreviewPanelWarning) {
        LivePreviewPanelHiddenDialog(onDismiss = { showLivePreviewPanelWarning = false })
    }
    if (showFolderPicker) {
        StorageBrowser(
            title = stringResource(R.string.settings_download_folder_title),
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
            title = stringResource(R.string.settings_pick_background_title),
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

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.SYSTEM -> R.string.settings_theme_system
    },
)

@Composable
private fun startupLabel(mode: tv.own.owntv.features.settings.data.StartupMode): String = stringResource(
    when (mode) {
        tv.own.owntv.features.settings.data.StartupMode.HOME -> R.string.settings_startup_home
        tv.own.owntv.features.settings.data.StartupMode.LAST_CHANNEL -> R.string.settings_startup_last_channel
        tv.own.owntv.features.settings.data.StartupMode.FAVORITES -> R.string.settings_startup_favorites
    },
)

@Composable
private fun navModeLabel(mode: tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode): String = stringResource(
    if (mode == tv.own.owntv.features.settings.data.SettingsRepository.NavMenuMode.DYNAMIC) R.string.settings_dynamic else R.string.settings_static,
)

/** Chip text for the Language settings row: system-default label, or the selected locale's endonym. */
@Composable
private fun languageChipText(tag: String): String {
    if (tag.isEmpty()) return stringResource(R.string.settings_language_system_default)
    return SupportedLocales.all.find { it.languageTag == tag }?.endonym
        ?: stringResource(R.string.settings_language_system_default)
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

    // PopupFontTheme swaps in the selected popup family and applies the shared popup type scale.
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().imePadding().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // dialogPanel already applies verticalScroll; imePadding on the parent Box lifts the
            // whole panel above the on-screen keyboard so the hex field stays visible.
            modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp),
        ) {
            Text(stringResource(R.string.settings_accent_dialog), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
            Text(stringResource(R.string.settings_hex_code), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // Kept above the picker on purpose: the on-screen keyboard covers the lower half of the
            // screen, so the hex field must sit high enough to stay visible while the user types.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                tv.own.owntv.ui.components.OwnTVTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(6); hexError = false },
                    label = stringResource(R.string.settings_hex),
                    placeholder = "52DBC8",
                    modifier = Modifier.width(200.dp),
                )
                OwnTVButton(stringResource(R.string.settings_apply), onClick = {
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
                Text(stringResource(R.string.settings_hex_error), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_color_picker), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
                OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_use_color), onClick = { onPickCustom(pickedHex); onDismiss() })
            }
        }
    }
    }
}


// Fork: this build's own source and releases. It sits next to the GPL line, so it should point at the
// source of THIS binary (which carries upstream's history in full) rather than at upstream's repo,
// and it matches where the in-app updater now looks. See UpdateManager.REPO.
private const val GITHUB_REPO = "github.com/attembot/OwnTV"
private const val TELEGRAM_LINK = "t.me/owntvplayer"

/** About OwnTV: version, license, author and project link — all readable on screen (no TV browser). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLockup(markSize = 48, textSize = 30)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_about_version, tv.own.owntv.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.settings_about_description_full),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.settings_about_license), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(GITHUB_REPO, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            // Community: Telegram link + a QR, side-by-side to keep the dialog compact, so TV users can
            // join from their phone — no TV browser needed.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_join_telegram), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(TELEGRAM_LINK, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_telegram_scan),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.telegram_qr),
                        contentDescription = stringResource(R.string.settings_telegram_qr),
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_contributions),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
        }
    }
}

/**
 * Read-only viewer for the persisted playback error history (B5): the last ~10 failures with their
 * plain-English reason, media spec, raw engine text, engine, stream type and device info — so users
 * who can't pull logcat can read/report what happened after dismissing the error screen.
 */
@Composable
private fun String.playbackDisplayName(): String = when (trim().lowercase(java.util.Locale.ROOT)) {
    "mpv" -> stringResource(R.string.settings_player_mpv)
    "exoplayer", "exo" -> stringResource(R.string.settings_player_exoplayer)
    else -> this
}

@Composable
private fun PlaybackErrorLogDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val entries by androidx.compose.runtime.produceState<List<tv.own.owntv.player.PlaybackErrorLog.Entry>?>(initialValue = null, refresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            tv.own.owntv.player.PlaybackErrorLog.read(context)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entries) { if (entries != null) runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val dateContext = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // scroll = false: the entries live in a LazyColumn, which manages its own scrolling. A plain
        // verticalScroll column can't work here — with 25 entries and nothing focusable inside them the
        // panel grew past the screen and the D-pad had no way to move the scroll, so the oldest entries
        // were simply unreachable.
        Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp, scroll = false)) {
            Text(stringResource(R.string.settings_playback_error_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_playback_error_description_full),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val list = entries
            when {
                list == null -> Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                list.isEmpty() -> Text(stringResource(R.string.settings_no_playback_errors), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                // Each entry is focusable even though there is nothing to activate: on a TV that is the
                // only thing that makes a list scroll. Up from the buttons walks back through the history.
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list) { e ->
                        FocusableSurface(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                            surface = GlassSurface.DIALOGS,
                        ) { _ ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                // The kind matters at a glance now: a log full of "Event" lines next to one
                                // "Error" tells a very different story from ten failures in a row.
                                val kindLabel = when (e.kind) {
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.ERROR -> stringResource(R.string.settings_playback_kind_error)
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.EVENT -> stringResource(R.string.settings_playback_kind_event)
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.REPORT -> stringResource(R.string.settings_playback_kind_report)
                                }
                                Text(
                                    stringResource(
                                        R.string.settings_playback_entry_with_kind,
                                        formatBestDateTime(dateContext, "dMMM", e.atMs),
                                        kindLabel,
                                        e.engine.playbackDisplayName(),
                                        stringResource(if (e.live) R.string.settings_live else R.string.settings_vod),
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (e.kind == tv.own.owntv.player.PlaybackErrorLog.Kind.ERROR) colors.primary else colors.onSurfaceVariant,
                                )
                                val reasonText = e.reason?.displayText() ?: e.legacyReason
                                reasonText?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                                }
                                e.mediaSpec()?.let { spec ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(spec.displayText(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                } ?: e.spec?.let { legacySpec ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(legacySpec, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                e.raw?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(R.string.settings_device_details, e.model, e.android), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            exportPath?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_backup_saved_to, it), style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
            if (exportFailed) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_backup_export_error), style = MaterialTheme.typography.bodySmall, color = colors.favorite)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Export also includes the live diagnostics ring, so keep it available when the visible
                // error list is empty; an engine handoff can leave useful diagnostics without an entry.
                OwnTVButton(stringResource(R.string.settings_export), onClick = {
                    scope.launch {
                        val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            tv.own.owntv.player.PlaybackErrorLog.export(context)
                        }
                        exportPath = path
                        exportFailed = path == null
                    }
                }, style = OwnTVButtonStyle.SECONDARY)
                if (!entries.isNullOrEmpty()) {
                    OwnTVButton(stringResource(R.string.settings_clear_log), onClick = {
                        tv.own.owntv.player.PlaybackErrorLog.clear(context)
                        exportPath = null
                        exportFailed = false
                        refresh++
                    }, style = OwnTVButtonStyle.SECONDARY)
                }
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}

/**
 * Warn before enabling Auto frame rate below Android 12, where smooth refresh-rate alternatives cannot
 * be queried and a mode switch can trigger a visible HDMI re-handshake.
 */
@Composable
private fun AutoFrameRateWarningDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(
                stringResource(R.string.settings_auto_frame_rate_warning_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.settings_auto_frame_rate_warning_description,
                    android.os.Build.VERSION.RELEASE,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.settings_auto_frame_rate_keep_off),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.settings_auto_frame_rate_turn_on_anyway),
                    onClick = onEnable,
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun LivePreviewPanelHiddenDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(
                stringResource(R.string.settings_live_preview_panel_hidden_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_live_preview_panel_hidden_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.common_ok),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
    }
}

/** Stable, non-display choices for the history picker. */
private enum class HistoryScope(val type: tv.own.owntv.core.model.MediaType?, val labelRes: Int) {
    LIVE(tv.own.owntv.core.model.MediaType.LIVE, R.string.settings_history_live),
    MOVIES(tv.own.owntv.core.model.MediaType.MOVIE, R.string.settings_history_movies),
    SERIES(tv.own.owntv.core.model.MediaType.SERIES, R.string.settings_history_series),
    ALL(null, R.string.settings_history_all),
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
    var pending by remember { mutableStateOf<HistoryScope?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pending != null) pending = null else onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = pending
            if (p == null) {
                Text(stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_choose_history),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                Spacer(Modifier.height(10.dp))
                OwnTVButton(stringResource(R.string.settings_history_live), onClick = { pending = HistoryScope.LIVE }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_history_movies), onClick = { pending = HistoryScope.MOVIES }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_history_series), onClick = { pending = HistoryScope.SERIES }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_all_history), onClick = { pending = HistoryScope.ALL }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.settings_clear_history_confirm, stringResource(p.labelRes)), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_cannot_undo), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OwnTVButton(stringResource(R.string.settings_no), onClick = { pending = null }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus))
                    OwnTVButton(stringResource(R.string.settings_yes_clear), onClick = { onClear(p.type) })
                }
            }
        }
    }
}

private enum class FontPickerTarget { MAIN, POPUP }

@Composable
private fun fontFamilyLabel(family: AppFontFamily): String = stringResource(
    when (family) {
        AppFontFamily.LORA -> R.string.settings_font_lora
        AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
        AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
        AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
        AppFontFamily.POPPINS -> R.string.settings_font_poppins
    },
)

/** Staged font editor: Back cancels; Apply commits size + both families atomically. */
@Composable
private fun FontCustomizationDialog(
    current: FontCustomization,
    onApply: (FontCustomization) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var draft by remember(current) { mutableStateOf(current) }
    var picker by remember { mutableStateOf<FontPickerTarget?>(null) }
    var pickerReturn by remember { mutableStateOf<FontPickerTarget?>(null) }
    val firstFocus = remember { FocusRequester() }
    val mainFocus = remember { FocusRequester() }
    val popupFocus = remember { FocusRequester() }

    LaunchedEffect(picker) {
        if (picker == null) {
            val target = when (pickerReturn) {
                FontPickerTarget.MAIN -> mainFocus
                FontPickerTarget.POPUP -> popupFocus
                null -> firstFocus
            }
            kotlinx.coroutines.delay(50)
            runCatching { target.requestFocus() }
        }
    }
    BackHandler {
        if (picker != null) picker = null else onDismiss()
    }

    if (picker == null) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .dialogPanel(width = 600.dp, padding = 28.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.settings_font_customization),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_font_size_range, UiFontScale.MIN, UiFontScale.MAX),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.settings_font_size),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(
                        stringResource(R.string.settings_decrease),
                        dimmed = draft.sizePercent <= UiFontScale.MIN,
                        modifier = Modifier.focusRequester(firstFocus),
                    ) {
                        draft = draft.copy(sizePercent = UiFontScale.clamp(draft.sizePercent - UiFontScale.STEP))
                    }
                    Text(
                        stringResource(R.string.common_percent, draft.sizePercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = TextAlign.Center,
                    )
                    StepButton(
                        stringResource(R.string.settings_increase),
                        dimmed = draft.sizePercent >= UiFontScale.MAX,
                    ) {
                        draft = draft.copy(sizePercent = UiFontScale.clamp(draft.sizePercent + UiFontScale.STEP))
                    }
                }
                Spacer(Modifier.height(20.dp))
                FontChoiceRow(
                    title = stringResource(R.string.settings_main_interface_font),
                    family = draft.mainFamily,
                    modifier = Modifier.focusRequester(mainFocus),
                ) {
                    pickerReturn = FontPickerTarget.MAIN
                    picker = FontPickerTarget.MAIN
                }
                Spacer(Modifier.height(10.dp))
                FontChoiceRow(
                    title = stringResource(R.string.settings_popup_font),
                    family = draft.popupFamily,
                    modifier = Modifier.focusRequester(popupFocus),
                ) {
                    pickerReturn = FontPickerTarget.POPUP
                    picker = FontPickerTarget.POPUP
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(
                        stringResource(R.string.settings_reset),
                        onClick = { draft = FontCustomization() },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.settings_apply), onClick = { onApply(draft) })
                }
            }
        }
    } else {
        val target = picker ?: return
        FontFamilyPickerDialog(
            title = stringResource(
                if (target == FontPickerTarget.MAIN) R.string.settings_main_interface_font
                else R.string.settings_popup_font,
            ),
            selected = if (target == FontPickerTarget.MAIN) draft.mainFamily else draft.popupFamily,
            onSelect = { family ->
                draft = if (target == FontPickerTarget.MAIN) draft.copy(mainFamily = family)
                else draft.copy(popupFamily = family)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun FontChoiceRow(
    title: String,
    family: AppFontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 70.dp),
        shape = RoundedCornerShape(16.dp),
        surface = GlassSurface.DIALOGS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(
                fontFamilyLabel(family),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family.asComposeFamily()),
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text("›", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun FontFamilyPickerDialog(
    title: String,
    selected: AppFontFamily,
    onSelect: (AppFontFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { AppFontFamily.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { focus.getValue(selected).requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .dialogPanel(width = 640.dp, padding = 24.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_choose_font), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            AppFontFamily.entries.forEach { family ->
                FocusableSurface(
                    onClick = { onSelect(family) },
                    selected = family == selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .focusRequester(focus.getValue(family)),
                    shape = RoundedCornerShape(14.dp),
                    surface = GlassSurface.DIALOGS,
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(
                            fontFamilyLabel(family),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = family.asComposeFamily()),
                            color = if (family == selected) colors.primary else colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.settings_font_preview),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family.asComposeFamily()),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_ui_zoom), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_ui_zoom_range, UiZoom.MIN, UiZoom.MAX),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Initial focus lands on the DECREASE button: the dialog is most often opened to escape an
                // over-zoomed screen (where everything's too big to navigate), so "–" must be first under
                // the cursor. The buttons stay focusable at the limits (clamped + dimmed, never disabled)
                // so focus always lands inside the dialog — a disabled "+" at MAX zoom was leaving focus
                // stranded outside, trapping the user at high zoom.
                StepButton(stringResource(R.string.settings_decrease), dimmed = current <= UiZoom.MIN, modifier = Modifier.focusRequester(firstFocus)) {
                    val next = UiZoom.clamp(current - UiZoom.STEP)
                    if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else onSet(next)
                }
                Text(
                    stringResource(R.string.common_percent, current),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                    modifier = Modifier.width(120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(stringResource(R.string.settings_increase), dimmed = current >= UiZoom.MAX) {
                    onSet(UiZoom.clamp(current + UiZoom.STEP))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.settings_reset), onClick = { onSet(UiZoom.DEFAULT) }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss)
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
                modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.settings_low_zoom_warning_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OwnTVButton(
                        stringResource(R.string.settings_low_zoom_accept),
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
                                start = FocusRequester.Cancel
                                end = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
    }
}

/** Solid-interface radiance controls, kept together in one compact TV-safe popup. */
@Composable
private fun AmbientGlowDialog(
    glowEnabled: Boolean,
    pulseEnabled: Boolean,
    onToggleGlow: () -> Unit,
    onTogglePulse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Column(
                modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.settings_ambient_glow), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_ambient_glow_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton(
                    stringResource(
                        R.string.settings_section_toggle,
                        stringResource(R.string.settings_ambient_glow_effect),
                        stringResource(if (glowEnabled) R.string.common_on else R.string.common_off),
                    ),
                    onClick = onToggleGlow,
                    style = if (glowEnabled) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    icon = OwnTVIcon.PALETTE,
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                )
                if (glowEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        stringResource(
                            R.string.settings_section_toggle,
                            stringResource(R.string.settings_ambient_glow_pulse),
                            stringResource(if (pulseEnabled) R.string.common_on else R.string.common_off),
                        ),
                        onClick = onTogglePulse,
                        style = if (pulseEnabled) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                        icon = OwnTVIcon.THEME,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * A stepper for the Glass effect fill strength — how opaque the translucent panels are over the
 * background photo. Higher = more solid (less see-through). Changes apply live. Range 20–95% in 5%
 * steps so panels can never go fully transparent (text would be unreadable) or fully solid (pointless).
 */
@Composable
private fun GlassEffectDialog(
    glassOn: Boolean,
    preset: GlassPreset,
    alphaPercent: Int,
    blurPercent: Int,
    highlightPercent: Int,
    allowFullTransparency: Boolean,
    depthEffects: Boolean,
    bgOn: Boolean,
    scope: Set<GlassSurface>,
    onToggleGlass: () -> Unit,
    onSetPreset: (GlassPreset) -> Unit,
    onSetAlpha: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetHighlight: (Int) -> Unit,
    onSetAllowFullTransparency: (Boolean) -> Unit,
    onSetDepthEffects: (Boolean) -> Unit,
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
    val max = 100
    val step = 5
    fun clamp(v: Int) = v.coerceIn(min, max)
    // Backdrop blur ("frost") stepper — 0..100 in 10% steps. 0 keeps the Tier-1 translucency-only look;
    // only has an effect when a background image is set and the device supports it (API 31+).
    val blurMin = 0
    val blurMax = 100
    val blurStep = 10
    fun blurClamp(v: Int) = v.coerceIn(blurMin, blurMax)
    val highlightStep = 5
    fun highlightClamp(v: Int) = v.coerceIn(0, 100)
    BackHandler { onDismiss() }
    if (!showSurfaces) Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // Shared user-selected popup font, matching the app's other dialogs.
        tv.own.owntv.ui.theme.PopupFontTheme {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_glass_effect_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_glass_effect_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Master on/off for the glass (works with or without a background image).
            OwnTVButton(
                if (glassOn) stringResource(R.string.settings_glass_effect_on) else stringResource(R.string.settings_glass_effect_off),
                onClick = onToggleGlass,
                style = if (glassOn) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.THEME,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            if (glassOn) {
                Spacer(Modifier.height(12.dp))
                // Wallpaper belongs to Glass mode and stays hidden until Glass is enabled.
                OwnTVButton(
                    if (bgOn) stringResource(R.string.settings_background_on) else stringResource(R.string.settings_background_off),
                    onClick = onOpenBackground,
                    style = OwnTVButtonStyle.SECONDARY,
                    icon = OwnTVIcon.IMAGE,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.settings_glass_preset_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    glassPresetDescription(preset),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                GlassPreset.entries.chunked(2).forEach { rowPresets ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowPresets.forEach { choice ->
                            OwnTVButton(
                                label = glassPresetLabel(choice),
                                onClick = { onSetPreset(choice) },
                                style = OwnTVButtonStyle.SECONDARY,
                                selected = preset == choice,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_transparency_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_transparency_description, min, max),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = alphaPercent <= min) { onSetAlpha(clamp(alphaPercent - step)) }
                    Text(
                        stringResource(R.string.settings_surface_transparency, alphaPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = alphaPercent >= max) { onSetAlpha(clamp(alphaPercent + step)) }
                }
                // Backdrop blur — the real "frost" behind the panels (needs a background image; API 31+).
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.settings_blur_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (bgOn) R.string.settings_blur_description_enabled else R.string.settings_blur_description_disabled, blurMin, blurMax),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = blurPercent <= blurMin) { onSetBlur(blurClamp(blurPercent - blurStep)) }
                    Text(
                        stringResource(R.string.settings_surface_transparency, blurPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = blurPercent >= blurMax) { onSetBlur(blurClamp(blurPercent + blurStep)) }
                }

                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.settings_glass_highlight_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_glass_highlight_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = highlightPercent <= 0) {
                        onSetHighlight(highlightClamp(highlightPercent - highlightStep))
                    }
                    Text(
                        stringResource(R.string.settings_surface_transparency, highlightPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = highlightPercent >= 100) {
                        onSetHighlight(highlightClamp(highlightPercent + highlightStep))
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.settings_glass_full_transparency_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OwnTVButton(
                    label = "${stringResource(R.string.settings_glass_full_transparency_title)}: ${stringResource(if (allowFullTransparency) R.string.common_on else R.string.common_off)}",
                    onClick = { onSetAllowFullTransparency(!allowFullTransparency) },
                    style = OwnTVButtonStyle.SECONDARY,
                    selected = allowFullTransparency,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.settings_glass_depth_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OwnTVButton(
                    label = "${stringResource(R.string.settings_glass_depth_title)}: ${stringResource(if (depthEffects) R.string.common_on else R.string.common_off)}",
                    onClick = { onSetDepthEffects(!depthEffects) },
                    style = OwnTVButtonStyle.SECONDARY,
                    selected = depthEffects,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.settings_glass_live_preview), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Display-only samples: a row, a focused card, and a focused chrome chip.
                    Box(
                        Modifier.weight(1.35f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).glass(
                            surface = GlassSurface.PANELS,
                            baseFill = colors.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                            interaction = GlassInteraction.SELECTED,
                        ),
                    )
                    Box(
                        Modifier.weight(0.8f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).glass(
                            surface = GlassSurface.CARDS,
                            baseFill = colors.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                            interaction = GlassInteraction.FOCUSED,
                        ),
                    )
                    Box(
                        Modifier.weight(0.85f).height(30.dp).clip(RoundedCornerShape(15.dp)).glass(
                            surface = GlassSurface.TOPBAR,
                            baseFill = colors.surfaceContainerHighest,
                            shape = RoundedCornerShape(15.dp),
                            interaction = GlassInteraction.FOCUSED,
                        ),
                    )
                }
                // Advanced: choose exactly which surfaces render as glass.
                Spacer(Modifier.height(16.dp))
                OwnTVButton(
                    if (scope == ALL_GLASS_SURFACES) stringResource(R.string.settings_surface_count_all)
                    else pluralStringResource(R.plurals.settings_surface_count, scope.size, scope.size, ALL_GLASS_SURFACES.size),
                    onClick = { showSurfaces = true },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (glassOn) OwnTVButton(
                    stringResource(R.string.settings_reset),
                    onClick = {
                        onSetPreset(GlassPreset.BALANCED)
                        onSetHighlight((GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH * 100).roundToInt())
                        onSetAllowFullTransparency(false)
                        onSetDepthEffects(true)
                        onSetScope(GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss)
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
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        // Six toggles + two group headers overflow a 720p panel — dialogPanel already scrolls the body
        // (scroll = true by default), so do NOT add another verticalScroll here.
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_browsing_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_browsing_description_full),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            // SECONDARY chrome on every row (matching GlassSurfacesDialog): with an accent fill on each
            // "On" row the focused row becomes hard to pick out on a TV. State reads from the ": On/Off"
            // text; focus is carried by the button's own highlight.
            BrowsingGroupLabel(stringResource(R.string.settings_browsing_last_category), stringResource(R.string.settings_browsing_last_category_description))
            OwnTVButton(
                stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_live), stringResource(if (catLive) R.string.common_on else R.string.common_off)), onClick = onToggleCatLive,
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_movies), stringResource(if (catMovies) R.string.common_on else R.string.common_off)), onClick = onToggleCatMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_series), stringResource(if (catSeries) R.string.common_on else R.string.common_off)), onClick = onToggleCatSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))
            BrowsingGroupLabel(
                stringResource(R.string.settings_browsing_last_item),
                stringResource(R.string.settings_browsing_last_item_description),
            )
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_live), stringResource(if (itemLive) R.string.common_on else R.string.common_off)), onClick = onToggleItemLive,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_movies), stringResource(if (itemMovies) R.string.common_on else R.string.common_off)), onClick = onToggleItemMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_series), stringResource(if (itemSeries) R.string.common_on else R.string.common_off)), onClick = onToggleItemSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun glassPresetLabel(preset: GlassPreset): String = stringResource(
    when (preset) {
        GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear
        GlassPreset.CLEAR -> R.string.settings_glass_preset_clear
        GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced
        GlassPreset.TINTED -> R.string.settings_glass_preset_tinted
        GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque
        GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom
    },
)

@Composable
private fun glassPresetDescription(preset: GlassPreset): String = stringResource(
    when (preset) {
        GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear_description
        GlassPreset.CLEAR -> R.string.settings_glass_preset_clear_description
        GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced_description
        GlassPreset.TINTED -> R.string.settings_glass_preset_tinted_description
        GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque_description
        GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom_description
    },
)

/** User-facing label for a glassable surface. */
@Composable
private fun glassSurfaceLabel(s: GlassSurface): String = stringResource(
    when (s) {
        GlassSurface.PANELS -> R.string.settings_glass_surface_panels
        GlassSurface.SIDEBAR -> R.string.settings_glass_surface_sidebar
        GlassSurface.PREVIEW -> R.string.settings_glass_surface_preview
        GlassSurface.DIALOGS -> R.string.settings_glass_surface_dialogs
        GlassSurface.TOPBAR -> R.string.settings_glass_surface_topbar
        GlassSurface.CARDS -> R.string.settings_glass_surface_cards
        GlassSurface.MINI_PLAYER -> R.string.settings_glass_surface_miniplayer
    },
)

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
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        Column(
            modifier = Modifier.dialogPanel(width = 440.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_glass_surfaces), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_glass_surfaces_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // All rows use the SECONDARY chrome: with the accent (PRIMARY) fill on every "On" row the
            // focused row was indistinguishable on TV. State lives in the ": On/Off" text; focus in the
            // button's own focus highlight.
            OwnTVButton(
                if (scope == ALL_GLASS_SURFACES) stringResource(R.string.settings_all_surfaces_on) else stringResource(R.string.settings_all_surfaces_off),
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
                    stringResource(R.string.settings_surface_toggle, glassSurfaceLabel(s), stringResource(if (on) R.string.common_on else R.string.common_off)),
                    onClick = { onSetScope(toggled(s)) },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
        }
    }
}

/** "UTC", "UTC+05:00", "UTC-03:30" — labels a UTC offset (in minutes) for catch-up. */
private fun utcOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "UTC"
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign%02d:%02d".format(Locale.ROOT, abs / 60, abs % 60)
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
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_catchup), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_catchup_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Mode toggle: Device / Manual.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.settings_catchup_timezone_device),
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.DEVICE) },
                    style = if (!manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                OwnTVButton(
                    stringResource(R.string.settings_manual),
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.MANUAL) },
                    style = if (manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }
            if (manual) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Dimmed, never disabled — a disabled button leaves the focus graph and the D-pad
                    // then walks straight out of the dialog.
                    StepButton(stringResource(R.string.settings_decrease), dimmed = offsetMinutes <= offsetRange.first) { onAdjustOffset(-60) }
                    Text(
                        utcOffsetLabel(offsetMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.width(150.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = offsetMinutes >= offsetRange.last) { onAdjustOffset(60) }
                }
            }
            // Which player takes an archive programme. Archives are the streams the in-app engines
            // struggle with most, so an external app is a useful fallback — "Ask" puts the choice on
            // the "Watch from start" action itself instead of forcing one answer forever.
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_catchup_player), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRepository.CatchupPlayer.entries.forEach { p ->
                    OwnTVButton(
                        when (p) {
                            SettingsRepository.CatchupPlayer.ASK -> stringResource(R.string.settings_catchup_player_ask)
                            SettingsRepository.CatchupPlayer.INTERNAL -> stringResource(R.string.settings_catchup_player_internal)
                            SettingsRepository.CatchupPlayer.EXTERNAL -> stringResource(R.string.settings_catchup_player_external)
                        },
                        onClick = { onSetPlayer(p) },
                        style = if (player == p) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                        compact = true,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Global guide shift. Some XMLTV feeds publish in a timezone the channels don't actually air in;
 * this moves every programme by a fixed amount. A per-channel override (channel long-press → EPG
 * time offset) wins over it — that's what a lineup carrying both East and West feeds needs, since
 * one global shift can only ever fix one of the two.
 */
@Composable
private fun EpgOffsetSettingDialog(
    offsetMinutes: Int,
    offsetRange: IntRange,
    onAdjust: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.content_epg_time_offset), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_epg_offset_dialog_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Dimmed, never disabled: a disabled button leaves the focus graph, so reaching a limit
                // used to drop focus out of the dialog entirely. The adjust is clamped anyway.
                StepButton("–", dimmed = offsetMinutes <= offsetRange.first, modifier = Modifier.focusRequester(firstFocus)) { onAdjust(-30) }
                Text(
                    epgShiftLabel(offsetMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary,
                    modifier = Modifier.width(150.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton("+", dimmed = offsetMinutes >= offsetRange.last) { onAdjust(30) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (offsetMinutes != 0) {
                    // Reset removes itself from the row (the offset becomes 0), taking the focused
                    // element with it — so hand focus to Done in the same click.
                    OwnTVButton(
                        stringResource(R.string.common_reset),
                        onClick = { onReset(); runCatching { doneFocus.requestFocus() } },
                        style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.weight(1f),
                    )
                }
                OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss, modifier = Modifier.weight(1f).focusRequester(doneFocus))
            }
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
        // Diagnostic + production-safe scrolling path: a full-width row must not move an aligned
        // backdrop texture inside the scroll container. Focus still gets luminous tint and rim;
        // the static parent panel retains real frost. This also avoids stale HWUI damage trails on
        // affected Android TV GPUs.
        glassFrostScale = 0f,
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
        text = stringResource(R.string.settings_soon),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
                text = if (on) stringResource(R.string.common_on) else stringResource(R.string.common_off),
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
