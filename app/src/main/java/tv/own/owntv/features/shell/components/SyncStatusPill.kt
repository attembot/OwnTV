package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.sync.EpgActivityTracker
import tv.own.owntv.core.sync.SyncActivityTracker
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * Unobtrusive "sync running" pill (shell overlay, bottom middle). Reflects BOTH catalog syncs (a
 * backgrounded first import, the movies/series remainder worker, auto refresh — all funnel through
 * SyncManager → [SyncActivityTracker]) AND EPG/guide syncs (manual resync from Settings, auto startup
 * refresh — [EpgSyncWorker] → [EpgActivityTracker]). Semi-transparent, never focusable, renders nothing
 * when no sync is active. The shell hides it during fullscreen playback.
 *
 * **One line per running sync.** It used to show a single line for whichever sync happened to be
 * first, with the rest collapsed into "(+2 more) · EPG too" — so with a playlist and a guide running
 * together you could see neither one's progress. Two playlists syncing at once were entirely
 * indistinguishable. Now every active sync gets its own row with its own counter, capped at
 * [MAX_ROWS] so a many-source setup can't grow the overlay across the screen.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val catalogTracker: SyncActivityTracker = koinInject()
    val epgTracker: EpgActivityTracker = koinInject()
    val activeCatalog by catalogTracker.active.collectAsStateWithLifecycle()
    val activeEpg by epgTracker.active.collectAsStateWithLifecycle()
    val lastCompleted by catalogTracker.lastCompleted.collectAsStateWithLifecycle()

    val colors = OwnTVTheme.colors

    val completedQueue = remember { mutableStateListOf<SyncActivityTracker.CompletedSync>() }
    var currentCompleted by remember { mutableStateOf<SyncActivityTracker.CompletedSync?>(null) }

    val anyActive = activeCatalog.isNotEmpty() || activeEpg.isNotEmpty()

    LaunchedEffect(lastCompleted) {
        val completed = lastCompleted ?: return@LaunchedEffect
        if (completedQueue.none { it.timestamp == completed.timestamp && it.sourceId == completed.sourceId }) {
            completedQueue.add(completed)
        }
    }

    LaunchedEffect(anyActive, currentCompleted, completedQueue.size) {
        if (!anyActive && currentCompleted == null && completedQueue.isNotEmpty()) {
            val next = completedQueue.removeAt(0)
            currentCompleted = next
            // Clear it from the tracker so it isn't re-queued and re-shown when the pill is
            // recomposed from scratch (e.g. after exiting fullscreen playback).
            catalogTracker.consumeCompleted(next.timestamp)
        }
    }

    LaunchedEffect(currentCompleted) {
        if (currentCompleted != null) {
            delay(5000)
            currentCompleted = null
        }
    }

    if (!anyActive && currentCompleted == null) return

    // Catalog syncs first (they're the slower, more interesting ones), then guides, in a stable
    // order so a row doesn't jump around as progress updates arrive.
    val rows = buildList {
        activeCatalog.values.sortedBy { it.sourceId }.forEach { add(catalogLine(it)) }
        activeEpg.values.sortedBy { it.sourceId }.forEach { add(epgLine(it)) }
    }
    val shown = rows.take(MAX_ROWS)
    val hidden = rows.size - shown.size
    val lineCount = shown.size + (if (hidden > 0) 1 else 0) + (if (currentCompleted != null) 1 else 0)

    // A tall stack under a 50% corner radius reads as a lozenge, not a pill — soften to a rounded
    // card as soon as there's more than one line.
    val radius = if (lineCount > 1) 18.dp else 50.dp
    val shape = RoundedCornerShape(radius)

    Column(
        modifier = modifier
            .padding(bottom = 14.dp)
            .widthIn(max = 620.dp)
            .clip(shape)
            // Liquid Glass: the pill is small chrome like the top-bar chips, so it frosts with
            // TOPBAR and takes a lighter frost than a full panel. Off glass it falls back to the
            // same translucent fill it always had.
            .glass(
                surface = GlassSurface.TOPBAR,
                baseFill = colors.surfaceContainerHigh.copy(alpha = 0.72f),
                shape = shape,
                cornerRadius = radius,
                frostScale = 0.8f,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shown.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OwnTVSpinner(sizeDp = 14)
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hidden > 0) {
            Text(
                "+$hidden more syncing",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        currentCompleted?.let { completed ->
            Text(
                completedLine(completed),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Syncing Test · 11,527 channels · 64,366 movies · 21,440 series" — the same per-type breakdown
 * Settings → Manage Sources shows, via the shared [SyncProgressCounts.label]. A flat "N items" hid
 * which part of the catalog was actually moving.
 *
 * The `|| count > 0` on each active flag mirrors ManageSourcesScreen: the syncer clears a phase's
 * active flag when it moves on, and without this a finished phase's count would vanish from the line
 * mid-sync. Counts are cumulative, so a type that never runs (M3U movies/series) stays at 0 and is
 * left out.
 */
private fun catalogLine(sync: SyncActivityTracker.ActiveSync): String = buildString {
    append("Syncing ")
    append(sync.sourceName)
    val stage = sync.stage
    if (stage != null) {
        val counts = SyncProgressCounts(
            live = stage.liveProcessed,
            movies = stage.moviesProcessed,
            series = stage.seriesProcessed,
            liveActive = stage.liveActive || stage.liveProcessed > 0,
            moviesActive = stage.moviesActive || stage.moviesProcessed > 0,
            seriesActive = stage.seriesActive || stage.seriesProcessed > 0,
        )
        if (counts.hasItems) append(" · ").append(counts.label())
    }
}

/** "Updating guide · Main EPG · 4,550 channels · 220,143 programmes" — matches the EPG Sources screen. */
private fun epgLine(sync: EpgActivityTracker.ActiveEpgSync): String = buildString {
    append("Updating guide · ")
    append(sync.sourceName)
    if (sync.channels > 0) append(" · ").append(sync.channels.formatted()).append(" channels")
    if (sync.programmes > 0) append(" · ").append(sync.programmes.formatted()).append(" programmes")
}

private fun completedLine(completed: SyncActivityTracker.CompletedSync): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val boldStyle = androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        when (val res = completed.result) {
            is SyncResult.Success -> {
                pushStyle(boldStyle)
                append("Sync complete")
                pop()
                append(" · ")
                append(completed.sourceName)
                append(" · ")
                append(res.categoryChangeSummary())
            }
            is SyncResult.Failed -> {
                pushStyle(boldStyle)
                append("Sync failed")
                pop()
                append(" · ")
                append(completed.sourceName)
                append(": ")
                append(res.message)
            }
            SyncResult.Cancelled -> {
                pushStyle(boldStyle)
                append("Sync cancelled")
                pop()
                append(" · ")
                append(completed.sourceName)
            }
        }
    }

private fun Int.formatted(): String = String.format(java.util.Locale.getDefault(), "%,d", this)

/** Beyond this many concurrent syncs the pill summarises the rest, rather than covering the screen. */
private const val MAX_ROWS = 4
