package tv.own.owntv.features.setup

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class SourceKind { XTREAM, M3U, STALKER }

/** UI state of the Stalker "Test connection" probe (mapped from the owning ViewModel's state). */
sealed interface StalkerTestUi {
    data object Idle : StalkerTestUi
    data object Testing : StalkerTestUi
    data class Ok(val message: String) : StalkerTestUi
    data class Failed(val message: String) : StalkerTestUi
}

/** MAG User-Agent presets (plan §7 "Header/UA pickiness") — value goes into the User-Agent field. */
private val MAG_UA_PRESETS = listOf(
    "Default (MAG200)" to "",
    "MAG250" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG250 stbapp ver: 2 rev: 250 Safari/533.3",
    "MAG254" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp ver: 2 rev: 250 Safari/533.3",
    "MAG270" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG270 stbapp ver: 2 rev: 250 Safari/533.3",
    "MAG420" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/601.1 (KHTML, like Gecko) MAG420 stbapp ver: 4 rev: 2721 Safari/601.1",
)

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
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
        isDefault: Boolean,
        preferHls: Boolean,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, epgUrl: String, autoRefresh: PlaylistAutoRefresh, isDefault: Boolean) -> Unit,
    // The last submission from the Remote companion screen, retained as a StateFlow so it survives the
    // Remote → Manual hand-off (this screen mounts after the phone posted). When present, the matching
    // type is selected and the fields pre-filled; the user then presses Start Import. Consumed once via
    // [onRemotePayloadConsumed] so it can't re-fill a later, unrelated Manual add.
    remotePayload: kotlinx.coroutines.flow.StateFlow<CompanionPayload?>? = null,
    onRemotePayloadConsumed: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceEntity? = null,
    initialAutoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
    initialIsDefault: Boolean = false,
    showDefaultToggle: Boolean = true,
    // Stalker portal (plan Phase B). Null = the Stalker option is hidden (e.g. the setup wizard).
    onStartStalker: ((
        name: String,
        portalUrl: String,
        mac: String,
        userAgent: String,
        autoRefresh: PlaylistAutoRefresh,
        isDefault: Boolean,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
    ) -> Unit)? = null,
    onTestStalker: ((portalUrl: String, mac: String, userAgent: String) -> Unit)? = null,
    stalkerTest: StalkerTestUi = StalkerTestUi.Idle,
) {
    val colors = OwnTVTheme.colors
    val editing = initial != null
    var kind by remember {
        mutableStateOf(
            when (initial?.type) {
                SourceType.M3U -> SourceKind.M3U
                SourceType.STALKER -> SourceKind.STALKER
                else -> SourceKind.XTREAM
            },
        )
    }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var server by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var m3uUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.M3U) initial.url else "") }
    var portalUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.STALKER) initial.url else "") }
    var mac by remember(initial) { mutableStateOf(initial?.mac ?: "") }
    var showUaPresetPicker by remember { mutableStateOf(false) }
    var epgUrl by remember(initial) { mutableStateOf(initial?.epgUrl ?: "") }
    var userAgent by remember(initial) { mutableStateOf(initial?.userAgent ?: "") }
    var autoRefresh by remember(initialAutoRefresh) { mutableStateOf(initialAutoRefresh) }
    var isDefault by remember(initialIsDefault) { mutableStateOf(initialIsDefault) }
    var preferHls by remember(initial) { mutableStateOf(initial?.preferHls == true) }
    // Edit: On(=Now)/Off from persisted flags. Add: default all Now for Xtream; Stalker defaults
    // Live Now + Movies/Series Later when the kind switches (see LaunchedEffect below).
    var syncLive by remember(initial) {
        mutableStateOf(if (initial?.syncLive == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncMovies by remember(initial) {
        mutableStateOf(if (initial?.syncMovies == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncSeries by remember(initial) {
        mutableStateOf(if (initial?.syncSeries == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var hasRemoteStalkerScopes by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val startImportFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Profile-wide "hide new categories on sync" default (issue #87, Phase 4). Written straight to
    // DataStore — no callback signature change — so it lands on the ACTIVE profile at add time. The row
    // is only shown while ADDING (an edit re-syncs nothing new) and only when a profile exists (the
    // setup wizard may still be profile-less; the value defaults to off there).
    val settings: SettingsRepository = koinInject()
    val profileDao: ProfileDao = koinInject()
    val scope = rememberCoroutineScope()
    var hideNewCatsProfile by remember { mutableStateOf(-1L) }
    LaunchedEffect(Unit) {
        val preferred = settings.activeProfileId.first()
        hideNewCatsProfile = profileDao.resolveExistingProfileId(preferred) ?: -1L
    }
    val hideNewCats by settings.hideNewCategoriesDefault(hideNewCatsProfile)
        .collectAsStateWithLifecycle(initialValue = false)

    // Pre-fill from a Remote (companion) submission handed off by the host. StateFlow replays its
    // current value to this new collector, so the payload posted before this screen mounted still lands.
    LaunchedEffect(remotePayload) {
        remotePayload?.collect { payload ->
            if (payload == null || editing) return@collect
            name = payload.name
            userAgent = payload.userAgent
            epgUrl = payload.epgUrl
            isDefault = payload.isDefault
            autoRefresh = runCatching { PlaylistAutoRefresh.valueOf(payload.autoRefresh) }.getOrDefault(PlaylistAutoRefresh.OFF)
            when (payload.type) {
                SourceType.M3U -> {
                    hasRemoteStalkerScopes = false
                    m3uUrl = payload.server
                    kind = SourceKind.M3U
                }
                SourceType.STALKER -> {
                    portalUrl = payload.portalUrl
                    mac = payload.mac
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = true
                    kind = SourceKind.STALKER
                }
                else -> {
                    server = payload.server
                    username = payload.user
                    password = payload.pass
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = false
                    kind = SourceKind.XTREAM
                }
            }
            onRemotePayloadConsumed() // one-shot: don't re-fill a later, unrelated Manual add
            runCatching { kotlinx.coroutines.delay(150); startImportFocus.requestFocus() }
        }
    }

    // Stalker add defaults: Live Now, Movies/Series Later (VOD has no bulk endpoint). Skip when
    // editing, retrying a failed add, or applying explicit choices from a remote Stalker payload.
    LaunchedEffect(kind, initial, hasRemoteStalkerScopes) {
        if (initial != null || kind != SourceKind.STALKER || hasRemoteStalkerScopes) return@LaunchedEffect
        if (syncLive == SyncScopeChoice.Now && syncMovies == SyncScopeChoice.Now && syncSeries == SyncScopeChoice.Now) {
            syncMovies = SyncScopeChoice.Later
            syncSeries = SyncScopeChoice.Later
        }
    }

    val showContentToggles = kind == SourceKind.XTREAM || kind == SourceKind.STALKER
    val hasAnySectionOn = syncLive != SyncScopeChoice.Off || syncMovies != SyncScopeChoice.Off || syncSeries != SyncScopeChoice.Off
    val macValid = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac) != null
    val canStart = when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank() && hasAnySectionOn
        SourceKind.M3U -> m3uUrl.isNotBlank()
        SourceKind.STALKER -> tv.own.owntv.core.stalker.StalkerClient.isValidPortalUrl(portalUrl) && macValid && hasAnySectionOn
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
                if (editing) "Update this source's details, or change its auto-refresh setting." else "OwnTV is a player — bring your own Xtream, M3U, or Stalker source.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            // Source type selector (locked while editing — the type can't change, so initial focus
            // goes to the Name field instead of a dead chip).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // While editing, the type is fixed — show only the matching chip.
                if (!editing || kind == SourceKind.XTREAM) {
                    KindChip("Xtream", kind == SourceKind.XTREAM, Modifier.weight(1f).then(if (!editing) Modifier.focusRequester(firstFocus) else Modifier)) { if (!editing) kind = SourceKind.XTREAM }
                }
                if (!editing || kind == SourceKind.M3U) {
                    KindChip("M3U / M3U8", kind == SourceKind.M3U, Modifier.weight(1f)) { if (!editing) kind = SourceKind.M3U }
                }
                if (onStartStalker != null && (!editing || kind == SourceKind.STALKER)) {
                    KindChip("Stalker (MAC)", kind == SourceKind.STALKER, Modifier.weight(1f)) { if (!editing) kind = SourceKind.STALKER }
                }
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
                SourceKind.STALKER -> {
                    OwnTVTextField(portalUrl, { portalUrl = it }, label = "Portal URL", placeholder = "http://host:port/c/", keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(mac, { mac = it }, label = "MAC address", placeholder = "00:1A:79:AA:BB:CC", modifier = Modifier.fillMaxWidth())
                    if (mac.isNotBlank() && !macValid) {
                        Spacer(Modifier.height(6.dp))
                        Text("Enter 12 characters, e.g. 00:1A:79:AA:BB:CC", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                    }
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        label = "Device model preset (User-Agent)…",
                        onClick = { showUaPresetPicker = true },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (onTestStalker != null) {
                        Spacer(Modifier.height(10.dp))
                        OwnTVButton(
                            label = if (stalkerTest is StalkerTestUi.Testing) "Testing…" else "Test connection",
                            onClick = { onTestStalker(portalUrl, mac, userAgent) },
                            style = OwnTVButtonStyle.SECONDARY,
                            enabled = canStart && stalkerTest !is StalkerTestUi.Testing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        when (stalkerTest) {
                            is StalkerTestUi.Ok -> {
                                Spacer(Modifier.height(6.dp))
                                Text("✓ ${stalkerTest.message}", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                            }
                            is StalkerTestUi.Failed -> {
                                Spacer(Modifier.height(6.dp))
                                Text(stalkerTest.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                            }
                            else -> Unit
                        }
                    }
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

            // Shown for every Xtream source, on Add (incl. the setup wizard and the Remote hand-off)
            // as well as Edit: `hlsSupported` is only known AFTER the first sync has read
            // user_info.allowed_output_formats, so gating the row on it would hide the option on a
            // fresh install entirely. Detection only refines the wording below.
            if (kind == SourceKind.XTREAM) {
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = "Prefer HLS for Live TV",
                    desc = "Prioritize HLS (.m3u8) over MPEG-TS for Live TV & Catch-up. Falls back to MPEG-TS if HLS fails." +
                        if (initial?.hlsSupported == true) " Your provider reports HLS support." else "",
                    checked = preferHls,
                ) { preferHls = it }
            }

            if (showContentToggles) {
                Spacer(Modifier.height(20.dp))
                Text("What to sync", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (editing) {
                        "Off sections stay hidden and are never fetched. Turning a section back On shows cached rows immediately."
                    } else {
                        "Now imports first · Later syncs in the background · Off is never fetched or shown."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SyncScopeRow(label = "Live TV", desc = "Channels and categories", value = syncLive, editing = editing) { syncLive = it }
                Spacer(Modifier.height(8.dp))
                SyncScopeRow(label = "Movies", desc = "VOD movie catalog", value = syncMovies, editing = editing) { syncMovies = it }
                Spacer(Modifier.height(8.dp))
                SyncScopeRow(label = "Series", desc = "TV series catalog", value = syncSeries, editing = editing) { syncSeries = it }
            }

            if (!editing && hideNewCatsProfile >= 0) {
                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = "Hide new categories by default",
                    desc = "Profile-wide: categories that appear on future syncs start hidden. See them anytime in Customize.",
                    checked = hideNewCats,
                ) { hidden -> scope.launch { settings.setHideNewCategoriesDefault(hideNewCatsProfile, hidden) } }
            }

            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    label = if (editing) "Save" else "Start Import",
                    onClick = {
                        when (kind) {
                            SourceKind.XTREAM -> onStartXtream(name, server, username, password, userAgent, epgUrl, autoRefresh, syncLive, syncMovies, syncSeries, isDefault, preferHls)
                            SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, epgUrl, autoRefresh, isDefault)
                            SourceKind.STALKER -> onStartStalker?.invoke(
                                name, portalUrl, mac, userAgent, autoRefresh, isDefault, syncLive, syncMovies, syncSeries,
                            )
                        }
                    },
                    enabled = canStart,
                    modifier = Modifier.focusRequester(startImportFocus),
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
      if (showUaPresetPicker) {
          PickerDialog(
              title = "Device model preset",
              options = MAG_UA_PRESETS.map { (label, _) -> label to label },
              selected = MAG_UA_PRESETS.firstOrNull { it.second == userAgent }?.first ?: MAG_UA_PRESETS.first().first,
              onSelect = { label ->
                  userAgent = MAG_UA_PRESETS.firstOrNull { it.first == label }?.second ?: ""
                  showUaPresetPicker = false
              },
              onDismiss = { showUaPresetPicker = false },
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
        surface = GlassSurface.CARDS,
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
        surface = GlassSurface.CARDS,
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

/** Single focusable row; D-pad ◀/▶ (or click) cycles Now/Later/Off — Edit uses On/Off only. */
@Composable
private fun SyncScopeRow(
    label: String,
    desc: String,
    value: SyncScopeChoice,
    editing: Boolean,
    onChange: (SyncScopeChoice) -> Unit,
) {
    val colors = OwnTVTheme.colors
    val options = if (editing) {
        listOf(SyncScopeChoice.Now, SyncScopeChoice.Off)
    } else {
        listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    }
    fun cycle(delta: Int) {
        val idx = options.indexOf(value).coerceAtLeast(0)
        onChange(options[(idx + delta + options.size) % options.size])
    }
    fun optionLabel(choice: SyncScopeChoice): String = when (choice) {
        SyncScopeChoice.Now -> if (editing) "On" else "Now"
        SyncScopeChoice.Later -> "Later"
        SyncScopeChoice.Off -> "Off"
    }
    FocusableSurface(
        onClick = { cycle(+1) },
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { cycle(-1); true }
                    Key.DirectionRight -> { cycle(+1); true }
                    else -> false
                }
            },
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Text(
                "◀ ${optionLabel(value)} ▶",
                style = MaterialTheme.typography.titleMedium,
                color = if (value == SyncScopeChoice.Off) colors.onSurfaceVariant else colors.primary,
            )
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
        surface = GlassSurface.CARDS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}
