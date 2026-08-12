package tv.own.owntv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.displayLabel
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalActionSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.format.localizedDecimal

private val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
private val TEAL = Color(0xFF52DBC8)

@Composable
internal fun MediaSpec.displayText(): String {
    val decoderText = decoder?.let {
        when (it) {
            is DecoderSpec.Hardware -> buildList {
                add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Software -> buildList {
                add(stringResource(R.string.player_decoder_software))
                if (it.gpu) add(stringResource(R.string.player_decoder_gpu))
            }.joinToString(stringResource(R.string.player_metadata_separator))
            is DecoderSpec.Named -> buildList {
                add(
                    when (it.value.lowercase()) {
                        "exoplayer" -> stringResource(R.string.settings_player_exoplayer)
                        "mpv" -> stringResource(R.string.settings_player_mpv)
                        else -> it.value
                    },
                )
                if (it.hardware) add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }.joinToString(stringResource(R.string.player_metadata_separator))
        }
    }
    return listOfNotNull(codec, resolution, decoderText)
        .joinToString(stringResource(R.string.player_metadata_separator))
}

@Composable
private fun PlaybackFailure.displayText(): String = when (this) {
    PlaybackFailure.Channel -> stringResource(R.string.player_error_channel)
    PlaybackFailure.LostConnection -> stringResource(R.string.player_error_lost_connection)
    PlaybackFailure.StreamLink -> stringResource(R.string.player_error_stream_link)
    PlaybackFailure.NotStreaming -> stringResource(R.string.player_error_not_streaming)
    PlaybackFailure.AudioNoVideo -> stringResource(R.string.player_error_audio_no_video)
    PlaybackFailure.FileCorrupt -> stringResource(R.string.player_error_file_corrupt)
    PlaybackFailure.MultipleVideos -> stringResource(R.string.player_error_multiple_videos)
    PlaybackFailure.DecoderBusy -> stringResource(R.string.player_error_decoder_busy)
    PlaybackFailure.NoInternet -> stringResource(R.string.player_error_no_internet)
    PlaybackFailure.Surround -> stringResource(R.string.player_error_surround)
    PlaybackFailure.ImageSubtitleAudio -> stringResource(R.string.player_error_image_subtitle_audio)
    PlaybackFailure.ImageFormat -> stringResource(R.string.player_error_image_format)
    PlaybackFailure.ImageShow -> stringResource(R.string.player_error_image_show)
    PlaybackFailure.BothEnginesExoFirst -> stringResource(R.string.player_error_both_engines_exo_first)
    is PlaybackFailure.BothEnginesMpvFirst -> stringResource(
        R.string.player_error_both_engines_mpv_first,
        exoError.displayText(),
    )
    is PlaybackFailure.ExoDecode -> stringResource(R.string.player_error_exo_decode, code)
    is PlaybackFailure.ExoPlay -> stringResource(R.string.player_error_exo_play, code)
    is PlaybackFailure.HardwareFallback -> stringResource(R.string.player_error_hardware_fallback, resolution)
    is PlaybackFailure.HardwareDisabled -> stringResource(R.string.player_error_hardware_disabled, resolution)
    is PlaybackFailure.HardwareFormat -> stringResource(R.string.player_error_hardware_format, resolution, codec)
    is PlaybackFailure.StreamUnavailable -> stringResource(
        R.string.player_error_stream_unavailable,
        if (customUserAgentHint) stringResource(R.string.player_error_custom_user_agent) else "",
    )
    PlaybackFailure.MpvOpenDecode -> stringResource(R.string.player_error_mpv_open_decode)
    PlaybackFailure.MpvStreamNeverStarted -> stringResource(R.string.player_error_mpv_stream_never_started)
    is PlaybackFailure.Raw -> message
}

private const val DIRECT_TUNE_TIMEOUT_MS = 2_000L
private const val DIRECT_TUNE_FEEDBACK_MS = 1_500L
private const val DIRECT_TUNE_PLAYBACK_WAIT_MS = 8_000L
private const val MAX_DIRECT_TUNE_DIGITS = 5

private enum class HudDialog { NONE, AUDIO, SUBS, SPEED, ZOOM, VOLUME, SUB_TIMING }

/** What the top-left channel OSD shows for direct tune: the digits being typed, the channel a number
 *  resolved to, or a failure message. All three render as the same card as the channel OSD. */
private sealed interface TuneOsd {
    data class Entry(val digits: String) : TuneOsd
    data class Tuned(val info: DirectTuneChannelInfo) : TuneOsd
    data class Message(val digits: String, val text: String) : TuneOsd
}

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    onPip: (() -> Unit)? = null,
    onMultiView: (() -> Unit)? = null, // enter MultiView seeded with this channel (live only)
    // Switch to audio-only mode (stops video decode, surfaces the top-bar now-playing bar). Null hides it.
    onAudioMode: (() -> Unit)? = null,
    // True while the shell draws an overlay ABOVE the HUD (e.g. the channel-list overlay). The HUD goes
    // inert: its auto-hide timer pauses and — crucially — it makes no focus requests, so it can't yank
    // D-pad focus off the overlay. The existing dialog guard below covers only the HUD's OWN dialogs;
    // shell-level overlays need this flag. Default false = no behavior change for other callers.
    inert: Boolean = false,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    // Live: open the channel-list overlay (Left while the controls are hidden). Null = not a live channel.
    onOpenChannelList: (() -> Unit)? = null,
    // Live: open the watch-history list (Right while the controls are hidden) — jump straight back to a
    // recent channel without leaving full-screen. Null = not a live channel.
    onOpenHistoryList: (() -> Unit)? = null,
    // Live rewind / timeshift (catch-up channels). onRewindLive non-null = this live channel can rewind;
    // timeshiftOffsetSec non-null = currently watching that many seconds behind the live edge.
    onRewindLive: (() -> Unit)? = null,
    onForwardLive: (() -> Unit)? = null,
    onGoToLive: (() -> Unit)? = null,
    onScrubLive: ((Int) -> Unit)? = null, // timeline scrub: +sec = back, −sec = toward live
    timeshiftOffsetSec: Int? = null,
    // Direct tune: enter a provider channel number to switch channels. Null = disabled (not live / no channel).
    onTuneToNumber: (suspend (Int) -> DirectTuneResult)? = null,
    // Channel identity key for direct tune: changing this cancels any in-flight submission.
    directTuneContextKey: Long = 0L,
    // Live "compatibility mode": pin this channel to the mpv engine (fixes UHD artifacts / undecodable
    // streams ExoPlayer can't handle). null = not a live channel; true = currently pinned to mpv.
    compatMode: Boolean? = null,
    onToggleCompatMode: (() -> Unit)? = null,
    // True picture-in-picture corner controls — shown only while a second (corner) stream is running.
    // onCornerClose non-null = a corner is active; cornerAudioOn = the corner currently has the sound.
    onCornerSwap: (() -> Unit)? = null,   // swap the corner stream into the main window (and vice versa)
    onCornerAudio: (() -> Unit)? = null,  // move the audio between the main and corner windows
    onCornerMove: (() -> Unit)? = null,   // cycle the corner window through the four screen corners
    onCornerGrow: (() -> Unit)? = null,   // grow the corner window +10% (capped)
    onCornerShrink: (() -> Unit)? = null, // shrink the corner window -10% (never below the base size)
    onCornerClose: (() -> Unit)? = null,  // close the corner window
    onChangeMain: (() -> Unit)? = null,   // pick a new channel for the FULL-SCREEN window (corner untouched)
    onChangeCorner: (() -> Unit)? = null, // pick a new channel for the PiP corner (main untouched)
    cornerAudioOn: Boolean = false,
    // VOD engine toggle: switch THIS movie/episode between mpv and ExoPlayer (e.g. to reach tracks only
    // one engine exposes, or to try the other engine on a problem file). null = not a VOD;
    // true = currently playing on ExoPlayer.
    vodOnExo: Boolean? = null,
    onToggleVodEngine: (() -> Unit)? = null,
    // Movie/episode only: open the OpenSubtitles search from the Subtitles dialog (subtitle plan §4).
    // Null for Live TV and when there's no current-item context, which hides the ADD SUBTITLES row.
    onSearchSubtitles: (() -> Unit)? = null,
    // Movie/episode only: pick a local subtitle file (plan §7) — no account needed, same gating.
    onSelectLocalSubtitle: (() -> Unit)? = null,
    // Favorite toggle for the CURRENT item (live channel / movie / series). Null hides the button
    // (no item context). [favorite] = current state — fills the star teal when true.
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    // Live guide card (Before / Now playing / Next for the playing channel) — supplied by the shell
    // (the EPG data lives in LiveViewModel, not the player). Rendered on the right edge whenever the
    // controls are visible, like the top-bar channel card; informational only, never focusable.
    liveEpgCard: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
    val providerBackOff by player.providerBackOff.collectAsStateWithLifecycle()
    val nav by player.nav.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val streamChips by player.streamChips.collectAsStateWithLifecycle()
    val engineChip by player.engineChip.collectAsStateWithLifecycle()
    val audioCount by player.audioCount.collectAsStateWithLifecycle()
    val audioDelayMs by player.audioDelayMs.collectAsStateWithLifecycle()
    val subCount by player.subCount.collectAsStateWithLifecycle()
    val zoomMode by player.zoomMode.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val isLive = player.isLiveContent
    val switchedToExo = stringResource(R.string.player_switch_exo)
    val switchedToMpv = stringResource(R.string.player_switch_mpv)
    val tuneNotFound = stringResource(R.string.player_channel_not_found)
    val multipleChannels = stringResource(R.string.player_multiple_channels)
    val tuneFailed = stringResource(R.string.player_tune_failed)

    val nextUpTitle by player.nextUpTitle.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf(HudDialog.NONE) }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val catchFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    // Next-episode countdown card (VOD queues only): appears in the last ~30s before the automatic
    // advance (which fires at duration − 8s), counts down to it, and offers Play now / Cancel.
    var autoNextDismissed by remember { mutableStateOf(false) }
    // Re-arm when the queued next episode changes (i.e. after an advance to a new item).
    LaunchedEffect(nextUpTitle, nav.hasNext) { autoNextDismissed = false }
    val msToAdvance = if (!isLive && duration > 0L) (duration - 8_000L) - position else Long.MAX_VALUE
    val showNextCard = !isLive && error == null && nav.hasNext && nextUpTitle != null &&
        msToAdvance in 0L..30_000L && !autoNextDismissed
    val nextCountdown = ((msToAdvance + 999L) / 1000L).toInt().coerceIn(0, 30)

    var controlsVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) } // stream technical-info overlay
    // Used only by "Report this stream", which writes the current readout into the playback log (F18).
    val reportContext = androidx.compose.ui.platform.LocalContext.current
    var wakeTick by remember { mutableIntStateOf(0) }
    val forceShow = error != null || dialog != HudDialog.NONE
    // First Back hides the controls (instead of leaving the channel); with the controls already hidden
    // this handler is disabled, so Back falls through to the shell, which exits the player. Also disabled
    // while an error/dialog is up (a dialog handles its own Back; an error should exit).
    BackHandler(enabled = controlsVisible && !forceShow) { controlsVisible = false }
    // Channel zap (live only): a brief "now watching" card on up/down without revealing the full HUD.
    val canZap = onChannelUp != null && onChannelDown != null
    var channelFlash by remember { mutableIntStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(channelFlash) { if (channelFlash > 0) { showFlash = true; delay(3000); showFlash = false } }

    // Engine-switch confirmation toast: a brief "Switched to MPV/ExoPlayer" at the bottom-center when the
    // user flips the engine via the HUD toggle. Mirrors the channel-flash pattern above.
    var engineMsg by remember { mutableStateOf<String?>(null) }
    var engineFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(engineFlash) { if (engineFlash > 0) { delay(1800); engineMsg = null } }
    // Wrap the engine toggles so a click also surfaces the toast naming the engine we're switching TO.
    val toggleCompat: (() -> Unit)? = onToggleCompatMode?.let { cb -> {
        engineMsg = if (compatMode == true) switchedToExo else switchedToMpv; engineFlash++; cb()
    } }
    val toggleVod: (() -> Unit)? = onToggleVodEngine?.let { cb -> {
        engineMsg = if (vodOnExo == true) switchedToMpv else switchedToExo; engineFlash++; cb()
    } }

    // ---- Direct tune (channel-number entry) ----
    var digitBuffer by remember { mutableStateOf("") }
    var submissionRequest by remember { mutableStateOf<Int?>(null) }
    var submissionTick by remember { mutableIntStateOf(0) }

    var lookupInFlight by remember { mutableStateOf(false) }

    var tuneOsd by remember { mutableStateOf<TuneOsd?>(null) }
    var tuneOsdTick by remember { mutableIntStateOf(0) }

    val digitsActive = digitBuffer.isNotEmpty()
    val heldDigitKeys = remember { mutableSetOf<Key>() }

    val cancelDirectTune: () -> Unit = {
        digitBuffer = ""
        submissionRequest = null
        tuneOsd = null
        heldDigitKeys.clear()
        submissionTick++
        tuneOsdTick++
    }

    val zap: (Int) -> Unit = { d ->
        cancelDirectTune()
        (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++
    }

    // Restartable timeout: each new digit restarts the ~2 s window. On expiry, submit.
    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isEmpty()) return@LaunchedEffect
        delay(DIRECT_TUNE_TIMEOUT_MS)
        val num = digitBuffer.toIntOrNull()
        digitBuffer = ""
        if (num != null) { submissionRequest = num; submissionTick++ }
        else tuneOsd = null
    }
    // Submission: keyed on the immutable tick so setting submissionRequest=null doesn't cancel us.
    // lookupInFlight covers only the suspend callback, not the result-display period.
    LaunchedEffect(submissionTick) {
        val num = submissionRequest ?: return@LaunchedEffect
        submissionRequest = null
        lookupInFlight = true
        val result = try {
            onTuneToNumber?.invoke(num)
        } finally {
            lookupInFlight = false
            // A KeyUp can be lost when focus or the window changes mid-entry (dialog, PiP, app switch),
            // which would strand that digit in the held set and make the key dead until the next KeyUp.
            // A completed submission ends the entry, so no held state can legitimately survive it.
            heldDigitKeys.clear()
        }
        tuneOsd = when (result) {
            is DirectTuneResult.Found -> TuneOsd.Tuned(result.channel)
            is DirectTuneResult.NotFound -> TuneOsd.Message(num.toString(), tuneNotFound)
            is DirectTuneResult.Ambiguous -> TuneOsd.Message(num.toString(), multipleChannels)
            is DirectTuneResult.Failed -> TuneOsd.Message(num.toString(), tuneFailed)
            is DirectTuneResult.Cancelled -> null
            null -> null
        }
        if (tuneOsd != null) tuneOsdTick++
    }
    // Result-feedback expiry, keyed on tuneOsdTick so a new entry invalidates the old timer. A tuned
    // channel holds the OSD until the new stream is actually on screen (the lookup returns the moment
    // playback is KICKED OFF, not when it starts) and then DIRECT_TUNE_FEEDBACK_MS longer.
    LaunchedEffect(tuneOsdTick) {
        when (val osd = tuneOsd) {
            is TuneOsd.Tuned -> {
                if (osd.info.restarted) {
                    withTimeoutOrNull(DIRECT_TUNE_PLAYBACK_WAIT_MS) {
                        // Two phases: the outgoing stream can still report playing for a beat (Stalker/mpv
                        // resolve their URL asynchronously), so wait for the teardown before the start.
                        snapshotFlow { isPlaying && !buffering && error == null }.first { !it }
                        snapshotFlow { (isPlaying && !buffering) || error != null }.first { it }
                    }
                }
                delay(DIRECT_TUNE_FEEDBACK_MS)
                tuneOsd = null
            }
            is TuneOsd.Message -> { delay(DIRECT_TUNE_FEEDBACK_MS); tuneOsd = null }
            is TuneOsd.Entry, null -> Unit
        }
    }
    // Cancellation triggers (CH+/-, D-pad, overlay open, HUD dialog open).
    LaunchedEffect(inert) { if (inert) cancelDirectTune() }
    LaunchedEffect(dialog) { if (dialog != HudDialog.NONE) cancelDirectTune() }
    // Channel-key cleanup: narrow to pending entry state only. Do not clear timed result feedback
    // from a successful tune that changed the playing channel.
    LaunchedEffect(directTuneContextKey) {
        if (digitBuffer.isNotEmpty() || submissionRequest != null) {
            digitBuffer = ""
            submissionRequest = null
            heldDigitKeys.clear()
            submissionTick++
            // Abandoned digits have no timer of their own — drop the card with the entry it belonged to.
            if (tuneOsd is TuneOsd.Entry) tuneOsd = null
        }
    }
    // Back cancels digit entry before it hides/exits controls.
    BackHandler(enabled = digitsActive) { digitBuffer = ""; tuneOsd = null }

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
    LaunchedEffect(controlsVisible, player) { if (controlsVisible) player.refreshStreamChips() }
    DisposableEffect(showInfo, player) {
        if (showInfo) player.refreshStreamChips()
        player.setBitrateTrackingEnabled(showInfo)
        onDispose { player.setBitrateTrackingEnabled(false) }
    }
    LaunchedEffect(controlsVisible, wakeTick, forceShow, inert) {
        // Don't auto-hide under an overlay — hiding is what triggers the catch-all focus grab below.
        if (controlsVisible && !forceShow && !inert) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible, error, dialog, inert, showNextCard) {
        // Never steal focus while a dialog is open (its rows own it) or while a shell overlay is up
        // (inert — the overlay owns the D-pad); when either closes this re-runs and hands focus back.
        if (dialog != HudDialog.NONE || inert) return@LaunchedEffect
        // The next-episode countdown card owns focus while it's up so Play now / Cancel are reachable.
        if (showNextCard) { runCatching { nextFocus.requestFocus() }; return@LaunchedEffect }
        if (controlsVisible) {
            if (error != null) runCatching { retryFocus.requestFocus() } else runCatching { playFocus.requestFocus() }
        } else runCatching { catchFocus.requestFocus() }
    }

    // The player sits over opaque video (never a glass surface — see Glass.kt), so its HUD buttons
    // stay flat regardless of glass mode: opt out of the DIALOGS default explicitly.
    CompositionLocalProvider(LocalActionSurface provides null) {
    Box(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
            // ---- Direct-tune digit capture (before the existing KeyDown guard) ----
            // Number keys are consumed globally here, HUD visible or not: on a TV remote a digit press
            // during live playback can only mean "tune to this channel", and swallowing both KeyDown and
            // KeyUp keeps a half-typed number from leaking into whatever else is focused underneath.
            // onTuneToNumber is null outside fullscreen live (see OwnTVShell), so nothing else is affected.
            if (onTuneToNumber != null && !inert && dialog == HudDialog.NONE) {
                val digit = keyToDigit(e.key)
                if (digit != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        heldDigitKeys.remove(e.key)
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        if (lookupInFlight || !heldDigitKeys.add(e.key)) {
                            return@onPreviewKeyEvent true
                        }
                        val enteredDigits = digitBuffer + digit
                        tuneOsd = TuneOsd.Entry(enteredDigits)
                        tuneOsdTick++
                        if (enteredDigits.length == MAX_DIRECT_TUNE_DIGITS) {
                            digitBuffer = ""
                            submissionRequest = enteredDigits.toIntOrNull()
                            submissionTick++
                        } else {
                            digitBuffer = enteredDigits
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                // Enter/Center/NumpadEnter: submit immediately while digits are pending.
                if (e.type == KeyEventType.KeyDown && !lookupInFlight && digitsActive &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    val num = digitBuffer.toIntOrNull()
                    digitBuffer = ""
                    if (num != null) { submissionRequest = num; submissionTick++ }
                    return@onPreviewKeyEvent true
                }
            }
            // ---- Existing key handling (unchanged, but skip for digit KeyUp already consumed above) ----
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Channel surfing: dedicated CH+/CH- and media prev/next keys always zap. D-pad Up/Down
                // zap ONLY while the HUD is hidden (when it's visible, Up/Down navigate the controls) —
                // this is the only way to change channels on remotes without CH keys (e.g. Fire TV).
                //
                // Direction is channel-number order, not list-position order: "up" (CH+, D-pad Up) is
                // always the NEXT channel — further down an ascending list, delta +1 — matching the
                // de facto TV convention (Live Channels, YouTube TV, Pluto TV). All of these keys move
                // the same way; there is deliberately no split between CH+ and D-pad Up. Wrapping is
                // intended: CH-/Down from the first channel lands on the last, and vice versa.
                canZap && (e.key == Key.ChannelUp || e.key == Key.MediaNext) -> { zap(1); true }
                canZap && (e.key == Key.ChannelDown || e.key == Key.MediaPrevious) -> { zap(-1); true }
                canZap && !controlsVisible && e.key == Key.DirectionUp -> { zap(1); true }
                canZap && !controlsVisible && e.key == Key.DirectionDown -> { zap(-1); true }
                // The category list lives at logical Start; history lives at logical End.
                onOpenChannelList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START -> { onOpenChannelList(); true }
                onOpenHistoryList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END -> { onOpenHistoryList(); true }
                controlsVisible -> { wakeTick++; false }
                else -> false
            }
        },
    ) {
        if (!controlsVisible && !showNextCard) {
            Box(
                Modifier.fillMaxSize().focusRequester(catchFocus).focusable()
                    .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key != Key.Back) { controlsVisible = true; true } else false },
            )
        }

        // Stream technical info — drawn over everything (and kept up even when the controls auto-hide), so
        // you can read live bitrate/buffer while watching. Toggled from the bottom bar's info button.
        if (showInfo) {
            // Sits clear of the taller unified top strip (logo + guide) rather than under the old title row.
            StreamInfoOverlay(player, modifier = Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 20.dp))
        }

        // Top-left OSD stack: the channel card (briefly on a zap, or the freshly tuned channel) plus the
        // direct-tune card, which pushes down under it. Drawn outside the controls-visible block so both
        // zapping and digit entry stay visible with the HUD hidden. With the controls up the unified top
        // strip already names the channel, so only a direct tune — whose card names the channel the stream
        // is still switching to — draws here.
        val tuned = (tuneOsd as? TuneOsd.Tuned)?.info
        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 28.dp, top = if (controlsVisible) 92.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLive && (tuned != null || (showFlash && !controlsVisible))) {
                // A fresh tune drives the card from the lookup result, not player metadata: the Stalker and
                // mpv paths publish their metadata after an async resolve, which would show the old channel.
                if (tuned != null) {
                    ChannelOsdCard(title = tuned.name, subtitle = tuned.number?.let { stringResource(R.string.player_channel_number, it) }, logoUrl = tuned.logoUrl)
                } else {
                    ChannelCard(player)
                }
            }
            when (val osd = tuneOsd) {
                is TuneOsd.Entry -> ChannelNumberCard(osd.digits)
                is TuneOsd.Message -> ChannelNumberCard(osd.digits, error = osd.text)
                is TuneOsd.Tuned, null -> Unit
            }
        }

        if (controlsVisible) {
            // Scrims: a FLAT semi-transparent panel behind the controls, feathered to transparent only at
            // the inner edge. A pure gradient faded out exactly where the chips and the Now/Next text sit,
            // so those washed out on bright scenes; a hard-edged band would instead draw a visible seam
            // across the picture. The colour stops give the panel first, then the feather.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(210.dp)
                .background(Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.72f),
                    0.5f to Color.Black.copy(alpha = 0.68f),
                    1.0f to Color.Transparent,
                )))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.68f),
                    1.0f to Color.Black.copy(alpha = 0.78f),
                )))

            // The active engine (MPV/EXO) leads the mini chips so users can always tell which player is on.
            // One unified strip: back · logo · chips-over-channel-name · Now/Next guide. The channel name
            // used to be drawn twice (here and in a floating card below), with the guide stranded on the
            // right edge — that space belongs to the history list now.
            TopBar(
                player, isLive, listOfNotNull(engineChip) + streamChips.ifEmpty { listOfNotNull(videoRes) }, duration, onBack,
                modifier = Modifier.align(Alignment.TopStart),
                trailing = if (error == null) liveEpgCard else null,
            )

            // Hide the transport (play/seek/prev/next) and bottom bar while an error is up — the error
            // overlay owns the screen with its own Retry, so the play/rewind/forward must not show behind it.
            if (error == null) {
                CenterControls(player, nav, isPlaying, isLive, onRewindLive, onForwardLive, onGoToLive, timeshiftOffsetSec, playFocus, modifier = Modifier.align(Alignment.Center))

                val reportPosition = formatTime(position)
                val reportDuration = duration.takeIf { it > 0 }?.let { formatTime(it) }
                val reportSavedMessage = stringResource(R.string.player_report_saved)

                BottomBar(
                    player = player, isLive = isLive, position = position, duration = duration,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    speedLabel = formatSpeed(speed),
                    onScrubLive = onScrubLive, timeshiftOffsetSec = timeshiftOffsetSec,
                    compatMode = compatMode, onToggleCompatMode = toggleCompat,
                    vodOnExo = vodOnExo, onToggleVodEngine = toggleVod,
                    onInfo = { showInfo = !showInfo }, infoOn = showInfo,
                    onReport = {
                        val meta = player.currentMeta.value
                        val snapshot = buildString {
                            appendLine(player.streamInfo().joinToString("\n") { (k, v) -> "  $k: $v" })
                            appendLine("  position: $reportPosition${reportDuration?.let { " / $it" }.orEmpty()}")
                        }
                        PlaybackErrorLog.report(
                            context = reportContext,
                            engine = engineChip ?: "?",
                            live = isLive,
                            title = meta.title,
                            snapshot = snapshot,
                        )
                        engineMsg = reportSavedMessage
                        engineFlash++
                    },
                    favorite = favorite, onToggleFavorite = onToggleFavorite,
                    onOpenDialog = { dialog = it }, onPip = onPip, onMultiView = onMultiView, onAudioMode = onAudioMode, onBack = onBack,
                    onCornerSwap = onCornerSwap, onCornerAudio = onCornerAudio, onCornerMove = onCornerMove, onCornerGrow = onCornerGrow, onCornerShrink = onCornerShrink, onCornerClose = onCornerClose,
                    onChangeMain = onChangeMain, onChangeCorner = onChangeCorner,
                    cornerAudioOn = cornerAudioOn,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // Next-episode countdown card (VOD queue only) — surfaces the automatic advance with Play now /
        // Cancel. Shown independently of the main controls so it appears even after they auto-hide.
        if (showNextCard) {
            NextEpisodeCard(
                seconds = nextCountdown,
                title = nextUpTitle ?: "",
                playFocus = nextFocus,
                onPlayNow = { autoNextDismissed = true; player.next() },
                onCancel = { autoNextDismissed = true; player.cancelAutoNext() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 120.dp),
            )
        }

        // Engine-switch confirmation toast (bottom-center, semi-transparent) — shown briefly after the
        // user flips the engine via the HUD's MPV/EXO toggle.
        engineMsg?.let { msg ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Status overlay (always shown).
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_playback_error), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                error?.let {
                    Text(it.displayText(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                // Structured technical detail so a user can report the real cause without adb/logcat:
                // plain reason → media spec (codec • resolution • decoder) → raw engine/codec line.
                errorInfo?.let { info ->
                    info.reason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.displayText(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.displayText(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.player_raw_error, it), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                Spacer(Modifier.height(18.dp))
                OwnTVButton(stringResource(R.string.common_retry), onClick = { player.retry() }, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(retryFocus))
            }
            // A provider wait looks like loading, because that is what it is: the channel is queued behind
            // the panel's own countdown and the engine re-asks by itself. The line under the spinner says
            // why nothing is happening yet, so nobody reaches for Retry (or thinks the channel is dead).
            buffering -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                OwnTVSpinner(sizeDp = 56)
                providerBackOff?.let { wait ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            R.string.player_provider_retry_after,
                            wait.httpCode,
                            wait.message ?: stringResource(R.string.player_provider_busy),
                            wait.secondsLeft,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }
    } // CompositionLocalProvider

    when (dialog) {
        // Track lists are SNAPSHOT once when the dialog opens (re-polled only while still empty —
        // heavy HDR/DTS streams report their tracks late). Reading player.xxxTracks() directly in
        // composition handed the dialog a fresh list on every HUD recomposition, endlessly rebuilding
        // the rows and losing/yanking D-pad focus.
        HudDialog.AUDIO -> {
            var audioTracks by remember { mutableStateOf(player.audioTracks()) }
            LaunchedEffect(Unit) { while (audioTracks.isEmpty()) { delay(300); audioTracks = player.audioTracks() } }
            TrackDialog(
                stringResource(R.string.player_audio_track), audioTracks,
                onSelect = { player.selectAudio(it.mpvId); dialog = HudDialog.NONE }, onOff = null,
                onDismiss = { dialog = HudDialog.NONE },
                // A/V-sync nudge wherever the engine can actually shift audio: mpv, VOD *and* live (a live
                // stream can arrive with the provider's own drift baked in). Hidden on ExoPlayer (F19e).
                audioDelayMs = if (player.audioDelayAvailable()) audioDelayMs else null,
                onAdjustAudioDelay = if (player.audioDelayAvailable()) ({ d -> player.adjustAudioDelay(d) }) else null,
            )
        }
        HudDialog.SUBS -> {
            var subTracks by remember { mutableStateOf(player.textTracks()) }
            LaunchedEffect(Unit) { while (subTracks.isEmpty()) { delay(300); subTracks = player.textTracks() } }
            TrackDialog(
                stringResource(R.string.player_subtitles), subTracks,
                onSelect = { player.selectSubtitle(it.mpvId); dialog = HudDialog.NONE },
                onOff = { player.disableSubtitles(); dialog = HudDialog.NONE },
                onDismiss = { dialog = HudDialog.NONE },
                onSearchSubtitles = onSearchSubtitles?.let { open -> { dialog = HudDialog.NONE; open() } },
                onSelectLocalSubtitle = onSelectLocalSubtitle?.let { open -> { dialog = HudDialog.NONE; open() } },
                // Subtitle timing (plan §8): only when adjustment applies to the ACTIVE subtitle on the
                // current engine (any mpv text sub; external side-loads on ExoPlayer).
                onSubtitleTiming = if (player.subtitleTimingAvailable()) ({ dialog = HudDialog.SUB_TIMING }) else null,
            )
        }
        HudDialog.SUB_TIMING -> SubtitleTimingDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SPEED -> SpeedDialog(current = speed, onSelect = { player.setSpeed(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.ZOOM -> ZoomDialog(current = zoomMode, onSelect = { player.setZoomMode(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.VOLUME -> VolumeDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.NONE -> Unit
    }
}

// ---------------- Top bar ----------------

@Composable
private fun TopBar(
    player: PlaybackEngine, isLive: Boolean, chips: List<String>, duration: Long,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    // Live only: the Now/Next guide, rendered at the far end of the same strip.
    trailing: (@Composable () -> Unit)? = null,
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    val localizedSubtitle = meta.localizedSubtitle()
    val vodSubtitle = if (isLive) {
        meta.subtitle
    } else {
        buildList {
            localizedSubtitle?.takeIf { it.isNotBlank() }?.let(::add)
            meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        }.joinToString(stringResource(R.string.content_metadata_separator)).ifBlank { null }
    }
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        // Live: the channel logo sits with the channel NAME (identity), not with the programme — so the
        // whole "which channel am I on" group reads as one unit however wide the TV is.
        if (isLive) {
            ChannelLogo(meta.logoUrl, displayTitle, size = 46)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            val chipRow: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val durMin = (duration / 60000)
                    val parts = buildList {
                        meta.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (!isLive && durMin > 0) add(stringResource(R.string.player_duration_minutes, durMin))
                        addAll(chips) // aspect · resolution · fps · audio
                    }
                    parts.forEachIndexed { i, label ->
                        if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                    }
                    if (isLive) {
                        if (parts.isNotEmpty()) Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                        LiveBadge()
                    }
                }
            }
            // Live stacks the technical chips ABOVE the channel name; VOD keeps title-then-chips.
            if (isLive) {
                chipRow()
                Spacer(Modifier.height(2.dp))
                // Channel number ahead of the name — this is where you look to learn the number of a
                // channel you arrived at by zapping. meta.subtitle carries it ("#123") only while the
                // "Channel numbers" setting is on, so an off setting leaves the name alone.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.45f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(displayTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                vodSubtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(displayTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                chipRow()
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(28.dp))
            trailing()
        }
    }
}

/** The channel logo tile, falling back to the first letters of the channel name. */
@Composable
private fun ChannelLogo(logoUrl: String?, title: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text((title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCCDC3232)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text(stringResource(R.string.player_live), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** The player's channel OSD: channel logo beside its name and number. */
@Composable
private fun ChannelOsdCard(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)), contentAlignment = Alignment.Center) {
            if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text((title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title ?: "", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    ChannelOsdCard(title = displayTitle, subtitle = meta.localizedSubtitle(), logoUrl = meta.logoUrl, modifier = modifier)
}

/** Direct-tune entry OSD: the number as it's typed, on the same surface (position, radius, scrim) the
 *  channel card uses, so a resolved number simply becomes that card. A blinking caret says "still
 *  accepting digits" and the bar along the bottom drains over the auto-submit window, so the wait is
 *  visible instead of mysterious. [error] turns it into the failure readout for the same number. */
@Composable
private fun ChannelNumberCard(digits: String, error: String? = null, modifier: Modifier = Modifier) {
    val caret = rememberInfiniteTransition(label = "tuneCaret")
    val caretAlpha by caret.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "tuneCaretAlpha",
    )
    val countdown = remember { Animatable(0f) }
    LaunchedEffect(digits, error) {
        if (error != null) { countdown.snapTo(0f); return@LaunchedEffect }
        countdown.snapTo(1f)
        countdown.animateTo(0f, tween(DIRECT_TUNE_TIMEOUT_MS.toInt(), easing = LinearEasing))
    }
    Column(
        modifier.widthIn(min = 148.dp, max = 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f))
            // Painted, not laid out: a real bar would fillMaxWidth and stretch the card to its max width.
            .drawWithContent {
                drawContent()
                val barHeight = 3.dp.toPx()
                val top = Offset(0f, size.height - barHeight)
                drawRect(Color.White.copy(alpha = 0.08f), topLeft = top, size = Size(size.width, barHeight))
                drawRect(TEAL, topLeft = top, size = Size(size.width * countdown.value, barHeight))
            }
            .padding(bottom = 3.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                stringResource(R.string.player_channel_label),
                style = MaterialTheme.typography.labelSmall, color = TEAL, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    digits,
                    style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                if (error == null) {
                    Box(
                        Modifier.padding(start = 4.dp, bottom = 4.dp).width(3.dp).height(22.dp)
                            .clip(RoundedCornerShape(2.dp)).background(TEAL.copy(alpha = caretAlpha)),
                    )
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Center transport ----------------

@Composable
private fun CenterControls(
    player: PlaybackEngine, nav: NavState, isPlaying: Boolean, isLive: Boolean,
    onRewindLive: (() -> Unit)?, onForwardLive: (() -> Unit)?, onGoToLive: (() -> Unit)?, timeshiftOffsetSec: Int?,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
) {
    val rewindMode = onRewindLive != null // this is a catch-up-capable Live channel
    val timeshifting = timeshiftOffsetSec != null
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (timeshifting) {
            // Counts down as the archive catches up to the live edge; grows if you pause.
            Text(
                if (timeshiftOffsetSec <= 1) stringResource(R.string.player_at_live_edge) else stringResource(R.string.player_behind_live, mmss(timeshiftOffsetSec)),
                style = MaterialTheme.typography.labelLarge,
                color = OwnTVTheme.colors.accent,
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (nav.hasPrev) CircleButton(OwnTVIcon.SKIP_PREVIOUS, size = 52) { player.previous() }
            when {
                rewindMode -> CircleButton(OwnTVIcon.REWIND, size = 52) { onRewindLive() } // step back into the archive
                !isLive -> CircleButton(OwnTVIcon.REWIND, size = 52) { player.seekBy(-10_000) }
            }
            CircleButton(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, size = 72, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
            when {
                rewindMode && timeshifting -> CircleButton(OwnTVIcon.FORWARD, size = 52) { onForwardLive!!() } // toward live
                !isLive && !rewindMode -> CircleButton(OwnTVIcon.FORWARD, size = 52) { player.seekBy(10_000) }
            }
            if (rewindMode && timeshifting && onGoToLive != null) {
                CircleButton(OwnTVIcon.LIVE_TV, size = 52, primary = true) { onGoToLive() } // jump to the live edge
            }
            if (nav.hasNext) CircleButton(OwnTVIcon.SKIP_NEXT, size = 52) { player.next() }
        }
    }
}

/** mm:ss for a seconds offset (e.g. 150 → "2:30"). */
@Composable
private fun mmss(sec: Int): String = stringResource(R.string.player_track_seconds, sec / 60, sec % 60)

// ---------------- Bottom bar ----------------

@Composable
private fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffsetSec: Int?,
    compatMode: Boolean?, onToggleCompatMode: (() -> Unit)?,
    vodOnExo: Boolean?, onToggleVodEngine: (() -> Unit)?,
    onInfo: (() -> Unit)? = null, infoOn: Boolean = false, onReport: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onMultiView: (() -> Unit)? = null, onAudioMode: (() -> Unit)?, onBack: () -> Unit,
    onCornerSwap: (() -> Unit)? = null, onCornerAudio: (() -> Unit)? = null, onCornerMove: (() -> Unit)? = null, onCornerGrow: (() -> Unit)? = null, onCornerShrink: (() -> Unit)? = null, onCornerClose: (() -> Unit)? = null,
    onChangeMain: (() -> Unit)? = null, onChangeCorner: (() -> Unit)? = null,
    cornerAudioOn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // Dedicated PiP row (only while a corner stream is up) — its own labeled strip so it's obvious
        // which window each action touches: "Change main" retunes the full-screen stream, "Change PiP"
        // retunes the inset. Kept separate from the media controls so neither row overflows.
        if (onCornerClose != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .focusGroup(),
            ) {
                OwnTVIcon(OwnTVIcon.PIP, tint = TEAL, filled = true, modifier = Modifier.size(16.dp))
                Text(
                    "PiP",
                    style = MaterialTheme.typography.labelLarge,
                    color = TEAL,
                    modifier = Modifier.padding(start = 2.dp, end = 8.dp),
                )
                if (onChangeMain != null) CtrlButton(OwnTVIcon.LIVE_TV, label = "Change main") { onChangeMain() }
                if (onChangeCorner != null) CtrlButton(OwnTVIcon.PLAYLIST, label = "Change PiP") { onChangeCorner() }
                if (onCornerSwap != null) CtrlButton(OwnTVIcon.FULLSCREEN, label = "Swap windows") { onCornerSwap() }
                // Swap-arrows icon on Sound (not a mute glyph): it MOVES the audio between the two windows.
                if (onCornerAudio != null) CtrlButton(OwnTVIcon.SWAP, active = cornerAudioOn, label = "Sound") { onCornerAudio() }
                if (onCornerMove != null) CtrlButton(OwnTVIcon.MOVE, label = "Move") { onCornerMove() }
                if (onCornerGrow != null) CtrlButton(OwnTVIcon.ADD, label = "Size +") { onCornerGrow() }
                if (onCornerShrink != null) CtrlButton(OwnTVIcon.MINUS, label = "Size -") { onCornerShrink() }
                CtrlButton(OwnTVIcon.CLOSE, label = "Close") { onCornerClose() }
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            // Catch-up live channel → a scrubbable live timeline (last LIVE_WINDOW up to the live edge).
            onScrubLive != null -> {
                LiveTimelineBar(offsetSec = timeshiftOffsetSec ?: 0, onScrub = onScrubLive)
                Spacer(Modifier.height(10.dp))
            }
            !isLive && duration > 0 -> {
                SeekBar(positionMs = position, durationMs = duration, onSeek = { player.seekBy(it) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().focusGroup()) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CtrlButton(volumeIcon(volume)) { onOpenDialog(HudDialog.VOLUME) }
                SpeedButton(label = speedLabel, active = speedLabel != stringResource(R.string.player_speed_normal_short)) { onOpenDialog(HudDialog.SPEED) }
                CtrlButton(OwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }) { onOpenDialog(HudDialog.SUBS) }
                CtrlButton(OwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }) { onOpenDialog(HudDialog.AUDIO) }
                // Favorite the current channel/movie/series without leaving the stream (teal heart = on).
                if (onToggleFavorite != null) CtrlButton(OwnTVIcon.FAVORITE, active = favorite) { onToggleFavorite() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Live "compatibility mode" (Live TV + channels opened from the Guide): pin this channel
                // to mpv. The pill shows the active engine and flips on click (teal while pinned to mpv).
                if (onToggleCompatMode != null) {
                    EngineToggle(label = stringResource(if (compatMode == true) R.string.player_engine_mpv else R.string.player_engine_exo), active = compatMode == true) { onToggleCompatMode() }
                }
                // VOD engine toggle (Movies/Series): flip THIS movie/episode between mpv and ExoPlayer.
                // The pill shows the active engine (teal while ExoPlayer owns playback).
                if (onToggleVodEngine != null) {
                    EngineToggle(label = stringResource(if (vodOnExo == true) R.string.player_engine_exo else R.string.player_engine_mpv), active = vodOnExo == true) { onToggleVodEngine() }
                }
                // Aspect/zoom works in every mode now — direct mode resizes the surface view itself
                // (see MpvVideoSurface), GL mode scales internally.
                CtrlButton(OwnTVIcon.ASPECT, active = zoomMode != ZoomMode.FIT) { onOpenDialog(HudDialog.ZOOM) }
                // (The corner/PiP controls live in their own labeled row above — see the top of this Column.)
                if (onPip != null) CtrlButton(OwnTVIcon.PIP, label = "PiP") { onPip() }
                if (onMultiView != null) CtrlButton(OwnTVIcon.VIDEO, label = "MultiView") { onMultiView() } // enter the multi-stream grid
                if (onAudioMode != null) CtrlButton(OwnTVIcon.HEADPHONES) { onAudioMode() }
                // Stream technical info (codec/res/HDR/bitrate/decoder/audio/buffer) — toggles the overlay.
                // Parked at the far right, where the redundant exit-fullscreen button used to sit (Back
                // already leaves the player, so that button never did anything the remote couldn't).
                if (onInfo != null) CtrlButton(OwnTVIcon.INFO, active = infoOn) { onInfo() }
                // "Report this stream": copies the readout the user is looking at into the playback log,
                // so a "this channel judders" complaint carries the codec/decoder/bitrate that caused it.
                // Only offered while the info overlay is open — there is nothing to report otherwise, and
                // the bar stays as short as it was for everyone who never needs this.
                if (infoOn && onReport != null) CtrlButton(OwnTVIcon.SHARE) { onReport() }
            }
        }
    }
}

private fun volumeIcon(volume: Int): OwnTVIcon = when {
    volume == 0 -> OwnTVIcon.VOLUME_MUTE
    volume < 50 -> OwnTVIcon.VOLUME_LOW
    else -> OwnTVIcon.VOLUME_HIGH
}

// ---------------- Buttons ----------------

@Composable
private fun CircleButton(icon: OwnTVIcon, size: Int, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(size.dp),
        shape = CircleShape,
        focusedScale = 1.1f,
        focusedContainerColor = if (primary) Color.White else Color.White.copy(alpha = 0.22f),
        unfocusedContainerColor = if (primary) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f),
        selectedContainerColor = Color.White.copy(alpha = 0.10f),
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVIcon(icon, tint = if (primary) Color(0xFF0E1513) else Color.White, filled = true, modifier = Modifier.size((size * 0.42f).dp))
    }
}

@Composable
private fun SpeedButton(label: String, active: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        // The rate itself is the icon — the extra ">>" glyph read as a seek control next to the real
        // rewind/forward buttons, and "1.0x" already says everything the button does.
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun formatSpeed(speed: Double): String = if (speed == 1.0) {
    stringResource(R.string.player_speed_normal_short)
} else {
    stringResource(R.string.player_speed, localizedDecimal(speed))
}

/** Next-episode countdown card: "Next episode in Ns" + title, with Play now / Cancel. Play now advances
 *  immediately; Cancel suppresses the automatic advance for the current item. */
@Composable
private fun NextEpisodeCard(
    seconds: Int,
    title: String,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.player_next_episode, seconds),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.player_play_now),
                onClick = onPlayNow,
                icon = OwnTVIcon.PLAY,
                modifier = Modifier.focusRequester(playFocus),
            )
            OwnTVButton(
                stringResource(R.string.common_cancel),
                onClick = onCancel,
                icon = OwnTVIcon.CLOSE,
                style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
            )
        }
    }
}

/** The MPV/EXO engine toggle: a one-line pill showing the active engine, flipped on click. Teal while on
 *  the non-default engine (ExoPlayer for VOD; mpv "compatibility" pin for Live). Mirrors [SpeedButton]. */
@Composable
private fun EngineToggle(label: String, active: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OwnTVIcon(OwnTVIcon.SWAP, tint = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), filled = true, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CtrlButton(icon: OwnTVIcon, badge: Int? = null, active: Boolean = false, label: String? = null, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        // Icon-only buttons stay a 44dp square; labeled ones grow into a pill so the text fits.
        modifier = if (label == null) Modifier.size(44.dp) else Modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val tint = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = if (label == null) Modifier else Modifier.padding(horizontal = 14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                OwnTVIcon(icon, tint = tint, filled = true, modifier = Modifier.size(22.dp))
                if (badge != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(TEAL),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.common_number_grouped, badge), style = MaterialTheme.typography.labelSmall, color = Color(0xFF003730), fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
            }
        }
    }
}

// ---------------- Seekbar ----------------

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
                // Physical by design: left rewinds and right advances media time in every locale.
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onSeek(-10_000); true }
                    Key.DirectionRight -> { onSeek(10_000); true }
                    else -> false
                } else false
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (focused) 0.4f else 0.22f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(TEAL))
        }
        if (focused) {
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(TEAL))
            }
            // Time-remaining bubble above the thumb (elapsed is shown at the bar's left, total at the right,
            // so the bubble shows what's LEFT: "-12:34"). Uses a negative offset (not bottom padding) so it
            // floats clear above the 24dp-tall bar — padding can't lift it out of the height-constrained parent.
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.offset(y = (-32).dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        stringResource(R.string.player_time_remaining, formatTime((durationMs - positionMs).coerceAtLeast(0))),
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                        color = Color.White,
                    )
                }
            }
        }
    }
    }
}

