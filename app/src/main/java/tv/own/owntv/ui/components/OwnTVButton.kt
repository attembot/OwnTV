package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.LocalActionSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/** Visual emphasis for [OwnTVButton]. */
enum class OwnTVButtonStyle { PRIMARY, SECONDARY }

/**
 * Remote-friendly TV button built on [FocusableSurface]. PRIMARY fills with the brand accent;
 * SECONDARY is an outline that fills on focus.
 */
@Composable
fun OwnTVButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OwnTVButtonStyle = OwnTVButtonStyle.PRIMARY,
    icon: OwnTVIcon? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    // Denser pill (tighter padding + smaller icon) for space-constrained popups like the storage picker.
    compact: Boolean = false,
) {
    if (LocalStagePopup.current) {
        StagePopupButton(label, onClick, modifier, style, icon, enabled, selected, onLongClick, compact)
        return
    }
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(50) // M3 full/pill button

    val primary = style == OwnTVButtonStyle.PRIMARY
    // Frost with whatever surface the host renders on (DIALOGS inside a popup, CARDS on a panel),
    // read from LocalActionSurface. Null → flat (e.g. the fullscreen player over opaque video).
    // Pills are small chrome, so use a lighter frost than the big panels (cf. top-bar chips 0.45).
    val surface = LocalActionSurface.current

    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        shape = shape,
        focusedScale = 1.012f,
        // M3 tonal: PRIMARY keeps the primary fill; SECONDARY is a tonal surface that lifts to the
        // primary container on focus.
        unfocusedContainerColor = if (primary) colors.primary else colors.card,
        focusedContainerColor = if (primary) colors.primary else colors.primaryContainer,
        selectedContainerColor = if (primary || selected) colors.primary else colors.card,
        surface = surface,
        glassFrostScale = 0.9f,
        // Always-on glass edge so the pill reads as glass even when unfocused.
        glassIdleRimAlpha = 0.18f,
    ) { focused ->
        val contentColor = when {
            primary || (selected && !focused) -> colors.onPrimary
            focused -> colors.onPrimaryContainer
            else -> colors.textPrimary
        }

        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 13.dp else 22.dp,
                vertical = if (compact) 6.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            if (selected) {
                Box(Modifier.size(if (compact) 6.dp else 7.dp).background(contentColor, CircleShape))
            }
            if (icon != null) {
                OwnTVIcon(icon = icon, tint = contentColor, filled = true, modifier = Modifier.size(if (compact) 14.dp else 20.dp))
            }
            // Long labels (German / Finnish / Russian) must ellipsize, not hard-clip. The previous
            // `softWrap = false` with no `overflow` sliced the text mid-glyph on almost every translated
            // button. `weight(1f, fill = false)` lets a short label stay content-sized while a long one
            // is constrained to the remaining row width and ellipsizes. (See docs/internationalization.md
            // Phase 0a.)
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** True inside an [OwnTVPopup]: its buttons take the Stage look, so every old popup follows the new design. */
val LocalStagePopup = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * [OwnTVButton] inside a popup, as the Stage buttons draw it: the main action (PRIMARY, or a selected
 * choice) on accent 20% in accent text, the others on white 8% in text colour, radius 14; focused = the
 * FILLED treatment (accent fill, focus ring, glow). Disabled at 45%. Behaviour is unchanged.
 */
@Composable
private fun StagePopupButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    style: OwnTVButtonStyle,
    icon: OwnTVIcon?,
    enabled: Boolean,
    selected: Boolean,
    onLongClick: (() -> Unit)?,
    compact: Boolean,
) {
    val a = tv.own.owntv.ui.theme.stageAccent
    val primary = style == OwnTVButtonStyle.PRIMARY || selected
    val shape = RoundedCornerShape(14.dp)
    tv.own.owntv.ui.stage.StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 14.dp,
        enabled = enabled,
        modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        idle = Modifier.background(if (primary) a.accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center,
    ) { focused ->
        val content = when {
            focused -> a.onAccent
            primary -> a.accent
            else -> tv.own.owntv.ui.theme.StageColors.Text
        }
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 13.dp else 22.dp, vertical = if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            if (icon != null) OwnTVIcon(icon = icon, tint = content, filled = true, modifier = Modifier.size(if (compact) 14.dp else 20.dp))
            Text(
                text = label,
                style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
                    .copy(fontWeight = FontWeight.Bold),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
