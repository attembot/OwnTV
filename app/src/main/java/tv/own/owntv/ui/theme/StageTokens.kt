package tv.own.owntv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens of the Stage redesign, copied from the frozen mockup (`future-plan/ui-audit-227`,
 * `mockup.css` + `stage.css`).
 *
 * The mockup is drawn at 1920×1080 and is matched at the default UI Zoom of 90% (decision D1). The TV
 * runs 320 dpi with a 1920×1080 override, so density = 2.0 × zoom = 1.8 px per dp there, and one
 * mockup pixel is 1 / 1.8 dp. Every value can then be copied from the mockup unchanged: an 84 px row
 * is `84.mpx`. At other zooms Stage scales with the setting like everything else.
 */
private const val DP_PER_MPX = 1f / 1.8f

/** A mockup pixel as dp (see the file comment). */
val Number.mpx: Dp get() = (toFloat() * DP_PER_MPX).dp

/** A mockup pixel as sp, so the user's font size still scales Stage type. */
val Number.mpxSp: TextUnit get() = (toFloat() * DP_PER_MPX).sp

/** Stage's fixed neutrals. Accent, on-accent and focus stay the user's own, from [OwnTVTheme.colors]. */
object StageColors {
    val Text = Color(0xFFF1F5F3)
    val Muted = Color(0xFFA7B3AE)
    val Dim = Color(0xFF6D7A75)
    /** Idle text of menu items, sheet items and category rows (`#dbe4e0` / `#d5dfdb` in the mockup). */
    val ItemText = Color(0xFFD5DFDB)
    val MenuItemText = Color(0xFFDBE4E0)
    val TagText = Color(0xFFC8D3CE)
    /** The one white used for "idle control fill" everywhere: tools, search fields, segmented. */
    val ControlFill = Color.White.copy(alpha = 0.06f)
    val GlassTint = Color(0xFF121A1E)
    val GlassOff = Color(0xFF121A1C)
    val RatingStar = Color(0xFFFFCC4D)
    /** `.ok`: "✓ Ready to watch". */
    val Ok = Color(0xFF7BE3A4)
    /** The amber of "⚠ Never" and the Never-backed-up card (`#ffb86b`). */
    val Warn = Color(0xFFFFB86B)
    /** `.danger`: Clear watch history, Clear log (`#ff8a7a`). */
    val Danger = Color(0xFFFF8A7A)
}

/** Corner radii, in mockup pixels. Each phase adds the ones its screens use (rail, video, guide cell…). */
object StageRadii {
    val Sheet = 32.mpx
    val Menu = 28.mpx
    val Button = 22.mpx
    val Row = 20.mpx
    val Pill = 17.mpx
    val Poster = 16.mpx
    val SheetItem = 16.mpx
    val Tool = 15.mpx
    val MenuItem = 14.mpx
    val Plate = 12.mpx
    val Tag = 6.mpx
}

/** The accent triple every Stage treatment is built from. */
data class StageAccent(val accent: Color, val onAccent: Color, val focus: Color)

val stageAccent: StageAccent
    @Composable
    @ReadOnlyComposable
    get() = OwnTVTheme.colors.let { StageAccent(it.primary, it.onPrimary, it.focusBorder) }

/**
 * Stage type: a mockup size and weight in the user's main font (Plus Jakarta Sans by default).
 * Line height is left to the font, as the mockup's `line-height: normal` leaves it to the browser.
 */
@Composable
@ReadOnlyComposable
fun stageText(size: Number, weight: Int, letterSpacing: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = LocalMainFontFamily.current,
    fontSize = size.mpxSp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacing,
)
