package tv.own.owntv.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The HUD's chrome: the top strip, the channel/direct-tune OSD cards, the centre transport and the
 * bottom bar. Split out of [PlayerHud] — behaviour unchanged; these are `internal` rather than
 * file-private only because [PlayerHud] now lives in a sibling file.
 */

// ---------------- Top bar ----------------

@Composable
internal fun TopBar(
    player: PlaybackEngine, isLive: Boolean, chips: List<String>, duration: Long,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    // Live only: the Now/Next guide, rendered at the far end of the same strip.
    trailing: (@Composable () -> Unit)? = null,
    // The clock. Rendered between two equal-weight halves so it lands on the true centre of the screen
    // whatever the channel name and guide card happen to be doing on either side of it.
    centre: (@Composable () -> Unit)? = null,
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
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
      // Left half. Equal weight to the right half, so [centre] sits on the real midpoint of the screen
      // rather than the midpoint of whatever space the title happened to leave over.
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
      } // end left half
      if (centre != null) {
          Spacer(Modifier.width(20.dp))
          centre()
          Spacer(Modifier.width(20.dp))
      }
      // Right half, pushed to the far edge. Same weight as the left, hence the true centring above.
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
          if (trailing != null) trailing()
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
internal fun ChannelOsdCard(
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
internal fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier) {
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
internal fun ChannelNumberCard(digits: String, error: String? = null, modifier: Modifier = Modifier) {
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
internal fun CenterControls(
    player: PlaybackEngine, nav: NavState, isPlaying: Boolean, isLive: Boolean,
    onRewindLive: (() -> Unit)?, onForwardLive: (() -> Unit)?, onGoToLive: (() -> Unit)?, timeshiftOffsetSec: Int?,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
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
                !isLive -> CircleButton(OwnTVIcon.REWIND, size = 52) { player.seekBy(-seekStep) }
            }
            CircleButton(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, size = 72, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
            when {
                rewindMode && timeshifting -> CircleButton(OwnTVIcon.FORWARD, size = 52) { onForwardLive!!() } // toward live
                !isLive && !rewindMode -> CircleButton(OwnTVIcon.FORWARD, size = 52) { player.seekBy(seekStep) }
            }
            if (rewindMode && timeshifting && onGoToLive != null) {
                CircleButton(OwnTVIcon.LIVE_TV, size = 52, primary = true) { onGoToLive() } // jump to the live edge
            }
            if (nav.hasNext) CircleButton(OwnTVIcon.SKIP_NEXT, size = 52) { player.next() }
        }
    }
}

// ---------------- Bottom bar ----------------

@Composable
internal fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffsetSec: Int?, onOpenJumpBack: (() -> Unit)?,
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
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
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
                    stringResource(R.string.fork_pip_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = TEAL,
                    modifier = Modifier.padding(start = 2.dp, end = 8.dp),
                )
                if (onChangeMain != null) CtrlButton(OwnTVIcon.LIVE_TV, label = stringResource(R.string.fork_pip_change_main)) { onChangeMain() }
                if (onChangeCorner != null) CtrlButton(OwnTVIcon.PLAYLIST, label = stringResource(R.string.fork_pip_change_corner)) { onChangeCorner() }
                if (onCornerSwap != null) CtrlButton(OwnTVIcon.FULLSCREEN, label = stringResource(R.string.fork_pip_swap_windows)) { onCornerSwap() }
                // Swap-arrows icon on Sound (not a mute glyph): it MOVES the audio between the two windows.
                if (onCornerAudio != null) CtrlButton(OwnTVIcon.SWAP, active = cornerAudioOn, label = stringResource(R.string.fork_pip_sound)) { onCornerAudio() }
                if (onCornerMove != null) CtrlButton(OwnTVIcon.MOVE, label = stringResource(R.string.fork_pip_move)) { onCornerMove() }
                if (onCornerGrow != null) CtrlButton(OwnTVIcon.ADD, label = stringResource(R.string.fork_pip_size_increase)) { onCornerGrow() }
                if (onCornerShrink != null) CtrlButton(OwnTVIcon.MINUS, label = stringResource(R.string.fork_pip_size_decrease)) { onCornerShrink() }
                CtrlButton(OwnTVIcon.CLOSE, label = stringResource(R.string.fork_pip_close)) { onCornerClose() }
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
                SeekBar(positionMs = position, durationMs = duration, stepMs = seekStep, onSeek = { player.seekBy(it) })
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
                // "Go back to…" — jump straight to a time in this channel's archive. Only on catch-up
                // channels. CATCHUP (a TV with a replay loop): REWIND is already the transport button
                // beside it, and a plain clock would not say which of the two time controls this is.
                if (onOpenJumpBack != null) CtrlButton(OwnTVIcon.CATCHUP) { onOpenJumpBack() }
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
                if (onPip != null) CtrlButton(OwnTVIcon.PIP, label = stringResource(R.string.fork_pip_label)) { onPip() }
                if (onMultiView != null) CtrlButton(OwnTVIcon.VIDEO, label = stringResource(R.string.fork_action_multiview)) { onMultiView() } // enter the multi-stream grid
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

/** Next-episode countdown card: "Next episode in Ns" + title, with Play now / Cancel. Play now advances
 *  immediately; Cancel suppresses the automatic advance for the current item. */
@Composable
internal fun NextEpisodeCard(
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
