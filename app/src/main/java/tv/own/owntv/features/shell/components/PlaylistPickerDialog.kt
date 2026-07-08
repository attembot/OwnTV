package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The top-bar quick switcher: pick which playlist the whole app shows right now. "All playlists" (id -1)
 * is the merged view; any other row narrows Live/Movies/Series/EPG/Search/Home to that one playlist. The
 * choice persists (it writes the same "Default" setting used in Settings → Sources), so it survives a restart.
 */
@Composable
fun PlaylistPickerDialog(
    playlists: List<SourceEntity>,
    activeId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    // Focus the currently-selected row on open so the user starts on their current choice.
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
        ) {
            Text("Show playlist", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose which playlist to browse. This applies everywhere and is remembered.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth().heightScrollCap().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaylistRow(
                    label = "All playlists",
                    selected = activeId <= 0,
                    modifier = if (activeId <= 0) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = { onSelect(-1L); onDismiss() },
                )
                playlists.forEach { source ->
                    PlaylistRow(
                        label = source.name,
                        selected = source.id == activeId,
                        modifier = if (source.id == activeId) Modifier.focusRequester(selectedFocus) else Modifier,
                        onClick = { onSelect(source.id); onDismiss() },
                    )
                }
            }
        }
    }
}

/** Caps the list height so a long playlist set scrolls inside the dialog instead of overflowing it. */
private fun Modifier.heightScrollCap(): Modifier = this.height(340.dp)

@Composable
private fun PlaylistRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        shape = RoundedCornerShape(14.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainer,
        selectedContainerColor = colors.secondaryContainer,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        val content = when {
            selected -> colors.onSecondaryContainer
            focused -> colors.onSurface
            else -> colors.onSurfaceVariant
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                // Accent dot marks the current choice (no dedicated check glyph in OwnTVIcon).
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(colors.primary))
            }
        }
    }
}
