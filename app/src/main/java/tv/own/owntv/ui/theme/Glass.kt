package tv.own.owntv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass — translucent frosted surface treatment.
 *
 * Each surface that can go glassy is tagged with a [GlassSurface]. When the feature is enabled
 * (a background image is set), a panel whose surface is in [GlassConfig.scope] renders with a
 * translucent fill + specular top-edge highlight (Tier 1, all devices). Backdrop blur (Tier 2,
 * the real "frost") is layered on in Phase 4 via a separate cached blur.
 *
 * The fullscreen player is intentionally NOT a [GlassSurface] — it covers the shell when visible,
 * so leaving it opaque is correct.
 */
enum class GlassSurface {
    /** The large rounded content panels (Home/Movies/Series/Live/etc. root containers). */
    PANELS,

    /** The left navigation rail (sidebar). */
    SIDEBAR,

    /** The right detail column (preview pane / channel detail). */
    PREVIEW,

    /** Centered popup dialogs and sheets (accent picker, settings sheets, chooser dialogs). */
    DIALOGS,

    /** The top bar: active section + search pill + clock + playlist chip. */
    TOPBAR,

    /** Poster cards and list items. */
    CARDS,

    /** The docked (non-fullscreen) mini-player bar. */
    MINI_PLAYER,
}

/** Every surface that can be glassed. Used to implement the "All" master tick. */
val ALL_GLASS_SURFACES: Set<GlassSurface> = GlassSurface.entries.toSet()

/**
 * Resolved glass state.
 *
 * @param scope which surfaces are glassy. Empty = feature off (panels stay solid).
 * @param alpha panel fill alpha when glassed, in 0..1. Default 0.75 (the "nice preset").
 *   Pure 0 means fully transparent (image shows through unobstructed), 1 = opaque (no glass effect).
 * @param blurStrength how much real backdrop blur ("frost") to apply, in 0..1. 0 = Tier-1
 *   translucency only (sharp background reads through); 1 = fully frosted. Default 0.8. Only has an
 *   effect on API 31+ ([supportsBackdropBlur]) and when a background image is present. The strength
 *   is the draw alpha of the single shared blurred-backdrop slice — O(1) to change, no re-blur.
 */
@Stable
data class GlassConfig(
    val scope: Set<GlassSurface> = emptySet(),
    val alpha: Float = DEFAULT_GLASS_ALPHA,
    val blurStrength: Float = DEFAULT_BLUR_STRENGTH,
) {
    /** Glass is "on" only when at least one surface is scoped. */
    val enabled: Boolean get() = scope.isNotEmpty()

    /** True when [surface] should render as glass. */
    fun isGlassy(surface: GlassSurface): Boolean = enabled && surface in scope

    /** Bitmask encode/decode — used by SettingsRepository persistence. */
    fun toBitmask(): Int {
        var bits = 0
        for (s in scope) bits = bits or (1 shl s.ordinal)
        return bits
    }

    companion object {
        const val DEFAULT_GLASS_ALPHA: Float = 0.75f
        const val DEFAULT_BLUR_STRENGTH: Float = 0.8f

        fun fromBitmask(bits: Int, alpha: Float = DEFAULT_GLASS_ALPHA, blurStrength: Float = DEFAULT_BLUR_STRENGTH): GlassConfig {
            val scope = GlassSurface.entries.filter { (bits shr it.ordinal) and 1 == 1 }.toSet()
            return GlassConfig(scope = scope, alpha = alpha, blurStrength = blurStrength)
        }
    }
}

/** Ambient glass state. Default is "off" so the app looks unchanged until a background image is set. */
val LocalGlass = compositionLocalOf { GlassConfig() }

