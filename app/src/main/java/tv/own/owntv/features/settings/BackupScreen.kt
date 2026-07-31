package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

/**
 * Phase 12 — Backup & Restore (Settings → Backup), with selective sections: the user picks what to
 * back up (profiles & sources / customizations / favorites / history / resume) and, on restore,
 * which of the file's sections to apply. Uses an in-app file picker (no SAF).
 */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: BackupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var browser by remember { mutableStateOf(BrowseMode.FOLDER) } // which picker
    var showBrowser by remember { mutableStateOf(false) }
    var showExportPicker by remember { mutableStateOf(false) }
    // Opening the folder browser in the SAME frame the section picker closes makes the browser's
    // initial focus grab race the picker's teardown — focus ends up trapped on the screen behind
    // the overlay. Defer the open by a beat instead.
    var pendingFolderBrowser by remember { mutableStateOf(false) }
    LaunchedEffect(pendingFolderBrowser) {
        if (pendingFolderBrowser) {
            kotlinx.coroutines.delay(120)
            browser = BrowseMode.FOLDER
            showBrowser = true
            pendingFolderBrowser = false
        }
    }
    var exportSections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }
    // Export step 0: which profiles ride in the file (backup is profile-based). PIN-locked profiles
    // other than the active one must be unlocked with their PIN to be ticked.
    var showProfilePicker by remember { mutableStateOf(false) }
    var exportProfiles by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val profileChoices by vm.profileChoices.collectAsStateWithLifecycle()
    // After the folder is picked, hold it here to ask about password protection before exporting.
    var exportFolder by remember { mutableStateOf<File?>(null) }
    val firstFocus = remember { FocusRequester() }
    val restoreBtnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { firstFocus.requestFocus() } }

    // Restore: first pick Remote (phone upload) or Local (file picker). Remote opens a full-screen
    // companion panel; an uploaded file drops back into the same inspect → section-picker flow.
    var showRestoreChooser by remember { mutableStateOf(false) }
    var showRemoteRestore by remember { mutableStateOf(false) }
    val remoteState by vm.remoteState.collectAsStateWithLifecycle()

    // Export: Remote (serve the file for a phone/laptop to download) or Local (save to a folder).
    var showExportChooser by remember { mutableStateOf(false) }
    var exportToRemote by remember { mutableStateOf(false) }
    var showRemoteExportPassword by remember { mutableStateOf(false) }
    var showRemoteExport by remember { mutableStateOf(false) }
    // If the remote export fails to prepare, drop the panel so the base screen shows the error.
    LaunchedEffect(state) {
        if (state is BackupViewModel.State.Error && showRemoteExport) {
            vm.stopRemoteExport(); showRemoteExport = false
        }
    }

    BackHandler { onBack() }

    // Dialog-close focus return: closing the section picker / file browser refocuses the button
    // that opened it. The restore crosses INTO this group from the dialog, so onEnter intercepts
    // it — it consults dialogReturn first (and clears it) instead of hijacking.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val anyDialogOpen = showBrowser || showExportPicker || showProfilePicker || pendingFolderBrowser || exportFolder != null ||
        showRestoreChooser || showRemoteRestore || showExportChooser || showRemoteExportPassword || showRemoteExport ||
        state is BackupViewModel.State.ChooseRestore || state is BackupViewModel.State.NeedPassword
    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            dialogReturn?.let { btn ->
                kotlinx.coroutines.delay(80)
                runCatching { btn.requestFocus() }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) — so it must prefer the pending return button.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Save your profiles, sources, customizations, favorites, history and resume positions to a " +
                "file — or restore them on a new device. You choose what to include each time. " +
                "Channels/movies re-sync from your sources after restoring.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.widthIn(max = 680.dp),
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton("Export backup", onClick = { dialogReturn = firstFocus; showExportChooser = true }, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(firstFocus))
            OwnTVButton("Restore backup", onClick = { dialogReturn = restoreBtnFocus; showRestoreChooser = true }, style = OwnTVButtonStyle.SECONDARY, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(restoreBtnFocus))
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            BackupViewModel.State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(12.dp))
                Text("Working…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            is BackupViewModel.State.Done -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = colors.primary)
            is BackupViewModel.State.Error -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFEF4444))
            else -> Unit
        }
    }

    // Export step 0: pick the profiles to include (all unticked — the user chooses; locked
    // non-active profiles need their PIN to be ticked).
    if (showProfilePicker) {
        profileChoices?.let { choices ->
            ProfilePickerDialog(
                profiles = choices.profiles,
                activeId = choices.activeId,
                verifyPin = vm::verifyPin,
                onConfirm = { picked ->
                    exportProfiles = picked
                    showProfilePicker = false
                    showExportPicker = true
                },
                onDismiss = { showProfilePicker = false },
            )
        }
    }

    // Export step 1: choose what to include, then pick the folder (local) or continue (remote).
    if (showExportPicker) {
        SectionPickerDialog(
            title = "What to back up",
            sections = BackupManager.Section.entries,
            initial = BackupManager.Section.entries.toSet(),
            confirmLabel = if (exportToRemote) "Continue" else "Choose folder",
            onConfirm = { chosen ->
                exportSections = chosen
                showExportPicker = false
                if (exportToRemote) showRemoteExportPassword = true else pendingFolderBrowser = true
            },
            onDismiss = { showExportPicker = false },
        )
    }

    // Restore step 2: the picked file was inspected — choose which of its sections to apply.
    (state as? BackupViewModel.State.ChooseRestore)?.let { choose ->
        SectionPickerDialog(
            title = "What to restore",
            sections = BackupManager.Section.entries.filter { it in choose.available },
            initial = choose.available,
            confirmLabel = "Restore",
            onConfirm = { chosen -> vm.beginImport(choose.file, chosen, choose.encrypted, choose.password) },
            onDismiss = { vm.reset() },
        )
    }

    if (showBrowser) {
        StorageBrowser(
            title = if (browser == BrowseMode.FOLDER) "Choose a folder to save the backup" else "Pick a backup file to restore",
            mode = browser,
            // `.own` is what we write now; `.json` stays so pre-4.2 backups keep restoring.
            fileExtensions = BackupManager.RESTORE_EXTENSIONS,
            onPick = { file -> showBrowser = false; if (browser == BrowseMode.FOLDER) exportFolder = file else vm.inspect(file) },
            onDismiss = { showBrowser = false },
        )
    }

    // Export step 3: ask whether to protect passwords with a backup passphrase (or export without them).
    exportFolder?.let { folder ->
        BackupPasswordDialog(
            title = "Encrypt this backup?",
            message = "With a backup password, the whole file is encrypted — playlists, profiles and saved " +
                "passwords included — and nothing in it can be read without that password. Keep it safe: " +
                "if you lose it, the backup cannot be opened at all. Without a password the file is not " +
                "encrypted and saved passwords are left out of it.",
            confirmLabel = "Encrypt & export",
            skipLabel = "Export unencrypted",
            onConfirm = { pass -> exportFolder = null; vm.export(folder, exportSections, pass, exportProfiles) },
            onSkip = { exportFolder = null; vm.export(folder, exportSections, null, exportProfiles) },
            onDismiss = { exportFolder = null },
        )
    }

    // Restore password prompt. Two shapes, see BackupViewModel.State.NeedPassword:
    //  - sealed .own  → asked FIRST, mandatory; unlocking then reveals the section picker.
    //  - field-encrypted → asked after the section picker, optional (skip = no saved passwords).
    (state as? BackupViewModel.State.NeedPassword)?.let { need ->
        BackupPasswordDialog(
            title = if (need.retry) "Wrong backup password" else "Enter backup password",
            message = when {
                need.retry && need.sealed -> "That password didn't match. This backup is encrypted — it can't be " +
                    "opened without the password it was created with."
                need.retry -> "That password didn't match. Try again, or skip to restore everything except saved passwords."
                need.sealed -> "This backup is encrypted. Enter the backup password to open it and choose what to restore."
                else -> "This backup's passwords are encrypted. Enter the backup password to restore them, or skip " +
                    "to restore everything else and re-enter passwords later."
            },
            confirmLabel = if (need.sealed) "Unlock" else "Restore",
            skipLabel = if (need.sealed) null else "Skip (no passwords)",
            onConfirm = { pass ->
                val sections = need.sections
                if (sections == null) vm.unlock(need.file, pass) else vm.import(need.file, sections, pass)
            },
            onSkip = { need.sections?.let { vm.import(need.file, it, null) } },
            onDismiss = { vm.reset() },
        )
    }

    // Export step 0: Remote (serve for a phone/laptop to download) or Local (save to a folder).
    if (showExportChooser) {
        RemoteLocalChooserDialog(
            title = "Export a backup",
            message = "Send the backup to another device (phone or laptop) on the same Wi-Fi, or save it to a file on this device.",
            onRemote = { showExportChooser = false; exportToRemote = true; vm.loadProfiles(); showProfilePicker = true },
            onLocal = { showExportChooser = false; exportToRemote = false; vm.loadProfiles(); showProfilePicker = true },
            onDismiss = { showExportChooser = false },
        )
    }

    // Remote export step 2: password prompt, then export to cache + start serving the file.
    if (showRemoteExportPassword) {
        BackupPasswordDialog(
            title = "Encrypt this backup?",
            message = "With a backup password, the whole file is encrypted — playlists, profiles and saved " +
                "passwords included — and nothing in it can be read without that password. Keep it safe: " +
                "if you lose it, the backup cannot be opened at all. Without a password the file is not " +
                "encrypted and saved passwords are left out of it.",
            confirmLabel = "Encrypt & prepare",
            skipLabel = "Prepare unencrypted",
            onConfirm = { pass -> showRemoteExportPassword = false; showRemoteExport = true; vm.exportRemote(exportSections, pass, exportProfiles) },
            onSkip = { showRemoteExportPassword = false; showRemoteExport = true; vm.exportRemote(exportSections, null, exportProfiles) },
            onDismiss = { showRemoteExportPassword = false },
        )
    }

    // Remote export: full-screen panel with PIN + QR while the file is served for download.
    if (showRemoteExport) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            RemoteBackupExportScreen(
                state = remoteState,
                preparing = state == BackupViewModel.State.Working,
                onStop = { vm.stopRemoteExport() },
                onBack = { vm.stopRemoteExport(); showRemoteExport = false },
            )
        }
    }

    // Restore step 0: Remote (send the backup from a phone) or Local (pick a file on this device).
    if (showRestoreChooser) {
        RemoteLocalChooserDialog(
            title = "Restore a backup",
            message = "Send the backup from another device (phone or laptop) on the same Wi-Fi, or pick a backup file already on this device.",
            onRemote = { showRestoreChooser = false; showRemoteRestore = true },
            onLocal = { showRestoreChooser = false; browser = BrowseMode.FILE; showBrowser = true },
            onDismiss = { showRestoreChooser = false },
        )
    }

    // Remote restore: full-screen companion panel (PIN + QR). An uploaded file feeds the normal
    // inspect → section-picker flow; the panel closes itself when a file arrives.
    if (showRemoteRestore) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            RemoteBackupRestoreScreen(
                state = remoteState,
                backups = vm.remoteBackups,
                onStart = { port -> vm.startRemoteRestore(port) },
                onStop = { vm.stopRemoteRestore() },
                onBackupReceived = { file -> showRemoteRestore = false; vm.inspect(file) },
                onBack = { vm.stopRemoteRestore(); showRemoteRestore = false },
            )
        }
    }
}

