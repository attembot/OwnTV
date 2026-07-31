package tv.own.owntv.features.live

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.ChannelGenre
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontFamily

/** Layer 2–4 for Live TV: real category rail, Paging channel list, and a live preview pane. */
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onOpenMultiView: (ChannelEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: LiveViewModel = koinViewModel()
    val pip = org.koin.compose.koinInject<tv.own.owntv.features.multiview.PipController>()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val showChannelNumbers by vm.showChannelNumbers.collectAsStateWithLifecycle()
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val catchupPlayer by vm.catchupPlayer.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val previewCategoryName by vm.previewCategoryName.collectAsStateWithLifecycle()
    val previewArmed by vm.previewArmed.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val livePreviewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()

    // Current programme title for each loaded channel (id → title), batched in ONE query against the
    // stored guide. Drives the small "now playing" subtitle on each channel row. Recomputed when the page
    // contents change and every 60s (the programme airing "now" turns over). Channels with no guide are
    // simply absent from the map → their row shows no second line.
    val channelIdsKey = remember(channels.itemSnapshotList) {
        channels.itemSnapshotList.items.filterNotNull().map { it.id }
    }
    val nowPlaying by produceState<Map<Long, String>>(initialValue = emptyMap(), channelIdsKey) {
        if (channelIdsKey.isEmpty()) { value = emptyMap(); return@produceState }
        val loaded = channels.itemSnapshotList.items.filterNotNull()
        value = runCatching { vm.nowPlayingFor(loaded) }.getOrDefault(emptyMap())
        // Refresh periodically so a programme ending/starting is reflected while the list stays open.
        // This producer is auto-cancelled (and restarted) when channelIdsKey changes.
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = runCatching { vm.nowPlayingFor(loaded) }.getOrDefault(emptyMap())
        }
    }
    // Preview runs only when the player isn't busy (previewEnabled) AND the user hasn't turned it off.
    val effectivePreview = previewEnabled && livePreviewSetting

    // NOTE: do NOT stop the player when LiveScreen leaves composition — going fullscreen disposes
    // this screen, and stopping here would abort the stream that was just started. Playback is
    // stopped on fullscreen exit (shell BackHandler) instead.

    // In-pane preview: play the focused channel after the focus settles (700ms). Disabled while the
    // fullscreen/mini player owns the surface (previewEnabled=false) to avoid two surfaces fighting.
    LaunchedEffect(previewChannel?.id, effectivePreview, previewArmed) {
        // previewArmed gates the case where the last channel was restored on startup — we don't auto-preview
        // it until the user actually focuses a channel (then it plays normally).
        if (!effectivePreview || !previewArmed) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        delay(700)
        vm.playPreview(ch)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }

    // CH+- key paging: shared settings + a hoisted rail state so the same modifier can page both the
    // category rail and this channel list. Channel-list pane focus is tracked separately from rail
    // focus so chNavPaging only consumes the keys for whichever pane is active.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position via a per-category
    // state map (so A→B→A lands back where you were in A). OFF → reset the shared state to the top whenever
    // the category changes (fixes the cross-category scroll-leak bug).
    val perCategoryStates = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    val effectiveListState =
        if (rememberLive) perCategoryStates.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() }
        else listState
    LaunchedEffect(selectedKey, rememberLive) {
        if (!rememberLive) runCatching { listState.scrollToItem(0) }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var channelPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var catchupChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Programme picked in the catch-up dialog, awaiting the "Watch from start / Watch channel" choice.
    // The Live picker used to start the archive straight from the pick, so the same programme opened
    // from the Guide (which asks) and from here behaved differently — this makes the two match.
    var catchupDetail by remember {
        mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(null)
    }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    // When the long-press menu closes (Cancel, Favourite, Hide) WITHOUT opening another dialog, return focus
    // to the channel it was opened from — otherwise focus falls back to the nav panel.
    var contextMenuOpen by remember { mutableStateOf(false) }
    // Id of the channel the context menu was opened on, plus a dedicated requester bound to that row.
    // The previous restore was racy (delay(60) + selFocus bound to the *previewed* channel): when the
    // menu scrim disposed the focused menu button, Compose auto-restored focus and the CategoryRail's
    // entry-redirect pinned it to the rail before selFocus.requestFocus() ran. Tracking the long-press
    // target by id and binding a dedicated requester makes the restore deterministic.
    var contextChannelId by remember { mutableStateOf<Long?>(null) }
    val contextFocus = remember { FocusRequester() }
    var enteringMoveMode by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) { if (moveState != null) enteringMoveMode = false }
    // Land focus back on the long-pressed channel's row (or a sensible fallback if it's gone).
    suspend fun restoreToContextRow() {
        val targetId = contextChannelId
        if (targetId == null) { runCatching { selFocus.requestFocus() }; return }

        val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            runCatching { effectiveListState.scrollToItem(idx) }
            withFrameNanos { } // wait one frame so the row is laid out and contextFocus is attached
            runCatching { contextFocus.requestFocus() }
        } else {
            // Row is gone (e.g. "Hide channel" removed it) — clear the anchor and land on the first row.
            contextChannelId = null
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(contextChannel) {
        val opened = contextChannel != null
        if (opened) { contextMenuOpen = true; return@LaunchedEffect }
        if (!contextMenuOpen) return@LaunchedEffect
        contextMenuOpen = false
        // A follow-up dialog (rename / match EPG / catch-up / move) grabs focus itself — only restore
        // for plain closes (Cancel, Favourite, Hide, Close). Those dialogs restore on their own close.
        if (renaming != null || matchingEpg != null || catchupChannel != null || enteringMoveMode) return@LaunchedEffect
        restoreToContextRow()
    }
    // The Match EPG dialog grabbed focus while open — when it closes (pick/clear/dismiss), put focus
    // back on the channel it was opened for instead of letting it fall to the nav panel.
    var matchEpgWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(matchingEpg) {
        if (matchingEpg != null) { matchEpgWasOpen = true; return@LaunchedEffect }
        if (!matchEpgWasOpen) return@LaunchedEffect
        matchEpgWasOpen = false
        restoreToContextRow()
        // Picking a match rewrites customizations, which recreates the pager on its own schedule —
        // the rebuilt rows land a moment later and yank focus off the row we just restored, and the
        // exact timing varies with list size. Re-assert the target row briefly instead of racing a
        // single load-state transition.
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            runCatching { effectiveListState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)

    Row(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.title, it.icon, showGenreDot = it.key is LiveKey.Folder) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
            // Focusing a folder stops the in-pane preview — but only when a preview is actually running.
            // When the player is docked (live PiP) or fullscreen, previewEnabled is false and stopPreview
            // would kill that stream (e.g. while navigating left to leave Live), so we skip it.
            onFocused = { if (previewEnabled) vm.stopPreview() },
            listState = catListState,
            modifier = Modifier
                .onFocusChanged { railPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { railPaneFocused },
                    lastIndex = { railItems.size - 1 },
                    currentTargetIndex = { selectedIndex },
                    // Selecting a category loads only its first paged page (~50 items), not all channels
                    // at once, so this is fast. The rail's LaunchedEffect scrolls + focuses the pill.
                    onJumpToIndex = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
                ),
        )

        // Layer 3 — header + channel list (fixed-width column; the preview pane fills the rest)
        Column(
            modifier = Modifier
                .width(Dimens.ChannelListWidth)
                .fillMaxHeight()
                .roundedPanel(fillColor = ContentPanelFill)
                // Track whether this pane holds focus so chNavPaging only consumes CH keys when it does.
                .onFocusChanged { channelPaneFocused = it.hasFocus }
                // CH+- key paging for this channel list. Long-press jumps to first/last channel;
                // short press skips N. currentTargetIndex falls back to the visible top when the
                // previewed channel isn't in the loaded window (paged data).
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { channelPaneFocused },
                    // On the "All" list (every channel) a long-press jump to the very last item is
                    // pointless and janks, so disable long-press there — short-press skipping stays.
                    longPressEnabled = { selectedKey != LiveKey.All },
                    lastIndex = { channels.itemCount - 1 },
                    currentTargetIndex = {
                        val pc = previewChannel
                        if (pc != null) {
                            val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == pc.id }
                            if (idx >= 0) idx else effectiveListState.firstVisibleItemIndex
                        } else {
                            effectiveListState.firstVisibleItemIndex
                        }
                    },
                    onJumpToIndex = { idx ->
                        // Scroll the target into view, then focus it. selFocus is bound to the
                        // previewed channel by gridFocusTarget, so we make the target the previewed
                        // one (which also fires the debounced 700ms preview — desired).
                        val target = channels.itemSnapshotList.items.getOrNull(idx)?.id
                        scope.launch {
                            runCatching { effectiveListState.scrollToItem(idx) }
                            withFrameNanos { }
                            if (target != null) {
                                // Set the previewed channel so selFocus binds to the new row, then focus.
                                val item = channels.itemSnapshotList.items.firstOrNull { it.id == target }
                                if (item != null) {
                                    vm.onChannelFocused(item)
                                    runCatching { selFocus.requestFocus() }
                                }
                            } else {
                                runCatching { firstItemFocus.requestFocus() }
                            }
                        }
                    },
                )
                // Entering this pane (from the rail or the preview) must land on a channel row, never
                // the search bar: prefer the last-focused channel, else the first row. onEnter fires
                // only for directional entry from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy list's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
                .focusGroup()
                .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
        ) {
            Text(
                "Live TV / ${selectedItem?.title ?: "All"}",
                style = MaterialTheme.typography.headlineMedium,
                color = OwnTVTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedItem?.title ?: "All"} (${formatCount(count)} channels)",
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = "Search ${selectedItem?.title ?: "channels"}…",
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.hasFocus && previewEnabled) vm.stopPreview() },
                )
                Spacer(Modifier.size(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort)
            }
            Spacer(Modifier.height(14.dp))

            if (channels.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No channels found for “${searchQuery.trim()}”" else "No channels here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = effectiveListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                        contentType = channels.itemContentType { "channel" },
                    ) { index ->
                        val channel = channels[index]
                        if (channel != null) {
                            ChannelRow(
                                channel = channel,
                                isFavorite = favoriteIds.contains(channel.id),
                                nowTitle = nowPlaying[channel.id],
                                showNumber = showChannelNumbers,
                                modifier = Modifier.gridFocusTarget(
                                    itemId = channel.id, index = index,
                                    contextId = contextChannelId, contextFocus = contextFocus,
                                    selectedId = previewChannel?.id, selectedFocus = selFocus,
                                    firstItemFocus = firstItemFocus,
                                ),
                                onFocus = { vm.onChannelFocused(channel) },
                                onClick = {
                                    vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                    // External player on for Live TV: the channel went to another app, so
                                    // don't mount the fullscreen player (it would spin up an idle engine).
                                    if (!externalPlayerOn) onFullscreen()
                                },
                                onLongClick = { contextChannel = channel; contextChannelId = channel.id },
                            )
                        }
                    }
                }
            }
        }

        // Layer 4 — preview pane (informational only — no focusable actions; management lives in long-press)
        Box(modifier = Modifier.weight(1f).fillMaxSize().roundedPanel(fillColor = PreviewPanelFill).padding(Dimens.GapLarge)) {
            LivePreviewPane(
                channel = previewChannel,
                categoryName = previewCategoryName,
                nowNext = nowNext,
                previewEngine = vm.previewEngine,
                showVideo = effectivePreview,
            )
        }
    }

    catchupChannel?.let { ch ->
        CatchupDialog(
            channelName = ch.name,
            loadProgrammes = { vm.catchupProgrammes(ch) },
            onPick = { prog -> catchupChannel = null; catchupDetail = ch to prog },
            onDismiss = { catchupChannel = null },
        )
    }

    // Same dialog the Guide shows for a programme, so both routes offer the identical choice:
    // replay from the start, tune the channel live, favourite it, or back out.
    catchupDetail?.let { (ch, prog) ->
        tv.own.owntv.features.epg.ProgrammeDetailDialog(
            channelName = ch.name,
            programme = prog,
            loadDescription = { vm.programmeDescription(it) },
            canCatchup = true, // only reachable from the catch-up picker, which already gated on this
            isFavorite = favoriteIds.contains(ch.id),
            onToggleFavorite = { vm.toggleFavorite(ch) },
            onWatch = { catchupDetail = null; vm.watchFullscreen(ch, emptyList()); if (!externalPlayerOn) onFullscreen() },
            onPlayCatchup = { catchupDetail = null; vm.playCatchupProgramme(ch, prog); onFullscreen() },
            // External: the archive went to another app, so don't mount the fullscreen player over it.
            onPlayCatchupExternal = { catchupDetail = null; vm.playCatchupExternal(ch, prog) },
            catchupPlayer = catchupPlayer,
            onDismiss = { catchupDetail = null },
            compact = true,
        )
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = "Rename channel",
            initial = ch.name,
            hint = "Only for this profile. Leave blank to restore the original name.",
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    matchingEpg?.let { ch ->
        EpgMatchDialog(
            channelName = ch.name,
            currentMatch = vm.currentEpgMatch(ch),
            loadChannels = { q -> vm.availableEpgChannels(ch.name, q) },
            onPick = { epgId -> vm.setEpgMatch(ch, epgId); matchingEpg = null },
            onClear = { vm.setEpgMatch(ch, null); matchingEpg = null },
            onDismiss = { matchingEpg = null },
        )
    }

    // Long-press a channel → quick actions.
    contextChannel?.let { ch ->
        ChannelContextMenu(
            channelName = ch.name,
            isFavorite = favoriteIds.contains(ch.id),
            hasCatchup = ch.catchup,
            canMove = selectedKey is LiveKey.Folder || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onMatchEpg = { matchingEpg = ch; contextChannel = null },
            onCatchup = { catchupChannel = ch; contextChannel = null },
            onPlayExternal = { vm.playExternal(ch); contextChannel = null },
            onMove = { contextChannel = null; enteringMoveMode = true; vm.enterMoveMode(ch, selectedKey) },
            onRemoveFromHistory = { vm.removeFromHistory(ch.id); contextChannel = null },
            onWatchInCorner = { pip.openCorner(ch); contextChannel = null },
            onMultiView = { contextChannel = null; onOpenMultiView(ch) },
            onDismiss = { contextChannel = null },
        )
    }

    // Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = "Reorder channel",
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveUp,
            onMoveDown = vm::moveDown,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    nowTitle: String? = null,
    showNumber: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) onFocus() },
        shape = RoundedCornerShape(12.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.displayLogoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            // Provider channel number, in a fixed-width strip so every name below starts at the same x
            // however many digits the number has. Hidden entirely when the setting is off.
            if (showNumber) {
                tv.own.owntv.ui.components.ChannelNumberColumn(
                    number = channel.number,
                    color = colors.onSurfaceVariant,
                )
            }
            // Name + (optional) current programme. The subtitle is rendered only when guide data exists,
            // so channels without EPG look exactly as before — single line.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.FAVORITE, tint = colors.favorite, filled = true, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Long-press quick actions for a Live channel (favourite / rename / hide / match EPG / catch-up / move / remove history). */
@Composable
private fun ChannelContextMenu(
    channelName: String,
    isFavorite: Boolean,
    hasCatchup: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
    onCatchup: () -> Unit,
    onPlayExternal: () -> Unit,
    onMove: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onWatchInCorner: () -> Unit,
    onMultiView: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            .trapAllFocusExit().focusGroup()
            .longPressMenuGuard(), // the long-press OK is still held — don't let it auto-click a menu item
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(channelName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            OwnTVButton(
                if (isFavorite) "Remove from Favourites" else "Add to Favourites",
                onClick = onToggleFavorite, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.FAVORITE,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OwnTVButton("Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Hide channel", onClick = onHide, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Match EPG", onClick = onMatchEpg, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.EPG, modifier = Modifier.fillMaxWidth())
            if (hasCatchup) OwnTVButton("Catch-up", onClick = onCatchup, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            // Always offered, regardless of the Live TV external-player default — this is the per-channel
            // escape hatch for a stream neither in-app engine can open (same as Movies/Series/Downloads).
            OwnTVButton("Play in external player", onClick = onPlayExternal, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.PLAY, modifier = Modifier.fillMaxWidth())
            // Fork: second-stream entries. True PiP keeps this channel in a corner window; MultiView
            // opens it as the first of up to four tiles.
            OwnTVButton("Picture-in-picture", onClick = onWatchInCorner, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.PIP, modifier = Modifier.fillMaxWidth())
            OwnTVButton("MultiView", onClick = onMultiView, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.PIP, modifier = Modifier.fillMaxWidth())
            if (canMove) OwnTVButton("Move", onClick = onMove, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            if (isHistory) OwnTVButton("Remove from History", onClick = onRemoveFromHistory, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LivePreviewPane(
    channel: ChannelEntity?,
    categoryName: String?,
    nowNext: EpgNowNext?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
) {
    val colors = OwnTVTheme.colors
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    val previewHeight by previewEngine.videoHeight.collectAsStateWithLifecycle()
    val streamChips by previewEngine.streamChips.collectAsStateWithLifecycle()
    // Show the ExoPlayer surface once it's playing/buffering; on ERROR fall back to the channel logo.
    val previewPlaying = showVideo && previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val previewLoading = showVideo && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING
    val videoRes = previewHeight?.let { "${it}p" }
    if (channel == null) {
        PreviewPane(hint = "Focus a channel to see it here.")
        return
    }
    Column(
        // Scrollable so the EPG (Now/Next/Later) never gets clipped when it makes the pane taller
        // than the screen. The pane is informational only — there are NO focusable elements here,
        // so D-pad right never enters it (management actions live in the long-press menu).
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(Dimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.displayLogoUrl.isNullOrBlank()) {
                AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.size(120.dp))
            } else {
                OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
            }
            if (previewPlaying) {
                tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
            }
            if (previewLoading) {
                OwnTVSpinner(sizeDp = 28)
            }
            // Real stream spec — aspect · resolution · fps · audio. The channel NAME often lies ("…4K"),
            // so this shows what you'll actually get before you commit to watching. Falls back to just the
            // resolution until the full format is known.
            val chips = streamChips.takeIf { it.isNotEmpty() } ?: videoRes?.let { listOf(it) }.orEmpty()
            chips.takeIf { previewPlaying && it.isNotEmpty() }?.let { list ->
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    list.forEach { label ->
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(channel.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)

        // Metadata row — category · inferred genre (with colour dot) · catch-up status · EPG status.
        // All informational, never focusable.
        ChannelMetaRow(channel = channel, categoryName = categoryName, nowNext = nowNext)

        EpgSection(nowNext)

        // No action buttons — all management (Favorite / Rename / Hide / Match EPG / Catch-up) is in
        // the long-press menu. Just a hint so the watch affordance + where-to-find-options stay obvious.
        Spacer(Modifier.height(14.dp))
        Text(
            "Press OK to watch fullscreen · Long-press for options",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Informational metadata row under the channel name: the channel's category, its inferred genre
 * (with a colour dot), catch-up availability, and a short EPG-coverage hint. Purely visual —
 * nothing here is selectable/focusable, so D-pad navigation never enters the preview pane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelMetaRow(
    channel: ChannelEntity,
    categoryName: String?,
    nowNext: EpgNowNext?,
) {
    val colors = OwnTVTheme.colors
    // Genre is inferred from the channel's real category name. Unmatched categories fall back to OTHER
    // (grey dot) so every channel still gets a genre marker — the genre is never inferred from the
    // channel NAME (a station brand like "CNN" / "Hindi MTV Plus" would be misleading).
    val genre = remember(categoryName) { ChannelGenre.fromCategory(categoryName) }

    // EPG status — "EPG · Nd" when we know the stored coverage span (bulk-guide channels), plain "EPG"
    // when only now/next is available (short-EPG API channels), "No EPG" when nothing was resolved.
    val epgStatus = when {
        nowNext == null || (nowNext.now == null && nowNext.next == null) -> "No EPG"
        nowNext.coverageDays != null && nowNext.coverageDays > 0 -> "EPG · ${nowNext.coverageDays}d"
        else -> "EPG"
    }

    // Catch-up status — only meaningful when the channel actually supports it.
    val catchupLabel = if (channel.catchup) {
        channel.catchupDays.takeIf { it > 0 }?.let { "Catch-up · ${it}d" } ?: "Catch-up"
    } else null

    val chips = buildList {
        // Genre chip (always shown, with its colour dot — including the grey "Other" fallback so every
        // channel has a genre marker), then the raw category name when it differs from the genre label.
        add(MetaChip(genre.label, dot = genre.dot, primary = genre != ChannelGenre.OTHER))
        if (!categoryName.isNullOrBlank() && categoryName != genre.label) add(MetaChip(categoryName))
        if (catchupLabel != null) add(MetaChip(catchupLabel, accent = true))
        add(MetaChip(epgStatus))
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip -> MetaChipBadge(chip) }
    }
}

/** A single metadata chip — small text, optional colour dot, on a hairline-rounded surface. */
private data class MetaChip(
    val text: String,
    val dot: Color? = null,
    val primary: Boolean = false,
    val accent: Boolean = false,
)

@Composable
private fun MetaChipBadge(chip: MetaChip) {
    val colors = OwnTVTheme.colors
    val fg = when {
        chip.primary -> colors.primary
        chip.accent -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .height(26.dp)                  // uniform chip height — long category names can't wrap to 2 lines and make one chip taller than the others
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chip.dot?.let {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(it))
        }
        Text(
            chip.text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontFamily = PopupFontFamily,   // Lora serif, matching the popup-menu font
            fontWeight = FontWeight.Medium,
            maxLines = 1,                   // never wrap — keeps every chip the same height
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Now-playing (with progress) + up-next, from the channel's short EPG. Hidden when no guide exists. */
@Composable
private fun EpgSection(nowNext: EpgNowNext?) {
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    val now = nowNext?.now
    val next = nowNext?.next
    if (now == null && next == null) return

    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (now != null) {
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Bold)
            Text(
                now.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (now.stopMs - now.startMs).coerceAtLeast(1)
            val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerLowest),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
            }
            Text(
                "${formatTime(now.startMs)} – ${formatTime(now.stopMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (next != null) {
            Spacer(Modifier.height(2.dp))
            Text("NEXT  ·  ${formatTime(next.startMs)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                next.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Upcoming programmes after "next" — see what's on later without opening the Guide (#11).
        val later = nowNext.upcoming
        if (later.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("LATER", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            later.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatTime(p.startMs), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(p.title, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatCatchupTime(
    startMs: Long,
    stopMs: Long,
    formatTime: (Long) -> String,
): String {
    val day = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(startMs))
    return "$day ${formatTime(startMs)} – ${formatTime(stopMs)}"
}

/** Live TV catch-up: pick a recent (already-aired) programme on a catch-up channel to replay from start. */
@Composable
private fun CatchupDialog(
    channelName: String,
    loadProgrammes: suspend () -> List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>,
    onPick: (tv.own.owntv.core.database.entity.EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    val list by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(initialValue = null) {
        value = runCatching { loadProgrammes() }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    // Popup(focusable = true) is a hard focus boundary: a stray D-pad press or the Live screen's own
    // LaunchedEffect focus requests can no longer drop focus onto the channel grid behind the scrim
    // (same fix as EpgMatchDialog / ChannelContextMenu). trapAllFocusExit() additionally blocks
    // directional exits through the scrim. PopupFontTheme swaps in the Lora serif + scales fonts to
    // match the other popup menus (0.75f), and the box is shrunk to that same denser size.
    androidx.compose.ui.window.Popup(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
        Box(
            Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                .trapAllFocusExit()
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
        // Inner list is height-capped to the screen (minus the dialog chrome) so the Close button
        // stays reachable on small/low-res screens; the outer column can't verticalScroll (LazyColumn).
        val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(140.dp, 300.dp)
        Column(Modifier.dialogPanel(width = 460.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
            Text("Catch-up · $channelName", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text("Pick a recent programme to replay from the start.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            when (val progs = list) {
                null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                else -> if (progs.isEmpty()) {
                    Text(
                        "No recent guide data for this channel yet — make sure its EPG is matched (long-press it, or use Match EPG).",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(listHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(progs, key = { it.id }) { p ->
                            FocusableSurface(
                                onClick = { onPick(p) },
                                modifier = if (p == progs.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentAlignment = Alignment.CenterStart,
                                surface = GlassSurface.DIALOGS,
                            ) { _ ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatCatchupTime(p.startMs, p.stopMs, formatTime), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
        }
        }
    } // PopupFontTheme
    }
}

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.database.entity.EpgChannelEntity>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgChannelEntity>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    // Popup(focusable=true) is a hard focus boundary: a stray D-pad right/left with no target inside
    // can no longer drop focus onto the screen behind the scrim (same fix as EpgMatchReviewDialog).
    androidx.compose.ui.window.Popup(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
    tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // Same small-screen cap as CatchupDialog: search bar + buttons must stay reachable.
        val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp).coerceIn(140.dp, 240.dp)
        Column(Modifier.dialogPanel(width = 384.dp, corner = 16.dp, padding = 14.dp)) {
            Text("Match EPG", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Pick the guide channel for “$channelName”." + (currentMatch?.let { "  Current: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // Actions live in a right-hand column so a D-pad right from the search bar or ANY list row
            // reaches Close/Clear directly — no scrolling to the bottom of a long list.
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search guide channels…", modifier = Modifier.fillMaxWidth().focusRequester(searchFocus), surface = GlassSurface.DIALOGS)
                    Spacer(Modifier.height(12.dp))
                    val list = results
                    when {
                        list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                        list.isEmpty() -> Text(
                            if (query.isBlank()) "No EPG data yet — add an EPG source in Settings." else "No guide channels match “$query”.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                        else -> LazyColumn(Modifier.fillMaxWidth().height(listHeight), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list, key = { it.id }) { epg ->
                                FocusableSurface(
                                    onClick = { onPick(epg.epgChannelId) },
                                    modifier = if (epg == list.first()) Modifier.fillMaxWidth().focusRequester(firstItemFocus) else Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ ->
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                        Text(epg.displayName ?: epg.epgChannelId, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(epg.epgChannelId, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                    if (currentMatch != null) OwnTVButton("Clear match", onClick = onClear, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    } // PopupFontTheme
    } // Popup
}
