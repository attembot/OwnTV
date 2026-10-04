package tv.own.owntv.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import tv.own.owntv.core.live.CatchupJumps
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import androidx.compose.foundation.background
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Exact-time entry for a catch-up channel: a day wheel plus a two-part HH:MM, so any point the
 * provider still holds is reachable.
 *
 * The suggestion list it opens from can only ever offer round offsets — 1 hour, 3 hours, yesterday at
 * the same minute — so "yesterday at 10:31" had no route. This is that route.
 *
 * Three wheels rather than a text field or a clock face: a TV remote has no comfortable way to type,
 * and an analogue clock is worse still with a D-pad. Left/Right moves between the wheels, OK steps
 * into one, Up/Down then change it (holding a key auto-repeats), and OK or Back steps back out — see
 * [Wheel] for why editing is a mode rather than always-on.
 *
 * Every change is pushed through [CatchupJumps.clampToArchive], so the wheels stop dead at the live
 * edge and at the far end of the archive. Nothing needs to explain why: the number simply will not go
 * further than the recording does.
 */
@Composable
internal fun CatchupManualTimeDialog(
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    // One "now" for the dialog's lifetime. A ticking clock would shift every wheel under the user's
    // fingers and make the clamp boundaries move while they aim at them.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { java.util.TimeZone.getDefault() }
    val formatDay = rememberBestDateFormatter("EEEMMMd")

    // Start an hour back: inside the archive on any channel, and a sensible neighbourhood to nudge from.
    var point by remember {
        mutableStateOf(
            CatchupJumps.clampToArchive(
                CatchupJumps.pointAt(CatchupJumps.instantFor(3600, nowMs), nowMs, zone),
                nowMs, zone, windowSec,
            ),
        )
    }
    val maxDaysAgo = remember(windowSec) { CatchupJumps.selectableDays(windowSec) - 1 }

    fun nudge(deltaDays: Int = 0, deltaHours: Int = 0, deltaMinutes: Int = 0) {
        val p = point
        val next = CatchupJumps.Point(
            daysAgo = (p.daysAgo + deltaDays).coerceIn(0, maxDaysAgo),
            // Hours and minutes wrap: rolling 23 → 00 is how a clock behaves, and the archive clamp
            // below catches anything the wrap puts out of reach.
            hour = ((p.hour + deltaHours) + 24) % 24,
            minute = ((p.minute + deltaMinutes) + 60) % 60,
        )
        point = CatchupJumps.clampToArchive(next, nowMs, zone, windowSec)
    }

    val dayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { dayFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_catchup_jump_exact),
        body = stringResource(R.string.content_catchup_jump_hint),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_play), onClick = { onPick(CatchupJumps.offsetSecOf(point, nowMs, zone)) }, icon = tv.own.owntv.ui.components.OwnTVIcon.PLAY, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        Row(
            Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wheel(
                value = formatDay(CatchupJumps.instantOf(point, nowMs, zone)),
                onUp = { nudge(deltaDays = -1) },   // toward today
                onDown = { nudge(deltaDays = +1) }, // further back
                modifier = Modifier.weight(1.6f).focusRequester(dayFocus),
            )
            Wheel(value = two(point.hour), onUp = { nudge(deltaHours = +1) }, onDown = { nudge(deltaHours = -1) }, modifier = Modifier.weight(1f))
            Text(":", style = tv.own.owntv.ui.theme.stageText(30, 800), color = tv.own.owntv.ui.theme.StageColors.Muted)
            Wheel(value = two(point.minute), onUp = { nudge(deltaMinutes = +1) }, onDown = { nudge(deltaMinutes = -1) }, modifier = Modifier.weight(1f))
        }
    }
}

private fun two(n: Int): String = n.toString().padStart(2, '0')

/**
 * One value column, with an explicit edit mode.
 *
 * OK steps *into* the wheel, Up/Down then change the value, and OK or Back steps back *out*. The
 * obvious design — Up/Down always editing the focused wheel — is a trap on a TV: the wheels sit above
 * the Play button, so a wheel that swallows Down leaves no way to reach it, and the dialog becomes a
 * one-way street. Only while editing are Up/Down consumed; the rest of the time they fall through to
 * ordinary spatial navigation.
 *
 * Back is consumed while editing too, otherwise it would close the whole dialog instead of finishing
 * the edit the user is in the middle of.
 */
@Composable
private fun Wheel(
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val a = tv.own.owntv.ui.theme.stageAccent
    // OK arms the wheel (accent, arrows shown), ▲ ▼ turn it, OK or Back puts it down. Losing focus
    // (◀ ▶ to a neighbour) must not leave it armed behind the user's back.
    tv.own.owntv.ui.stage.StageSurface(
        onClick = { editing = !editing },
        radius = 16.mpx,
        focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
        idle = if (editing) Modifier.background(a.accent.copy(alpha = 0.18f), RoundedCornerShape(16.mpx)) else Modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.mpx)),
        modifier = modifier
            .height(120.mpx)
            .onFocusChanged { if (!it.isFocused) editing = false }
            // Preview, so Back is taken before the dialog's own Back can dismiss everything.
            .onPreviewKeyEvent { e ->
                if (!editing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    Key.Back -> { editing = false; true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Transparent rather than absent, so showing the arrows never reflows the row.
            Text("▲", style = tv.own.owntv.ui.theme.stageText(14, 700), color = if (editing) a.accent else Color.Transparent)
            Text(value, style = tv.own.owntv.ui.theme.stageText(28, 800), color = if (editing) a.accent else tv.own.owntv.ui.theme.StageColors.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text("▼", style = tv.own.owntv.ui.theme.stageText(14, 700), color = if (editing) a.accent else Color.Transparent)
        }
    }
}