private const val LIVE_WINDOW_SEC = 2 * 3600   // the live timeline shows the last 2 hours up to the edge
private const val LIVE_SCRUB_STEP_SEC = 60     // per Left/Right press (hold to scrub fast); buttons stay 30 s

/** Scrubbable live timeline for a catch-up channel: spans the last [LIVE_WINDOW_SEC] up to the live edge.
 *  Left = back in time, Right = toward live; the thumb is the watched point and the gap to the red LIVE dot
 *  on the right is how far behind live you are. Holding a key scrubs freely; the archive loads when you
 *  settle (the VM debounces). Going past the window keeps working via the ⏪ button — the bar just pins left. */
@Composable
private fun LiveTimelineBar(offsetSec: Int, onScrub: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = (1f - offsetSec.toFloat() / LIVE_WINDOW_SEC).coerceIn(0f, 1f) // 1 = live edge, 0 = far edge
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
                // Physical by design: left moves away from live; right moves toward the live edge.
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onScrub(LIVE_SCRUB_STEP_SEC); true }    // back in time
                    Key.DirectionRight -> { onScrub(-LIVE_SCRUB_STEP_SEC); true }  // toward live
                    else -> false
                } else false
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (focused) 0.4f else 0.22f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(TEAL))
        }
        // Live-edge marker (red dot) at the far right.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
        }
        if (focused) {
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(TEAL))
            }
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.padding(bottom = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(
                        if (offsetSec <= 1) stringResource(R.string.player_live) else stringResource(R.string.player_live_offset, mmss(offsetSec)),
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                        color = Color.White,
                    )
                }
            }
        }
    }
    }
}

