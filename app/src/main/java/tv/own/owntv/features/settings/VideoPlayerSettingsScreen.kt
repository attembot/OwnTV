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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.settings.data.SubtitleStyle
import tv.own.owntv.player.ZoomMode
import tv.own.owntv.player.alignment
import tv.own.owntv.ui.components.FocusableSurface
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.player.EnginePreference
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.AppFontFamily
import tv.own.owntv.ui.theme.asComposeFamily

/** Common language codes offered for the audio/subtitle preference. Display names resolve in Compose. */
private val LANGUAGE_CODES = listOf("", "eng", "spa", "fra", "deu", "ita", "por", "nld", "rus", "ara", "hin", "zho", "jpn", "kor", "tur")
private val SUB_SIZES = listOf(0.8f to R.string.settings_subtitle_small, 1.0f to R.string.settings_subtitle_normal, 1.3f to R.string.settings_subtitle_large, 1.6f to R.string.settings_subtitle_extra_large)

@Composable
private fun langName(code: String): String = stringResource(
    when (code) {
        "" -> R.string.settings_none_auto
        "eng" -> R.string.settings_language_english
        "spa" -> R.string.settings_language_spanish
        "fra" -> R.string.settings_language_french
        "deu" -> R.string.settings_language_german
        "ita" -> R.string.settings_language_italian
        "por" -> R.string.settings_language_portuguese
        "nld" -> R.string.settings_language_dutch
        "rus" -> R.string.settings_language_russian
        "ara" -> R.string.settings_language_arabic
        "hin" -> R.string.settings_language_hindi
        "zho" -> R.string.settings_language_chinese
        "jpn" -> R.string.settings_language_japanese
        "kor" -> R.string.settings_language_korean
        "tur" -> R.string.settings_language_turkish
        else -> R.string.settings_none_auto
    },
)

private fun nearestSubSize(scale: Float) = SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) } ?: SUB_SIZES[1]

@Composable
private fun subSizeName(scale: Float): String = stringResource(
    SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) }?.second ?: R.string.settings_subtitle_normal,
)

private fun resumeModeLabelRes(mode: tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode): Int = when (mode) {
    tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.AUTO -> R.string.settings_resume_always
    tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.ASK -> R.string.settings_resume_ask
    tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.NEVER -> R.string.settings_resume_never
}

private fun liveLatencyLabelRes(mode: tv.own.owntv.features.settings.data.LiveLatency): Int = when (mode) {
    tv.own.owntv.features.settings.data.LiveLatency.LOW -> R.string.settings_live_latency_low
    tv.own.owntv.features.settings.data.LiveLatency.BALANCED -> R.string.settings_live_latency_balanced
    tv.own.owntv.features.settings.data.LiveLatency.STABLE -> R.string.settings_live_latency_stable
    tv.own.owntv.features.settings.data.LiveLatency.CUSTOM -> R.string.settings_live_latency_custom
}

/**
 * Video Player settings — decoder, default aspect/zoom, subtitle size & language, audio sync. Each
 * value is persisted and applied to the shared mpv player (live where possible, otherwise next load).
 */
