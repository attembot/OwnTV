package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.localizedDecimal

/**
 * Shared HUD primitives: the accent colour, the two time formatters, the button vocabulary and the two
 * scrub bars. Split out of [PlayerHud] — behaviour unchanged; the declarations the other HUD files reach
 * for are `internal` rather than file-private for that reason alone.
 */

internal val TEAL = Color(0xFF52DBC8)

@Composable
internal fun formatTime(ms: Long): String = tv.own.owntv.ui.components.formatTimestamp(ms)

/** mm:ss for a seconds offset (e.g. 150 → "2:30"). */
@Composable
internal fun mmss(sec: Int): String = tv.own.owntv.ui.components.formatTimestamp(sec * 1000L)

// ---------------- Buttons ----------------

@Composable
internal fun CircleButton(icon: OwnTVIcon, size: Int, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun SpeedButton(label: String, active: Boolean, onClick: () -> Unit) {
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
internal fun formatSpeed(speed: Double): String = if (speed == 1.0) {
    stringResource(R.string.player_speed_normal_short)
} else {
    stringResource(R.string.player_speed, localizedDecimal(speed))
}

/** The MPV/EXO engine toggle: a one-line pill showing the active engine, flipped on click. Teal while on
 *  the non-default engine (ExoPlayer for VOD; mpv "compatibility" pin for Live). Mirrors [SpeedButton]. */
@Composable
internal fun EngineToggle(label: String, active: Boolean, onClick: () -> Unit) {
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
internal fun CtrlButton(icon: OwnTVIcon, badge: Int? = null, active: Boolean = false, label: String? = null, onClick: () -> Unit) {
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
internal fun SeekBar(positionMs: Long, durationMs: Long, stepMs: Long, onSeek: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onKeyEvent { e ->
                // Physical by design: left rewinds and right advances media time in every locale.
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> { onSeek(-stepMs); true }
                    Key.DirectionRight -> { onSeek(stepMs); true }
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
internal fun LiveTimelineBar(offsetSec: Int, onScrub: (Int) -> Unit) {
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