// ---------------- Dialogs ----------------

@Composable
private fun TrackDialog(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
    audioDelayMs: Int? = null,                 // non-null on the Audio dialog (VOD) → show the A/V-sync nudge
    onAdjustAudioDelay: ((Int) -> Unit)? = null,
    // Non-null on the Subtitles dialog for a movie/episode → an "ADD SUBTITLES" row that opens the
    // OpenSubtitles search (subtitle plan §4). Absent for Live TV and when no item context exists.
    onSearchSubtitles: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog for a movie/episode → "Select local subtitle file" (plan §7).
    onSelectLocalSubtitle: (() -> Unit)? = null,
    // Non-null on the Subtitles dialog when timing adjustment applies to the active track (plan §8) →
    // an "ADJUST" section with a "Subtitle timing" row.
    onSubtitleTiming: (() -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    // Open with focus on the CURRENTLY-selected track (so re-opening to change it lands on the right row),
    // else the "Off" row if nothing's selected, else the first track. The requestFocus must run from
    // INSIDE the target row (below) — a top-level LaunchedEffect fires before the LazyColumn has composed
    // that row, so requestFocus would throw "not initialized" and focus would fall back to the first item.
    val selectedIndex = tracks.indexOfFirst { it.selected }
    val focusOff = onOff != null && selectedIndex < 0
    // Safety net: the per-row one-shot requestFocus below can fire while the dialog window is still
    // mid-transition (seen on HDR/HDR10/DTS streams, whose surface re-layout delays window focus) or
    // before the engine has reported the tracks at all — leaving the dialog with NO focused row and
    // the D-pad locked out. Retry over a few frames, and re-run whenever the track list (re)arrives.
    // The selected row can sit beyond the LazyColumn viewport (e.g. subtitle 11 of 20): it never
    // composes, its focusRequester never attaches, and focus falls back to the first row ("Off").
    // Scroll it into view before requesting focus.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(tracks.size, focusOff) {
        val target = if (selectedIndex >= 0) selectedIndex + (if (onOff != null) 1 else 0) else 0
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (selectedIndex >= 0) runCatching { listState.scrollToItem(target) }
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    DialogScaffold(title = title, onDismiss = onDismiss, state = listState) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text(stringResource(R.string.player_no_tracks), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        if (onOff != null) {
            item {
                if (focusOff) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
                OptionRow(label = stringResource(R.string.common_off), selected = selectedIndex < 0, modifier = if (focusOff) Modifier.focusRequester(focus) else Modifier, onClick = onOff)
            }
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            val focusThis = index == selectedIndex || (selectedIndex < 0 && onOff == null && index == 0)
            if (focusThis) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
            OptionRow(
                // Image-based subs (PGS/VOBSUB/DVB) play via the ExoPlayer handoff on VOD — mark them so
                // it's clear they're a different kind of track, but they're fully selectable.
                label = if (!track.image) track.displayLabel() else stringResource(R.string.player_image_track, track.displayLabel()),
                selected = track.selected,
                modifier = if (focusThis) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(track) },
            )
        }
        // ADD SUBTITLES (subtitles dialog, movie/episode only) — OpenSubtitles search + local file (§4/§7).
        if (onSearchSubtitles != null || onSelectLocalSubtitle != null) {
            item {
                Text(
                    stringResource(R.string.player_add_subtitles),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            if (onSearchSubtitles != null) {
                item { OptionRow(label = stringResource(R.string.player_search_subtitles), selected = false, onClick = onSearchSubtitles) }
            }
            if (onSelectLocalSubtitle != null) {
                item { OptionRow(label = stringResource(R.string.player_select_local_subtitle), selected = false, onClick = onSelectLocalSubtitle) }
            }
        }
        // ADJUST (subtitles dialog): timing panel for the active subtitle (plan §8).
        if (onSubtitleTiming != null) {
            item {
                Text(
                    stringResource(R.string.player_adjust),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            item { OptionRow(label = stringResource(R.string.player_subtitle_timing), selected = false, onClick = onSubtitleTiming) }
        }
        // A/V-sync nudge (audio dialog, VOD only) — fixes a badly-muxed file where audio leads/lags the video.
        if (onAdjustAudioDelay != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.player_av_sync), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                    StepButton(stringResource(R.string.common_minus), enabled = (audioDelayMs ?: 0) > -5_000) { onAdjustAudioDelay(-50) }
                    Text(
                        formatDelay(audioDelayMs ?: 0),
                        style = MaterialTheme.typography.bodyMedium, color = colors.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(min = 78.dp, max = 140.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StepButton(stringResource(R.string.common_plus), enabled = (audioDelayMs ?: 0) < 5_000) { onAdjustAudioDelay(50) }
                }
            }
        }
    }
}

/**
 * Requests [focus] with retries: dialog-window content composes a frame or two after the calling
 * effect starts, so a one-shot requestFocus can fire before the target row exists and silently fail.
 */
private suspend fun requestFocusRetrying(focus: FocusRequester) {
    repeat(10) {
        androidx.compose.runtime.withFrameNanos {}
        if (runCatching { focus.requestFocus() }.isSuccess) return
        delay(50)
    }
}

@Composable
private fun formatDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_delay_zero)
    ms > 0 -> stringResource(R.string.player_delay_positive, ms)
    else -> stringResource(R.string.player_delay_negative, ms)
}

@Composable
private fun SpeedDialog(current: Double, onSelect: (Double) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val selectedIndex = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }.coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.settings_playback_speed), onDismiss = onDismiss) {
        items(SPEEDS.size) { index ->
            val speed = SPEEDS[index]
            OptionRow(
                label = if (speed == 1.0) stringResource(R.string.player_speed_normal) else stringResource(R.string.player_speed, localizedDecimal(speed)),
                selected = kotlin.math.abs(speed - current) < 0.01,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(speed) },
            )
        }
    }
}

