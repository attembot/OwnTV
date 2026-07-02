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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.ui.theme.OwnTVTheme

/** A browsable category for the [ChannelSwitcher]: a label and a lazy loader for its channels. */
class ChannelCategory(val label: String, val load: suspend () -> List<ChannelEntity>)

/**
 * A full-screen modal for retuning a playing window — a PiP corner or a MultiView tile — without
 * interrupting it. **Browse-first:** the left column is the playlist's [categories]; focusing one fills the
 * right column with its channels (live, like the main guide). A [search] field on top filters across the
 * whole playlist when you'd rather type. Picking a channel calls [onPick]; Back dismisses.
 *
 * Focus is purely D-pad/spatial (the two columns sit side by side, so left/right traverses cleanly), and
 * the picker holds focus because the player HUD goes inert beneath it — see PlayerHud's `inert`.
 */
@Composable
fun ChannelSwitcher(
    title: String,
    categories: List<ChannelCategory>,
    search: suspend (String) -> List<ChannelEntity>,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var channels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    val firstCategoryFocus = remember { FocusRequester() }
    val searching = query.isNotBlank()

    // The focused category's channels (reloads as focus moves down the category list).
    LaunchedEffect(selected, categories) {
        channels = if (selected in categories.indices) {
            runCatching { categories[selected].load() }.getOrDefault(emptyList())
        } else emptyList()
    }
    // Debounced search across the whole playlist.
    LaunchedEffect(query) {
        if (query.isBlank()) { searchResults = emptyList(); return@LaunchedEffect }
        delay(250)
        searchResults = runCatching { search(query) }.getOrDefault(emptyList())
    }
    // Land focus on the first category once, on open. (Don't re-grab on data changes — that fights typing.)
    LaunchedEffect(Unit) { if (categories.isNotEmpty()) runCatching { firstCategoryFocus.requestFocus() } }

    BackHandler { onDismiss() }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(28.dp)
            .focusGroup()
            // Trap D-pad focus inside the picker. Compose's geometric focus search ignores z-order, so
            // without this, Left/Up off the picker's edge lands on invisible HUD buttons behind the scrim
            // (the HUD is inert and won't reclaim focus) — OK could then trigger a hidden Exit. Back dismisses.
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(OwnTVTheme.colors.surfaceContainerHigh)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(14.dp))
            SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search all channels…")
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Left — categories. Focusing one selects it (fills the right column).
                LazyColumn(
                    Modifier.fillMaxHeight().weight(0.34f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Keyed by INDEX, not label: IPTV playlists routinely repeat category names across
                    // sources, and duplicate LazyColumn keys crash the composition outright.
                    itemsIndexed(categories, key = { i, _ -> i }) { i, cat ->
                        FocusableSurface(
                            onClick = { selected = i },
                            modifier = (if (i == 0) Modifier.focusRequester(firstCategoryFocus) else Modifier)
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) selected = i },
                            selected = i == selected && !searching,
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.02f,
                            focusedContainerColor = OwnTVTheme.colors.primary,
                            selectedContainerColor = OwnTVTheme.colors.primary.copy(alpha = 0.22f),
                            contentAlignment = Alignment.CenterStart,
                        ) { focused ->
                            Text(
                                cat.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (focused) OwnTVTheme.colors.onPrimary else OwnTVTheme.colors.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                        }
                    }
                }

                // Right — channels for the focused category, or search results while typing.
                val list = if (searching) searchResults else channels
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxHeight().weight(0.66f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searching) "No channels match “$query”." else "No channels in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OwnTVTheme.colors.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxHeight().weight(0.66f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Index keys here too — a recents/history join can legitimately repeat a channel.
                        itemsIndexed(list, key = { i, _ -> i }) { _, ch ->
                            FocusableSurface(
                                onClick = { onPick(ch) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                focusedScale = 1.02f,
                                focusedContainerColor = OwnTVTheme.colors.primary,
                                contentAlignment = Alignment.CenterStart,
                            ) { focused -> ChannelSwitcherRow(ch, focused) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Browse categories on the left, channels on the right · type above to search · Back to cancel",
                style = MaterialTheme.typography.labelSmall,
                color = OwnTVTheme.colors.onSurfaceVariant,
            )
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
