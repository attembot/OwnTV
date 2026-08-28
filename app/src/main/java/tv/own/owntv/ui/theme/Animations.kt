package tv.own.owntv.ui.theme

import androidx.annotation.StringRes
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import tv.own.owntv.R

/**
 * How much UI motion to render. A performance/comfort control: lower-end Android TV boxes can feel
 * laggy when moving quickly between menus, so the user can tone the animations down (or off).
 */
enum class AnimationLevel(@param:StringRes val labelRes: Int) {
    // On = normal motion; Off = instant (no transitions). The fixed grid (v4.0.0) removed the old reason for
    // a middle "Reduced" tier, so this is now a simple On/Off reduce-motion toggle. (Legacy "REDUCED" values
    // fall back to On via the settings store's safe parse.)
    FULL(R.string.common_on), OFF(R.string.common_off);

    /** Scale an animation duration to this level (OFF collapses to 0 → an instant snap). */
    fun scale(durationMs: Int): Int = when (this) {
        FULL -> durationMs
        OFF -> 0
    }
}

/** Current animation level, provided at the theme root from the user's setting. */
val LocalAnimationLevel = staticCompositionLocalOf { AnimationLevel.FULL }

/** True unless the user has turned animations fully Off — for spots that gate a transition entirely. */
val animationsOn: Boolean
    @Composable @ReadOnlyComposable get() = LocalAnimationLevel.current != AnimationLevel.OFF

/**
 * A tween whose duration follows the user's Animations setting (Off → an instant 0 ms snap).
 *
 * **Never pass this to `infiniteRepeatable`.** Compose divides the play time by the iteration
 * duration to work out which repeat it is in, so a 0 ms iteration is a divide-by-zero on the main
 * thread one frame after the animation starts. Gate the whole transition on [animationsOn] instead
 * and hand `infiniteRepeatable` a plain fixed-duration `tween`.
 */
@Composable
@ReadOnlyComposable
fun <T> ownTvTween(durationMs: Int = 200, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
    tween(LocalAnimationLevel.current.scale(durationMs), easing = easing)