@Composable
private fun ZoomDialog(current: ZoomMode, onSelect: (ZoomMode) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    // Land focus on the current mode (not always the first row) so re-opening starts on your selection.
    val selectedIndex = ZoomMode.entries.indexOf(current).coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.settings_player_zoom), onDismiss = onDismiss) {
        items(ZoomMode.entries.size) { index ->
            val mode = ZoomMode.entries[index]
            OptionRow(label = stringResource(mode.labelRes), selected = mode == current, modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier, onClick = { onSelect(mode) })
        }
    }
}

@Composable
private fun VolumeDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val volume by player.volume.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    // Real dialog window for the same focus isolation as DialogScaffold (see there).
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(Modifier.dialogPanel(padding = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_volume), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.common_minus), enabled = volume > 0, modifier = Modifier.focusRequester(focus)) { player.adjustVolume(-5) }
                    Text(stringResource(R.string.player_percent, volume), style = MaterialTheme.typography.headlineLarge, color = TEAL, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                    StepButton(stringResource(R.string.common_plus), enabled = volume < 150) { player.adjustVolume(5) }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(if (volume == 0) R.string.player_unmute else R.string.player_mute), onClick = { player.toggleMute() }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Subtitle-timing panel (subtitle plan §8.2/§8.3): 100 ms and 500 ms steps + Reset, applied live while
 * the video keeps playing behind (the backdrop is NOT dimmed so speech and text can be compared).
 * Positive = subtitles shown later; the direction is always spelled out. Back keeps the value.
 */
@Composable
private fun SubtitleTimingDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val delay by player.subDelayMs.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Box(Modifier.fillMaxSize().padding(bottom = 56.dp), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.dialogPanel(width = 560.dp, padding = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.player_subtitle_timing), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(formatSubDelay(delay), style = MaterialTheme.typography.headlineLarge, color = TEAL)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            delay > 0 -> stringResource(R.string.player_subtitles_later)
                            delay < 0 -> stringResource(R.string.player_subtitles_earlier)
                            else -> stringResource(R.string.player_no_offset)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_negative, 0.5), onClick = { player.adjustSubtitleDelay(-500) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_negative, 0.1), onClick = { player.adjustSubtitleDelay(-100) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.common_reset), onClick = { player.resetSubtitleDelay() }, modifier = Modifier.focusRequester(focus))
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_positive, 0.1), onClick = { player.adjustSubtitleDelay(100) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.player_subtitle_delay_positive, 0.5), onClick = { player.adjustSubtitleDelay(500) }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                    }
                }
            }
        }
    }
}

