package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * Shared panel chrome for centered popup dialogs: fixed width, rounded clip, surface fill —
 * and by default a [verticalScroll], so a dialog taller than the screen (small/low-resolution
 * TVs, large interface zoom) scrolls instead of clipping its lower controls out of reach.
 * D-pad focus automatically brings off-screen children into view inside the scroll area.
 *
 * Pass [scroll] = false when the dialog's column already contains a LazyColumn or `weight()`
 * children — nesting two same-direction scrollers is illegal in Compose, so the dialog must manage
 * its own scrolling (typically by capping the inner LazyColumn's height and leaving the outer
 * column fixed). The clip + glass fill still apply.
 *
 * Liquid Glass: when [GlassSurface.DIALOGS] is in scope, the fill becomes translucent + gains the
 * specular highlight, so the background image reads through the popups too (Phase 1: all on).
 */
@Composable
fun Modifier.dialogPanel(
    width: Dp = 440.dp,
    corner: Dp = 20.dp,
    padding: Dp = 24.dp,
    fill: Color? = null,
    scroll: Boolean = true,
): Modifier {
    val base = this
        .width(width)
        .clip(RoundedCornerShape(corner))
        .glass(
            surface = GlassSurface.DIALOGS,
            baseFill = fill ?: OwnTVTheme.colors.surfaceContainerHigh,
            shape = RoundedCornerShape(corner),
            cornerRadius = corner,
        )
    // verticalScroll + a nested LazyColumn is an illegal same-direction nest; callers with an inner
    // LazyColumn pass scroll = false and cap the list height themselves.
    return if (scroll) base.verticalScroll(rememberScrollState()).padding(padding) else base.padding(padding)
}
