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
import androidx.compose.foundation.layout.heightIn
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
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.data.SettingsRepository
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
    val currentSort by vm.currentSort.collectAsStateWithLifecycle()
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val rangeEndKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val rangeSelectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val pinLock by vm.pinLock.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var showNewCatPicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
    // The category whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // ＋ New category (issue #87): name prompt, then the empty combined category appears in the list.
    var creatingCategory by remember { mutableStateOf(false) }
    // Custom category pending deletion — confirmed in a scrim before anything is removed (plan §3.5).
    var deletingCategory by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // PIN gate: asked on every entry (state is per-composition, so leaving the screen re-locks it).
    var unlocked by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    // Set/Change/Remove PIN flow (only reachable once unlocked).
    var editingPin by remember { mutableStateOf<PinEdit?>(null) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPinStage by remember { mutableStateOf(false) }
    var pinMismatch by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val newCategoriesFocus = remember { FocusRequester() }
    val newCatPillFocus = remember { FocusRequester() } // "＋ New category" pill — restore target after its name prompt
    val pinFocus = remember { FocusRequester() }
    val removePinFocus = remember { FocusRequester() }
    var itemsReturnKey by remember { mutableStateOf<String?>(null) }
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
    // Restore focus to the row that opened a dialog (new-category picker / rename / new-category
    // prompt / delete confirm) when it closes — previously closing either always landed on the Live
    // TV section chip (firstFocus).
    LaunchedEffect(showNewCatPicker, showSortPicker, renaming, creatingCategory, deletingCategory, rangeEnd, editingPin) {
        if (showNewCatPicker || showSortPicker || renaming != null || creatingCategory ||
            deletingCategory != null || rangeEnd != null || editingPin != null
        ) return@LaunchedEffect
        dialogReturn?.let { opener ->
            kotlinx.coroutines.delay(60)
            runCatching { opener.requestFocus() }
        }
        dialogReturn = null
    }
    // The category list is disposed while its item screen is open. Back recreates the row, so
    // explicitly return to the same category name instead of letting focus escape to Settings.
    LaunchedEffect(selectedCategory, rows) {
        if (selectedCategory == null) {
            val key = itemsReturnKey ?: return@LaunchedEffect
            val index = rows.indexOfFirst { it.key == key }
            if (index >= 0) {
                listState.scrollToItem(headerOffset + index)
                kotlinx.coroutines.delay(80)
                runCatching { rowFocusers.getOrNull(index)?.requestFocus() }
            } else {
                runCatching { firstFocus.requestFocus() }
            }
            itemsReturnKey = null
        }
    }

    // While a span selection is in progress, Back cancels the selection instead of leaving the screen.
    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else if (selectedCategory != null) vm.closeItems() else onBack() }

    // Items screen — shown when the user presses OK on a category name. The items screen covers the
    // full panel including the dialogs, so when it's up, render nothing else.
    if (selectedCategory != null) {
        CustomizeItemsScreen(onBack = { vm.closeItems() })
    } else {
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
        Text(
            "Customize Categories & Items",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Hide & unhide channels, movies and series, and rename or reorder categories for this " +
                "profile. Survives re-syncs.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // One compact strip, matching the agreed mockup: section tabs left, actions right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionChip("Live TV", section == MediaType.LIVE, Modifier.focusRequester(firstFocus)) { vm.selectSection(MediaType.LIVE) }
            Spacer(Modifier.width(10.dp))
            SectionChip("Movies", section == MediaType.MOVIE) { vm.selectSection(MediaType.MOVIE) }
            Spacer(Modifier.width(10.dp))
            SectionChip("Series", section == MediaType.SERIES) { vm.selectSection(MediaType.SERIES) }
            Spacer(Modifier.weight(1f))
            // Sort pill — reuses the same per-section sort mode that Browse uses.
            OwnTVButton(
                label = "Sort: ${if (currentSort == SettingsRepository.SortMode.PLAYLIST) "Provider" else if (currentSort == SettingsRepository.SortMode.ALPHA) "A–Z" else currentSort.name} ▾",
                onClick = { dialogReturn = sortFocus; showSortPicker = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(sortFocus),
            )
            Spacer(Modifier.width(10.dp))
            // New categories pill — same setting as the old Row2, now compact.
            OwnTVButton(
                label = "New categories: ${if (hideNewCategories) "Hide" else "Show"} ▾",
                onClick = { dialogReturn = newCategoriesFocus; showNewCatPicker = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(newCategoriesFocus),
            )
            Spacer(Modifier.width(10.dp))
            // ＋ New category (issue #87) — creates an empty combined category; items are moved into
            // it from the browse context menus or this screen's items view.
            OwnTVButton(
                label = "＋ New category",
                onClick = { dialogReturn = newCatPillFocus; creatingCategory = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(newCatPillFocus),
            )
            Spacer(Modifier.width(10.dp))
            // Optional PIN lock, restyled as compact pills instead of the old full-width block.
            if (pinLock.pin == null) {
                OwnTVButton(
                    "🔒 Set PIN",
                    onClick = {
                        dialogReturn = pinFocus
                        firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.SET
                    },
                    modifier = Modifier.focusRequester(pinFocus),
                )
            } else {
                OwnTVButton(
                    "🔒 Change PIN",
                    onClick = {
                        dialogReturn = pinFocus
                        firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.CHANGE
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(pinFocus),
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton(
                    "Remove lock",
                    onClick = { dialogReturn = removePinFocus; editingPin = PinEdit.REMOVE },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(removePinFocus),
                )
            }
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
                        rangeMode == SpanSelector.Mode.HIDE ->
                            "Span selection started. Press Show/Hide on the end item to select the span."
                        rangeMode == SpanSelector.Mode.RENAME ->
                            "Rename span started. Press Rename on the end item to select the span."
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
                itemsIndexed(
                    hiddenChannels.entries.sortedBy { it.value.lowercase() },
                    key = { _, entry -> "hid:${entry.key}" },
                ) { hiddenIndex, (key, label) ->
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
                        OwnTVButton(
                            "Unhide",
                            onClick = { vm.unhideChannel(key) },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = if (hiddenIndex == 0) {
                                Modifier.focusProperties { up = firstFocus }
                            } else Modifier,
                        )
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
                val inMoveRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.MOVE
                val inRenameRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.RENAME
                CategoryRow(
                    row = row,
                    inRangeMode = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.HIDE,
                    inRenameRange = inRenameRange,
                    isInSpan = row.key in rangeSelectedKeys,
                    focusRequester = rowFocusers.getOrNull(index),
                    upFocusRequester = firstFocus.takeIf { index == 0 && hiddenChannels.isEmpty() },
                    onRowFocused = { focusedCatIndex = index },
                    // While a move span is active every arrow acts on the whole block, not this row.
                    onMoveUp = { if (inMoveRange) vm.moveRange(row, MoveKind.UP) else vm.move(row, up = true) },
                    onMoveDown = { if (inMoveRange) vm.moveRange(row, MoveKind.DOWN) else vm.move(row, up = false) },
                    onMoveTop = { if (inMoveRange) vm.moveRange(row, MoveKind.TOP) else vm.moveToEdge(row, top = true) },
                    onMoveBottom = { if (inMoveRange) vm.moveRange(row, MoveKind.BOTTOM) else vm.moveToEdge(row, top = false) },
                    onMoveLongPress = { vm.beginMoveRange(row) },
                    // Long-press Rename anchors a rename span; while one is active, pressing Rename on
                    // a second row opens the bulk rename flow over the whole span. On the anchor row
                    // itself it cancels, mirroring the Show/Hide span behavior.
                    // Restore focus to this row's first button when the rename dialog (or its delete
                    // confirm) closes — same restore target as the span-rename path below.
                    onRename = { dialogReturn = rowFocusers.getOrNull(index); renaming = row },
                    onRenameLongPress = { vm.beginRenameRange(row) },
                    onPickRenameEnd = {
                        if (row.key == rangeAnchorKey) {
                            vm.cancelRange()
                        } else {
                            dialogReturn = rowFocusers.getOrNull(index)
                            // No active span (anchor vanished?) — fall back to the single rename.
                            if (vm.finishRenameRange(row) == null) renaming = row
                        }
                    },
                    onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                    onHideLongPress = { vm.beginRange(row) },
                    onPickRangeEnd = {
                        if (row.key == rangeAnchorKey) vm.cancelRange()
                        else {
                            dialogReturn = rowFocusers.getOrNull(index)
                            rangeEnd = row
                        }
                    },
                    onOpenItems = { itemsReturnKey = row.key; vm.openItems(row) },
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

    if (showSortPicker) {
        PickerDialog(
            title = "Sort categories",
            options = listOf("PLAYLIST" to "Provider", "ALPHA" to "A–Z"),
            selected = currentSort.name,
            onSelect = { value ->
                val mode = runCatching { SettingsRepository.SortMode.valueOf(value) }.getOrNull()
                if (mode != null) vm.setSort(mode)
                showSortPicker = false
            },
            onDismiss = { showSortPicker = false },
        )
    }

    // Custom category pending deletion (opened from the rename dialog's Delete) — confirmed first,
    // plan §3.5: "It must never touch content."
    deletingCategory?.let { row ->
        PinConfirmDialog(
            title = "Delete “${row.displayName}”?",
            message = "The combined category is removed. Its items stay in their original categories.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteCustomCategory(row)
                deletingCategory = null
                renaming = null
            },
            onDismiss = { deletingCategory = null },
        )
    }

    renaming?.let { row ->
        val isCustom = row.categoryId == null
        TextInputDialog(
            title = if (isCustom) "Rename or delete category" else "Rename category",
            initial = row.displayName,
            hint = "Only for this profile. Leave blank to restore “${row.originalName}”.",
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
            // Custom combined categories can be deleted from their own rename dialog (plan §3.5);
            // the confirm scrim above runs before anything is removed.
            onDelete = if (isCustom) { { deletingCategory = row; renaming = null } } else null,
        )
    }

    // ＋ New category (issue #87): name the empty combined category, then it appears in the list.
    if (creatingCategory) {
        TextInputDialog(
            title = "New category",
            hint = "A combined category for this profile — move channels, movies or series into it.",
            confirmLabel = "Create",
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
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

    // Bulk rename (issue #86): choice popup, rule builder, review, restore-confirm, refusal.
    // Its popups restore D-pad focus to the row that opened the flow when the whole flow closes.
    BulkRenameFlow(vm.bulk, returnFocus = dialogReturn)

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
    } // else (selectedCategory == null)
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
    inRenameRange: Boolean,
    isInSpan: Boolean,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onRowFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveLongPress: () -> Unit,
    onRename: () -> Unit,
    onRenameLongPress: () -> Unit,
    onPickRenameEnd: () -> Unit,
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
    onOpenItems: () -> Unit,
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
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name as a focusable button — OK opens the category's items; D-pad walks names one press per
        // row. Right steps into move arrows, Rename and Hide/Show.
        FocusableSurface(
            onClick = onOpenItems,
            modifier = Modifier
                .weight(1f)
                // Match the action pills' normal 12.dp + label height so the name focus target is
                // not visibly shorter than the move/Rename/Hide controls beside it.
                .heightIn(min = 42.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester }
                    else Modifier,
                ),
            // Use the same compact pill treatment as the action buttons to the right.
            shape = RoundedCornerShape(50),
            focusedScale = 1f,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = colors.primaryContainer,
            surface = GlassSurface.CARDS,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        row.hidden -> colors.onSurfaceVariant
                        focused -> colors.onPrimaryContainer
                        else -> colors.onSurface
                    },
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
        // Long-press anchors a rename span; a normal press picks the span end while one is active,
        // otherwise it opens the single-row rename dialog.
        OwnTVButton(
            "Rename",
            onClick = { if (inRenameRange) onPickRenameEnd() else onRename() },
            onLongClick = onRenameLongPress,
            style = OwnTVButtonStyle.SECONDARY,
        )
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