@Composable
private fun formatSubDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_subtitle_delay_zero)
    ms > 0 -> stringResource(R.string.player_subtitle_delay_positive, ms / 1000.0)
    else -> stringResource(R.string.player_subtitle_delay_negative, -ms / 1000.0)
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier.size(64.dp), shape = RoundedCornerShape(18.dp), contentAlignment = Alignment.Center, surface = GlassSurface.DIALOGS) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) OwnTVTheme.colors.onSurface else OwnTVTheme.colors.outline)
    }
}

@Composable
private fun DialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val colors = OwnTVTheme.colors
    // A REAL dialog window, not an in-place overlay: it owns the D-pad focus scope, so nothing in the
    // HUD behind it (play button, catch-all focusable, stream-info chips) can compete for or steal
    // focus — which is what intermittently locked the subtitle/audio pickers out of focus on
    // codec-heavy (HDR/DTS) streams. Back is handled by the window itself via onDismissRequest.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        // Compact glass popup matching the storage picker: smaller font + narrow box.
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.72f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                // Glass effect panel (same translucent chrome as the volume/timing dialogs) — the
                // inner LazyColumn manages its own scroll, so scroll = false.
                Column(modifier = Modifier.dialogPanel(width = 260.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    // Cap to the screen (minus dialog chrome) so all rows stay reachable on small screens.
                    val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 160.dp).coerceIn(140.dp, 240.dp)
                    LazyColumn(state = state, modifier = Modifier.heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), selected = selected, shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer, contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.DIALOGS,
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.onPrimaryContainer else if (focused) colors.primary else colors.onSurface)
            if (selected) {
                Spacer(Modifier.weight(1f))
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) stringResource(R.string.player_track_hours, h, m, s)
    else stringResource(R.string.player_track_seconds, m, s)
}