/** Two-way chooser: send/receive over the LAN companion server, or use a local file. */
@Composable
private fun RemoteLocalChooserDialog(
    title: String,
    message: String,
    onRemote: () -> Unit,
    onLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Local file", onClick = onLocal, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton("Remote", onClick = onRemote, modifier = Modifier.focusRequester(firstFocus))
            }
        }
    }
}

/** A single-secret prompt with a confirm (encrypt/restore), a skip (no passwords) and cancel. */
@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    /** Null hides the skip button entirely — a sealed `.own` has nothing to fall back to. */
    skipLabel: String?,
    onConfirm: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var password by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OwnTVTextField(
                value = password,
                onValueChange = { password = it },
                label = "Backup password",
                isPassword = true,
                focusRequester = firstFocus,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                if (skipLabel != null) OwnTVButton(skipLabel, onClick = onSkip, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(confirmLabel, onClick = { onConfirm(password) }, enabled = password.isNotBlank())
            }
        }
    }
}

/**
 * Export step 0: choose which profiles the backup contains. All start UNTICKED (the user chooses
 * explicitly). Ticking the active profile or an unlocked one is immediate; ticking another
 * profile with a PIN prompts for it — wrong PIN shows "PIN incorrect" and leaves it unticked.
 */
@Composable
private fun ProfilePickerDialog(
    profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>,
    activeId: Long,
    verifyPin: (tv.own.owntv.core.database.entity.ProfileEntity, String) -> Boolean,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var ticked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pinFor by remember { mutableStateOf<tv.own.owntv.core.database.entity.ProfileEntity?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pinFor != null) pinFor = null else onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text("Which profiles to back up", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "The backup will contain only the selected profiles and their data. Locked profiles " +
                    "need their PIN.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            profiles.forEachIndexed { i, p ->
                val locked = p.pinHash != null && p.id != activeId
                CheckRow(
                    label = p.name + (if (p.id == activeId) "  ·  current" else ""),
                    desc = when {
                        locked -> "PIN locked"
                        p.isKids -> "Kids profile"
                        else -> null
                    },
                    checked = p.id in ticked,
                    onToggle = {
                        when {
                            p.id in ticked -> ticked = ticked - p.id
                            locked -> pinFor = p
                            else -> ticked = ticked + p.id
                        }
                    },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Continue", onClick = { onConfirm(ticked) }, enabled = ticked.isNotEmpty())
            }
        }
    }

    pinFor?.let { profile ->
        ProfilePinDialog(
            profileName = profile.name,
            verify = { pin -> verifyPin(profile, pin) },
            onUnlocked = { ticked = ticked + profile.id; pinFor = null },
            onDismiss = { pinFor = null },
        )
    }
}

