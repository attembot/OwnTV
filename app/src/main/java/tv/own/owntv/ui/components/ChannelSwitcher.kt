package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * A full-screen modal for retuning one playing window — a PiP corner or a MultiView tile — without
 * interrupting it. Seeded with [recent] (shown while the box is empty), then type-to-[search] across the
 * whole playlist. Picking a channel calls [onPick]; Back (or the on-screen hint) dismisses.
 *
 * Built for a remote: the first result auto-focuses so OK lands a pick in two presses (type, OK), and the
 * search field is the app's standard [SearchBar] pill so the D-pad can skip past it.
 */
@Composable
fun ChannelSwitcher(
    title: String,
    recent: List<ChannelEntity>,
    search: suspend (String) -> List<ChannelEntity>,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(recent) }
    val firstFocus = remember { FocusRequester() }

    // Debounced one-shot search; a blank query falls back to the recents we were seeded with.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = recent
        } else {
            delay(250)
            results = runCatching { search(query) }.getOrDefault(emptyList())
        }
    }
    // Land focus on the top result once when the picker opens. Do NOT re-grab when results change while the
    // user is typing — that was yanking focus out of the search field (closing the keyboard) mid-search.
    LaunchedEffect(Unit) { if (results.isNotEmpty()) runCatching { firstFocus.requestFocus() } }

    BackHandler { onDismiss() }

    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(28.dp).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(OwnTVTheme.colors.surfaceContainerHigh)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(14.dp))
            SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search channels…")
            Spacer(Modifier.height(14.dp))
            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Type to search your playlist." else "No channels match “$query”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(results, key = { _, ch -> ch.id }) { i, ch ->
                        FocusableSurface(
                            onClick = { onPick(ch) },
                            modifier = if (i == 0) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.02f,
                            focusedContainerColor = OwnTVTheme.colors.primary,
                            contentAlignment = Alignment.CenterStart,
                        ) { focused -> ChannelSwitcherRow(ch, focused) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Press Back to cancel", style = MaterialTheme.typography.labelSmall, color = OwnTVTheme.colors.onSurfaceVariant)
        }
    }
}

/** One channel row: its number (when the playlist provides one) and name. */
@Composable
private fun ChannelSwitcherRow(channel: ChannelEntity, focused: Boolean) {
    val textColor = if (focused) OwnTVTheme.colors.onPrimary else OwnTVTheme.colors.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        channel.number?.let { num ->
            Text(
                num.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) OwnTVTheme.colors.onPrimary.copy(alpha = 0.8f) else OwnTVTheme.colors.onSurfaceVariant,
            )
        }
        Text(channel.name, style = MaterialTheme.typography.titleMedium, color = textColor, maxLines = 1)
    }
}