/**
 * Which [GlassSurface] an ambient action control (e.g. `OwnTVButton`) should frost with, or null to
 * stay flat. Used by the shared action pill so a button frosts with whatever surface its host renders
 * on — `DIALOGS` inside a popup, `CARDS` on a settings panel — without each call site passing a flag.
 *
 * Defaults to [GlassSurface.DIALOGS]: most action pills live in dialogs/popups, and a `Popup` does
 * NOT inherit the screen's CompositionLocals, so each popup would otherwise need its own provider.
 * The DIALOGS default means dialog buttons are correct with zero per-dialog wiring; screens whose
 * buttons sit on a panel (e.g. the settings list, Customize) override it to `CARDS`, and surfaces that
 * should never frost (the fullscreen player, over opaque video) provide null.
 */
val LocalActionSurface = compositionLocalOf<GlassSurface?> { GlassSurface.DIALOGS }

/**
 * Holder for the single shared blurred copy of the background image (Phase 4 backdrop blur).
 * Provided by `MainActivity` when a background image is set AND [supportsBackdropBlur]; null
 * otherwise (panels then fall back to Tier-1 translucency over the sharp image).
 *
 * [rootSizePx] is the full app viewport size in pixels (the area the bitmap stands in for, since the
 * blurred bitmap is a downscaled stand-in). Each glass surface maps its on-screen rect from root px
 * → bitmap coords using [rootSizePx], then draws the matching slice translated to its own position so
 * the frost lines up with the photo behind it.
 */
@Stable
data class BlurredBackdrop(
    val bitmap: ImageBitmap,
    val rootSizePx: Size,
)

/** Ambient blurred backdrop, or null when blur isn't active. See [BlurredBackdrop]. */
val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdrop?> { null }

/**
 * Render this surface as glass: (Tier 2) a frosted slice of the blurred backdrop aligned to this
 * panel's on-screen position, then a translucent fill + a specular top-edge highlight, clipped to
 * [shape]. Falls back to Tier 1 (translucency + sheen, no frost) when there's no blurred backdrop
 * (no image / pre-API 31 / blurStrength 0). When the surface is not in scope the panel keeps its
 * normal solid [baseFill].
 *
 * Pass the [surface] so we honour the per-surface scope.
 *
 * @param baseFill the opaque colour this panel would normally use.
 * @param shape clip shape for the fill + highlight. Must match the container's own clip.
 */