@Composable
fun VideoPlayerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val hw by vm.hwDecoding.collectAsStateWithLifecycle()
    val vodEngine by vm.vodEnginePreference.collectAsStateWithLifecycle()
    val liveEngine by vm.liveEnginePreference.collectAsStateWithLifecycle()
    val enginePins by vm.vodEnginePinCount.collectAsStateWithLifecycle()
    val defaultVolume by vm.defaultVolume.collectAsStateWithLifecycle()
    val savedZoom by vm.savedZoomCount.collectAsStateWithLifecycle()
    val savedVolume by vm.savedVolumeCount.collectAsStateWithLifecycle()
    val seekStep by vm.seekStepSec.collectAsStateWithLifecycle()
    val liveRewindStep by vm.liveRewindStepSec.collectAsStateWithLifecycle()
    val deinterlace by vm.deinterlace.collectAsStateWithLifecycle()
    val measuredStats by vm.measuredStreamStats.collectAsStateWithLifecycle()
    val detailedDiagnostics by vm.detailedDiagnostics.collectAsStateWithLifecycle()
    val directTune by vm.directTune.collectAsStateWithLifecycle()
    val externalLive by vm.externalPlayerLive.collectAsStateWithLifecycle()
    val externalMovies by vm.externalPlayerMovies.collectAsStateWithLifecycle()
    val externalSeries by vm.externalPlayerSeries.collectAsStateWithLifecycle()
    val zoom by vm.defaultZoom.collectAsStateWithLifecycle()
    val subStyleOn by vm.subtitleStyleEnabled.collectAsStateWithLifecycle()
    val subScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subFont by vm.subtitleFont.collectAsStateWithLifecycle()
    val subColor by vm.subtitleColor.collectAsStateWithLifecycle()
    val subPosition by vm.subtitlePosition.collectAsStateWithLifecycle()
    val subBgOpacity by vm.subtitleBgOpacity.collectAsStateWithLifecycle()
    val audioDelay by vm.audioDelayMs.collectAsStateWithLifecycle()
    val audioLang by vm.preferredAudioLang.collectAsStateWithLifecycle()
    val subLang by vm.preferredSubLang.collectAsStateWithLifecycle()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    val liveLatency by vm.liveLatencyMode.collectAsStateWithLifecycle()
    val liveCustomSecs by vm.liveLatencyCustomSecs.collectAsStateWithLifecycle()
    val livePreroll by vm.livePrerollSecs.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    // The playlist whose per-playlist "Pre-buffer" override is being edited.
    var prerollSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    // Low-latency acknowledgement popup (shown for "Low latency" and below-Balanced custom values).
    // First lambda runs on "I understand", second on "Cancel".
    var lowWarning by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }

    // OpenSubtitles account lives as an in-place sub-screen of this tab (plan §15). These three
    // are declared before the early return so they survive while the sub-screen is shown — that's
    // what lets Back land focus on the row that opened it instead of the top of the list.
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    /** Whether the Custom-latency stepper actually set a value this time round — the mode switch to
     *  Custom, and the low-latency acknowledgement, both hang off that rather than off merely opening it. */
    var customCommitted by remember { mutableStateOf(false) }
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
            val returnRow = when (dialog) {
                Dialog.LIVE_CUSTOM -> Dialog.LIVE_LATENCY
                // The per-playlist value picker belongs to the playlist row that opened it.
                Dialog.LIVE_PREROLL_SOURCE -> Dialog.LIVE_PREROLL_SOURCES
                else -> dialog
            }
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
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                    }
                }
            }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(stringResource(R.string.settings_video_player_title), onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel(stringResource(R.string.settings_decoding))
        Row2(
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_hardware_decoding),
            desc = stringResource(R.string.settings_hardware_decoding_description),
            chip = if (hw) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = hw,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setHwDecoding(!hw) },
        )
        Row2(
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_deinterlace),
            desc = stringResource(R.string.settings_deinterlace_description),
            chip = if (deinterlace) stringResource(R.string.settings_auto) else stringResource(R.string.common_off),
            primaryChip = deinterlace,
            onClick = { vm.setDeinterlace(!deinterlace) },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_live_tv_player),
            desc = stringResource(R.string.settings_live_player_description),
            chip = engineLabel(liveEngine), chevron = true,
            primaryChip = liveEngine != EnginePreference.EXO_FIRST,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_ENGINE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_ENGINE },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_movies_series_player),
            desc = stringResource(R.string.settings_movies_player_description),
            chip = engineLabel(vodEngine), chevron = true,
            primaryChip = vodEngine != EnginePreference.MPV_FIRST,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_ENGINE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_ENGINE },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_reset_player_choices),
            desc = stringResource(R.string.settings_reset_player_choices_description),
            chip = if (enginePins == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, enginePins, enginePins),
            primaryChip = enginePins > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_PINS)),
            // Opens the confirmation even with nothing pinned: a settings row that swallows the OK
            // press is a dead end on a remote, and the chip already says whether there is anything
            // to reset.
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_PINS },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_external_player),
            desc = stringResource(R.string.settings_external_player_row_description),
            chip = externalPlayerChip(externalLive, externalMovies, externalSeries), chevron = true,
            primaryChip = externalLive || externalMovies || externalSeries,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.EXTERNAL_PLAYER)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.EXTERNAL_PLAYER },
        )
        Row2(
            icon = OwnTVIcon.ASPECT, title = stringResource(R.string.settings_default_zoom),
            desc = stringResource(R.string.settings_default_zoom_description),
            chip = stringResource(zoomMode.labelRes), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.ZOOM)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.ZOOM },
        )
        Row2(
            icon = OwnTVIcon.ASPECT, title = stringResource(R.string.settings_reset_saved_zoom),
            desc = stringResource(R.string.settings_reset_saved_zoom_description),
            chip = if (savedZoom == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, savedZoom, savedZoom),
            primaryChip = savedZoom > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_SAVED_ZOOM)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_SAVED_ZOOM },
        )
        Row2(
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_default_volume),
            desc = stringResource(R.string.settings_default_volume_description),
            chip = stringResource(R.string.player_percent, defaultVolume), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOLUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOLUME },
        )
        Row2(
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_reset_saved_volume),
            desc = stringResource(R.string.settings_reset_saved_volume_description),
            chip = if (savedVolume == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, savedVolume, savedVolume),
            primaryChip = savedVolume > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_SAVED_VOLUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_SAVED_VOLUME },
        )
        Row2(
            icon = OwnTVIcon.FORWARD, title = stringResource(R.string.settings_seek_step),
            desc = stringResource(R.string.settings_seek_step_description),
            chip = stringResource(R.string.settings_live_buffer_seconds, seekStep), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SEEK_STEP)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SEEK_STEP },
        )
        Row2(
            icon = OwnTVIcon.REWIND, title = stringResource(R.string.settings_live_rewind_step),
            desc = stringResource(R.string.settings_live_rewind_step_description),
            chip = stringResource(R.string.settings_live_buffer_seconds, liveRewindStep), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_REWIND_STEP)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_REWIND_STEP },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_resume_playback),
            desc = stringResource(R.string.settings_resume_playback_description),
            chip = stringResource(resumeModeLabelRes(resumeMode)), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESUME },
        )

        Divider()
        GroupLabel(stringResource(R.string.settings_subtitles))
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = stringResource(R.string.settings_subtitle_appearance),
            desc = stringResource(R.string.settings_subtitle_appearance_description),
            chip = stringResource(if (subStyleOn) R.string.common_on else R.string.common_off), primaryChip = subStyleOn, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_STYLE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_STYLE },
        )
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = stringResource(R.string.settings_preferred_subtitle_language),
            desc = stringResource(R.string.settings_preferred_language_description),
            chip = langName(subLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_LANG },
        )

        Divider()
        GroupLabel(stringResource(R.string.settings_audio))
        Row2(
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_preferred_audio_language),
            desc = stringResource(R.string.settings_preferred_language_description),
            chip = langName(audioLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_LANG },
        )
        Row2(
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_audio_sync),
            desc = stringResource(R.string.settings_audio_sync_description),
            chip = stringResource(R.string.settings_audio_delay_value, audioDelay), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_SYNC)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_SYNC },
        )

        Divider()
        GroupLabel(stringResource(R.string.settings_live_tv))
        Row2(
            icon = OwnTVIcon.LIVE_TV, title = stringResource(R.string.settings_live_latency),
            desc = stringResource(R.string.settings_live_latency_description),
            chip = if (liveLatency == tv.own.owntv.features.settings.data.LiveLatency.CUSTOM) stringResource(R.string.settings_live_buffer_seconds, liveCustomSecs) else stringResource(liveLatencyLabelRes(liveLatency)),
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_LATENCY)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_LATENCY },
        )
        Row2(
            icon = OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_live_preroll),
            desc = stringResource(R.string.settings_live_preroll_description),
            chip = if (livePreroll <= 0) {
                stringResource(R.string.common_off)
            } else {
                stringResource(R.string.settings_live_buffer_seconds, livePreroll)
            },
            primaryChip = livePreroll > 0,
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_PREROLL)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_PREROLL },
        )
        if (sources.isNotEmpty()) {
            Row2(
                icon = OwnTVIcon.LIVE_TV,
                title = stringResource(R.string.settings_live_preroll_per_playlist),
                desc = stringResource(R.string.settings_live_preroll_per_playlist_description),
                chip = sources.count { it.livePrerollSecs >= 0 }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.livePrerollSecs >= 0 },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_PREROLL_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_PREROLL_SOURCES },
            )
        }
        Row2(
            icon = OwnTVIcon.LIVE_TV, title = stringResource(R.string.settings_channel_numbers),
            desc = stringResource(R.string.settings_channel_numbers_description),
            chip = if (directTune) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = directTune,
            onClick = { vm.setDirectTune(!directTune) },
        )

        Divider()
        GroupLabel(stringResource(R.string.settings_diagnostics))
        Row2(
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_measured_stats),
            desc = stringResource(R.string.settings_measured_stats_description),
            chip = if (measuredStats) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = measuredStats,
            onClick = { vm.setMeasuredStreamStats(!measuredStats) },
        )
        Row2(
            icon = OwnTVIcon.INFO, title = stringResource(R.string.settings_detailed_playback_logging),
            desc = stringResource(R.string.settings_detailed_playback_logging_description),
            chip = stringResource(if (detailedDiagnostics) R.string.common_on else R.string.common_off), primaryChip = detailedDiagnostics,
            onClick = { vm.setDetailedDiagnostics(!detailedDiagnostics) },
        )
    }

    when (dialog) {
        Dialog.LIVE_ENGINE -> PickerDialog(
            title = stringResource(R.string.settings_live_tv_player),
            options = engineOptions(default = EnginePreference.EXO_FIRST),
            selected = liveEngine.name,
            onSelect = { vm.setLiveEnginePreference(EnginePreference.valueOf(it)); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.VOD_ENGINE -> PickerDialog(
            title = stringResource(R.string.settings_movies_series_player),
            options = engineOptions(default = EnginePreference.MPV_FIRST),
            selected = vodEngine.name,
            onSelect = { vm.setVodEnginePreference(EnginePreference.valueOf(it)); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ZOOM -> PickerDialog(
            title = stringResource(R.string.settings_default_zoom),
            options = ZoomMode.entries.map { it.name to stringResource(it.labelRes) },
            selected = zoomMode.name,
            onSelect = { vm.setDefaultZoom(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESUME -> PickerDialog(
            title = stringResource(R.string.settings_resume_playback),
            options = tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.entries.map { it.name to stringResource(resumeModeLabelRes(it)) },
            selected = resumeMode.name,
            onSelect = { vm.setResumeMode(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_STYLE -> SubtitleAppearanceDialog(
                enabled = subStyleOn,
                scale = subScale,
                font = subFont,
                color = subColor,
            position = subPosition,
            bgOpacity = subBgOpacity,
                onToggle = { vm.setSubtitleStyleEnabled(it) },
                onScale = { vm.setSubtitleScale(it) },
                onFont = { vm.setSubtitleFont(it) },
                onColor = { vm.setSubtitleColor(it) },
            onPosition = { vm.setSubtitlePosition(it) },
            onBgOpacity = { vm.setSubtitleBgOpacity(it) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_LANG -> PickerDialog(
            title = stringResource(R.string.settings_preferred_subtitle_language),
            options = LANGUAGE_CODES.map { it to langName(it) },
            selected = subLang,
            onSelect = { vm.setPreferredSubLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_LANG -> PickerDialog(
            title = stringResource(R.string.settings_preferred_audio_language),
            options = LANGUAGE_CODES.map { it to langName(it) },
            selected = audioLang,
            onSelect = { vm.setPreferredAudioLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_SYNC -> StepperDialog(
            title = stringResource(R.string.settings_audio_sync),
            // ±5s, matching what the player itself accepts. The narrower ±2s here meant a delay set in the
            // HUD could not be reproduced — or corrected — from Settings.
            // 25 ms steps: the offset being corrected here is the TV's own picture-processing delay, which
            // lands in the tens of milliseconds — a 50 ms step could only bracket it, never hit it.
            value = audioDelay, step = 25, min = -5000, max = 5000,
            format = { stringResource(R.string.settings_audio_delay, it) },
            onSet = { vm.setAudioDelayMs(it) },
            onReset = { vm.setAudioDelayMs(0) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_LATENCY -> PickerDialog(
            title = stringResource(R.string.settings_live_latency),
            options = tv.own.owntv.features.settings.data.LiveLatency.entries.map { it.name to stringResource(liveLatencyLabelRes(it)) },
            selected = liveLatency.name,
            onSelect = { name ->
                val mode = tv.own.owntv.features.settings.data.LiveLatency.fromName(name)
                dialog = Dialog.NONE
                when (mode) {
                    // "Low latency" — warn before applying; Cancel leaves the current choice untouched.
                    tv.own.owntv.features.settings.data.LiveLatency.LOW ->
                        lowWarning = Pair({ vm.setLiveLatencyMode(mode) }, {})
                    // "Custom" — enter the seconds first. The mode is committed by the stepper itself, not
                    // here: switching on open meant backing out of the number dialog still left the user on
                    // Custom, with a value they never chose.
                    tv.own.owntv.features.settings.data.LiveLatency.CUSTOM -> {
                        customCommitted = false
                        dialog = Dialog.LIVE_CUSTOM
                    }
                    else -> vm.setLiveLatencyMode(mode)
                }
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_CUSTOM -> StepperDialog(
            title = stringResource(R.string.settings_custom_live_buffer),
            value = liveCustomSecs,
            step = 1,
            min = tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_MIN,
            max = tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_MAX,
            format = { stringResource(R.string.settings_live_buffer_seconds, it) },
            onSet = {
                vm.setLiveLatencyCustomSecs(it)
                vm.setLiveLatencyMode(tv.own.owntv.features.settings.data.LiveLatency.CUSTOM)
                customCommitted = true
            },
            onReset = {
                vm.setLiveLatencyCustomSecs(tv.own.owntv.features.settings.data.LiveBuffer.CUSTOM_DEFAULT)
                vm.setLiveLatencyMode(tv.own.owntv.features.settings.data.LiveLatency.CUSTOM)
                customCommitted = true
            },
            onDismiss = {
                dialog = Dialog.NONE
                // A below-Balanced custom value gets the same acknowledgement; Cancel reverts to Balanced.
                if (customCommitted && tv.own.owntv.features.settings.data.LiveBuffer.isLowLatency(liveCustomSecs)) {
                    lowWarning = Pair({}, { vm.setLiveLatencyMode(tv.own.owntv.features.settings.data.LiveLatency.BALANCED) })
                }
            },
        )
        Dialog.LIVE_PREROLL -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll),
            options = tv.own.owntv.features.settings.data.LiveBuffer.PREROLL_CHOICES.map {
                it.toString() to if (it <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_video_seconds, it)
            },
            selected = livePreroll.toString(),
            onSelect = { vm.setLivePrerollSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_PREROLL_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val value = if (src.livePrerollSecs >= 0) {
                    stringResource(R.string.settings_video_seconds, src.livePrerollSecs)
                } else {
                    stringResource(R.string.settings_live_preroll_follow)
                }
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = prerollSource?.id?.toString() ?: "",
            onSelect = { id ->
                prerollSource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (prerollSource != null) Dialog.LIVE_PREROLL_SOURCE else Dialog.NONE
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_PREROLL_SOURCE -> PickerDialog(
            title = prerollSource?.name ?: stringResource(R.string.settings_sort_playlist),
            options = listOf("-1" to stringResource(R.string.settings_live_preroll_follow)) +
                tv.own.owntv.features.settings.data.LiveBuffer.PREROLL_CHOICES.map {
                    it.toString() to if (it <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_video_seconds, it)
                },
            selected = (prerollSource?.livePrerollSecs ?: -1).toString(),
            onSelect = { value ->
                prerollSource?.let { vm.setSourcePreroll(it.id, value.toIntOrNull() ?: -1) }
                prerollSource = null
                dialog = Dialog.NONE
            },
            onDismiss = { prerollSource = null; dialog = Dialog.NONE },
        )
        Dialog.EXTERNAL_PLAYER -> ExternalPlayerDialog(
            live = externalLive, movies = externalMovies, series = externalSeries,
            onToggle = { section, enabled -> vm.setExternalPlayer(section, enabled) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESET_PINS -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_player_choices_confirm),
            description = stringResource(R.string.settings_reset_player_choices_confirm_description),
            onConfirm = { vm.clearVodEnginePins(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.VOLUME -> StepperDialog(
            title = stringResource(R.string.settings_default_volume),
            // The same 0–150 range and 5% step the player's own volume dialog uses, so a level found
            // there can be set as the default here without landing between two values.
            value = defaultVolume, step = 5, min = 0, max = 150,
            format = { stringResource(R.string.player_percent, it) },
            onSet = { vm.setDefaultVolume(it) },
            onReset = { vm.setDefaultVolume(100) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SEEK_STEP -> PickerDialog(
            title = stringResource(R.string.settings_seek_step),
            options = tv.own.owntv.features.settings.data.SeekSteps.SEEK_CHOICES.map {
                it.toString() to stringResource(R.string.settings_live_buffer_seconds, it)
            },
            selected = seekStep.toString(),
            onSelect = { vm.setSeekStepSec(it.toIntOrNull() ?: tv.own.owntv.features.settings.data.SeekSteps.DEFAULT_SEEK_STEP_SEC); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_REWIND_STEP -> PickerDialog(
            title = stringResource(R.string.settings_live_rewind_step),
            options = tv.own.owntv.features.settings.data.SeekSteps.LIVE_REWIND_CHOICES.map {
                it.toString() to stringResource(R.string.settings_live_buffer_seconds, it)
            },
            selected = liveRewindStep.toString(),
            onSelect = { vm.setLiveRewindStepSec(it.toIntOrNull() ?: tv.own.owntv.features.settings.data.SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESET_SAVED_ZOOM -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_saved_zoom_confirm),
            description = stringResource(R.string.settings_reset_saved_zoom_confirm_description),
            onConfirm = { vm.clearSavedZoom(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.RESET_SAVED_VOLUME -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_saved_volume_confirm),
            description = stringResource(R.string.settings_reset_saved_volume_confirm_description),
            onConfirm = { vm.clearSavedVolume(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
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
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_low_latency_warning), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_low_latency_warning_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_low_latency_understand), onClick = onConfirm, modifier = Modifier.focusRequester(firstFocus))
            }
        }
    }
}

/** Confirmation before forgetting a whole set of remembered per-item choices (engine pins, zoom and
 *  volume). Focus starts on Cancel — the row that opens this is one press away from an ordinary
 *  setting, so a mis-press must not wipe choices the user made deliberately. */
@Composable
private fun ConfirmResetDialog(title: String, description: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onCancel() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.common_cancel), onClick = onCancel,
                    style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus),
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_reset), onClick = onConfirm)
            }
        }
    }
}

private enum class Dialog { NONE, LIVE_ENGINE, VOD_ENGINE, ZOOM, VOLUME, RESET_SAVED_ZOOM, RESET_SAVED_VOLUME, SEEK_STEP, LIVE_REWIND_STEP, SUB_STYLE, SUB_LANG, AUDIO_LANG, AUDIO_SYNC, RESUME, LIVE_LATENCY, LIVE_CUSTOM, LIVE_PREROLL, LIVE_PREROLL_SOURCES, LIVE_PREROLL_SOURCE, EXTERNAL_PLAYER, RESET_PINS }

/**
 * Label for one engine preference — "ExoPlayer, then mpv", "mpv only", and so on.
 *
 * The engine names themselves are brands and never translated (`settings_player_*` are
 * `translatable="false"`), so only the two sentence frames around them are, which is also why the same
 * four labels serve both sections.
 */
@Composable
internal fun engineLabel(preference: EnginePreference): String {
    val exo = stringResource(R.string.settings_player_exoplayer)
    val mpv = stringResource(R.string.settings_player_mpv)
    return when (preference) {
        EnginePreference.EXO_FIRST -> stringResource(R.string.settings_engine_order, exo, mpv)
        EnginePreference.MPV_FIRST -> stringResource(R.string.settings_engine_order, mpv, exo)
        EnginePreference.EXO_ONLY -> stringResource(R.string.settings_engine_only, exo)
        EnginePreference.MPV_ONLY -> stringResource(R.string.settings_engine_only, mpv)
    }
}

/** The four options for an engine picker, with [default] marked — Live TV and Movies & Series have
 *  different defaults, so which line carries the mark depends on the section, not on the option. */
@Composable
private fun engineOptions(default: EnginePreference): List<Pair<String, String>> =
    EnginePreference.entries.map { preference ->
        val label = engineLabel(preference)
        preference.name to if (preference == default) {
            stringResource(R.string.settings_engine_default, label)
        } else {
            label
        }
    }

/** Row chip for the External player row: "Off", "On" (all three), or the sections that are on. */
@Composable
private fun externalPlayerChip(live: Boolean, movies: Boolean, series: Boolean): String {
    val on = buildList {
        if (live) add(stringResource(R.string.common_nav_live_tv))
        if (movies) add(stringResource(R.string.common_nav_movies))
        if (series) add(stringResource(R.string.common_nav_series))
    }
    return when (on.size) {
        0 -> stringResource(R.string.common_off)
        3 -> stringResource(R.string.common_on)
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
            Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.dialogPanel(width = 280.dp, corner = 16.dp, padding = 14.dp, scroll = false),
                ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            if (searchable) {
                tv.own.owntv.ui.components.SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search_hint),
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
                OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
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
 * the Glass effect setting instead of hard-coding a solid panel.
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
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.LIVE_TV, stringResource(R.string.common_nav_live_tv), live),
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.MOVIES, stringResource(R.string.common_nav_movies), movies),
        Triple(tv.own.owntv.features.settings.data.SettingsRepository.ExternalPlayerSection.SERIES, stringResource(R.string.common_nav_series), series),
    )
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.dialogPanel(width = 300.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
                Text(stringResource(R.string.settings_external_player), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_external_player_description),
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
                                if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
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
    format: @Composable (Int) -> String,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val plusEnabled = value < max
    val minusEnabled = value > min
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(plusEnabled, minusEnabled)
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.dialogPanel(width = 360.dp, corner = 16.dp, padding = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(steppers.minus)) { onSet((value - step).coerceAtLeast(min)) }
                Text(
                    format(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(steppers.plus)) { onSet((value + step).coerceAtMost(max)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton(stringResource(R.string.common_reset), onClick = onReset, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
            }
        }
    }
    }
}

/** The quick text-color presets offered above the full picker (label → "#RRGGBB"). */
private val SUB_COLOR_PRESETS: List<Pair<Int, String>> = listOf(
    R.string.settings_subtitle_color_white to "#FFFFFF",
    R.string.settings_subtitle_color_yellow to "#FFEB3B",
    R.string.settings_subtitle_color_cyan to "#4FC3F7",
    R.string.settings_subtitle_color_green to "#8BC34A",
    R.string.settings_subtitle_color_grey to "#BDBDBD",
)

@Composable
private fun subOpacityLabel(pct: Int): String = when {
    !SubtitleStyle.hasOpacity(pct) -> stringResource(R.string.settings_subtitle_default)
    pct == SubtitleStyle.OPACITY_MIN -> stringResource(R.string.settings_subtitle_background_none)
    pct == SubtitleStyle.OPACITY_MAX -> stringResource(R.string.settings_subtitle_background_solid)
    else -> stringResource(R.string.common_percent, pct)
}

@Composable
private fun subColorLabel(hex: String): String = if (SubtitleStyle.hasColor(hex)) {
    hex.uppercase()
} else {
    stringResource(R.string.settings_subtitle_default)
}

@Composable
private fun subtitlePositionName(position: SubtitleStyle.Position): String = stringResource(
    when (position) {
        SubtitleStyle.Position.DEFAULT -> R.string.settings_subtitle_default
        SubtitleStyle.Position.TOP_LEFT -> R.string.player_mini_top_left
        SubtitleStyle.Position.TOP_CENTER -> R.string.player_mini_top_center
        SubtitleStyle.Position.TOP_RIGHT -> R.string.player_mini_top_right
        SubtitleStyle.Position.BOTTOM_LEFT -> R.string.player_mini_bottom_left
        SubtitleStyle.Position.BOTTOM_CENTER -> R.string.player_mini_bottom_center
        SubtitleStyle.Position.BOTTOM_RIGHT -> R.string.player_mini_bottom_right
    },
)

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
    font: AppFontFamily?,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onToggle: (Boolean) -> Unit,
    onScale: (Float) -> Unit,
    onFont: (AppFontFamily?) -> Unit,
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
                title = stringResource(R.string.settings_subtitle_size),
                options = SUB_SIZES.map { it.first.toString() to stringResource(it.second) },
                selected = nearestSubSize(scale).first.toString(),
                onSelect = { onScale(it.toFloat()); close() },
                    onDismiss = close,
                )
                SubDialog.FONT -> PickerDialog(
                    title = stringResource(R.string.settings_subtitle_font),
                    options = listOf("" to stringResource(R.string.settings_subtitle_default)) +
                        AppFontFamily.entries.map { it.name to subtitleFontFamilyLabel(it) },
                    selected = font?.name.orEmpty(),
                    onSelect = { selected ->
                        onFont(AppFontFamily.entries.firstOrNull { it.name == selected })
                        close()
                    },
                    onDismiss = close,
                )
                SubDialog.COLOR -> SubtitleColorDialog(color = color, onColor = onColor, onDismiss = close)
            SubDialog.POSITION -> SubtitlePositionDialog(position = position, onSelect = onPosition, onDismiss = close)
                SubDialog.TRANSPARENCY -> SubtitleTransparencyDialog(
                    scale = scale, font = font, color = color, position = position,
                bgOpacity = bgOpacity, onSet = onBgOpacity, onDismiss = close,
            )
            SubDialog.NONE -> Unit
        }
        return
    }

    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp)) {
                Text(stringResource(R.string.settings_subtitle_appearance), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_subtitle_customize_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // The overview sits above every row, including the master toggle, so the effect of a
                // change is judged against a picture instead of guessed from a chip.
        SubtitlePreview(enabled = enabled, scale = scale, font = font, color = color, position = position, bgOpacity = bgOpacity)
                Spacer(Modifier.height(16.dp))

                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = stringResource(R.string.settings_subtitle_customize),
                    desc = stringResource(R.string.settings_subtitle_customize_off),
                    chip = stringResource(if (enabled) R.string.common_on else R.string.common_off),
                    primaryChip = enabled,
                    modifier = Modifier.focusRequester(toggleFocus),
                    onClick = { onToggle(!enabled) },
                )

                if (enabled) {
                    val open = { target: SubDialog -> lastChild = target; child = target }
                    Spacer(Modifier.height(2.dp))
            Row2(
                icon = OwnTVIcon.SUBTITLE,
                title = stringResource(R.string.settings_subtitle_size),
                        desc = stringResource(R.string.settings_subtitle_size_description),
                        chip = subSizeName(scale), primaryChip = SubtitleStyle.hasScale(scale), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.SIZE)),
                onClick = { open(SubDialog.SIZE) },
            )
            Row2(
                icon = OwnTVIcon.SUBTITLE,
                title = stringResource(R.string.settings_subtitle_font),
                desc = stringResource(R.string.settings_choose_font),
                chip = font?.let { subtitleFontFamilyLabel(it) } ?: stringResource(R.string.settings_subtitle_default),
                primaryChip = font != null,
                chevron = true,
                modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.FONT)),
                onClick = { open(SubDialog.FONT) },
            )
            Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_color_short),
                        desc = stringResource(R.string.settings_subtitle_color_description),
                        chip = subColorLabel(color), primaryChip = SubtitleStyle.hasColor(color), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.COLOR)),
                        onClick = { open(SubDialog.COLOR) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_position_short),
                        desc = stringResource(R.string.settings_subtitle_position_description),
                        chip = subtitlePositionName(position), primaryChip = position != SubtitleStyle.Position.DEFAULT, chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.POSITION)),
                        onClick = { open(SubDialog.POSITION) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_background_transparency),
                        desc = stringResource(R.string.settings_subtitle_background_description),
                        chip = subOpacityLabel(bgOpacity), primaryChip = SubtitleStyle.hasOpacity(bgOpacity), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.TRANSPARENCY)),
                        onClick = { open(SubDialog.TRANSPARENCY) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    if (enabled) {
                        OwnTVButton(stringResource(R.string.settings_subtitle_reset_all), style = OwnTVButtonStyle.SECONDARY, onClick = {
                        onScale(SubtitleStyle.SCALE_DEFAULT)
                        onFont(null)
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
private enum class SubDialog { NONE, SIZE, FONT, COLOR, POSITION, TRANSPARENCY }

@Composable
private fun subtitleFontFamilyLabel(family: AppFontFamily): String = stringResource(
    when (family) {
        AppFontFamily.LORA -> R.string.settings_font_lora
        AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
        AppFontFamily.MONOSPACE -> R.string.settings_font_monospace
        AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
        AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
        AppFontFamily.POPPINS -> R.string.settings_font_poppins
    },
)

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
            modifier = Modifier.fillMaxSize().modalScrim()
                .imePadding().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 18.dp)) {
                Text(stringResource(R.string.settings_subtitle_color), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_color_default_description),
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
                        label = stringResource(R.string.settings_subtitle_hex),
                        placeholder = "FFFFFF",
                        modifier = Modifier.width(170.dp),
                    )
                    OwnTVButton(stringResource(R.string.settings_apply), onClick = {
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
                    Text(stringResource(R.string.settings_subtitle_color_hex_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
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
                    OwnTVButton(stringResource(R.string.settings_subtitle_use_default), style = OwnTVButtonStyle.SECONDARY, onClick = {
                        hexInput = ""
                        hexError = false
                        onColor(SubtitleStyle.COLOR_DEFAULT)
                    })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
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
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 430.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
                Text(stringResource(R.string.settings_subtitle_position), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_position_default_description),
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
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
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
                subtitlePositionName(position), style = MaterialTheme.typography.labelMedium, color = labelColor,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = position.alignment(),
                ) {
                    Box(
                        Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (selected) colors.onPrimaryContainer else colors.outline),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitlePositionName(position), style = MaterialTheme.typography.labelSmall, color = labelColor,
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
    font: AppFontFamily?,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDefault = !SubtitleStyle.hasOpacity(bgOpacity)
    // From "Default" either button adopts the mid value first, so neither is ever a dead end.
    val effective = if (isDefault) SubtitleStyle.OPACITY_START else bgOpacity
    val minusEnabled = isDefault || effective > SubtitleStyle.OPACITY_MIN
    val plusEnabled = isDefault || effective < SubtitleStyle.OPACITY_MAX
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(plusEnabled, minusEnabled)
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.dialogPanel(width = 380.dp, corner = 16.dp, padding = 18.dp, scroll = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.settings_subtitle_background_transparency), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_background_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
        SubtitlePreview(
            enabled = true, scale = scale, font = font, color = color, position = position,
                    bgOpacity = bgOpacity, height = 92.dp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(steppers.minus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective - SubtitleStyle.OPACITY_STEP).coerceAtLeast(SubtitleStyle.OPACITY_MIN),
                        )
                    }
                    Text(
                        subOpacityLabel(bgOpacity), style = MaterialTheme.typography.titleMedium,
                        color = colors.primary, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center,
                    )
                    StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(steppers.plus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective + SubtitleStyle.OPACITY_STEP).coerceAtMost(SubtitleStyle.OPACITY_MAX),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(stringResource(R.string.settings_subtitle_use_default), style = OwnTVButtonStyle.SECONDARY, onClick = { onSet(SubtitleStyle.OPACITY_DEFAULT) })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
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
    font: AppFontFamily? = null,
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
        contentAlignment = anchor.alignment(),
    ) {
        Text(
            stringResource(R.string.settings_subtitle_preview_sample),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale,
                fontFamily = if (enabled && font != null) font.asComposeFamily()
                    else MaterialTheme.typography.bodyLarge.fontFamily,
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
                stringResource(R.string.settings_subtitle_preview_stock),
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
