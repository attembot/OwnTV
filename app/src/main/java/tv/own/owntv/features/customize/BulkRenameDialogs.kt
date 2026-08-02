package tv.own.owntv.features.customize

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.RenameRules
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontTheme

/** Which field of a rule-builder row a nested picker is editing (focus returns to that row after). */
private enum class RuleField { TYPE, PLACEMENT, VALUE }

/** One preview line in the bulk-rename review. */
data class BulkPreviewRow(
    val key: String,
    val oldName: String,
    val newName: String,
    /** No rule changed this name — listed but not applicable. */
    val unchanged: Boolean,
    /** The rules would have blanked this name — rejected, original kept. */
    val blankRejected: Boolean,
    /** New name collides with another row's new name or an existing name in the section (warn only). */
    val duplicate: Boolean,
)

/** How many rows one bulk rename may carry (plan §2.7); above that the flow refuses. */
const val BULK_RENAME_MAX_ROWS = 2000

/**
 * One-shot bulk rename flow (issue #86): choice popup → rule builder / auto cleanup → review → apply.
 * The flow state (rules, options, preview) lives here — ViewModel state only, deliberately NOT
 * persisted: every session starts from scratch. Nothing is written until the review's Done, and
 * every accepted row lands in ONE [persist] call.
 */
