package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.settings.data.SubtitleStyle
import tv.own.owntv.player.ZoomMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/** Common languages offered for the audio/subtitle preference (code → display name; "" = no preference). */
private val LANGUAGES = listOf(
    "" to "None / Auto",
    "eng" to "English",
    "spa" to "Spanish",
    "fra" to "French",
    "deu" to "German",
    "ita" to "Italian",
    "por" to "Portuguese",
    "nld" to "Dutch",
    "rus" to "Russian",
    "ara" to "Arabic",
    "hin" to "Hindi",
    "zho" to "Chinese",
    "jpn" to "Japanese",
    "kor" to "Korean",
    "tur" to "Turkish",
)

// 1.0 is the renderer's own size, so it is the "Default" entry — see SubtitleStyle.SCALE_DEFAULT.
private val SUB_SIZES = listOf(0.8f to "Small", 1.0f to "Default", 1.3f to "Large", 1.6f to "Extra Large")

private fun langName(code: String) = LANGUAGES.firstOrNull { it.first == code }?.second ?: code.ifBlank { "None / Auto" }
private fun nearestSubSize(scale: Float) = SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) } ?: SUB_SIZES[1]
private fun subSizeName(scale: Float) = nearestSubSize(scale).second

/**
 * Video Player settings — decoder, default aspect/zoom, subtitle size & language, audio sync. Each
 * value is persisted and applied to the shared mpv player (live where possible, otherwise next load).
 */
