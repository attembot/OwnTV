package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.sync.SyncActivityTracker
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Unobtrusive "catalog sync running" pill (shell overlay, bottom middle). Shows for EVERY catalog
 * sync — a backgrounded first import, the movies/series remainder worker, auto refresh — because
 * they all funnel through SyncManager, which drives [SyncActivityTracker]. Semi-transparent, never
 * focusable, renders nothing when no sync is active. The shell hides it during fullscreen playback.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val tracker: SyncActivityTracker = koinInject()
    val active by tracker.active.collectAsStateWithLifecycle()
    val sync = active.values.firstOrNull() ?: return
    val colors = OwnTVTheme.colors

    val count = sync.stage?.totalProcessed ?: 0
    val others = active.size - 1
    val label = buildString {
        append("Syncing ")
        append(sync.sourceName)
        if (count > 0) append(" · ").append(String.format(java.util.Locale.getDefault(), "%,d", count)).append(" items")
        if (others > 0) append(" (+$others more)")
    }

    Row(
        modifier = modifier
            .padding(bottom = 14.dp)
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OwnTVSpinner(sizeDp = 14)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