class BulkRenameSession(
    private val scope: CoroutineScope,
    private val persist: suspend (Map<String, String>) -> Unit,
    private val restore: suspend (Set<String>) -> Unit,
    private val existingNames: suspend (selectedKeys: Set<String>) -> Set<String>,
) {
    enum class Screen { NONE, CHOICE, BUILDER, REVIEW, RESTORE_CONFIRM, REFUSED }

    private val _screen = MutableStateFlow(Screen.NONE)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** The rows being renamed: stable key → provider original name. */
    private val _entries = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val entries: StateFlow<List<Pair<String, String>>> = _entries.asStateFlow()

    private val _rules = MutableStateFlow<List<RenameRules.Rule>>(emptyList())
    val rules: StateFlow<List<RenameRules.Rule>> = _rules.asStateFlow()

    private val _options = MutableStateFlow(RenameRules.Options())
    val options: StateFlow<RenameRules.Options> = _options.asStateFlow()

    private val _preview = MutableStateFlow<List<BulkPreviewRow>>(emptyList())
    val preview: StateFlow<List<BulkPreviewRow>> = _preview.asStateFlow()

    /** Accepted rows (key → new name); written to the store in one call by [done]. */
    private val _accepted = MutableStateFlow<Map<String, String>>(emptyMap())
    val accepted: StateFlow<Map<String, String>> = _accepted.asStateFlow()

    private val _refusedMessage = MutableStateFlow<String?>(null)
    val refusedMessage: StateFlow<String?> = _refusedMessage.asStateFlow()

    fun start(entries: List<Pair<String, String>>) {
        if (entries.size > BULK_RENAME_MAX_ROWS) {
            _refusedMessage.value =
                "This category has too many items for one bulk rename — rename it in smaller spans."
            _screen.value = Screen.REFUSED
            return
        }
        _entries.value = entries
        _rules.value = emptyList()
        _options.value = RenameRules.Options()
        _preview.value = emptyList()
        _accepted.value = emptyMap()
        _screen.value = Screen.CHOICE
    }

    fun close() { _screen.value = Screen.NONE }

    // --- choice popup ---
    fun openBuilder() { _screen.value = Screen.BUILDER }
    fun autoCleanup() { _rules.value = RenameRules.autoCleanupRules(); computePreview() }
    fun requestRestore() { _screen.value = Screen.RESTORE_CONFIRM }
    fun confirmRestore() {
        scope.launch {
            restore(_entries.value.map { it.first }.toSet())
            close()
        }
    }

    // --- rule builder ---
    fun submitRules(rules: List<RenameRules.Rule>, options: RenameRules.Options) {
        _rules.value = rules
        _options.value = options
        computePreview()
    }
    fun backToChoice() { _screen.value = Screen.CHOICE }

    // --- review ---
    /** Applies the given pending rows (unchanged rows are never applicable) and removes them. */
    fun applyRows(keys: Set<String>) {
        val pending = _preview.value
        val next = _accepted.value.toMutableMap()
        pending.filter { it.key in keys && !it.unchanged }.forEach { next[it.key] = it.newName }
        _accepted.value = next
        _preview.value = pending.filterNot { it.key in keys }
    }
    fun applyAll() = applyRows(_preview.value.filter { !it.unchanged }.map { it.key }.toSet())
    fun declineRows(keys: Set<String>) { _preview.value = _preview.value.filterNot { it.key in keys } }
    fun declineAll() { _preview.value = emptyList() }
    fun editRules() { _screen.value = Screen.BUILDER }

    /** Writes every accepted row in ONE store transaction, then closes the flow. */
    fun done() {
        scope.launch {
            if (_accepted.value.isNotEmpty()) persist(_accepted.value)
            close()
        }
    }

    fun dismissRefused() { close() }

    /** Computes the preview on Dispatchers.Default — never the main thread (plan §2.5). */
    private fun computePreview() {
        scope.launch(Dispatchers.Default) {
            // Rows the user already applied stay applied when they choose "Edit rules". Re-running
            // them through a new rule set would make them reappear while their old accepted value
            // still remained queued for Done, which is both misleading and impossible to decline.
            val entries = _entries.value.filterNot { (key, _) -> key in _accepted.value }
            val rules = _rules.value
            val options = _options.value
            // Exclude the selected rows' own stored overrides. Otherwise re-applying the same
            // proposed name to an already-renamed row incorrectly warns that it collides with
            // itself; only names belonging to other rows are genuine existing-name collisions.
            // Exclude every row in this session from the external-name set. Rows accepted before
            // "Edit rules" contribute their accepted final names below; pending rows contribute
            // either a new-name batch candidate or (when unchanged) their original name. Counting
            // selected originals unconditionally produced false duplicate warnings for names that
            // the same batch was replacing.
            val existingOutsideSession = existingNames(_entries.value.map { it.first }.toSet())
            val rows = entries.map { (key, old) ->
                val raw = RenameRules.applyRaw(old, rules, options)
                when {
                    raw.isBlank() -> BulkPreviewRow(key, old, "", unchanged = true, blankRejected = true, duplicate = false)
                    raw == old -> BulkPreviewRow(key, old, old, unchanged = true, blankRejected = false, duplicate = false)
                    else -> BulkPreviewRow(key, old, raw, unchanged = false, blankRejected = false, duplicate = false)
                }
            }
            // Duplicates warn, never block: a new name that repeats inside the batch or already
            // exists in the section (a provider original or another rename).
            val newNameCounts = rows.filter { !it.unchanged }.groupingBy { it.newName }.eachCount()
            val occupiedNames = existingOutsideSession + _accepted.value.values +
                rows.filter { it.unchanged }.map { it.oldName }
            val batchDupKeys = rows
                .filter { !it.unchanged && (newNameCounts[it.newName] ?: 0) > 1 }
                .map { it.key }
                .toSet()
            _preview.value = rows.map { r ->
                if (r.duplicate) r
                else if (r.key in batchDupKeys || (!r.unchanged && r.newName in occupiedNames)) r.copy(duplicate = true)
                else r
            }
            _screen.value = Screen.REVIEW
        }
    }
}

/**
 * Hosts whichever bulk-rename dialog the session's screen calls for, and restores D-pad focus to
 * [returnFocus] (the row/pill that opened the flow) once the whole flow closes.
 */
