package tv.own.owntv.features.settings.data

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.theme.Dimens
import kotlin.math.roundToInt

/** The three browse sections that own a 3-panel layout (category rail · item list/grid · preview). */
enum class PanelSection { LIVE, MOVIES, SERIES }

/**
 * Manual panel-width adjustment (per section, per panel).
 *
 * Each panel holds its SHARE OF THE SCREEN in percent, and the three must add up to exactly 100 — the
 * user sees a running total and can't save until it reads 100%. That keeps the numbers meaning what
 * they look like they mean: "preview panel 40%" really is 40% of the row.
 *
 * Values are whole multiples of [STEP], so a total of exactly 100 is always reachable by stepping.
 */
object PanelWidthLimits {
    /** No panel may drop below this share — below ~10% a panel is unusable rather than just narrow. */
    const val MIN = 10
    const val MAX = 80
    const val STEP = 5
    const val TOTAL = 100

    fun clamp(pct: Int): Int = pct.coerceIn(MIN, MAX)

    /** Snap to the nearest [STEP] and clamp — every stored/displayed value goes through here. */
    fun snap(pct: Int): Int = clamp((pct.toFloat() / STEP).roundToInt() * STEP)
}

/** One section's three shares, in percent of the row. */
data class PanelShares(val category: Int, val list: Int, val preview: Int) {
    val total: Int get() = category + list + preview
    val isValid: Boolean get() = total == PanelWidthLimits.TOTAL
}

/** Resolved widths for one screen's three panels. */
data class PanelWidthSpec(val category: Dp, val list: Dp, val preview: Dp)

/**
 * The shares the app uses today, for a row [rowWidth] dp wide — what the dialog seeds with, so
 * "default" starts out looking like the shipped layout. Snapped to [PanelWidthLimits.STEP] and
 * corrected so the three always add up to 100.
 *
 * [gapTotal] is the space the browse row's `Arrangement.spacedBy(4.dp)` puts between the panels (two
 * gaps), which the panels themselves never occupy.
 */
fun defaultPanelShares(section: PanelSection, rowWidth: Dp, gapTotal: Dp = 8.dp): PanelShares {
    val content = (rowWidth - gapTotal).value.coerceAtLeast(1f)
    val rail = Dimens.RailWidthFixed.value
    val listDp: Float
    val previewDp: Float
    if (section == PanelSection.LIVE) {
        listDp = Dimens.ChannelListWidth.value
        previewDp = (content - rail - listDp).coerceAtLeast(1f)
    } else {
        val rest = (content - rail).coerceAtLeast(1f)
        listDp = rest * 1.8f / 2.8f
        previewDp = rest * 1f / 2.8f
    }
    val category = PanelWidthLimits.snap((rail / content * 100f).roundToInt())
    val list = PanelWidthLimits.snap((listDp / content * 100f).roundToInt())
    val preview = PanelWidthLimits.snap((previewDp / content * 100f).roundToInt())
    return balanceToTotal(PanelShares(category, list, preview))
}

/**
 * Nudges [shares] until they add up to exactly 100, moving the difference onto the biggest panel
 * first (it can absorb it least visibly) and spilling onto the others if that one hits a limit.
 */
fun balanceToTotal(shares: PanelShares): PanelShares {
    val values = intArrayOf(shares.category, shares.list, shares.preview)
    // Biggest first, so the correction lands where it shows least.
    val order = values.indices.sortedByDescending { values[it] }
    var diff = PanelWidthLimits.TOTAL - values.sum()
    for (i in order) {
        if (diff == 0) break
        val moved = (values[i] + diff).coerceIn(PanelWidthLimits.MIN, PanelWidthLimits.MAX)
        diff -= moved - values[i]
        values[i] = moved
    }
    return PanelShares(values[0], values[1], values[2])
}

/** Turns validated shares into concrete widths for a row [total] dp wide. */
fun computePanelWidths(shares: PanelShares, total: Dp, gapTotal: Dp = 8.dp): PanelWidthSpec {
    val content = (total - gapTotal).value.coerceAtLeast(1f)
    // Normalize by the real sum rather than trusting it to be 100: a value written by an older build
    // (or an abandoned edit) must still produce a sane layout instead of over/under-filling the row.
    val sum = shares.total.coerceAtLeast(1)
    val category = content * shares.category / sum
    val list = content * shares.list / sum
    return PanelWidthSpec(
        category = category.dp,
        list = list.dp,
        // The remainder, so rounding never leaves a sliver of background down the right edge.
        preview = (content - category - list).coerceAtLeast(1f).dp,
    )
}
