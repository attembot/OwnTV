package tv.own.owntv.features.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.features.settings.data.EpgAutoRefresh
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.sync.work.EpgSyncState

/**
 * Settings → EPG Sources: standalone XMLTV feeds that fill the guide, independent of playlists.
 * Add (auto-syncs) / Edit / Re-sync / Delete. [startOnAdd] opens the add form immediately (deep-link
 * from the Guide's "Add EPG" button).
 */
@Composable
fun EpgSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, startOnAdd: Boolean = false) {
    val vm: EpgSourcesViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val autoRefreshMap by vm.autoRefresh.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var editing by remember { mutableStateOf<EpgSource?>(null) }
    var adding by remember { mutableStateOf(startOnAdd) }
    var confirmDelete by remember { mutableStateOf<EpgSource?>(null) }
    val addFocus = remember { FocusRequester() }

    BackHandler { onBack() }

    // Grab focus when the list view is showing (entry, and after returning from the form or a dialog).
    // The Column's onEnter only fires on directional entry, not on this internal tab-swap.
    LaunchedEffect(adding, editing, confirmDelete) {
        if (!adding && editing == null && confirmDelete == null) {
            kotlinx.coroutines.delay(80); runCatching { addFocus.requestFocus() }
        }
    }

    // Add / edit form.
    if (adding || editing != null) {
        EpgSourceForm(
            initial = editing,
            initialAutoRefresh = editing?.let { autoRefreshMap[it.id] } ?: EpgAutoRefresh.OFF,
            loadPlaylistOptions = { vm.playlistEpgOptions() },
            onSave = { name, url, ua, autoRefresh ->
                val e = editing
                if (e == null) vm.add(name, url, ua, autoRefresh)
                else { vm.update(e, name, url, ua); vm.setAutoRefresh(e, autoRefresh) }
                adding = false; editing = null
            },
            onCancel = { adding = false; editing = null },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { addFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EPG Sources", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            OwnTVButton("Add EPG", onClick = { adding = true }, icon = OwnTVIcon.ADD, modifier = Modifier.focusRequester(addFocus))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "XMLTV guide feeds that fill the TV Guide. They sync automatically when added and merge " +
                "with your playlists' channels.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.widthIn(max = 700.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No EPG sources yet. Add an XMLTV link.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sources, key = { it.id }) { source ->
                    val syncState by remember(source.id) { vm.observeSync(source.id) }
                        .collectAsStateWithLifecycle(EpgSyncState.Idle)
                    EpgRow(
                        source = source,
                        autoRefresh = autoRefreshMap[source.id] ?: EpgAutoRefresh.OFF,
                        counts = { vm.counts(source.id) },
                        syncState = syncState,
                        onResync = { vm.resync(source) },
                        onCancelSync = { vm.cancelSync(source) },
                        onEdit = { editing = source },
                        onDelete = { confirmDelete = source },
                    )
                }
            }
        }
    }

    confirmDelete?.let { s ->
        ConfirmDialog(
            title = "Delete “${s.name}”?",
            message = "Removes this EPG source and its guide data. Your channels stay.",
            onConfirm = { vm.delete(s); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun EpgRow(
    source: EpgSource,
    autoRefresh: EpgAutoRefresh,
    counts: suspend () -> Triple<Int, Int, Int>,
    syncState: EpgSyncState,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val count by produceState<Triple<Int, Int, Int>?>(initialValue = null, source.id, source.lastSyncAt, source.lastError) {
        value = runCatching { counts() }.getOrNull()
    }
    val activeSync = syncState as? EpgSyncState.Syncing
    val syncPercent = activeSync?.let {
        if (it.baseProgrammes > 0 && it.programmes > 0) {
            ((it.programmes.toLong() * 100) / it.baseProgrammes).toInt().coerceAtMost(99)
        } else {
            null
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (activeSync != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        syncPercent?.let { "Syncing $it%" } ?: "Syncing",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
                if (autoRefresh != EpgAutoRefresh.OFF) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "⟳ ${autoRefresh.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
            }
            Text(source.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            val catchupNote = count?.third?.takeIf { it > 0 }?.let { " · $it with catch-up" } ?: ""
            val status = when {
                activeSync != null -> when {
                    activeSync.programmes > 0 -> "${activeSync.channels} channels · ${activeSync.programmes} programmes"
                    activeSync.channels > 0 -> "${activeSync.channels} channels"
                    else -> "Connecting…"
                }
                source.lastError != null -> "⚠ ${source.lastError}"
                count != null && count!!.second > 0 -> "✓ ${count!!.first} channels · ${count!!.second} programmes$catchupNote"
                source.lastSyncAt != null -> "Synced, no programmes in window$catchupNote"
                else -> "Not synced yet"
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (source.lastError != null && activeSync == null) Color(0xFFEF4444) else colors.primary)
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (syncState.isActive) {
                OwnTVButton("Cancel", onClick = onCancelSync, style = OwnTVButtonStyle.SECONDARY)
            } else {
                OwnTVButton("Re-sync", onClick = onResync, style = OwnTVButtonStyle.SECONDARY)
            }
            OwnTVButton("Edit", onClick = onEdit, style = OwnTVButtonStyle.SECONDARY)
            OwnTVButton("Delete", onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
internal fun EpgSourceForm(
    initial: EpgSource?,
    initialAutoRefresh: EpgAutoRefresh,
    loadPlaylistOptions: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onSave: (name: String, url: String, userAgent: String?, autoRefresh: EpgAutoRefresh) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var ua by remember { mutableStateOf(initial?.userAgent ?: "") }
    var autoRefresh by remember { mutableStateOf(initialAutoRefresh) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    BackHandler { onCancel() }

    Column(
        modifier = modifier.fillMaxSize().roundedPanel().padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(if (initial == null) "Add EPG source" else "Edit EPG source", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(20.dp))
        OwnTVTextField(name, { name = it }, label = "Name", placeholder = "e.g. UK Guide", modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).focusRequester(firstFocus))
        Spacer(Modifier.height(14.dp))
        val fillButtonFocus = remember { FocusRequester() }
        OwnTVTextField(url, { url = it }, label = "XMLTV URL", placeholder = "https://…/epg.xml(.gz)", modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).focusProperties { down = fillButtonFocus })
        Spacer(Modifier.height(8.dp))
        OwnTVButton("Fill from playlist", onClick = { showPlaylistPicker = true }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.PLAYLIST, modifier = Modifier.focusRequester(fillButtonFocus))
        Spacer(Modifier.height(14.dp))
        OwnTVTextField(ua, { ua = it }, label = "User-Agent (optional)", placeholder = "Leave blank for default", modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp))

        Spacer(Modifier.height(14.dp))
        // Auto-refresh dropdown — same Off/Startup/staleness-threshold semantics as playlist sources.
        EpgAutoRefreshRow(selected = autoRefresh) { showAutoRefreshPicker = true }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton("Cancel", onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
            OwnTVButton(if (initial == null) "Add & sync" else "Save & sync", onClick = { onSave(name, url, ua, autoRefresh) }, enabled = url.isNotBlank())
        }
    }

    if (showPlaylistPicker) {
        PlaylistEpgPicker(
            load = loadPlaylistOptions,
            onPick = { opt -> if (name.isBlank()) name = opt.name; url = opt.url; showPlaylistPicker = false },
            onDismiss = { showPlaylistPicker = false },
        )
    }
    if (showAutoRefreshPicker) {
        PickerDialog(
            title = "Auto refresh",
            options = EpgAutoRefresh.entries.map { it.name to it.label },
            selected = autoRefresh.name,
            onSelect = { value ->
                autoRefresh = runCatching { EpgAutoRefresh.valueOf(value) }.getOrDefault(EpgAutoRefresh.OFF)
                showAutoRefreshPicker = false
            },
            onDismiss = { showAutoRefreshPicker = false },
        )
    }
}

/** A focusable settings row showing the current EPG auto-refresh selection; opens a picker on click. */
@Composable
private fun EpgAutoRefreshRow(selected: EpgAutoRefresh, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto refresh", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    "Off, on startup, or when data is stale",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                selected.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun PlaylistEpgPicker(
    load: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onPick: (EpgSourcesViewModel.PlaylistEpg) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val options by produceState<List<EpgSourcesViewModel.PlaylistEpg>?>(initialValue = null) { value = runCatching { load() }.getOrDefault(emptyList()) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(options) { if (!options.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Fill from playlist", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(14.dp))
            val opts = options
            when {
                opts == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                opts.isEmpty() -> Text("None of your playlists provide an EPG URL.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(opts) { opt ->
                        FocusableSurface(
                            onClick = { onPick(opt) },
                            modifier = if (opt == opts.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { _ ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(opt.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                                Text(opt.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}