@Composable
fun BulkRenameFlow(session: BulkRenameSession, returnFocus: FocusRequester? = null) {
    val screen by session.screen.collectAsStateWithLifecycle()
    var wasOpen by remember { mutableStateOf(false) }
    // The opener row can change while the flow is idle (a second span started) — always restore to
    // the LATEST row, not the one from the composition that saw the flow open.
    val currentReturnFocus by rememberUpdatedState(returnFocus)
    LaunchedEffect(screen) {
        if (screen != BulkRenameSession.Screen.NONE) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            currentReturnFocus?.let { opener ->
                kotlinx.coroutines.delay(60)
                runCatching { opener.requestFocus() }
            }
        }
    }
    when (screen) {
        BulkRenameSession.Screen.CHOICE -> BulkRenameChoicePopup(session)
        BulkRenameSession.Screen.BUILDER -> BulkRuleBuilderDialog(session)
        BulkRenameSession.Screen.REVIEW -> BulkReviewDialog(session)
        BulkRenameSession.Screen.RESTORE_CONFIRM -> BulkRestoreConfirmDialog(session)
        BulkRenameSession.Screen.REFUSED -> BulkRefusedDialog(session)
        BulkRenameSession.Screen.NONE -> Unit
    }
}

/** [mockup frame 2] The four-way choice that opens a bulk rename. */
@Composable
private fun BulkRenameChoicePopup(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val count = session.entries.collectAsStateWithLifecycle().value.size
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { addFocus.requestFocus() } }
    BackHandler { session.close() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Bulk rename", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Text(
                "$count ${if (count == 1) "name" else "names"} selected. Add rules, or clean them up automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton("✎ Add rule", onClick = { session.openBuilder() }, modifier = Modifier.fillMaxWidth().focusRequester(addFocus))
            OwnTVButton("✨ Auto cleanup", onClick = { session.autoCleanup() }, modifier = Modifier.fillMaxWidth())
            OwnTVButton("↺ Restore original names", onClick = { session.requestRestore() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Cancel", onClick = { session.close() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
    }
    }
}

