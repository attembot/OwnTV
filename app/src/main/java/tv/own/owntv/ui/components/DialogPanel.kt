package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import tv.own.owntv.ui.stage.stageGlass

/**
 * Shared panel chrome for centered popup dialogs: fixed width, rounded clip, surface fill —
 * and by default a [verticalScroll], so a dialog taller than the screen (small/low-resolution
 * TVs, large interface zoom) scrolls instead of clipping its lower controls out of reach.
 * D-pad focus automatically brings off-screen children into view inside the scroll area.
 *
 * Pass [scroll] = false when the dialog's column already contains a LazyColumn or `weight()`
 * children — nesting two same-direction scrollers is illegal in Compose, so the dialog must manage
 * its own scrolling (typically by capping the inner LazyColumn's height and leaving the outer
 * column fixed). The clip + fill still apply.
 *
 * The fill is the Stage glass panel (rim, top highlight, shadow; solid with Glass Effect off), so every
 * popup built on this follows the new design. A dialog that asks for its own [fill] keeps it.
 */
@Composable
fun Modifier.dialogPanel(
    width: Dp = 440.dp,
    corner: Dp = 24.dp,
    padding: Dp = 24.dp,
    fill: Color? = null,
    scroll: Boolean = true,
    panelHeight: Dp? = null,
): Modifier {
    val shape = RoundedCornerShape(corner)
    val base = this
        .width(width)
        .then(panelHeight?.let { Modifier.height(it) } ?: Modifier)
        .then(if (fill != null) Modifier.clip(shape).background(fill) else Modifier.stageGlass(corner, overContent = true).clip(shape))
    // verticalScroll + a nested LazyColumn is an illegal same-direction nest; callers with an inner
    // LazyColumn pass scroll = false and cap the list height themselves.
    return if (scroll) base.verticalScroll(rememberScrollState()).padding(padding) else base.padding(padding)
}

/** Shared modal wash, as the Stage menus draw it: the screen behind stays readable, the popup in front. */
fun Modifier.modalScrim(strength: Float = 1f): Modifier =
    background(Color(2, 5, 6).copy(alpha = (0.55f * strength.coerceIn(0f, 1.35f)).coerceAtMost(0.72f)))