@Composable
fun Modifier.glass(
    surface: GlassSurface,
    baseFill: Color,
    shape: Shape = RectangleShape,
    cornerRadius: Dp = 22.dp,
    // Per-call frost multiplier (0..1) applied on top of the global blurStrength — lets small chrome
    // (e.g. top-bar chips) read as lighter glass than the big panels without changing the global setting.
    frostScale: Float = 1f,
): Modifier {
    val config = LocalGlass.current
    // Fully transparent fill = nothing to render (e.g. an idle nav/list item whose highlight fill
    // is Color.Transparent). Skip both the frost and the background so glass() can be called
    // unconditionally on highlights that toggle between transparent (idle) and filled (focused).
    if (baseFill.alpha == 0f) return this
    // No glass for this surface → keep the solid fill, no highlight, no frost.
    if (!config.isGlassy(surface)) return this.background(baseFill, shape)

    val a = config.alpha.coerceIn(0f, 1f)
    val translucent = baseFill.copy(alpha = a)

    // Phase 4 — real backdrop blur. The single shared blurred bitmap (provided by MainActivity) is
    // drawn here as a slice aligned to this panel's on-screen position, so the frost matches the photo
    // region behind it. O(1) per panel: one textured draw.
    val blurred = LocalBlurredBackdrop.current
    val frostAlpha = (config.blurStrength * frostScale).coerceIn(0f, 1f)
    // Capture this panel's rect in root coordinates (same approach as HomeScreen hero preview). Root
    // space is correct: the background image lives in the same root Compose tree above the shell.
    var bounds by remember { mutableStateOf(Rect.Zero) }

    // No blurred backdrop (no image / pre-API 31 / blur off) → Tier 1: translucent fill + sheen only.
    if (blurred == null || frostAlpha <= 0f) {
        return this
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .background(translucent, shape)
            .drawWithContent {
                drawContent()
                drawSheen()
            }
    }

    // Tier 2 — frost. Compose can't blur a node's own backdrop, so we approximate glassmorphism by
    // drawing the blurred slice OPAQUELY: it fully MASKS the sharp background photo that would
    // otherwise bleed through the translucent panel (a translucent blurred copy over a sharp original
    // is visually identical → no perceptible blur). frostAlpha then blends frost vs. the panel tint.
    val rootW = blurred.rootSizePx.width
    val rootH = blurred.rootSizePx.height
    val bmp = blurred.bitmap
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .drawWithContent {
            if (rootW > 0f && rootH > 0f && bounds.width > 0f && bounds.height > 0f && bmp.width > 0 && bmp.height > 0) {
                // Draw the WHOLE blurred bitmap scaled to the root viewport, translated so the slice that
                // lands behind THIS panel is the one visible. The node is already clipped to `shape`
                // (by the upstream .clip() in RoundedPanel/DialogPanel), so only the panel's region shows.
                // We deliberately do NOT use drawImage's src/dst-slice overload here: that overload takes
                // IntOffset/IntSize, whose integer truncation of the panel's sub-pixel position misaligns
                // RGB subpixels under GPU bilinear filtering → saturated red/green/blue blocks. Using the
                // DrawScope's float translate/scale + whole-image drawImage keeps sampling continuous.
                val sx = size.width / bounds.width       // node px ↔ root px scale (usually ~1)
                val sy = size.height / bounds.height
                // Order + pivot matter: translate must be OUTSIDE the scale (in root px, not scaled px)
                // and the scale must pivot at the origin (DrawScope.scale defaults to the center). Both
                // are invisible while the bitmap is at full root resolution (scale ≈ 1) but misalign the
                // frost whenever the bitmap is smaller than the root — e.g. a 4K root with the 1920-capped
                // bitmap (scale = 2).
                translate(-bounds.left * sx, -bounds.top * sy) {
                    scale(rootW / bmp.width * sx, rootH / bmp.height * sy, pivot = Offset.Zero) {
                        drawImage(bmp, topLeft = Offset.Zero)
                    }
                }
                // Blend the frost (opaque) with the panel's tinted fill by strength: at frostAlpha=1 the
                // pure frosted photo shows; below 1 the solid tint increasingly dominates. Also folds in
                // the alpha (transparency) control so users can still let some background texture through.
                val tintAlpha = (1f - frostAlpha) + frostAlpha * (1f - a)
                if (tintAlpha > 0f) drawRect(baseFill.copy(alpha = tintAlpha.coerceIn(0f, 1f)))
            } else {
                // Bounds not resolved yet (first frame) → keep the panel readable with the solid fill.
                drawRect(baseFill)
            }
            drawContent()
            drawSheen()
        }
}

/**
 * Specular top-edge highlight: a bright sweep near the top, like a glass bevel. Strength scales
 * *inversely* with surface size — small chrome (buttons, chips, search pills) is a small piece of
 * glass so it needs a brighter, glossier sheen to read; large panels stay subtle so they don't wash
 * out. This is what gives a button the polished "Liquid Glass" look instead of a flat tint.
 */
private fun DrawScope.drawSheen() {
    val minDim = size.minDimension
    val sheenHeight = minDim * 0.4f
    // ~140dp and below = button/chip scale (full gloss); ~600dp+ = large panel (subtle). Linear between.
    val gloss = (1f - ((minDim / 140f.dp.toPx()) - 1f).coerceIn(0f, 1f))
    val peak = 0.16f + 0.30f * gloss
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = peak), Color.White.copy(alpha = 0f)),
            startY = 0f,
            endY = sheenHeight,
        ),
    )
}

/**
 * True when backdrop blur is available on this device. Tier 2 (Phase 4) uses a cached blurred
 * copy of the background image; older devices fall back to Tier 1 translucency only.
 */
@Composable
@ReadOnlyComposable
fun supportsBackdropBlur(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
