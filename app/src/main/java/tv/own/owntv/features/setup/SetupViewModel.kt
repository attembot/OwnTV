package tv.own.owntv.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportStage
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.core.sync.withRemainderNote
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.util.friendlySyncError
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SettingsRepository
import java.io.File

/**
 * Drives onboarding for a profile (first-run and "add profile"): create the profile, then add content
 * (new source, link an existing unlocked profile's playlists, restore a backup, or skip). The new
 * profile is only made active on [finish], so the wizard stays put until the user completes it.
 */
class SetupViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val importFinalizer: tv.own.owntv.core.sync.ImportFinalizer,
    private val epgRepository: tv.own.owntv.core.repository.EpgRepository,
    private val epgSourceStore: tv.own.owntv.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val stalkerAuth: tv.own.owntv.core.stalker.StalkerAuthManager,
    private val companion: tv.own.owntv.core.companion.CompanionController,
) : ViewModel() {

    // ---- Remote (companion) add-source: a LAN web form fills the Add Source screen from a phone. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the Remote screen. */
    val remoteState get() = companion.state

    /** Live submission stream — the Remote screen collects it to hand off to the Manual form. */
    val remotePayloads get() = companion.payloads

    /** Retained last submission, so the Manual form pre-fills even after the Remote screen left. */
    val remotePayload get() = companion.lastPayload

    fun startRemoteListener(port: Int) = companion.start(port)
    fun stopRemoteListener() = companion.stop()
    fun consumeRemotePayload() = companion.consumePayload()

    // ---- Remote restore: a phone uploads a backup JSON to the TV over the LAN companion server. ----
    /** Uploaded backup files — the remote-restore screen collects this and hands each to [importBackup]. */
    val remoteBackups get() = companion.backups

    fun startRemoteRestore(port: Int) = companion.startForBackupRestore(port)
    fun stopRemoteRestore() = companion.stop()

    // Semi-auto EPG: after the first playlist imports, offer a one-tap guide sync (with a live count) if it
    // has a guide feed.
    private var pendingEpgSource: SourceEntity? = null
    // Set when the user leaves the wizard with "Run in background". A late Success must not raise
    // the EPG Ask prompt into the dead wizard UI, and a late failure must not silently DELETE the
    // source the user thinks they added — keep it (credentials intact) and only wipe partial content.
    private var backgroundHandoff = false
    private val _epgSync = MutableStateFlow<tv.own.owntv.features.settings.EpgSyncUi>(tv.own.owntv.features.settings.EpgSyncUi.Hidden)
    val epgSync: StateFlow<tv.own.owntv.features.settings.EpgSyncUi> = _epgSync.asStateFlow()

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch {
            tv.own.owntv.features.settings.runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it }
        }
    }

    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Hidden }

    /**
     * "Run in background" for the semi-auto EPG sync: enter the app now while the guide keeps
     * downloading. The sync launched by [syncPendingEpg] runs in this activity-scoped [viewModelScope],
     * so it survives leaving the wizard — exactly like the playlist [continueInBackground]. We only
     * finish onboarding; the in-flight job is deliberately not cancelled.
     */
    fun syncEpgInBackground(onDone: () -> Unit = {}) {
        pendingEpgSource = null
        finish(onDone)
    }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** Per-type breakdown (incl. EPG) shown on the onboarding "All set" screen. */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
        /** Encrypted backup needs the backup password before restoring; [retry] after a wrong attempt.
         *  [sealed] marks a whole-file-encrypted `.own`, where the password is mandatory — there is
         *  nothing to restore without it, so the wizard hides "Skip". */
        data class NeedPassword(val file: File, val retry: Boolean = false, val sealed: Boolean = false) : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Failed source preserved so AddSourceScreen pre-fills on retry — no re-typing on remote. */
    var lastFailedSource: SourceEntity? = null
        private set

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    private var createdProfileId = -1L
    private var importJob: Job? = null

    /** Creates the profile (not active yet); the rest of onboarding attaches content to it. */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            createdProfileId = profileDao.insert(
                ProfileEntity(
                    name = name.ifBlank { "Profile" },
                    avatarColor = 0,
                    avatarId = avatarId,
                    isKids = isKids,
                    pinHash = pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) },
                ),
            )
            onCreated(createdProfileId)
        }
    }

    fun startXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        userAgent: String = "",
        epgUrl: String = "",
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Now,
        series: SyncScopeChoice = SyncScopeChoice.Now,
        preferHls: Boolean = false,
    ) {
        val enabled = SyncContentTypes.fromChoices(live, movies, series)
        val priority = SyncContentTypes.priorityFromChoices(live, movies, series)
        runImport(autoRefresh, priority, enabledScope = enabled, enqueueRemainder = true, requiresNetwork = true) { profileId ->
            sourceRepository.addXtreamSource(
                profileId = profileId,
                name = name.ifBlank { "My IPTV" },
                serverUrl = server.trim(),
                username = username.trim(),
                password = password,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
                preferHls = preferHls,
            )
        }
    }

    /** Stalker/MAC portal onboarding — mirrors SettingsViewModel.addStalker: the handshake is verified
     *  BEFORE the source is saved, so a typo'd portal/MAC fails with a clear error instead of leaving a
     *  dead source on the brand-new profile. Defaults: Live Now, Movies/Series Later (Stalker VOD has
     *  no bulk endpoint). Off sections are never fetched or shown. */
    fun startStalker(
        name: String,
        portalUrl: String,
        mac: String,
        userAgent: String = "",
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        live: SyncScopeChoice = SyncScopeChoice.Now,
        movies: SyncScopeChoice = SyncScopeChoice.Later,
        series: SyncScopeChoice = SyncScopeChoice.Later,
    ) {
        val canonicalMac = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac)
        if (canonicalMac == null) {
            _state.value = ImportState.Failed("Invalid MAC address — use AA:BB:CC:DD:EE:FF")
            return
        }
        val enabled = SyncContentTypes.fromChoices(live, movies, series)
        val priority = SyncContentTypes.priorityFromChoices(live, movies, series)
        runImport(autoRefresh, contentTypes = priority, enabledScope = enabled, enqueueRemainder = true, requiresNetwork = true) { profileId ->
            stalkerAuth.testConnection(
                tv.own.owntv.core.stalker.StalkerCredentials(
                    sourceId = STALKER_TEST_SOURCE_ID,
                    portalUrl = portalUrl.trim(),
                    mac = canonicalMac,
                    userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                ),
            )
            sourceRepository.addStalkerSource(
                profileId, name.ifBlank { "My Portal" }, portalUrl.trim(), canonicalMac,
                userAgent.trim().takeIf { it.isNotBlank() },
                syncLive = enabled.live, syncMovies = enabled.movies, syncSeries = enabled.series,
            )
        }
    }

    fun startM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF) =
        runImport(autoRefresh, requiresNetwork = !url.isLocalPlaylistPath()) { profileId ->
            sourceRepository.addM3uSource(
                profileId = profileId,
                name = name.ifBlank { "My Playlist" },
                url = url.trim(),
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
            )
        }

    private fun runImport(
        autoRefresh: PlaylistAutoRefresh = PlaylistAutoRefresh.OFF,
        contentTypes: SyncContentTypes = SyncContentTypes(),
        enabledScope: SyncContentTypes = SyncContentTypes(),
        enqueueRemainder: Boolean = false,
        requiresNetwork: Boolean = true,
        addSource: suspend (Long) -> SourceEntity,
    ) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            var source: SourceEntity? = null
            try {
                if (requiresNetwork && !connectivity.isOnlineNow()) {
                    _state.value = ImportState.Failed(friendlySyncError(null, online = false))
                    return@launch
                }
                val profileId = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                source = addSource(profileId)
                val freshSync = source.lastSyncAt == null
                val remainder = if (enqueueRemainder) {
                    enabledScope.remainderAfter(contentTypes)
                } else {
                    SyncContentTypes(live = false, movies = false, series = false)
                }
                settings.setPlaylistAutoRefresh(source.id, autoRefresh)
                when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it }, contentTypes = contentTypes)) {
                    is SyncResult.Success -> {
                        // Just the playlist content — EPG is added separately (Settings → EPG sources).
                        val counts = importFinalizer.finalize(source, deferIndexes = freshSync)
                        val syncedSource = sourceDao.getById(source.id) ?: source
                        if (enqueueRemainder) enqueueRemainderSync(source, contentTypes, enabledScope)
                        if (freshSync && !remainder.hasAny) catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_add")
                        lastFailedSource = null
                        _state.value = ImportState.Success(
                            counts.summary(includeEpg = false).withWarnings(result).withRemainderNote(remainder),
                        )
                        if (!backgroundHandoff && epgRepository.guideUrl(syncedSource) != null) {
                            pendingEpgSource = syncedSource
                            _epgSync.value = tv.own.owntv.features.settings.EpgSyncUi.Ask(syncedSource.name)
                        }
                        viewModelScope.launch { runCatching { launcherIntegrationRepository.refreshProfile(profileId) } }
                    }
                    is SyncResult.Failed -> {
                        cleanupFailedAdd(source)
                        _state.value = ImportState.Failed(friendlySyncError(result.message, connectivity.isOnlineNow()))
                    }
                    SyncResult.Cancelled -> {
                        cleanupFailedAdd(source)
                        _state.value = ImportState.Idle
                    }
                }
            } catch (c: CancellationException) {
                cleanupFailedAdd(source)
                _state.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                cleanupFailedAdd(source)
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    private fun String.isLocalPlaylistPath(): Boolean =
        startsWith("/") || startsWith("file://") || startsWith("content://")

    private fun enqueueRemainderSync(source: SourceEntity, priority: SyncContentTypes, enabledScope: SyncContentTypes) {
        val remainder = enabledScope.remainderAfter(priority)
        if (remainder.hasAny) {
            catalogSyncScheduler.enqueueSync(source.id, reason = "add_remainder", contentTypes = remainder, completesInitialSync = true)
        }
    }

    /** Playlists belonging to unlocked (no-PIN) profiles that aren't already on the new profile. */
    suspend fun availableExistingSources(): List<SourceEntity> {
        val unlocked = profileDao.getAllOnce().filter { it.pinHash == null && it.id != createdProfileId }.map { it.id }.toSet()
        if (unlocked.isEmpty()) return emptyList()
        val links = sourceDao.allLinks()
        val fromUnlocked = links.filter { it.profileId in unlocked }.map { it.sourceId }.toSet()
        val alreadyMine = links.filter { it.profileId == createdProfileId }.map { it.sourceId }.toSet()
        val wanted = fromUnlocked - alreadyMine
        return sourceDao.getAllOnce().filter { it.id in wanted }
    }

    /**
     * Link the chosen existing sources to the new profile (shared content, separate favorites/history),
     * then re-sync each one so its catalog is fresh — exactly like adding a brand-new source. Drives the
     * same [state]/[progress] as [runImport], so the wizard can show the import screen.
     */
    fun linkExisting(sourceIds: Set<Long>) {
        importJob?.cancel()
        val job = viewModelScope.launch {
            _state.value = ImportState.Running
            _progress.value = null
            try {
                val pid = createdProfileId.takeIf { it > 0 } ?: ensureFallbackProfile()
                sourceIds.forEach { sourceDao.link(ProfileSourceCrossRef(profileId = pid, sourceId = it)) }
                val sources = sourceDao.getAllOnce().filter { it.id in sourceIds }
                var total = tv.own.owntv.core.sync.SyncCounts(0, 0, 0, 0)
                var failure: String? = null
                val warnings = mutableListOf<String>()
                for (source in sources) {
                    when (val result = sourceRepository.sync(source, onProgress = { _progress.value = it })) {
                        is SyncResult.Success -> {
                            val c = importFinalizer.finalize(source)
                            total = tv.own.owntv.core.sync.SyncCounts(total.channels + c.channels, total.movies + c.movies, total.series + c.series, total.epg + c.epg)
                            result.warningSummary()?.let { warnings.add(it) }
                        }
                        is SyncResult.Failed -> failure = result.message
                        SyncResult.Cancelled -> {}
                    }
                }
                runCatching { launcherIntegrationRepository.refreshProfile(pid) }
                val summary = listOf(total.summary(includeEpg = true), *warnings.toTypedArray()).joinToString("\n")
                _state.value = failure?.let { ImportState.Failed(friendlySyncError(it, connectivity.isOnlineNow())) } ?: ImportState.Success(summary)
            } catch (c: CancellationException) {
                _state.value = ImportState.Idle
                _progress.value = null
                throw c
            } catch (e: Exception) {
                _state.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    /** Restore everything from a backup file (replaces profiles & sources, then activates one). Encrypted
     *  backups first ask for the backup password via [ImportState.NeedPassword]. */
    fun importBackup(file: File, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            // A sealed .own reveals nothing before it is decrypted — ask for the password first.
            if (backup.isSealed(file)) {
                _state.value = ImportState.NeedPassword(file, sealed = true)
                return@launch
            }
            val inspection = backup.sectionsIn(file).getOrElse {
                _state.value = ImportState.Failed(it.message ?: "Couldn't read the backup file")
                return@launch
            }
            if (inspection.encrypted) _state.value = ImportState.NeedPassword(file)
            else doRestore(file, null, onDone)
        }
    }

    /** Continue an encrypted restore once the user provides (or skips, password = null) the passphrase. */
    fun restoreWithPassword(file: File, password: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = ImportState.Running
            doRestore(file, password, onDone)
        }
    }

    private suspend fun doRestore(file: File, password: String?, onDone: () -> Unit) {
        backup.import(file, backupPassword = password).fold(
            onSuccess = { summary ->
                val note = if (password.isNullOrBlank()) " Re-enter any saved passwords afterwards." else ""
                _state.value = ImportState.Success(
                    "Restored ${summary.items} items. Re-sync your sources to load content.$note${summary.skippedNote}",
                )
                onDone()
            },
            onFailure = {
                if (it is BackupManager.WrongPasswordException) {
                    _state.value = ImportState.NeedPassword(file, retry = true, sealed = backup.isSealed(file))
                }
                else _state.value = ImportState.Failed(it.message ?: "Restore failed")
            },
        )
    }

    private suspend fun ensureFallbackProfile(): Long {
        if (createdProfileId > 0) return createdProfileId
        createdProfileId = profileDao.insert(ProfileEntity(name = "Profile", avatarColor = 0, avatarId = 0))
        return createdProfileId
    }

    fun reset() {
        _state.value = ImportState.Idle
        _progress.value = null
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.value = ImportState.Idle
        _progress.value = null
    }

    /**
     * "Run in background": enter the app now while the import keeps running. This ViewModel is
     * activity-scoped, so the in-flight [importJob] survives leaving the wizard — the sync continues
     * exactly as if the user had waited (success still runs ImportFinalizer + the remainder enqueue).
     * Deliberately does NOT cancel: cancelling would run [cleanupFailedAdd] and delete the source.
     * The semi-auto EPG prompt is skipped (its dialog lives in the wizard); EPG stays user-initiated
     * from Settings → EPG Sources, matching the app's EPG opt-in policy.
     */
    fun continueInBackground(onDone: () -> Unit = {}) {
        backgroundHandoff = true
        dismissPendingEpg()
        finish(onDone)
    }

    /** Completes onboarding → makes the new profile active, routing the app into the shell. */
    fun finish(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (createdProfileId > 0) settings.setActiveProfile(createdProfileId)
            onDone()
        }
    }

    private suspend fun cleanupFailedAdd(source: SourceEntity?) {
        if (source == null) return
        withContext(NonCancellable) {
            catalogSyncScheduler.cancelSync(source.id)
            if (backgroundHandoff) {
                // The user already entered the app with "Run in background" — deleting the source
                // would make the playlist they just added silently vanish. Keep it so they can
                // re-sync from Settings → Playlists; wipe only the partial content (a never-synced
                // source re-syncs via insertFresh, which assumes empty tables — leftovers duplicate).
                runCatching { sourceRepository.clearSourceContent(source.id) }
            } else {
                runCatching { sourceRepository.deleteSource(source) }
                runCatching { settings.setPlaylistAutoRefresh(source.id, PlaylistAutoRefresh.OFF) }
            }
        }
    }

    private fun String.withWarnings(result: SyncResult.Success): String =
        listOfNotNull(this, result.warningSummary()).joinToString("\n")

    private companion object {
        /** Sentinel sourceId for the pre-save Stalker handshake (same as SettingsViewModel's). */
        const val STALKER_TEST_SOURCE_ID = -1L
    }
}
