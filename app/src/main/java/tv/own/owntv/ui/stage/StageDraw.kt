package tv.own.owntv.ui.stage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import tv.own.owntv.core.theme.BackgroundStyle
import tv.own.owntv.ui.theme.GlassPositionElement
import tv.own.owntv.ui.theme.GlassPositionState
import tv.own.owntv.ui.theme.LocalBackground
import tv.own.owntv.ui.theme.LocalBlurredBackdrop
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.drawBackdropSlice
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.drawEllipticalGlow
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx

/*
 * The CSS the Stage mockup is written in, drawn the same way: `box-shadow` glows and outer rings,
 * the page background, and the glass panel.
 */

private fun roundRect(bounds: Rect, inflate: Float, radius: Float, dy: Float = 0f) = RoundRect(
    left = bounds.left - inflate, top = bounds.top - inflate + dy,
    right = bounds.right + inflate, bottom = bounds.bottom + inflate + dy,
    cornerRadius = CornerRadius((radius + inflate).coerceAtLeast(0f)),
)

private fun roundRectPath(rect: RoundRect) = Path().apply { addRoundRect(rect) }

/**
 * CSS `box-shadow: 0 <dy> <blur> <spread> <color>` outside this element's rounded box. Like the browser,
 * nothing is drawn under the element itself, so a translucent fill never shows the glow through it.
 *
 * A browser blurs with sigma = blur / 2; Android's shadow layer takes a radius that Skia turns into
 * sigma = 0.57735 × radius + 0.5, hence the conversion. Hardware shadow layers need API 28, which every
 * TV this app supports has. [bounds] is the box, when it is not the element itself.
 */
