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
    // After the folder is picked, hold it here to ask about password protection before exporting.
    var exportFolder by remember { mutableStateOf<File?>(null) }
    val firstFocus = remember { FocusRequester() }
    val restoreBtnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { firstFocus.requestFocus() } }

    BackHandler { onBack() }

    // Dialog-close focus return: closing the section picker / file browser refocuses the button
    // that opened it. The restore crosses INTO this group from the dialog, so onEnter intercepts
    // it — it consults dialogReturn first (and clears it) instead of hijacking.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val anyDialogOpen = showBrowser || showExportPicker || pendingFolderBrowser || exportFolder != null ||
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
            OwnTVButton("Export backup", onClick = { dialogReturn = firstFocus; showExportPicker = true }, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(firstFocus))
            OwnTVButton("Restore backup", onClick = { dialogReturn = restoreBtnFocus; browser = BrowseMode.FILE; showBrowser = true }, style = OwnTVButtonStyle.SECONDARY, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(restoreBtnFocus))
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

    // Export step 1: choose what to include, then pick the folder.
    if (showExportPicker) {
        SectionPickerDialog(
            title = "What to back up",
            sections = BackupManager.Section.entries,
            initial = BackupManager.Section.entries.toSet(),
            confirmLabel = "Choose folder",
            onConfirm = { chosen ->
                exportSections = chosen
                showExportPicker = false
                pendingFolderBrowser = true
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
            onConfirm = { chosen -> vm.beginImport(choose.file, chosen, choose.encrypted) },
            onDismiss = { vm.reset() },
        )
    }

    if (showBrowser) {
        StorageBrowser(
            title = if (browser == BrowseMode.FOLDER) "Choose a folder to save the backup" else "Pick a backup file to restore",
            mode = browser,
            fileExtensions = setOf("json"),
            onPick = { file -> showBrowser = false; if (browser == BrowseMode.FOLDER) exportFolder = file else vm.inspect(file) },
            onDismiss = { showBrowser = false },
        )
    }

    // Export step 3: ask whether to protect passwords with a backup passphrase (or export without them).
    exportFolder?.let { folder ->
        BackupPasswordDialog(
            title = "Protect passwords?",
            message = "Source and proxy passwords can be encrypted with a backup password you choose. " +
                "You'll need the same password to restore them on another device. Without one, passwords " +
                "are left out of the file and must be re-entered after restoring.",
            confirmLabel = "Encrypt & export",
            skipLabel = "Export without passwords",
            onConfirm = { pass -> exportFolder = null; vm.export(folder, exportSections, pass) },
            onSkip = { exportFolder = null; vm.export(folder, exportSections, null) },
            onDismiss = { exportFolder = null },
        )
    }

    // Restore step 3 (encrypted only): prompt for the backup password, allow skipping or retrying.
    (state as? BackupViewModel.State.NeedPassword)?.let { need ->
        BackupPasswordDialog(
            title = if (need.retry) "Wrong backup password" else "Enter backup password",
            message = if (need.retry)
                "That password didn't match. Try again, or skip to restore everything except saved passwords."
            else
                "This backup's passwords are encrypted. Enter the backup password to restore them, or skip " +
                    "to restore everything else and re-enter passwords later.",
            confirmLabel = "Restore",
            skipLabel = "Skip (no passwords)",
            onConfirm = { pass -> vm.import(need.file, need.sections, pass) },
            onSkip = { vm.import(need.file, need.sections, null) },
            onDismiss = { vm.reset() },
        )
    }
}

/** A single-secret prompt with a confirm (encrypt/restore), a skip (no passwords) and cancel. */
@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    skipLabel: String,
    onConfirm: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var password by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(), contentAlignment = Alignment.Center) {
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
                OwnTVButton(skipLabel, onClick = onSkip, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(confirmLabel, onClick = { onConfirm(password) }, enabled = password.isNotBlank())
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

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(), contentAlignment = Alignment.Center) {
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
