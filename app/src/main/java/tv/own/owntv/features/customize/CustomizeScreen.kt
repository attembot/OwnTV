package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.Pin
import tv.own.owntv.features.profiles.PinDialog
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.Row2
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalActionSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Settings → Customize Categories & Items: hide / rename / reorder categories per section, and unhide
 * hidden channels, movies and series. Everything is per-profile and survives source re-syncs.
 * Optionally locked behind a PIN (set from this screen's top-right) so hidden items can't be
 * unhidden by someone else — the PIN is asked on every entry.
 */
@Composable
fun CustomizeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: CustomizeViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hiddenChannels by vm.hiddenChannels.collectAsStateWithLifecycle()
    val hideNewCategories by vm.hideNewCategories.collectAsStateWithLifecycle()
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val rangeEndKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val rangeSelectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val pinLock by vm.pinLock.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var showNewCatPicker by remember { mutableStateOf(false) }
    // The category whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // PIN gate: asked on every entry (state is per-composition, so leaving the screen re-locks it).
    var unlocked by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    // Set/Change/Remove PIN flow (only reachable once unlocked).
    var editingPin by remember { mutableStateOf<PinEdit?>(null) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPinStage by remember { mutableStateOf(false) }
    var pinMismatch by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val newCatRowFocus = remember { FocusRequester() } // "New category behavior" row — restore target after its picker
    // Opener row for whichever dialog (new-category picker, rename) is open — restored on close so
    // focus doesn't always jump back to the Live TV section chip.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    // CH+- key paging for the category list (same as Live/Movies/Series browse). The modifier consumes
    // the CH keys and moves focus itself, so it can never leak focus out of the list.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listPaneFocused by remember { mutableStateOf(false) }
    // Index (within `rows`) of the category row that currently holds focus — the paging anchor.
    var focusedCatIndex by remember { mutableIntStateOf(0) }
    // One FocusRequester per category row, so a jump can land focus on the target row.
    val rowFocusers = remember(rows) { rows.map { FocusRequester() } }
    // LazyColumn items before the category rows (hidden-items header + hidden rows + "Categories"
    // header) — the offset that maps a category index to its LazyColumn item index.
    val headerOffset = if (hiddenChannels.isNotEmpty()) hiddenChannels.size + 2 else 0

    // Wait for the stored lock state before showing anything (no unlocked flash).
    if (!pinLock.loaded) {
        Column(modifier.fillMaxSize().roundedPanel()) {}
        return
    }
    if (pinLock.pin != null && !unlocked) {
        Column(
            modifier = modifier.fillMaxSize().roundedPanel().padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Text("Customize Categories & Items", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "This screen is PIN-locked.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        PinDialog(
            title = if (pinError) "Wrong PIN — try again" else "Enter PIN",
            onSubmit = { entered ->
                if (entered == pinLock.pin || Pin.verify(entered, pinLock.pin)) {
                    unlocked = true
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onDismiss = onBack,
            compact = true,
        )
        return
    }

    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    // Restore focus to the row that opened a dialog (new-category picker / rename) when it closes —
    // previously closing either always landed on the Live TV section chip (firstFocus).
    LaunchedEffect(showNewCatPicker, renaming) {
        if (showNewCatPicker || renaming != null) return@LaunchedEffect
        dialogReturn?.let { opener ->
            kotlinx.coroutines.delay(60)
            runCatching { opener.requestFocus() }
        }
        dialogReturn = null
    }

    // While a span selection is in progress, Back cancels the selection instead of leaving the screen.
    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else onBack() }

    // Action pills on this panel frost with CARDS (the surface the panel rows use), not the DIALOGS
    // default. Covers the chip/move/unhide buttons; trailing Popups don't inherit this anyway.
    CompositionLocalProvider(LocalActionSurface provides GlassSurface.CARDS) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // Spatial D-pad entry from the sidebar would land mid-list — route it to the first chip.
            // onEnter fires only for directional entry from outside (internal moves don't re-trigger it).
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Customize Categories & Items",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Optional PIN lock on this screen, controlled from here. Per-profile and NOT exported in
            // backups. Only reachable once unlocked — to be here with a PIN set the user already
            // entered it, so changing or removing it needs no further verification.
            if (pinLock.pin == null) {
                OwnTVButton(
                    "🔒 Set PIN",
                    onClick = { firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.SET },
                )
            } else {
                OwnTVButton(
                    "🔒 Change PIN",
                    onClick = { firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.CHANGE },
                    style = OwnTVButtonStyle.SECONDARY,
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton(
                    "Remove lock",
                    onClick = { editingPin = PinEdit.REMOVE },
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Hide & unhide channels, movies and series, and rename or reorder categories for this " +
                "profile. Survives re-syncs.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Same preference for every section (Live/Movies/Series), so it lives above the section picker
        // rather than being buried in each section's category list.
        Row2(
            icon = OwnTVIcon.PLAYLIST,
            title = "New category behavior",
            desc = if (hideNewCategories) {
                "Hide - new categories added on re-sync are hidden by default"
            } else {
                "Show - new categories added on re-sync are shown by default"
            },
            chip = if (hideNewCategories) "Hide" else "Show",
            primaryChip = !hideNewCategories,
            chevron = true,
            onClick = { dialogReturn = newCatRowFocus; showNewCatPicker = true },
            modifier = Modifier.focusRequester(newCatRowFocus),
        )
        Spacer(Modifier.height(16.dp))

        // Section picker
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionChip("Live TV", section == MediaType.LIVE, Modifier.focusRequester(firstFocus)) { vm.selectSection(MediaType.LIVE) }
            SectionChip("Movies", section == MediaType.MOVIE) { vm.selectSection(MediaType.MOVIE) }
            SectionChip("Series", section == MediaType.SERIES) { vm.selectSection(MediaType.SERIES) }
        }
        Spacer(Modifier.height(16.dp))

        if (rangeAnchorKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        rangeMode == CustomizeViewModel.RangeMode.HIDE ->
                            "Span selection started. Press Show/Hide on the end item to select the span."
                        rangeEndKey == null ->
                            "Move span started. Press ⤒ ↑ ↓ ⤓ on the end item to move the whole span."
                        else ->
                            "${rangeSelectedKeys.size} categories selected. Keep pressing ⤒ ↑ ↓ ⤓ to move them together."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton("Cancel", onClick = { vm.cancelRange() }, style = OwnTVButtonStyle.SECONDARY)
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                // Pin vertical focus inside the category list: a held Up/Down that outruns the lazy
                // composition would otherwise escape to the section chips / sidebar. Every other browse
                // list in the app (Movies/Series/Live/Epg/Downloads) uses this same trap.
                .trapVerticalFocusExit()
                .onFocusChanged { listPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { listPaneFocused },
                    lastIndex = { rows.lastIndex },
                    currentTargetIndex = { focusedCatIndex },
                    onJumpToIndex = { idx ->
                        scope.jumpLazyListTo(listState, headerOffset + idx) {
                            rowFocusers.getOrNull(idx)?.let { runCatching { it.requestFocus() } }
                        }
                    },
                ),
        ) {
            // Hidden items of this section first (hidden via each section's long-press menu) — kept on
            // top so they're findable even when a provider has hundreds of categories below.
            if (hiddenChannels.isNotEmpty()) {
                item {
                    Text(
                        when (section) {
                            MediaType.LIVE -> "Hidden channels"
                            MediaType.MOVIE -> "Hidden movies"
                            else -> "Hidden series"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Unhide to bring an item back to the lists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(hiddenChannels.entries.sortedBy { it.value.lowercase() }, key = { "hid:${it.key}" }) { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label.ifBlank { key },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OwnTVButton("Unhide", onClick = { vm.unhideChannel(key) }, style = OwnTVButtonStyle.SECONDARY)
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text("Categories", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "No categories in this section yet — add a source first.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
                val inMoveRange = rangeAnchorKey != null && rangeMode == CustomizeViewModel.RangeMode.MOVE
                CategoryRow(
                    row = row,
                    inRangeMode = rangeAnchorKey != null && rangeMode == CustomizeViewModel.RangeMode.HIDE,
                    isInSpan = row.key in rangeSelectedKeys,
                    focusRequester = rowFocusers.getOrNull(index),
                    onRowFocused = { focusedCatIndex = index },
                    // While a move span is active every arrow acts on the whole block, not this row.
                    onMoveUp = { if (inMoveRange) vm.moveRange(row, CustomizeViewModel.MoveKind.UP) else vm.move(row, up = true) },
                    onMoveDown = { if (inMoveRange) vm.moveRange(row, CustomizeViewModel.MoveKind.DOWN) else vm.move(row, up = false) },
                    onMoveTop = { if (inMoveRange) vm.moveRange(row, CustomizeViewModel.MoveKind.TOP) else vm.moveToEdge(row, top = true) },
                    onMoveBottom = { if (inMoveRange) vm.moveRange(row, CustomizeViewModel.MoveKind.BOTTOM) else vm.moveToEdge(row, top = false) },
                    onMoveLongPress = { vm.beginMoveRange(row) },
                    onRename = { renaming = row },
                    onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                    onHideLongPress = { vm.beginRange(row) },
                    onPickRangeEnd = { if (row.key == rangeAnchorKey) vm.cancelRange() else rangeEnd = row },
                )
            }
        }
    }

    if (showNewCatPicker) {
        PickerDialog(
            title = "New category behavior",
            options = listOf("SHOW" to "Show", "HIDE" to "Hide"),
            selected = if (hideNewCategories) "HIDE" else "SHOW",
            onSelect = { value -> vm.setHideNewCategories(value == "HIDE"); showNewCatPicker = false },
            onDismiss = { showNewCatPicker = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = "Rename category",
            initial = row.displayName,
            hint = "Only for this profile. Leave blank to restore “${row.originalName}”.",
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    rangeEnd?.let { row ->
        val count = vm.keysInRange(row)?.size ?: 0
        RangeHideDialog(
            count = count,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    editingPin?.let { mode ->
        when (mode) {
            PinEdit.REMOVE -> PinConfirmDialog(
                title = "Remove PIN lock?",
                message = "The Customize screen will open without asking for a PIN again.",
                confirmLabel = "Remove",
                onConfirm = { vm.setPin(null); editingPin = null },
                onDismiss = { editingPin = null },
            )
            // SET and CHANGE are the same flow (enter a new PIN, then confirm). To reach here with a
            // PIN already set the user unlocked the screen, so neither verifies the old PIN.
            PinEdit.SET, PinEdit.CHANGE -> {
                if (confirmPinStage) {
                    key("confirm", pinMismatch) {
                        PinDialog(
                            title = if (pinMismatch) "PINs don't match — re-enter" else "Confirm new PIN",
                            onSubmit = { entered ->
                                if (entered == firstPin) {
                                    vm.setPin(entered); editingPin = null
                                } else {
                                    pinMismatch = true
                                }
                            },
                            onDismiss = { editingPin = null },
                            compact = true,
                        )
                    }
                } else {
                    key("first") {
                        PinDialog(
                            title = "Enter a new PIN",
                            onSubmit = { entered ->
                                firstPin = entered
                                confirmPinStage = true
                                pinMismatch = false
                            },
                            onDismiss = { editingPin = null },
                            compact = true,
                        )
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
}

/**
 * Confirms a range select: hide or show every category in the chosen span (or cancel). [count] is
 * the number of categories the span covers, inclusive.
 */
@Composable
private fun RangeHideDialog(count: Int, onHide: () -> Unit, onShow: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val hideFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hideFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
        ) {
            Text("Hide or show categories", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "$count ${if (count == 1) "category" else "categories"} selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Show", onClick = onShow, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton("Hide", onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { focused ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                selected -> colors.onPrimaryContainer
                focused -> colors.primary
                else -> colors.onSurface
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CategoryRow(
    row: CustomizeCatRow,
    inRangeMode: Boolean,
    isInSpan: Boolean,
    focusRequester: FocusRequester?,
    onRowFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveLongPress: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Tint every row in the span while a range is in progress, so the selected block is obvious.
            .background(if (isInSpan) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // CH+- paging: a focusGroup with a FocusRequester so a jump lands focus on this row's first
            // button; report up whenever any of the row's buttons gains focus (the paging anchor).
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.hidden) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.hidden || row.renamed || row.providerName != null) {
                Text(
                    buildString {
                        if (row.hidden) append("Hidden")
                        if (row.renamed) {
                            if (isNotEmpty()) append("  ·  ")
                            append("was “${row.originalName}”")
                        }
                        row.providerName?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Long-pressing any arrow anchors a move span; pressing an arrow on a second row picks the
        // span end and moves the whole block, and keeps it selected for further steps.
        OwnTVButton("⤒", onClick = onMoveTop, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↑", onClick = onMoveUp, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("⤓", onClick = onMoveBottom, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton(
            label = if (row.hidden) "Show" else "Hide",
            // Long-press anchors a range; a normal press picks the span end while a range is active,
            // otherwise it toggles just this category.
            onClick = { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onLongClick = onHideLongPress,
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}

/** PIN lock editing flow opened from the Customize header. */
private enum class PinEdit { SET, CHANGE, REMOVE }

/**
 * Generic Yes/No confirmation scrim used to remove the Customize PIN lock. Mirrors [RangeHideDialog]'s
 * structure so D-pad focus and the back button behave the same way as the other scrim dialogs here.
 */
@Composable
private fun PinConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 290.dp, corner = 16.dp, padding = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(confirmLabel, onClick = onConfirm, modifier = Modifier.focusRequester(confirmFocus))
            }
        }
    }
    }
}