fun DrawScope.drawBoxShadow(
    color: Color,
    blur: Float,
    radius: Float,
    dy: Float = 0f,
    spread: Float = 0f,
    bounds: Rect = size.toRect(),
) {
    if (color.alpha <= 0f) return
    val shadowRadius = ((blur / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val box = roundRect(bounds, spread, radius, dy)
    clipPath(roundRectPath(roundRect(bounds, 0f, radius)), ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            // The box itself must not show where spread or dy moves it past the element, so it is
            // painted at alpha 1/255. A non-opaque shadow colour keeps its own alpha (Paint docs),
            // and every Stage glow is non-opaque; a fully transparent paint could be skipped outright.
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.copy(alpha = 1f / 255f).toArgb()
                setShadowLayer(shadowRadius, 0f, 0f, color.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(
                box.left, box.top, box.right, box.bottom, box.topLeftCornerRadius.x, box.topLeftCornerRadius.y, paint,
            )
        }
    }
}

/** CSS `box-shadow: 0 0 0 <width> <color>`: a solid ring just outside the element, starting at [inset] out. */
fun DrawScope.drawOuterRing(color: Color, width: Float, radius: Float, inset: Float = 0f) {
    val half = width / 2f
    val r = roundRect(size.toRect(), inset + half, radius)
    drawRoundRect(
        color = color,
        topLeft = Offset(r.left, r.top),
        size = Size(r.width, r.height),
        cornerRadius = r.topLeftCornerRadius,
        style = Stroke(width),
    )
}

/** CSS `box-shadow: inset 0 0 0 <width> <color>`: a ring inside the element's edge. */
fun DrawScope.drawInnerRing(color: Color, width: Float, radius: Float) {
    val half = width / 2f
    val r = roundRect(size.toRect(), -half, radius)
    drawRoundRect(
        color = color,
        topLeft = Offset(r.left, r.top),
        size = Size(r.width, r.height),
        cornerRadius = r.topLeftCornerRadius,
        style = Stroke(width),
    )
}

/**
 * The page behind a Stage screen (`.mine .bg`), following Glass & background:
 * - Stage colours: three soft lights over a dark 160° wash (the accent light is the switchable one);
 * - Plain: #070B0A, with Accent light a faint 7% accent light (today's Glass-off page);
 * - Picture: nothing here — MainActivity draws the picture under the whole app — except the Accent
 *   light, drawn over the picture the way the mockup does (`wp-soft`).
 * Each light is a cached texture (see GradientTextures), never a brush. The 160° wash runs between
 * three near-black stops that differ by at most 5 per channel, so it is drawn as a vertical fade.
 */
@Composable
fun Modifier.stageBackground(accent: Color): Modifier {
    val bg = LocalBackground.current
    return when {
        bg.showsPicture -> if (bg.accentLight) drawBehindLights { w, h ->
            light(accent, 0.22f, Offset(w * 0.08f, h * 0.04f), 900.mpx.toPx(), 620.mpx.toPx(), 0.62f)
        } else this
        bg.style == BackgroundStyle.PLAIN -> this
            .drawBehind { drawRect(Color(0xFF070B0A)) }
            .then(if (bg.accentLight) Modifier.drawBehindLights { _, _ ->
                light(accent, 0.07f, Offset.Zero, 1200.mpx.toPx(), 700.mpx.toPx(), 0.60f)
            } else Modifier)
        else -> this
            .gradientWash(true, 0f to Color(0xFF0B1216), 0.55f to Color(0xFF06090B), 1f to Color(0xFF070A10))
            .drawBehindLights { w, h ->
                light(Color(255, 160, 120), 0.07f, Offset(w * 0.70f, h * 0.18f), 700.mpx.toPx(), 500.mpx.toPx(), 0.60f)
                light(Color(96, 120, 255), 0.16f, Offset(w, h), 1100.mpx.toPx(), 760.mpx.toPx(), 0.60f)
                if (bg.accentLight) light(accent, 0.20f, Offset(w * 0.08f, h * 0.04f), 900.mpx.toPx(), 620.mpx.toPx(), 0.62f)
            }
    }
}

private fun Modifier.drawBehindLights(lights: DrawScope.(Float, Float) -> Unit) = drawWithCache {
    onDrawBehind { lights(size.width, size.height) }
}

private fun DrawScope.light(color: Color, alpha: Float, center: Offset, rx: Float, ry: Float, end: Float) =
    drawEllipticalGlow(
        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
        positions = listOf(0f, end),
        center = center,
        radiusX = rx,
        radiusY = ry,
    )

/**
 * The Stage glass panel (`.mine .glass`), for chrome only: rail, sheets, menus, pills, cards.
 *
 * Glass on: the #121A1E tint at the user's Glass opacity (56% = the mockup's), a 1 px top highlight
 * (white 10%), a 1 px rim (white 7%) and the drop shadow `0 24 60` black 45%. Over a picture the panel
 * first draws the matching slice of the blurred picture — the mockup's `backdrop-filter`, without a live
 * blur pass (the G10 cannot afford one): the picture is blurred once when it is chosen.
 * Glass off: solid #121A1C, rim white 5%, shadow black 50%.
 *
 * [overContent] chrome — the rail, menus and popups, which float over text — keeps at least 80% so the
 * words behind it are never readable through it; above that the user's opacity applies.
 */
@Composable
fun Modifier.stageGlass(radius: Dp, overContent: Boolean = false): Modifier {
    val config = LocalGlass.current
    val on = config.enabled
    // Over content (sheets, the rail, menus) the opacity is scaled into 55-100% rather than floored at 80%:
    // the floor made every setting below 80% look the same (owner, 2026-10-02), the scale keeps text
    // readable and still answers each step (50% -> 78%).
    val fill = if (on) StageColors.GlassTint.copy(alpha = if (overContent) 0.55f + 0.45f * config.alpha.coerceIn(0f, 1f) else config.alpha) else StageColors.GlassOff
    val blurred = if (on && LocalBackground.current.showsPicture) LocalBlurredBackdrop.current else null
    val frost = blurred?.frostFor(0.9f)
    val position = if (frost != null) remember { GlassPositionState() } else null
    return (if (position != null) this.then(GlassPositionElement(position)) else this).drawWithCache {
        val r = radius.toPx()
        val px = 1.mpx.toPx()
        val shape = roundRectPath(roundRect(size.toRect(), 0f, r))
        val shifted = roundRectPath(roundRect(size.toRect(), 0f, r, dy = px))
        onDrawBehind {
            drawBoxShadow(Color.Black.copy(alpha = if (on) 0.45f else 0.5f), 60.mpx.toPx(), r, dy = 24.mpx.toPx())
            if (blurred != null && frost != null && position != null) {
                clipPath(shape) { drawBackdropSlice(blurred, frost, position.bounds) }
            }
            drawPath(shape, fill)
            if (on) {
                // inset 0 1px 0: the sliver of the box its own copy, moved 1 px down, does not cover.
                clipPath(shape) {
                    clipPath(shifted, ClipOp.Difference) { drawRect(Color.White.copy(alpha = 0.10f)) }
                }
            }
            drawInnerRing(Color.White.copy(alpha = if (on) 0.07f else 0.05f), px, r)
        }
    }
}