@Composable
fun VideoPlayerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val hw by vm.hwDecoding.collectAsStateWithLifecycle()
    val vodExo by vm.vodPreferExo.collectAsStateWithLifecycle()
    val measuredStats by vm.measuredStreamStats.collectAsStateWithLifecycle()
    val directTune by vm.directTune.collectAsStateWithLifecycle()
    val externalLive by vm.externalPlayerLive.collectAsStateWithLifecycle()
    val externalMovies by vm.externalPlayerMovies.collectAsStateWithLifecycle()
    val externalSeries by vm.externalPlayerSeries.collectAsStateWithLifecycle()
    val zoom by vm.defaultZoom.collectAsStateWithLifecycle()
    val subStyleOn by vm.subtitleStyleEnabled.collectAsStateWithLifecycle()
    val subScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subColor by vm.subtitleColor.collectAsStateWithLifecycle()
    val subPosition by vm.subtitlePosition.collectAsStateWithLifecycle()
    val subBgOpacity by vm.subtitleBgOpacity.collectAsStateWithLifecycle()
    val audioDelay by vm.audioDelayMs.collectAsStateWithLifecycle()
    val audioLang by vm.preferredAudioLang.collectAsStateWithLifecycle()
    val subLang by vm.preferredSubLang.collectAsStateWithLifecycle()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    val liveLatency by vm.liveLatencyMode.collectAsStateWithLifecycle()
    val liveCustomSecs by vm.liveLatencyCustomSecs.collectAsStateWithLifecycle()
    // Low-latency acknowledgement popup (shown for "Low latency" and below-Balanced custom values).
    // First lambda runs on "I understand", second on "Cancel".
    var lowWarning by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }

    // OpenSubtitles account lives as an in-place sub-screen of this tab (plan §15). These three
    // are declared before the early return so they survive while the sub-screen is shown — that's
    // what lets Back land focus on the row that opened it instead of the top of the list.
    var showOpenSubAccount by remember { mutableStateOf(false) }
    var returnedFromOpenSub by remember { mutableStateOf(false) }
    val openSubRowFocus = remember { FocusRequester() }
    if (showOpenSubAccount) {
        OpenSubtitlesAccountScreen(
            onBack = { showOpenSubAccount = false; returnedFromOpenSub = true },
            modifier = modifier,
        )
        return
    }

    var dialog by remember { mutableStateOf(Dialog.NONE) }
    val firstFocus = remember { FocusRequester() }
    // Kick focus into the group; the group's onEnter (below) decides the actual target — first row on
    // a fresh open, or the OpenSubtitles row when we're returning from that sub-screen.
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    // Dialog-close focus return: closing a picker refocuses the row that opened it. The restore
    // request crosses INTO this screen's focus group from the dialog, so the group's onEnter
    // intercepts it — it consults dialogReturn first (and clears it) instead of hijacking.
    val dialogRowFocus = remember { Dialog.entries.associateWith { FocusRequester() } }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    // Hoisted scroll state: snapshot at click time, restore on dialog close, so the list doesn't
    // visibly jump/scroll-animate when a scrim picker opens or closes over it (same fix as the
    // Settings root list — Compose resets the scrollable's offset when a scrim dialog tears down).
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    val anyDialogOpen = dialog != Dialog.NONE || lowWarning != null
    LaunchedEffect(dialog, lowWarning) {
        if (dialog != Dialog.NONE) {
            // The custom-seconds dialog has no row of its own — it belongs to the Live latency row.
            val returnRow = if (dialog == Dialog.LIVE_CUSTOM) Dialog.LIVE_LATENCY else dialog
            dialogReturn = dialogRowFocus.getValue(returnRow)
        } else if (lowWarning != null) {
            // The warning popup has no row of its own — it always returns to the Live latency row.
            // Re-assert this here because the picker→popup transition lets focus dip back into the
            // list, firing onEnter and clearing dialogReturn before the popup grabs focus.
            dialogReturn = dialogRowFocus.getValue(Dialog.LIVE_LATENCY)
        } else {
            // Don't steal focus back to the row while the low-latency warning popup is up — it keeps
            // focus itself. Restore only once it (and every dialog) is closed. First snap the scroll
            // back to where the user was (one frame, so the scrim is gone) — then the opener row is
            // already in view and requestFocus() won't animate.
            withFrameNanos { }
            runCatching { scrollState.scrollTo(savedScroll) }
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
        }
    }

    val zoomMode = runCatching { ZoomMode.valueOf(zoom) }.getOrDefault(ZoomMode.FIT)

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) and the return from the OpenSubtitles sub-screen —
            // so it must prefer the pending return row over the first row.
            .focusProperties {
                onEnter = {
                    if (lowWarning != null) {
                        // The warning popup is opening and will grab focus itself. Route the
                        // transitional dip to the Live latency row WITHOUT clearing dialogReturn,
                        // so the popup-close restore still has a target to return to.
                        runCatching { dialogRowFocus.getValue(Dialog.LIVE_LATENCY).requestFocus() }
                    } else {
                        val target = dialogReturn ?: if (returnedFromOpenSub) openSubRowFocus else firstFocus
                        dialogReturn = null
                        returnedFromOpenSub = false
                        runCatching { target.requestFocus() }
                    }
                }
            }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header("Video Player", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Decoding")
        Row2(
            icon = OwnTVIcon.VIDEO, title = "Hardware decoding",
            desc = "Use the TV's hardware decoder. Turn off if some streams stutter or show artifacts.",
            chip = if (hw) "On" else "Off", primaryChip = hw,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setHwDecoding(!hw) },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = "Movies & Series player",
            desc = "mpv (recommended) has the widest format support — DTS/TrueHD audio, unusual files — " +
                "plus the A/V sync fix. Switch to ExoPlayer only if movies or episodes fail to start: " +
                "it plays some streams mpv can't on certain TVs, but can't decode DTS/TrueHD audio and " +
                "has no A/V sync fix. Whichever you pick, the other is tried automatically if it fails.",
            chip = if (vodExo) "ExoPlayer" else "mpv", primaryChip = !vodExo,
            onClick = { vm.setVodPreferExo(!vodExo) },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = "External player",
            desc = "Open streams in an external app (VLC, MX Player) instead of the built-in player, " +
                "chosen per section. Useful for streams this app can't decode, or if you prefer another " +
                "player. Resume position and prev/next are unavailable while playing externally; streams " +
                "needing a custom User-Agent or referer may not play.",
            chip = externalPlayerChip(externalLive, externalMovies, externalSeries), chevron = true,
            primaryChip = externalLive || externalMovies || externalSeries,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.EXTERNAL_PLAYER)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.EXTERNAL_PLAYER },
        )
        Row2(
            icon = OwnTVIcon.ASPECT, title = "Default zoom",
            desc = "Aspect/zoom applied when playback starts.",
            chip = zoomMode.label, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.ZOOM)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.ZOOM },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = "Resume playback",
            desc = "What to do when a movie or episode has a saved position.",
            chip = resumeMode.label, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESUME },
        )

        Divider()
        GroupLabel("Subtitles")
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "Subtitle appearance",
            desc = "Size, text color, on-screen position and background transparency. While off, " +
                "subtitles keep their stock look — including the styling broadcasters embed in Live TV " +
                "subtitles.",
            chip = if (subStyleOn) "On" else "Off", primaryChip = subStyleOn, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_STYLE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_STYLE },
        )
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "Preferred subtitle language",
            desc = "Auto-select this subtitle track when available.",
            chip = langName(subLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_LANG },
        )
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "OpenSubtitles",
            desc = "Sign in to search and download subtitles, and manage downloaded subtitles. " +
                "Per profile; a free opensubtitles.com account is required.",
            chevron = true,
            modifier = Modifier.focusRequester(openSubRowFocus),
            onClick = { showOpenSubAccount = true },
        )

        Divider()
        GroupLabel("Audio")
        Row2(
            icon = OwnTVIcon.AUDIO, title = "Preferred audio language",
            desc = "Auto-select this audio track when available.",
            chip = langName(audioLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_LANG },
        )
        Row2(
            icon = OwnTVIcon.AUDIO, title = "Audio sync",
            desc = "Shift audio earlier or later to match the video.",
            chip = "%+d ms".format(audioDelay), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_SYNC)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_SYNC },
        )

        Divider()
        GroupLabel("Live TV")
        Row2(
            icon = OwnTVIcon.LIVE_TV, title = "Live latency",
            desc = "How close to the live broadcast to play. Lower means closer to live but a smaller " +
                "buffer, so weak streams may stutter or reconnect more. Applies to the next channel you open.",
            chip = if (liveLatency == tv.own.owntv.features.settings.data.LiveLatency.CUSTOM) "${liveCustomSecs}s" else liveLatency.label,
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_LATENCY)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_LATENCY },
        )
        Row2(
            icon = OwnTVIcon.LIVE_TV, title = "Channel numbers",
            desc = "Show your provider's channel number beside the name in the Live TV list, the " +
                "channel-list overlay and the player — and type a number on the remote during " +
                "full-screen playback to jump straight to that channel (OK tunes at once, Back cancels). " +
                "Off hides every number and ignores the number keys; nothing is lost, and turning it " +
                "back on restores them.",
            chip = if (directTune) "On" else "Off", primaryChip = directTune,
            onClick = { vm.setDirectTune(!directTune) },
        )

        Divider()
        GroupLabel("Diagnostics")
        Row2(
            icon = OwnTVIcon.VIDEO, title = "Measured stream stats",
            desc = "Show live fps, bitrate and dropped frames in the stream-info overlay (measured while " +
                "the overlay is open, for streams that don't declare these values). Turn off only if a " +
                "low-end TV stutters — it never affects the actual video, only the diagnostic numbers.",
            chip = if (measuredStats) "On" else "Off", primaryChip = measuredStats,
            onClick = { vm.setMeasuredStreamStats(!measuredStats) },
        )
    }

    when (dialog) {
        Dialog.ZOOM -> PickerDialog(
            title = "Default zoom",
            options = ZoomMode.entries.map { it.name to it.label },
            selected = zoomMode.name,
            onSelect = { vm.setDefaultZoom(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESUME -> PickerDialog(
            title = "Resume playback",
            options = tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.entries.map { it.name to it.label },
            selected = resumeMode.name,
            onSelect = { vm.setResumeMode(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_STYLE -> SubtitleAppearanceDialog(
            enabled = subStyleOn,
            scale = subScale,
            color = subColor,
            position = subPosition,
            bgOpacity = subBgOpacity,
            onToggle = { vm.setSubtitleStyleEnabled(it) },
            onScale = { vm.setSubtitleScale(it) },
            onColor = { vm.setSubtitleColor(it) },
            onPosition = { vm.setSubtitlePosition(it) },
            onBgOpacity = { vm.setSubtitleBgOpacity(it) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_LANG -> PickerDialog(
            title = "Subtitle language",
            options = LANGUAGES,
            selected = subLang,
            onSelect = { vm.setPreferredSubLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_LANG -> PickerDialog(
            title = "Audio language",
            options = LANGUAGES,
            selected = audioLang,
            onSelect = { vm.setPreferredAudioLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_SYNC -> StepperDialog(
            title = "Audio sync",
            value = audioDelay, step = 50, min = -2000, max = 2000,
            format = { "%+d ms".format(it) },
            onSet = { vm.setAudioDelayMs(it) },
            onReset = { vm.setAudioDelayMs(0) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_LATENCY -> PickerDialog(
            title = "Live latency",
            options = tv.own.owntv.features.settings.data.LiveLatency.entries.map { it.name to it.label },
            selected = liveLatency.name,
            onSelect = { name ->
                val mode = tv.own.owntv.features.settings.data.LiveLatency.fromName(name)
                dialog = Dialog.NONE
                when (mode) {
                    // "Low latency" — warn before applying; Cancel leaves the current choice untouched.
                    tv.own.owntv.features.settings.data.LiveLatency.LOW ->
                        lowWarning = Pair({ vm.setLiveLatencyMode(mode) }, {})
                    // "Custom" — enter the seconds; the below-Balanced warning fires when that dialog closes.
                    tv.own.owntv.features.settings.data.LiveLatency.CUSTOM -> {
                        vm.setLiveLatencyMode(mode)
                        dialog = Dialog.LIVE_CUSTOM
                    }
                    else -> vm.setLiveLatencyMode(mode)
                }
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_CUSTOM -> StepperDialog(
            title = "Custom live buffer",
            value = liveCustomSecs,
            step = 1,
            min = tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_MIN,
            max = tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_MAX,
            format = { "${it}s" },
            onSet = { vm.setLiveLatencyCustomSecs(it) },
            onReset = { vm.setLiveLatencyCustomSecs(tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_DEFAULT) },
            onDismiss = {
                dialog = Dialog.NONE
                // A below-Balanced custom value gets the same acknowledgement; Cancel reverts to Balanced.
                if (tv.own.owntv.features.settings.data.LiveBuffer.isLowLatency(liveCustomSecs)) {
                    lowWarning = Pair({}, { vm.setLiveLatencyMode(tv.own.owntv.features.settings.data.LiveLatency.BALANCED) })
                }
            },
        )
        Dialog.EXTERNAL_PLAYER -> ExternalPlayerDialog(
            live = externalLive, movies = externalMovies, series = externalSeries,
            onToggle = { section, enabled -> vm.setExternalPlayer(section, enabled) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NONE -> Unit
    }

    lowWarning?.let { (onConfirm, onCancel) ->
        LiveLatencyWarningDialog(
            onConfirm = { lowWarning = null; onConfirm() },
            onCancel = { lowWarning = null; onCancel() },
        )
    }
}

/** Acknowledgement popup when picking a below-Balanced live buffer (Low latency, or a low custom value). */
@Composable
private fun LiveLatencyWarningDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onCancel() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text("⚠️ Low latency", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(
                "Playing closer to the live broadcast leaves a smaller buffer. On slower connections or " +
                    "unstable streams this can cause more buffering, stutter, or reconnects.\n\n" +
                    "Choose Balanced if a channel becomes unreliable.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("I understand", onClick = onConfirm, modifier = Modifier.focusRequester(firstFocus))
            }
        }
    }
}

private enum class Dialog { NONE, ZOOM, SUB_STYLE, SUB_LANG, AUDIO_LANG, AUDIO_SYNC, RESUME, LIVE_LATENCY, LIVE_CUSTOM, EXTERNAL_PLAYER }

/** Row chip for the External player row: "Off", "On" (all three), or the sections that are on. */
private fun externalPlayerChip(live: Boolean, movies: Boolean, series: Boolean): String {
    val on = buildList {
        if (live) add("Live TV")
        if (movies) add("Movies")
        if (series) add("Series")
    }
    return when (on.size) {
        0 -> "Off"
        3 -> "On"
        else -> on.joinToString(", ")
    }
}

// --- Shared building blocks (kept local to the settings sub-screens) ---

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusableSurface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            surface = GlassSurface.CARDS,
            contentAlignment = Alignment.Center,
        ) { _ -> OwnTVIcon(OwnTVIcon.BACK, tint = OwnTVTheme.colors.onSurface, modifier = Modifier.size(20.dp)) }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

/** A settings row with an icon tile, title/description and a trailing value chip (+ optional chevron). */
@Composable
internal fun Row2(
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    primaryChip: Boolean = true,
    chevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(Dimens.IconTileSize).clip(RoundedCornerShape(Dimens.IconTileCorner)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { OwnTVIcon(icon = icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (chip != null) {
                val bg = if (primaryChip) colors.primaryContainer else colors.secondaryContainer
                val on = if (primaryChip) colors.onPrimaryContainer else colors.onSecondaryContainer
                Text(
                    chip, style = MaterialTheme.typography.labelMedium, color = on, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (chevron) OwnTVIcon(OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A single-select list dialog (value → label). */
@Composable
internal fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    // When searchable, filter the option labels live (e.g. finding a category among hundreds).
    val shown = if (searchable && query.isNotBlank()) {
        options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    } else {
        options
    }
    val selIndex = shown.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LaunchedEffect(shown, selected, searchable) {
        // Nested pickers attach in the same frame their opener loses focus. Wait until this popup's
        // focus window exists, otherwise focus remains on the Add/Remove or Prefix/Suffix button.
        kotlinx.coroutines.delay(80)
        runCatching { (if (searchable) searchFr else fr).requestFocus() }
    }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.dialogPanel(width = 280.dp, corner = 16.dp, padding = 14.dp, scroll = false),
                ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            if (searchable) {
                tv.own.owntv.ui.components.SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search…",
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFr),
                    surface = GlassSurface.DIALOGS,
                )
                Spacer(Modifier.height(12.dp))
            }
            // Cap the list to the screen (minus dialog chrome) so Close stays reachable on small screens.
            val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(140.dp, 240.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(shown, key = { _, o -> o.first }) { index, (value, label) ->
                    val isSel = value == selected
                    FocusableSurface(
                        onClick = { onSelect(value) },
                        modifier = if (index == selIndex) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = isSel,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                        surface = GlassSurface.DIALOGS,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isSel) colors.onPrimaryContainer else colors.onSurface, modifier = Modifier.weight(1f))
                            if (isSel) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            }
                }
            }
        }
    }
}

/**
 * External player defaults, one independent toggle per section. Unlike [PickerDialog] these aren't
 * mutually exclusive, so the dialog stays open as rows are flipped and closes only on Close/Back.
 * Same chrome as every other settings popup — `dialogPanel` + `GlassSurface.DIALOGS`, so it follows
 * the Liquid Glass setting instead of hard-coding a solid panel.
 */
@Composable
private fun ExternalPlayerDialog(
    live: Boolean,
    movies: Boolean,
    series: Boolean,
    onToggle: (tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    val rows = listOf(
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.LIVE_TV, "Live TV", live),
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.MOVIES, "Movies", movies),
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.SERIES, "Series", series),
    )
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.dialogPanel(width = 300.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
                Text("External player", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick which sections open in an external app.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                rows.forEachIndexed { index, (section, label, enabled) ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    FocusableSurface(
                        onClick = { onToggle(section, !enabled) },
                        modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentAlignment = Alignment.CenterStart,
                        surface = GlassSurface.DIALOGS,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text(
                                if (enabled) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                }
            }
        }
    }
}

/** A +/- stepper dialog for an integer value. */
@Composable
internal fun StepperDialog(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    format: (Int) -> String,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val frPlus = remember { FocusRequester() }
    val frMinus = remember { FocusRequester() }
    val plusEnabled = value < max
    val minusEnabled = value > min
    // "+" is the natural landing spot, but at [max] it is disabled and so cannot take focus. Focus is
    // trapped inside this dialog, so silently failing to focus anything left the D-pad dead with only
    // Back working — the reported "+/- unreachable" at the top of the range. Land on whichever stepper
    // is usable, and hand focus over if the one holding it becomes disabled mid-adjustment.
    LaunchedEffect(Unit) { runCatching { (if (plusEnabled) frPlus else frMinus).requestFocus() } }
    LaunchedEffect(plusEnabled) { if (!plusEnabled && minusEnabled) runCatching { frMinus.requestFocus() } }
    LaunchedEffect(minusEnabled) { if (!minusEnabled && plusEnabled) runCatching { frPlus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.dialogPanel(width = 280.dp, corner = 16.dp, padding = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(frMinus)) { onSet((value - step).coerceAtLeast(min)) }
                Text(format(value), style = MaterialTheme.typography.titleMedium, color = colors.primary, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
                StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(frPlus)) { onSet((value + step).coerceAtMost(max)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton("Reset", onClick = onReset, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }
    }
    }
}

/** The quick text-color presets offered above the full picker (label → "#RRGGBB"). */
private val SUB_COLOR_PRESETS: List<Pair<String, String>> = listOf(
    "White" to "#FFFFFF",
    "Yellow" to "#FFEB3B",
    "Cyan" to "#4FC3F7",
    "Green" to "#8BC34A",
    "Grey" to "#BDBDBD",
)

private fun subOpacityLabel(pct: Int): String = when {
    !SubtitleStyle.hasOpacity(pct) -> "Default"
    pct == SubtitleStyle.OPACITY_MIN -> "None"
    pct == SubtitleStyle.OPACITY_MAX -> "Solid"
    else -> "$pct%"
}

private fun subColorLabel(hex: String): String = if (SubtitleStyle.hasColor(hex)) hex.uppercase() else "Default"

/**
 * Subtitle appearance (#96) — the menu for the whole custom look: a master toggle, then size, text
 * color, screen position and background transparency, each opening its own popup, with a live
 * preview above them all.
 *
 * Two levels of opt-in, and both matter. The master toggle gates everything: while it's off none of
 * these values reach any renderer, so subtitles keep their stock look — most importantly the styling
 * broadcasters embed in Live TV (CEA-608/teletext) cues, which can only be overridden by discarding
 * embedded styles wholesale. Each option then carries its own "Default", so turning the toggle on
 * still changes nothing until something is actually picked.
 *
 * Every control writes through immediately, so a change is visible on a paused stream behind the
 * dialog rather than only on the next channel change.
 */
@Composable
private fun SubtitleAppearanceDialog(
    enabled: Boolean,
    scale: Float,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onToggle: (Boolean) -> Unit,
    onScale: (Float) -> Unit,
    onColor: (String) -> Unit,
    onPosition: (SubtitleStyle.Position) -> Unit,
    onBgOpacity: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var child by remember { mutableStateOf(SubDialog.NONE) }
    // Which row opened the popup that is closing: closing a child returns focus to it, the same
    // contract the settings list itself follows.
    var lastChild by remember { mutableStateOf(SubDialog.NONE) }
    val toggleFocus = remember { FocusRequester() }
    val rowFocus = remember { SubDialog.entries.associateWith { FocusRequester() } }
    LaunchedEffect(child) {
        if (child == SubDialog.NONE) {
            withFrameNanos { }
            kotlinx.coroutines.delay(60)
            runCatching {
                (if (lastChild == SubDialog.NONE) toggleFocus else rowFocus.getValue(lastChild)).requestFocus()
            }
        }
    }

    // A child popup replaces this panel rather than stacking over it: focus stays unambiguous on a
    // D-pad, and the popups that need one carry their own preview, so nothing is lost by hiding this.
    if (child != SubDialog.NONE) {
        val close = { child = SubDialog.NONE }
        when (child) {
            SubDialog.SIZE -> PickerDialog(
                title = "Subtitle size",
                options = SUB_SIZES.map { it.first.toString() to it.second },
                selected = nearestSubSize(scale).first.toString(),
                onSelect = { onScale(it.toFloat()); close() },
                onDismiss = close,
            )
            SubDialog.COLOR -> SubtitleColorDialog(color = color, onColor = onColor, onDismiss = close)
            SubDialog.POSITION -> SubtitlePositionDialog(position = position, onSelect = onPosition, onDismiss = close)
            SubDialog.TRANSPARENCY -> SubtitleTransparencyDialog(
                scale = scale, color = color, position = position,
                bgOpacity = bgOpacity, onSet = onBgOpacity, onDismiss = close,
            )
            SubDialog.NONE -> Unit
        }
        return
    }

    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp)) {
                Text("Subtitle appearance", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turn this on, then change only what you want — anything left on Default keeps the " +
                        "stock look, including the colors and placement broadcasters embed in Live TV subtitles.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // The overview sits above every row, including the master toggle, so the effect of a
                // change is judged against a picture instead of guessed from a chip.
                SubtitlePreview(enabled = enabled, scale = scale, color = color, position = position, bgOpacity = bgOpacity)
                Spacer(Modifier.height(16.dp))

                Row2(
                    icon = OwnTVIcon.SUBTITLE, title = "Customize subtitles",
                    desc = "Off keeps subtitles exactly as the stream and the player draw them.",
                    chip = if (enabled) "On" else "Off", primaryChip = enabled,
                    modifier = Modifier.focusRequester(toggleFocus),
                    onClick = { onToggle(!enabled) },
                )

                if (enabled) {
                    val open = { target: SubDialog -> lastChild = target; child = target }
                    Spacer(Modifier.height(2.dp))
                    Row2(
                        icon = OwnTVIcon.SUBTITLE, title = "Size",
                        desc = "Scale subtitle text.",
                        chip = subSizeName(scale), primaryChip = SubtitleStyle.hasScale(scale), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.SIZE)),
                        onClick = { open(SubDialog.SIZE) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE, title = "Color",
                        desc = "Text color — a preset, the picker, or a hex code.",
                        chip = subColorLabel(color), primaryChip = SubtitleStyle.hasColor(color), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.COLOR)),
                        onClick = { open(SubDialog.COLOR) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE, title = "Position",
                        desc = "Where subtitles sit on screen.",
                        chip = position.label, primaryChip = position != SubtitleStyle.Position.DEFAULT, chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.POSITION)),
                        onClick = { open(SubDialog.POSITION) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE, title = "Background transparency",
                        desc = "How solid the box behind the text is.",
                        chip = subOpacityLabel(bgOpacity), primaryChip = SubtitleStyle.hasOpacity(bgOpacity), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.TRANSPARENCY)),
                        onClick = { open(SubDialog.TRANSPARENCY) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    if (enabled) {
                        OwnTVButton("Reset all", style = OwnTVButtonStyle.SECONDARY, onClick = {
                            onScale(SubtitleStyle.SCALE_DEFAULT)
                            onColor(SubtitleStyle.COLOR_DEFAULT)
                            onPosition(SubtitleStyle.Position.DEFAULT)
                            onBgOpacity(SubtitleStyle.OPACITY_DEFAULT)
                        })
                    }
                }
            }
        }
    }
}

/** The four options of [SubtitleAppearanceDialog], each opening its own popup. */
private enum class SubDialog { NONE, SIZE, COLOR, POSITION, TRANSPARENCY }

/**
 * Subtitle text color — the same D-pad-tuned picker the accent color uses (shared controls live in
 * `ui.components`), plus a "Use default" escape that hands the color back to the stream and player.
 */
@Composable
private fun SubtitleColorDialog(color: String, onColor: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    // Seeded once from the stored color; the picker writes straight through to settings, so this is
    // only the working position of the hue bar / square between key presses.
    val hsv = remember {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(color.ifBlank { "#FFFFFF" }), it)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var hexInput by remember { mutableStateOf(color.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    fun applyPicked(hex: String) {
        hexInput = hex.removePrefix("#")
        hexError = false
        onColor(hex)
    }

    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                .imePadding().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 18.dp)) {
                Text("Subtitle color", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Default leaves the color to the stream and the player.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SUB_COLOR_PRESETS.forEachIndexed { index, (_, hex) ->
                        tv.own.owntv.ui.components.ColorSwatch(
                            color = Color(SubtitleStyle.colorArgb(hex)),
                            selected = color.equals(hex, ignoreCase = true),
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = {
                                android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(hex), hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                applyPicked(hex)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Hex field above the picker: the on-screen keyboard covers the lower half of the
                // screen, so it has to stay high enough to remain visible while typing.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    tv.own.owntv.ui.components.OwnTVTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.take(6); hexError = false },
                        label = "Hex",
                        placeholder = "FFFFFF",
                        modifier = Modifier.width(170.dp),
                    )
                    OwnTVButton("Apply", onClick = {
                        val hex = "#" + hexInput.trim().removePrefix("#").uppercase()
                        if (tv.own.owntv.ui.theme.parseAccentHex(hex) != null) {
                            android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(hex), hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            applyPicked(hex)
                        } else {
                            hexError = true
                        }
                    })
                }
                if (hexError) {
                    Spacer(Modifier.height(8.dp))
                    Text("Enter 6 hex digits, e.g. FFEB3B", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                }

                Spacer(Modifier.height(14.dp))
                tv.own.owntv.ui.components.HueBar(hue = hue) { h ->
                    hue = h
                    applyPicked(tv.own.owntv.ui.components.hsvToHex(hue, sat, value))
                }
                Spacer(Modifier.height(12.dp))
                tv.own.owntv.ui.components.SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                    sat = s; value = v
                    applyPicked(tv.own.owntv.ui.components.hsvToHex(hue, sat, value))
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton("Use default", style = OwnTVButtonStyle.SECONDARY, onClick = {
                        hexInput = ""
                        hexError = false
                        onColor(SubtitleStyle.COLOR_DEFAULT)
                    })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton("Done", onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Subtitle position — Default plus the six fixed anchors, drawn as miniature screens so the choice
 * is made by looking rather than by reading a label.
 */
@Composable
private fun SubtitlePositionDialog(
    position: SubtitleStyle.Position,
    onSelect: (SubtitleStyle.Position) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 430.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
                Text("Subtitle position", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Default leaves placement to the stream — including where a broadcaster puts a " +
                        "live caption. Any other choice always wins.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PositionCell(
                    position = SubtitleStyle.Position.DEFAULT,
                    selected = position == SubtitleStyle.Position.DEFAULT,
                    modifier = Modifier.fillMaxWidth().let {
                        if (position == SubtitleStyle.Position.DEFAULT) it.focusRequester(selectedFocus) else it
                    },
                    onClick = { onSelect(SubtitleStyle.Position.DEFAULT) },
                )
                SubtitleStyle.Position.ANCHORS.chunked(3).forEach { anchorRow ->
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        anchorRow.forEach { anchor ->
                            PositionCell(
                                position = anchor,
                                selected = position == anchor,
                                modifier = Modifier.weight(1f).let {
                                    if (position == anchor) it.focusRequester(selectedFocus) else it
                                },
                                onClick = { onSelect(anchor) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton("Done", onClick = onDismiss)
                }
            }
        }
    }
}

/** One cell of the position picker: a miniature screen with the subtitle bar where it will land. */
@Composable
private fun PositionCell(
    position: SubtitleStyle.Position,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDefault = position == SubtitleStyle.Position.DEFAULT
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(if (isDefault) 40.dp else 64.dp),
        selected = selected,
        shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer,
        surface = GlassSurface.DIALOGS,
        contentAlignment = Alignment.Center,
    ) { _ ->
        val labelColor = if (selected) colors.onPrimaryContainer else colors.onSurface
        if (isDefault) {
            Text(
                position.label, style = MaterialTheme.typography.labelMedium, color = labelColor,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = when {
                        position.isTop && position.isLeft -> Alignment.TopStart
                        position.isTop && position.isRight -> Alignment.TopEnd
                        position.isTop -> Alignment.TopCenter
                        position.isLeft -> Alignment.BottomStart
                        position.isRight -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    },
                ) {
                    Box(
                        Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (selected) colors.onPrimaryContainer else colors.outline),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    position.label, style = MaterialTheme.typography.labelSmall, color = labelColor,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Background transparency — a ±10% stepper. "Default" is its own state rather than a value in the
 * range: it means the box is left to the renderer (and, on Live TV, to the broadcaster).
 */
@Composable
private fun SubtitleTransparencyDialog(
    scale: Float,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val frPlus = remember { FocusRequester() }
    val frMinus = remember { FocusRequester() }
    val isDefault = !SubtitleStyle.hasOpacity(bgOpacity)
    // From "Default" either button adopts the mid value first, so neither is ever a dead end.
    val effective = if (isDefault) SubtitleStyle.OPACITY_START else bgOpacity
    val minusEnabled = isDefault || effective > SubtitleStyle.OPACITY_MIN
    val plusEnabled = isDefault || effective < SubtitleStyle.OPACITY_MAX
    LaunchedEffect(Unit) { runCatching { (if (plusEnabled) frPlus else frMinus).requestFocus() } }
    // Focus must never be left on a stepper that goes disabled: focus is trapped in the dialog, so
    // that leaves the D-pad dead with only Back working (the same guard [StepperDialog] carries).
    LaunchedEffect(plusEnabled) { if (!plusEnabled && minusEnabled) runCatching { frMinus.requestFocus() } }
    LaunchedEffect(minusEnabled) { if (!minusEnabled && plusEnabled) runCatching { frPlus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.dialogPanel(width = 380.dp, corner = 16.dp, padding = 18.dp, scroll = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Background transparency", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "How solid the box behind the text is.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SubtitlePreview(
                    enabled = true, scale = scale, color = color, position = position,
                    bgOpacity = bgOpacity, height = 92.dp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(frMinus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective - SubtitleStyle.OPACITY_STEP).coerceAtLeast(SubtitleStyle.OPACITY_MIN),
                        )
                    }
                    Text(
                        subOpacityLabel(bgOpacity), style = MaterialTheme.typography.titleMedium,
                        color = colors.primary, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center,
                    )
                    StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(frPlus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective + SubtitleStyle.OPACITY_STEP).coerceAtMost(SubtitleStyle.OPACITY_MAX),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton("Use default", style = OwnTVButtonStyle.SECONDARY, onClick = { onSet(SubtitleStyle.OPACITY_DEFAULT) })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton("Done", onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * A stand-in video frame with a sample subtitle drawn the way the renderers will draw it — same
 * color, background alpha, text scale and anchor. Anything on "Default" (or everything, while the
 * master toggle is off) falls back to the stock look.
 */
@Composable
private fun SubtitlePreview(
    enabled: Boolean,
    scale: Float,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val colors = OwnTVTheme.colors
    val textColor = if (enabled && SubtitleStyle.hasColor(color)) Color(SubtitleStyle.colorArgb(color)) else Color.White
    val boxColor = if (enabled && SubtitleStyle.hasOpacity(bgOpacity)) {
        Color(SubtitleStyle.backgroundArgb(bgOpacity))
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    val anchor = if (enabled) position else SubtitleStyle.Position.DEFAULT
    val textScale = if (enabled) scale else SubtitleStyle.SCALE_DEFAULT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            // A busy-ish backdrop: a flat panel would make even a solid box look harmless.
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF2E4A6B), Color(0xFF7A5C3E), Color(0xFF3B6B4A)),
                ),
            ),
        contentAlignment = when {
            anchor.isTop && anchor.isLeft -> Alignment.TopStart
            anchor.isTop && anchor.isRight -> Alignment.TopEnd
            anchor.isTop -> Alignment.TopCenter
            anchor.isLeft -> Alignment.BottomStart
            anchor.isRight -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        },
    ) {
        Text(
            "The quick brown fox",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale,
            ),
            color = textColor,
            modifier = Modifier
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(boxColor)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        if (!enabled) {
            Text(
                "Preview — stock look",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ -> Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) colors.onSurface else colors.outline) }
}
