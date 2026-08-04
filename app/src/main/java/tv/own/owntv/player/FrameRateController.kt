package tv.own.owntv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * Auto frame rate (AFR) — switch the DISPLAY to a refresh rate that matches the video, so 24/25/50 fps
 * content stops juddering on a fixed 60Hz output.
 *
 * Two independent mechanisms exist on Android and OwnTV uses both:
 *
 * 1. `Surface.setFrameRate()` (API 30+) — a *hint* attached to the video surface. Cheap and seamless,
 *    but it does nothing at all on Android 10 and below. See [MpvVideoSurface] and ExoPlayer's
 *    `setVideoChangeFrameRateStrategy`.
 * 2. `WindowManager.LayoutParams.preferredDisplayModeId` (API 23+) — an explicit request for one of the
 *    display's advertised modes. This is what actually works on older TV boxes, notably Fire OS 7
 *    (API 28) where mechanism 1 is unavailable. That's the case in the report this was written for
 *    (Fire TV Stick 4K Max, Fire OS 7.7.1.3 — AFR worked in other players, which use this API).
 *
 * This controller implements mechanism 2. It is deliberately **window-level**, not surface-level:
 * `preferredDisplayModeId` is an attribute of the Activity's window, so it cannot live inside
 * [MpvVideoSurface] / `ExoPreviewSurface`. Both engines route through here, which also means VOD/mpv
 * gets working AFR on Fire OS 7 for the first time (previously mechanism 1 only → a silent no-op).
 *
 * Mode selection keeps the CURRENT resolution and only varies the refresh rate — never switch a 4K
 * output down to 1080p just to hit a rate. A mode is a match when its refresh rate is an integer
 * multiple (1x–3x) of the video fps, which makes 24fps prefer 24/48/72Hz and 25fps prefer 50Hz while
 * still accepting 60Hz for 30fps. Among matches the LOWEST multiple wins (a true 24Hz beats 72Hz).
 *
 * Everything is best-effort: no matching mode, a locked-down display, or a manufacturer that ignores
 * the request all degrade to "leave the display alone", never to a crash or a black screen.
 */
object FrameRateController {

    private const val TAG = "FrameRate"
    /** Refresh rates within this many Hz of an exact multiple count as a match (panels report 59.94 etc). */
    private const val TOLERANCE_HZ = 0.35f
    private const val MAX_MULTIPLE = 3
    /** Grace period before a release actually lands — see [reset]. */
    private const val RESET_DELAY_MS = 1_500L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingReset: Runnable? = null

    private fun cancelPendingReset() {
        pendingReset?.let { handler.removeCallbacks(it) }
        pendingReset = null
    }

    /**
     * Request a display mode matching [fps] for [activity]'s window. Pass fps <= 0 to leave the current
     * mode alone (an unknown frame rate is not a reason to switch anything).
     */
    fun apply(activity: Activity, fps: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || fps <= 0f) return
        cancelPendingReset()
        runCatching {
            val display: Display = activity.windowManager.defaultDisplay ?: return
            val current = display.mode ?: return
            val target = display.supportedModes
                ?.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                ?.mapNotNull { mode -> multipleOf(mode.refreshRate, fps)?.let { mode to it } }
                ?.minByOrNull { (mode, mult) -> mult * 1000 + abs(mode.refreshRate - mult * fps) }
                ?.first
                ?: return
            if (target.modeId == activity.window.attributes.preferredDisplayModeId) return
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = target.modeId }
            Log.i(TAG, "AFR: video ${fps}fps -> display mode ${target.modeId} (${target.refreshRate}Hz)")
        }.onFailure { Log.w(TAG, "AFR apply failed: ${it.message}") }
    }

    /**
     * Hand the display back to the system default (mode id 0) — called when playback stops.
     *
     * Deferred by [RESET_DELAY_MS] and cancelled by any [apply] in the meantime. The video surface is
     * disposed and remounted on ordinary events (the mpv<->ExoPlayer engine swap, the Realtek fresh-
     * Surface reset, dock/expand), and releasing the mode on each of those would make the TV re-handshake
     * HDMI twice for no reason — a visible black flash on many panels. A real stop still releases, just
     * a moment later.
     */
    fun reset(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        cancelPendingReset()
        val task = Runnable { resetNow(activity) }
        pendingReset = task
        handler.postDelayed(task, RESET_DELAY_MS)
    }

    private fun resetNow(activity: Activity) {
        pendingReset = null
        runCatching {
            if (activity.window.attributes.preferredDisplayModeId == 0) return
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 }
            Log.i(TAG, "AFR: display mode released")
        }.onFailure { Log.w(TAG, "AFR reset failed: ${it.message}") }
    }

    /** 1..MAX_MULTIPLE if [refreshRate] is (about) that many times [fps], else null. */
    private fun multipleOf(refreshRate: Float, fps: Float): Int? =
        (1..MAX_MULTIPLE).firstOrNull { abs(refreshRate - it * fps) <= TOLERANCE_HZ }

    /**
     * The refresh rate a matching display mode would give for [fps], or null when switching would not
     * help — because the display is already on a clean multiple, or because no mode at the current
     * resolution is one. Used to decide whether suggesting Auto frame rate is honest (F13): on a panel
     * with only a 60 Hz mode, 25 fps judder cannot be fixed by AFR and the app must not pretend it can.
     */
    fun betterRefreshRateFor(activity: Activity, fps: Float): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || fps <= 0f) return null
        return runCatching {
            val display: Display = activity.windowManager.defaultDisplay ?: return null
            val current = display.mode ?: return null
            if (multipleOf(current.refreshRate, fps) != null) return null // already clean — nothing to gain
            display.supportedModes
                ?.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                ?.mapNotNull { mode -> multipleOf(mode.refreshRate, fps)?.let { mode to it } }
                ?.minByOrNull { (mode, mult) -> mult * 1000 + abs(mode.refreshRate - mult * fps) }
                ?.first?.refreshRate
        }.getOrNull()
    }

    /** The refresh rate the display is on right now, or null if it can't be read. */
    fun currentRefreshRate(activity: Activity): Float? = runCatching {
        activity.windowManager.defaultDisplay?.mode?.refreshRate
    }.getOrNull()
}

/**
 * Applies [FrameRateController] for as long as this composable is in the tree, following [fps] as the
 * player reports it, and releases the display mode on dispose. Mount it from the full-screen video
 * surfaces only — a mini/preview window must not reconfigure the whole display.
 */
@Composable
fun AutoFrameRateEffect(fps: Float?, enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    // Apply on every fps change, but keep the release in its OWN effect keyed only on the activity —
    // keying the disposal on fps too would reset the display to default and re-request on each fps
    // update, i.e. an extra HDMI mode flip per change.
    LaunchedEffect(activity, enabled, fps) {
        if (activity == null) return@LaunchedEffect
        if (enabled) FrameRateController.apply(activity, fps ?: 0f) else FrameRateController.reset(activity)
    }
    DisposableEffect(activity) {
        onDispose { if (activity != null) FrameRateController.reset(activity) }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
