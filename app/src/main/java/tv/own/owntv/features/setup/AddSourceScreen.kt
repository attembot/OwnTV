package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class SourceKind { XTREAM, M3U }

@Composable
fun AddSourceScreen(
    onStartXtream: (
        name: String,
        server: String,
        user: String,
        pass: String,
        userAgent: String,
        epgUrl: String,
        autoRefresh: PlaylistAutoRefresh,
        syncLive: Boolean,
        syncMovies: Boolean,
        syncSeries: Boolean,
        isDefault: Boolean,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, epgUrl: String, autoRefresh: PlaylistAutoRefresh, isDefault: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceEntity? = null,
    initialAutoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
    initialIsDefault: Boolean = false,
    showDefaultToggle: Boolean = true,
) {
    val colors = OwnTVTheme.colors
    val editing = initial != null
    var kind by remember { mutableStateOf(if (initial?.type == SourceType.M3U) SourceKind.M3U else SourceKind.XTREAM) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var server by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var m3uUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.M3U) initial.url else "") }
    var epgUrl by remember(initial) { mutableStateOf(initial?.epgUrl ?: "") }
    var userAgent by remember(initial) { mutableStateOf(initial?.userAgent ?: "") }
    var autoRefresh by remember(initialAutoRefresh) { mutableStateOf(initialAutoRefresh) }
    var isDefault by remember(initialIsDefault) { mutableStateOf(initialIsDefault) }
    var syncLive by remember { mutableStateOf(true) }
    var syncMovies by remember { mutableStateOf(true) }
    var syncSeries by remember { mutableStateOf(true) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val showContentToggles = kind == SourceKind.XTREAM && !editing
    val canStart = when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank() && (syncLive || syncMovies || syncSeries)
        SourceKind.M3U -> m3uUrl.isNotBlank()
    }

    Box(modifier.fillMaxSize().roundedPanel()) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (editing) "Edit source" else "Add your source", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                if (editing) "Update this source's details, or change its auto-refresh setting." else "OwnTV is a player — bring your own M3U or Xtream source.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            // Source type selector (locked while editing — the type can't change, so initial focus
            // goes to the Name field instead of a dead chip).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KindChip("Xtream", kind == SourceKind.XTREAM, Modifier.weight(1f).then(if (!editing) Modifier.focusRequester(firstFocus) else Modifier)) { if (!editing) kind = SourceKind.XTREAM }
                KindChip("M3U / M3U8", kind == SourceKind.M3U, Modifier.weight(1f)) { if (!editing) kind = SourceKind.M3U }
            }
            Spacer(Modifier.height(20.dp))

            OwnTVTextField(name, { name = it }, label = "Name (optional)", placeholder = "My IPTV", modifier = Modifier.fillMaxWidth(), focusRequester = if (editing) firstFocus else null)
            Spacer(Modifier.height(14.dp))

            when (kind) {
                SourceKind.XTREAM -> {
                    OwnTVTextField(server, { server = it }, label = "Server URL", placeholder = "http://host:port", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(username, { username = it }, label = "Username", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(password, { password = it }, label = if (editing) "Password (leave blank to keep)" else "Password", isPassword = true, modifier = Modifier.fillMaxWidth())
                }
                SourceKind.M3U -> {
                    val pickedName = remember(m3uUrl) {
                        if (m3uUrl.startsWith("/")) java.io.File(m3uUrl).name else null
                    }
                    OwnTVTextField(m3uUrl, { m3uUrl = it }, label = "Playlist URL or local file", placeholder = "http://…/playlist.m3u", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        label = if (pickedName != null) "Local file: $pickedName" else "Choose a local .m3u / .m3u8 file…",
                        onClick = { showFileBrowser = true },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // EPG is managed separately now (Settings → EPG Sources), so no EPG field here. For an
            // Xtream server the guide URL is still derived automatically; M3U EPG can be added there.
            Spacer(Modifier.height(14.dp))
            OwnTVTextField(userAgent, { userAgent = it }, label = "User-Agent (optional)", placeholder = "e.g. VLC/3.0.20 LibVLC/3.0.20", modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            // Auto-refresh dropdown (replaces the old binary "Refresh on startup" toggle). Off/Startup or a
            // staleness threshold — the source is refreshed when its data is at least this old.
            AutoRefreshRow(selected = autoRefresh) { showAutoRefreshPicker = true }

            if (showDefaultToggle) {
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = "Default playlist",
                    desc = "Show only this playlist across the app. Turn off for all playlists; change anytime from the top bar.",
                    checked = isDefault,
                ) { isDefault = it }
            }

            if (showContentToggles) {
                Spacer(Modifier.height(20.dp))
                Text("Sync first", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Pick what to import now. The rest syncs in the background.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                ToggleRow(label = "Live TV", desc = "Channels and categories", checked = syncLive) { syncLive = it }
                Spacer(Modifier.height(8.dp))
                ToggleRow(label = "Movies", desc = "VOD movie catalog", checked = syncMovies) { syncMovies = it }
                Spacer(Modifier.height(8.dp))
                ToggleRow(label = "Series", desc = "TV series catalog", checked = syncSeries) { syncSeries = it }
            }

            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    label = if (editing) "Save" else "Start Import",
                    onClick = {
                        when (kind) {
                            SourceKind.XTREAM -> onStartXtream(name, server, username, password, userAgent, epgUrl, autoRefresh, syncLive, syncMovies, syncSeries, isDefault)
                            SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, epgUrl, autoRefresh, isDefault)
                        }
                    },
                    enabled = canStart,
                )
            }
        }
      }
      // In-app, TV-safe file picker (SAF / system file picker is missing on many TVs).
      if (showFileBrowser) {
          StorageBrowser(
              title = "Pick a playlist file (.m3u / .m3u8)",
              mode = BrowseMode.FILE,
              fileExtensions = setOf("m3u", "m3u8"),
              onPick = { file ->
                  showFileBrowser = false
                  m3uUrl = file.absolutePath
                  if (name.isBlank()) name = file.nameWithoutExtension
              },
              onDismiss = { showFileBrowser = false },
          )
      }
      if (showAutoRefreshPicker) {
          PickerDialog(
              title = "Auto refresh",
              options = PlaylistAutoRefresh.entries.map { it.name to it.label },
              selected = autoRefresh.name,
              onSelect = { value ->
                  autoRefresh = runCatching { PlaylistAutoRefresh.valueOf(value) }.getOrDefault(PlaylistAutoRefresh.OFF)
                  showAutoRefreshPicker = false
              },
              onDismiss = { showAutoRefreshPicker = false },
          )
      }
    }
}

/** A focusable settings row showing the current auto-refresh selection; opens a picker on click. */
@Composable
private fun AutoRefreshRow(selected: PlaylistAutoRefresh, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { onToggle(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.size(52.dp, 30.dp).clip(CircleShape).background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        shape = RoundedCornerShape(14.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}
