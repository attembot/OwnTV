package tv.own.owntv.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import kotlin.math.roundToInt

/** How long the mismatched stream must play before the suggestion appears — long enough that it never
 *  interrupts zapping, short enough that the judder is still on screen when it's read. */
private const val SETTLE_MS = 8_000L

/**
 * F13: the one-time "your TV is at 60 Hz and this stream is 25 fps" suggestion.
 *
 * On mpv's direct `vo=mediacodec_embed` path the app does not control scan-out timing — frames go to the
 * decoder's surface and the display presents them on its own cadence. 25 fps on a fixed 60 Hz output
 * therefore becomes an uneven 2:3 pulldown (visible judder) while 50 fps maps cleanly. That is a known
 * limitation of the direct path, not something OwnTV can smooth over, and **Auto frame rate is the only
 * cure** — which ships off by default, so the affected user has no way to know it exists.
 *
 * Deliberately conservative, because an unsolicited dialog over a playing video is intrusive:
 * - full-screen only, and only while Auto frame rate is OFF;
 * - only when the display really is on a rate that isn't a multiple of the stream's, *and* a matching
 *   mode exists at the current resolution ([FrameRateController.betterRefreshRateFor]). On a panel with
 *   nothing but 60 Hz there is nothing to offer, so nothing is said;
 * - only after [SETTLE_MS] of continuous playback at that frame rate;
 * - shown **once ever**, whichever way it is answered.
 */
@Composable
fun AutoFrameRatePrompt(
    fps: Float?,
    afrEnabled: Boolean,
    alreadyPrompted: Boolean,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var offer by remember { mutableStateOf<Pair<Float, Float>?>(null) } // current Hz to target Hz

    LaunchedEffect(fps, afrEnabled, alreadyPrompted) {
        offer = null
        if (afrEnabled || alreadyPrompted) return@LaunchedEffect
        val rate = fps ?: return@LaunchedEffect
        if (rate <= 0f) return@LaunchedEffect
        val activity = context.findActivityForPrompt() ?: return@LaunchedEffect
        val target = FrameRateController.betterRefreshRateFor(activity, rate) ?: return@LaunchedEffect
        val current = FrameRateController.currentRefreshRate(activity) ?: return@LaunchedEffect
        delay(SETTLE_MS) // cancelled by any fps change, so zapping never triggers it
        offer = current to target
    }

    val (currentHz, targetHz) = offer ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val colors = OwnTVTheme.colors
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 520.dp, padding = 28.dp)) {
            Text("This channel may judder", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(
                "It plays at ${fps?.roundToInt()} fps while your TV is running at ${currentHz.roundToInt()} Hz, " +
                    "which can't show those frames evenly — motion looks slightly jerky.\n\n" +
                    "Auto frame rate switches the TV to ${targetHz.roundToInt()} Hz while you watch and puts it " +
                    "back afterwards. Some TVs blank for a moment when the rate changes; you can turn it off " +
                    "again in Settings → Video Player.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Not now", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Turn on", onClick = onEnable, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}

private fun android.content.Context.findActivityForPrompt(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