/** PIN prompt for including a locked, non-active profile in the backup. */
@Composable
private fun ProfilePinDialog(
    profileName: String,
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 480.dp, padding = 28.dp)) {
            Text("“$profileName” is locked", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "This isn't your current profile. Enter its PIN to include it in the backup — " +
                    "without the PIN, its data won't be backed up.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OwnTVTextField(
                value = pin,
                onValueChange = { pin = it; wrong = false },
                label = "Profile PIN",
                isPassword = true,
                focusRequester = fieldFocus,
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text("PIN incorrect.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    "Unlock",
                    onClick = { if (verify(pin)) onUnlocked() else { wrong = true; pin = "" } },
                    enabled = pin.isNotBlank(),
                )
            }
        }
    }
}

/** Multi-select dialog over backup sections, with an "Everything" toggle on top. */
@Composable
private fun SectionPickerDialog(
    title: String,
    sections: List<BackupManager.Section>,
    initial: Set<BackupManager.Section>,
    confirmLabel: String,
    onConfirm: (Set<BackupManager.Section>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var selected by remember { mutableStateOf(initial) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            CheckRow(
                label = "Everything",
                desc = null,
                checked = selected.size == sections.size,
                onToggle = { selected = if (selected.size == sections.size) emptySet() else sections.toSet() },
                modifier = Modifier.focusRequester(firstFocus),
            )
            Spacer(Modifier.height(6.dp))
            sections.forEach { section ->
                CheckRow(
                    label = section.label,
                    desc = section.desc,
                    checked = section in selected,
                    onToggle = { selected = if (section in selected) selected - section else selected + section },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(confirmLabel, onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    desc: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) colors.primary else Color.Transparent)
                    .border(2.dp, if (checked) colors.primary else colors.outline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(colors.onPrimary))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (desc != null) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
