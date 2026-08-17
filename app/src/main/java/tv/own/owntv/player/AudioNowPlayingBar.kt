package tv.own.owntv.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.LocalPopupFontFamily

/**
 * The wide "now-playing" bar shown in the top bar (left of the weather chip) while [PlayerMode.AUDIO]
 * is active — Audio Mode plan §6. Video is stopped; only audio plays.
 *
 * **Two-stage focus (owner spec):**
 *  1. **Collapsed** — no D-pad focus: equaliser cover + title + a static play/pause glyph.
 *  2. **Stage 1 — pill focused:** focus lands on the WHOLE bar as one target (highlighted, expanded to
 *     show the full row). The inner buttons are NOT individually navigable yet.
 *  3. **Stage 2 — activated:** press OK on the focused pill → focus moves inside and is **trapped**
 *     there: D-pad left/right only step between the buttons (never escaping the bar), OK runs the
 *     focused button, and **Back** is the only way out — it returns to Stage 1.
 *
 * The trap is enforced manually with [onPreviewKeyEvent] because Compose's default 2D focus search
 * would let left/right leak out to the neighbouring top-bar chips.
 *
 * The equaliser animates only while playing and freezes flat when paused, on every state. prev/next are
 * context-wired by the shell (channel zap / episode queue / disabled for a standalone movie).
 */