/** [mockup frame 3] Rule builder: rows of type/placement/value/✕, options checkboxes, Apply. */
@Composable
private fun BulkRuleBuilderDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val rules = session.rules.collectAsStateWithLifecycle().value
    val options = session.options.collectAsStateWithLifecycle().value
    // Draft rows edited locally until Apply commits them to the session (and starts the preview).
    var draft by remember { mutableStateOf(rules) }
    var trim by remember { mutableStateOf(options.trimLeftovers) }
    var ignoreCase by remember { mutableStateOf(options.ignoreCase) }
    var error by remember { mutableStateOf<String?>(null) }
    // Nested picker target: (row index, field). The row's focus is restored when it closes.
    var editing by remember { mutableStateOf<Pair<Int, RuleField>?>(null) }
    var pickerRow by remember { mutableIntStateOf(-1) }
    var pendingRowFocus by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val addRuleFocus = remember { FocusRequester() }
    val rowFocusers = remember(draft.size) { List(draft.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { (rowFocusers.firstOrNull() ?: addRuleFocus).requestFocus() }
    }
    // Adding/deleting a rule disposes and rebuilds row focus nodes. Hand focus to the new row or the
    // Add button explicitly instead of relying on spatial fallback through a full-screen scrim.
    LaunchedEffect(draft.size, pendingRowFocus) {
        if (pendingRowFocus != Int.MIN_VALUE) {
            kotlinx.coroutines.delay(60)
            val target = rowFocusers.getOrNull(pendingRowFocus) ?: addRuleFocus
            runCatching { target.requestFocus() }
            pendingRowFocus = Int.MIN_VALUE
        }
    }
    // Focus the row that opened a nested picker once that picker closes (a scrim teardown can leave
    // focus in limbo otherwise).
    LaunchedEffect(editing) {
        if (editing == null && pickerRow >= 0) {
            val row = pickerRow
            pickerRow = -1
            kotlinx.coroutines.delay(60)
            runCatching { rowFocusers.getOrNull(row)?.requestFocus() }
        } else if (editing != null) {
            pickerRow = editing!!.first
        }
    }
    BackHandler { session.backToChoice() }

    fun submit() {
        val out = mutableListOf<RenameRules.Rule>()
        for (rule in draft) {
            if (rule.pattern != null) {
                // Auto-cleanup rules are internal regexes, but remain visible/removable in this
                // editor so "Edit rules" genuinely tunes the generated preset (plan §2.6).
                out += rule
            } else if (rule.action == RenameRules.Action.ADD) {
                if (RenameRules.tokensOf(rule).size != 1) {
                    error = "“Add” needs exactly one value — semicolons are for Remove rules."
                    return
                }
                out += rule
            } else if (RenameRules.tokensOf(rule).isNotEmpty()) {
                out += rule
            }
        }
        if (out.isEmpty()) {
            error = "Add at least one rule."
            return
        }
        error = null
        session.submitRules(out, RenameRules.Options(trimLeftovers = trim, ignoreCase = ignoreCase))
    }

    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 760.dp, padding = 24.dp)) {
            Text("Bulk rename rules", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Rules run top to bottom. Separate multiple values with “;” — Add takes exactly one.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.forEachIndexed { i, rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .then(if (i < rowFocusers.size) Modifier.focusRequester(rowFocusers[i]) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OwnTVButton(
                            label = "${rule.action.name} ▾",
                            onClick = { editing = i to RuleField.TYPE },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.width(120.dp),
                        )
                        OwnTVButton(
                            label = "${rule.placement.name} ▾",
                            onClick = { editing = i to RuleField.PLACEMENT },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.width(120.dp),
                        )
                        OwnTVButton(
                            label = rule.editorLabel ?: rule.value.ifBlank { "value;value;value" },
                            onClick = { editing = i to RuleField.VALUE },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        OwnTVButton(
                            "✕",
                            onClick = {
                                val next = draft.toMutableList().apply { removeAt(i) }
                                draft = next
                                pendingRowFocus = if (next.isEmpty()) -1 else i.coerceAtMost(next.lastIndex)
                            },
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                    }
                }
                OwnTVButton(
                    "＋ Add another rule",
                    onClick = {
                        pendingRowFocus = draft.size
                        draft = draft + RenameRules.Rule(RenameRules.Action.ADD, RenameRules.Placement.PREFIX, "")
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth().focusRequester(addRuleFocus),
                )
            }
            Spacer(Modifier.height(12.dp))

            // The two options as toggle chips (Trim leftover spaces / Ignore case, both default ON).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OwnTVButton(
                    label = if (trim) "✓ Trim leftover spaces" else "Trim leftover spaces",
                    onClick = { trim = !trim },
                    style = if (trim) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
                OwnTVButton(
                    label = if (ignoreCase) "✓ Ignore upper/lower case" else "Ignore upper/lower case",
                    onClick = { ignoreCase = !ignoreCase },
                    style = if (ignoreCase) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }

            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.favorite)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = { session.backToChoice() }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Apply", onClick = { submit() })
            }
        }
    }

    when (editing?.second) {
        RuleField.TYPE -> PickerDialog(
            title = "Rule type",
            options = listOf("ADD" to "Add", "REMOVE" to "Remove"),
            selected = draft[editing!!.first].action.name,
            onSelect = { value ->
                runCatching { RenameRules.Action.valueOf(value) }.getOrNull()?.let { action ->
                    val i = editing!!.first
                    draft = draft.toMutableList().apply {
                        set(i, get(i).copy(action = action, pattern = null, editorLabel = null))
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.PLACEMENT -> PickerDialog(
            title = "Where",
            options = listOf("PREFIX" to "Before (prefix)", "SUFFIX" to "After (suffix)"),
            selected = draft[editing!!.first].placement.name,
            onSelect = { value ->
                runCatching { RenameRules.Placement.valueOf(value) }.getOrNull()?.let { placement ->
                    val i = editing!!.first
                    draft = draft.toMutableList().apply {
                        set(i, get(i).copy(placement = placement, pattern = null, editorLabel = null))
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.VALUE -> TextInputDialog(
            title = "Rule value",
            initial = draft[editing!!.first].value,
            hint = "Separate multiple values with “;” — Add takes exactly one.",
            onConfirm = { value ->
                val i = editing!!.first
                draft = draft.toMutableList().apply {
                    set(i, get(i).copy(value = value, pattern = null, editorLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
    }
}

/** [mockup frame 4] Review modeled on EpgMatchReviewDialog: left rows, right centred bulk column. */
@Composable
private fun BulkReviewDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val rows = session.preview.collectAsStateWithLifecycle().value
    BackHandler { session.done() }
    // Focus lands on the first APPLICABLE row (an unchanged first row has no buttons); if nothing
    // can be applied, it lands on "Apply all" instead. Mirrors EpgMatchReviewDialog's first-row focus.
    val firstChanged = rows.indexOfFirst { !it.unchanged }
    val firstApplyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstApplyFocus.requestFocus() } }

    // Popup(focusable=true) creates a hard focus boundary — applying/declining removes rows from the
    // LazyColumn, but focus stays inside instead of escaping to the screen behind (same pattern and
    // reason as EpgMatchReviewDialog).
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { session.done() }) {
        // Dense TV review: one-third smaller than the previous 0.75 scale.
        PopupFontTheme(fontScale = 0.50f) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        // scroll = false: this column holds a LazyColumn, which manages its own scrolling.
                Column(Modifier.dialogPanel(width = 680.dp, corner = 16.dp, padding = 12.dp, scroll = false)) {
            Text("Review renames", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Nothing is written until you apply a row — Done writes everything applied in one step.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            val listHeight = (LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(160.dp, 320.dp)
            Row(Modifier.fillMaxWidth()) {
                // Left: the pending rows. Rows are removed on Apply/Decline; keys keep the list stable.
                LazyColumn(
                    Modifier.weight(1f).height(listHeight),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { index, r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(colors.surface).padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.oldName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.unchanged) {
                                    Text(
                                        if (r.blankRejected) "blank name rejected — original kept"
                                        else "unchanged — no rule matched",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        "→ ${r.newName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (r.duplicate) colors.favorite else colors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (r.duplicate) {
                                        Text(
                                            "duplicate name — another item already uses it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.favorite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (!r.unchanged) {
                                FocusableSurface(
                                    onClick = { session.applyRows(setOf(r.key)) },
                                    modifier = if (index == firstChanged) Modifier.focusRequester(firstApplyFocus) else Modifier,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedContainerColor = colors.primaryContainer,
                                    contentAlignment = Alignment.Center,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ -> Text("Apply", style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                                FocusableSurface(
                                    onClick = { session.declineRows(setOf(r.key)) },
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedContainerColor = colors.surfaceContainerHigh,
                                    contentAlignment = Alignment.Center,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ -> Text("Decline", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Right: bulk actions, VERTICALLY CENTRED so D-pad right from any middle row lands
                // here without scrolling (owner requirement for this dialog).
                Column(
                    Modifier.width(132.dp).height(listHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${rows.count { !it.unchanged }} will change",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${rows.count { it.unchanged }} unchanged",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${rows.count { it.duplicate }} duplicates",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.favorite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OwnTVButton(
                        "Apply all", onClick = { session.applyAll() }, icon = OwnTVIcon.PLAY,
                        modifier = Modifier.fillMaxWidth().then(if (firstChanged == -1) Modifier.focusRequester(firstApplyFocus) else Modifier),
                        compact = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton("Decline all", onClick = { session.declineAll() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton("✎ Edit rules", onClick = { session.editRules() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton("Done", onClick = { session.done() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                }
            }
        }
    }
    } // PopupFontTheme
    } // Popup
}

/** Confirm before ↺ Restore original names — the only undo for a bulk apply, so it's not optional. */
@Composable
private fun BulkRestoreConfirmDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { restoreFocus.requestFocus() } }
    BackHandler { session.backToChoice() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 420.dp, padding = 24.dp)) {
            Text("Restore original names?", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Every selected name goes back to the provider's original. Only this selection is affected.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = { session.backToChoice() }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Restore", onClick = { session.confirmRestore() }, modifier = Modifier.focusRequester(restoreFocus))
            }
        }
    }
    }
}

/** The > [BULK_RENAME_MAX_ROWS] refusal (plan §2.7): a clear message, nothing else. */
@Composable
private fun BulkRefusedDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val message = session.refusedMessage.collectAsStateWithLifecycle().value
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { okFocus.requestFocus() } }
    BackHandler { session.dismissRefused() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 420.dp, padding = 24.dp)) {
            Text("Too many items", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(message ?: "", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("OK", onClick = { session.dismissRefused() }, modifier = Modifier.focusRequester(okFocus))
            }
        }
    }
    }
}
