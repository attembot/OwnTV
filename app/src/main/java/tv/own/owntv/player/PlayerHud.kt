package tv.own.owntv.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.theme.OwnTVTheme

private val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
private val TEAL = Color(0xFF52DBC8)

private enum class HudDialog { NONE, AUDIO, SUBS, SPEED, ZOOM, VOLUME }

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    onPip: (() -> Unit)? = null,
    onMultiView: (() -> Unit)? = null, // enter MultiView seeded with this channel (live only)
    // True while the shell draws an overlay ABOVE the HUD (e.g. the channel-list overlay). The HUD goes
    // inert: its auto-hide timer pauses and — crucially — it makes no focus requests, so it can't yank
    // D-pad focus off the overlay. The existing dialog guard below covers only the HUD's OWN dialogs;
    // shell-level overlays need this flag. Default false = no behavior change for other callers.
    inert: Boolean = false,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    // Live: open the channel-list overlay (Left while the controls are hidden). Null = not a live channel.
    onOpenChannelList: (() -> Unit)? = null,
    // Live rewind / timeshift (catch-up channels). onRewindLive non-null = this live channel can rewind;
    // timeshiftOffsetSec non-null = currently watching that many seconds behind the live edge.
    onRewindLive: (() -> Unit)? = null,
    onForwardLive: (() -> Unit)? = null,
    onGoToLive: (() -> Unit)? = null,
    onScrubLive: ((Int) -> Unit)? = null, // timeline scrub: +sec = back, −sec = toward live
    timeshiftOffsetSec: Int? = null,
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
    modifier: Modifier = Modifier,
) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
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
    val zap: (Int) -> Unit = { d -> (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++ }

    // Engine-switch confirmation toast: a brief "Switched to MPV/ExoPlayer" at the bottom-center when the
    // user flips the engine via the HUD toggle. Mirrors the channel-flash pattern above.
    var engineMsg by remember { mutableStateOf<String?>(null) }
    var engineFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(engineFlash) { if (engineFlash > 0) { delay(1800); engineMsg = null } }
    // Wrap the engine toggles so a click also surfaces the toast naming the engine we're switching TO.
    val toggleCompat: (() -> Unit)? = onToggleCompatMode?.let { cb -> {
        engineMsg = if (compatMode == true) "Switched to ExoPlayer" else "Switched to MPV"; engineFlash++; cb()
    } }
    val toggleVod: (() -> Unit)? = onToggleVodEngine?.let { cb -> {
        engineMsg = if (vodOnExo == true) "Switched to MPV" else "Switched to ExoPlayer"; engineFlash++; cb()
    } }

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
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

    Box(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Channel surfing: dedicated CH+/CH- and media prev/next keys always zap. D-pad Up/Down
                // zap ONLY while the HUD is hidden (when it's visible, Up/Down navigate the controls) —
                // this is the only way to change channels on remotes without CH keys (e.g. Fire TV).
                canZap && (e.key == Key.ChannelUp || e.key == Key.MediaPrevious) -> { zap(-1); true }
                canZap && (e.key == Key.ChannelDown || e.key == Key.MediaNext) -> { zap(1); true }
                canZap && !controlsVisible && e.key == Key.DirectionUp -> { zap(-1); true }
                canZap && !controlsVisible && e.key == Key.DirectionDown -> { zap(1); true }
                // Left while the HUD is hidden opens the channel-list overlay (live only).
                onOpenChannelList != null && !controlsVisible && e.key == Key.DirectionLeft -> { onOpenChannelList(); true }
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
            StreamInfoOverlay(player, modifier = Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 20.dp))
        }

        // Channel flash card (zapping with the HUD hidden) — shown independently of the full controls.
        if (isLive && showFlash && !controlsVisible) {
            ChannelCard(player, modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 28.dp))
        }

        if (controlsVisible) {
            // Scrim gradients top + bottom for legibility.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(240.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

            // The active engine (MPV/EXO) leads the mini chips so users can always tell which player is on.
            TopBar(player, isLive, listOfNotNull(engineChip) + streamChips.ifEmpty { listOfNotNull(videoRes) }, duration, onBack, modifier = Modifier.align(Alignment.TopStart))
            if (isLive) ChannelCard(player, modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 92.dp))

            // Hide the transport (play/seek/prev/next) and bottom bar while an error is up — the error
            // overlay owns the screen with its own Retry, so the play/rewind/forward must not show behind it.
            if (error == null) {
                CenterControls(player, nav, isPlaying, isLive, onRewindLive, onForwardLive, onGoToLive, timeshiftOffsetSec, playFocus, modifier = Modifier.align(Alignment.Center))

                BottomBar(
                    player = player, isLive = isLive, position = position, duration = duration,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    speedLabel = formatSpeed(speed),
                    onScrubLive = onScrubLive, timeshiftOffsetSec = timeshiftOffsetSec,
                    compatMode = compatMode, onToggleCompatMode = toggleCompat,
                    vodOnExo = vodOnExo, onToggleVodEngine = toggleVod,
                    onInfo = { showInfo = !showInfo }, infoOn = showInfo,
                    onOpenDialog = { dialog = it }, onPip = onPip, onMultiView = onMultiView, onBack = onBack,
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
                Text("Playback error", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                // Structured technical detail so a user can report the real cause without adb/logcat:
                // plain reason → media spec (codec • resolution • decoder) → raw engine/codec line.
                errorInfo?.let { info ->
                    info.reason?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("err: $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                Spacer(Modifier.height(18.dp))
                OwnTVButton("Retry", onClick = { player.retry() }, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(retryFocus))
            }
            buffering -> OwnTVSpinner(modifier = Modifier.align(Alignment.Center), sizeDp = 56)
        }
    }

    when (dialog) {
        // Track lists are SNAPSHOT once when the dialog opens (re-polled only while still empty —
        // heavy HDR/DTS streams report their tracks late). Reading player.xxxTracks() directly in
        // composition handed the dialog a fresh list on every HUD recomposition, endlessly rebuilding
        // the rows and losing/yanking D-pad focus.
        HudDialog.AUDIO -> {
            var audioTracks by remember { mutableStateOf(player.audioTracks()) }
            LaunchedEffect(Unit) { while (audioTracks.isEmpty()) { delay(300); audioTracks = player.audioTracks() } }
            TrackDialog(
                "Audio Track", audioTracks,
                onSelect = { player.selectAudio(it.mpvId); dialog = HudDialog.NONE }, onOff = null,
                onDismiss = { dialog = HudDialog.NONE },
                // A/V-sync nudge only for VOD (mpv) — live A/V is the provider's; ExoPlayer has no audio-delay.
                audioDelayMs = if (!isLive) audioDelayMs else null,
                onAdjustAudioDelay = if (!isLive) ({ d -> player.adjustAudioDelay(d) }) else null,
            )
        }
        HudDialog.SUBS -> {
            var subTracks by remember { mutableStateOf(player.textTracks()) }
            LaunchedEffect(Unit) { while (subTracks.isEmpty()) { delay(300); subTracks = player.textTracks() } }
            TrackDialog("Subtitles", subTracks, onSelect = { player.selectSubtitle(it.mpvId); dialog = HudDialog.NONE }, onOff = { player.disableSubtitles(); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        }
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
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(meta.title ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val durMin = (duration / 60000)
                val parts = buildList {
                    meta.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (!isLive && durMin > 0) add("$durMin min")
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
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCCDC3232)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    Row(
        modifier = modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)), contentAlignment = Alignment.Center) {
            val logo = meta.logoUrl
            if (!logo.isNullOrBlank()) AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text((meta.title ?: "?").take(3).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
        }
        Column {
            Text(meta.title ?: "", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (timeshiftOffsetSec!! <= 1) "● At the live edge" else "● ${mmss(timeshiftOffsetSec)} behind live",
                style = MaterialTheme.typography.labelLarge,
                color = OwnTVTheme.colors.accent,
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (nav.hasPrev) CircleButton(OwnTVIcon.SKIP_PREVIOUS, size = 52) { player.previous() }
            when {
                rewindMode -> CircleButton(OwnTVIcon.REWIND, size = 52) { onRewindLive!!() } // step back into the archive
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
private fun mmss(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

// ---------------- Bottom bar ----------------

@Composable
private fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffsetSec: Int?,
    compatMode: Boolean?, onToggleCompatMode: (() -> Unit)?,
    vodOnExo: Boolean?, onToggleVodEngine: (() -> Unit)?,
    onInfo: (() -> Unit)? = null, infoOn: Boolean = false,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onMultiView: (() -> Unit)? = null, onBack: () -> Unit,
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
                SpeedButton(label = speedLabel, active = speedLabel != "1.0x") { onOpenDialog(HudDialog.SPEED) }
                CtrlButton(OwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }) { onOpenDialog(HudDialog.SUBS) }
                CtrlButton(OwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }) { onOpenDialog(HudDialog.AUDIO) }
                // Stream technical info (codec/res/HDR/bitrate/decoder/audio/buffer) — toggles the overlay.
                if (onInfo != null) CtrlButton(OwnTVIcon.VIDEO, active = infoOn) { onInfo() }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Live "compatibility mode" (Live TV + channels opened from the Guide): pin this channel
                // to mpv. The pill shows the active engine and flips on click (teal while pinned to mpv).
                if (onToggleCompatMode != null) {
                    EngineToggle(label = if (compatMode == true) "MPV" else "EXO", active = compatMode == true) { onToggleCompatMode() }
                }
                // VOD engine toggle (Movies/Series): flip THIS movie/episode between mpv and ExoPlayer.
                // The pill shows the active engine (teal while ExoPlayer owns playback).
                if (onToggleVodEngine != null) {
                    EngineToggle(label = if (vodOnExo == true) "EXO" else "MPV", active = vodOnExo == true) { onToggleVodEngine() }
                }
                // Aspect/zoom works in every mode now — direct mode resizes the surface view itself
                // (see MpvVideoSurface), GL mode scales internally.
                CtrlButton(OwnTVIcon.ASPECT, active = zoomMode != ZoomMode.FIT) { onOpenDialog(HudDialog.ZOOM) }
                // (The corner/PiP controls live in their own labeled row above — see the top of this Column.)
                if (onPip != null) CtrlButton(OwnTVIcon.PIP, label = "PiP") { onPip() }
                if (onMultiView != null) CtrlButton(OwnTVIcon.VIDEO, label = "MultiView") { onMultiView() } // enter the multi-stream grid
                CtrlButton(OwnTVIcon.FULLSCREEN_EXIT, label = "Exit") { onBack() }
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
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OwnTVIcon(OwnTVIcon.FORWARD, tint = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), filled = true, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) TEAL else if (focused) Color.White else Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatSpeed(speed: Double): String = if (speed == 1.0) "1.0x" else "${speed}x"

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
            "Next episode in ${seconds}s",
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
                "Play now",
                onClick = onPlayNow,
                icon = OwnTVIcon.PLAY,
                modifier = Modifier.focusRequester(playFocus),
            )
            OwnTVButton(
                "Cancel",
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
                        Text("$badge", style = MaterialTheme.typography.labelSmall, color = Color(0xFF003730), fontWeight = FontWeight.Bold)
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
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
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
                    Text("-${formatTime((durationMs - positionMs).coerceAtLeast(0))}", style = MaterialTheme.typography.labelMedium, color = Color.White)
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
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
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
                    Text(if (offsetSec <= 1) "LIVE" else "-${mmss(offsetSec)}", style = MaterialTheme.typography.labelMedium, color = Color.White)
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
    LaunchedEffect(tracks.size, focusOff) { requestFocusRetrying(focus) }
    DialogScaffold(title = title, onDismiss = onDismiss) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text("No tracks available.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        if (onOff != null) {
            item {
                if (focusOff) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
                OptionRow(label = "Off", selected = selectedIndex < 0, modifier = if (focusOff) Modifier.focusRequester(focus) else Modifier, onClick = onOff)
            }
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            val focusThis = index == selectedIndex || (selectedIndex < 0 && onOff == null && index == 0)
            if (focusThis) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
            OptionRow(
                // Image-based subs (PGS/VOBSUB/DVB) play via the ExoPlayer handoff on VOD — mark them so
                // it's clear they're a different kind of track, but they're fully selectable.
                label = if (!track.image) track.label else "${track.label}  ·  image",
                selected = track.selected,
                modifier = if (focusThis) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(track) },
            )
        }
        // A/V-sync nudge (audio dialog, VOD only) — fixes a badly-muxed file where audio leads/lags the video.
        if (onAdjustAudioDelay != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("A/V sync", style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                    StepButton("–", enabled = (audioDelayMs ?: 0) > -5_000) { onAdjustAudioDelay(-50) }
                    Text(
                        formatDelay(audioDelayMs ?: 0),
                        style = MaterialTheme.typography.bodyMedium, color = colors.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(78.dp),
                    )
                    StepButton("+", enabled = (audioDelayMs ?: 0) < 5_000) { onAdjustAudioDelay(50) }
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

private fun formatDelay(ms: Int): String = when {
    ms == 0 -> "0 ms"
    ms > 0 -> "+$ms ms"
    else -> "$ms ms"
}

@Composable
private fun SpeedDialog(current: Double, onSelect: (Double) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val selectedIndex = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }.coerceAtLeast(0)
    DialogScaffold(title = "Playback Speed", onDismiss = onDismiss) {
        items(SPEEDS.size) { index ->
            val speed = SPEEDS[index]
            OptionRow(
                label = if (speed == 1.0) "1.0x (Normal)" else "${speed}x",
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
    DialogScaffold(title = "Player Zoom", onDismiss = onDismiss) {
        items(ZoomMode.entries.size) { index ->
            val mode = ZoomMode.entries[index]
            OptionRow(label = mode.label, selected = mode == current, modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier, onClick = { onSelect(mode) })
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
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(Modifier.dialogPanel(padding = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Volume", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("–", enabled = volume > 0, modifier = Modifier.focusRequester(focus)) { player.adjustVolume(-5) }
                    Text("$volume%", style = MaterialTheme.typography.headlineLarge, color = TEAL, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                    StepButton("+", enabled = volume < 150) { player.adjustVolume(5) }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(if (volume == 0) "Unmute" else "Mute", onClick = { player.toggleMute() }, style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton("Done", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier.size(64.dp), shape = RoundedCornerShape(18.dp), contentAlignment = Alignment.Center) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) OwnTVTheme.colors.onSurface else OwnTVTheme.colors.outline)
    }
}

@Composable
private fun DialogScaffold(title: String, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val colors = OwnTVTheme.colors
    // A REAL dialog window, not an in-place overlay: it owns the D-pad focus scope, so nothing in the
    // HUD behind it (play button, catch-all focusable, stream-info chips) can compete for or steal
    // focus — which is what intermittently locked the subtitle/audio pickers out of focus on
    // codec-heavy (HDR/DTS) streams. Back is handled by the window itself via onDismissRequest.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(12.dp))
                // Cap to the screen (minus dialog chrome) so all rows stay reachable on small screens.
                val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 160.dp).coerceIn(160.dp, 360.dp)
                LazyColumn(modifier = Modifier.heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
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
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else if (focused) colors.primary else colors.onSurface)
            if (selected) {
                Spacer(Modifier.weight(1f))
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