@Composable
fun AudioNowPlayingBar(
    player: PlaybackEngine,
    isLive: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    focusable: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()

    var hasFocus by remember { mutableStateOf(false) } // any part of the bar holds focus (stage 1 or 2)
    var active by remember { mutableStateOf(false) }    // stage 2 — inner buttons live and trapped
    var pendingReturn by remember { mutableStateOf(false) } // Back pressed → hand focus back to the pill

    val pillFocus = remember { FocusRequester() }

    // Button slots, fixed order: 0=prev 1=play 2=next 3=volume 4=fullscreen 5=close. prev/next are only
    // focusable when there's somewhere to go (episode queue / channel zap); everything else is always on.
    val slotCount = 6
    val requesters = remember { List(slotCount) { FocusRequester() } }
    val enabled = booleanArrayOf(canPrev, true, canNext, true, true, true)
    val navSlots = (0 until slotCount).filter { enabled[it] }
    var focusedSlot by remember { mutableIntStateOf(1) } // play by default

    val expanded = (hasFocus || active) && focusable
    val hasTime = !isLive && duration > 0L

    fun moveFocus(dir: Int) {
        val pos = navSlots.indexOf(focusedSlot)
        val next = pos + dir
        if (pos >= 0 && next in navSlots.indices) {
            val slot = navSlots[next]
            focusedSlot = slot
            runCatching { requesters[slot].requestFocus() }
        }
        // out of range → do nothing: focus stays inside (trapped).
    }

    // Enter/exit stage 2: drop focus onto the play button (trapped), or hand it back to the whole pill.
    LaunchedEffect(active) {
        if (active) {
            focusedSlot = if (1 in navSlots) 1 else navSlots.first()
            runCatching { requesters[focusedSlot].requestFocus() }
        } else if (pendingReturn) {
            pendingReturn = false
            runCatching { pillFocus.requestFocus() }
        }
    }
    // Losing focusability (bar dismissed / mode left) collapses back to a single target.
    LaunchedEffect(focusable) { if (!focusable) { active = false; pendingReturn = false } }
    // Back while activated → return to the whole-pill focus (stage 1). This is the ONLY exit from stage 2.
    if (active) BackHandler { pendingReturn = true; active = false }

    Box(
        modifier = modifier
            // Just track focus. We never reset `active` here: while activated the trap below keeps focus
            // inside, so the only "focus lost" events are the transient drops during the stage-1↔2
            // hand-off — resetting on those is exactly what used to collapse the bar on OK. Real exits go
            // through Back (→ stage 1) or losing [focusable] (→ collapsed), handled explicitly below.
            .onFocusChanged { hasFocus = it.hasFocus }
            // Trap the D-pad while activated: left/right only step between buttons, up/down are swallowed,
            // and OK falls through so the focused button's own click handler runs it.
            .onPreviewKeyEvent { ev ->
                if (!active || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.horizontalDirection(layoutDirection)) {
                    HorizontalDirection.START -> { moveFocus(-1); true }
                    HorizontalDirection.END -> { moveFocus(1); true }
                    null -> when (ev.key) {
                        Key.DirectionUp, Key.DirectionDown -> true // swallow: never escape vertically
                        else -> false
                    }
                }
            }
            .focusGroup(),
    ) {
        // --- Content (drawn in all states; buttons focusable only in stage 2) ---
        // Focus never fills or outlines the card in stage 1 — the catcher's slight scale-up is the only
        // cue. Stage 2 (active) shows the accent outline; the focused button carries its accent ring/icon.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                // AMOLED-dark card (owner spec) in dark; the same "extreme" in light = a crisp pure-white
                // card. Never a glassy tint in either theme.
                .background(if (colors.isDark) Color(0xFF080D0C) else Color(0xFFFFFFFF))
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Bare equalizer, no cover tile (mini-player mock in future-plan/audio-hud-options.html).
            Equalizer(playing = isPlaying, color = colors.primary, modifier = Modifier.size(width = 26.dp, height = 20.dp))

            Column(Modifier.widthIn(max = if (expanded) 260.dp else 150.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    meta.title ?: "",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(
                        if (hasFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    ),
                )
                if (expanded) {
                    when {
                        isLive -> LiveRow(colors.favorite)
                        // The 2nd line is reserved for the timelapse only — series info would crowd out
                        // the time on the 1-line, width-clamped text. (Series subtitle still shows in
                        // the fullscreen HUD; it only falls back to `meta.subtitle` below when there's
                        // no time to show, e.g. before duration is known.)
                        hasTime -> Text(
                            stringResource(R.string.player_time_progress, fmtTime(position), fmtTime(duration)),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalPopupFontFamily.current),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        meta.localizedSubtitle() != null -> Text(
                            meta.localizedSubtitle() ?: "",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Breathing room between the text block and the transport buttons (mock: buttons pushed right).
            if (expanded) Spacer(Modifier.width(14.dp))

            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AudioBtn(0, OwnTVIcon.SKIP_PREVIOUS, active && enabled[0], canPrev, requesters, { focusedSlot = it }, onClick = onPrev)
                    // Play/pause leads the row visually: one step bigger than the rest (mock proportions).
                    AudioBtn(1, if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, active, true, requesters, { focusedSlot = it }, sizeDp = 36, iconDp = 17) { player.togglePlayPause() }
                    AudioBtn(2, OwnTVIcon.SKIP_NEXT, active && enabled[2], canNext, requesters, { focusedSlot = it }, onClick = onNext)
                    AudioBtn(3, if (volume <= 0) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH, active, true, requesters, { focusedSlot = it }) { player.toggleMute() }
                    AudioBtn(4, OwnTVIcon.EXPAND, active, true, requesters, { focusedSlot = it }, onClick = onExpand)
                    AudioBtn(5, OwnTVIcon.CLOSE, active, true, requesters, { focusedSlot = it }, onClick = onClose)
                }
            }
        }

        // --- Stage 1 focus catcher: the whole pill as one target. Focusable only until activated; once
        // activated it steps aside so the trapped buttons own focus. OK enters stage 2. ---
        FocusableSurface(
            onClick = { active = true },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(pillFocus)
                .focusProperties { canFocus = focusable && !active },
            shape = RoundedCornerShape(18.dp),
            focusedScale = 1.03f,
            glowElevation = 0,
            // Fully invisible catcher: stage-1 focus is drawn by the card itself (top/bottom accent
            // lines above) — no fill, no glow shadow, no built-in border ("glass" look fix).
            showFocusBorder = false,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
        ) { _ -> }
    }
}

@Composable
private fun LiveRow(dotColor: Color) {
    val colors = OwnTVTheme.colors
    val transition = rememberInfiniteTransition(label = "liveDot")
    val a by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor).alpha(a))
        Text(stringResource(R.string.player_live), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

/** Bars that dance while [playing] and freeze flat when paused (Audio Mode plan §3/§6). */
@Composable
private fun Equalizer(playing: Boolean, color: Color, modifier: Modifier) {
    val bars = 5
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until bars).map { i ->
        transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 90, easing = LinearEasing), RepeatMode.Reverse),
            label = "eqBar$i",
        )
    }
    Canvas(modifier) {
        val gap = size.width * 0.12f
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = if (playing) heights[i].value else 0.18f
            val bh = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Composable
private fun AudioBtn(
    slot: Int,
    icon: OwnTVIcon,
    focusable: Boolean,
    enabled: Boolean,
    requesters: List<FocusRequester>,
    onFocused: (Int) -> Unit,
    sizeDp: Int = 28,
    iconDp: Int = 13,
    onClick: () -> Unit,
) {
    // White-on-dark circles; focus = accent ring (built-in focus border) + accent icon, background
    // stays dark — outline-only focus per owner spec (mini-player mock in audio-hud-options.html).
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(sizeDp.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .focusRequester(requesters[slot])
            .onFocusChanged { if (it.isFocused) onFocused(slot) }
            .focusProperties { canFocus = focusable && enabled },
        shape = CircleShape,
        focusedScale = 1.02f,
        glowElevation = 0,
        // Bare icons — no circle fill or glow behind them; focus shows only the accent ring + tint.
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(icon, tint = if (focused) colors.primary else if (colors.isDark) Color.White else colors.onSurface, filled = true, modifier = Modifier.size(iconDp.dp))
    }
}

@Composable
private fun fmtTime(ms: Long): String = tv.own.owntv.ui.components.formatTimestamp(ms)
