@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.features.settings.data.SettingsRepository

/** One category row in the Customize screen (hidden rows stay visible here, marked, for unhiding).
 *  [providerName] is null when only one source is in scope. */
data class CustomizeCatRow(
    val key: String,
    val originalName: String,
    val displayName: String,
    val hidden: Boolean,
    val renamed: Boolean,
    val providerName: String? = null,
)

/**
 * Drives Settings → Customize: per-profile hide / rename / reorder of categories (Live/Movies/Series)
 * and the unhide list for hidden Live channels. All edits live in [CustomizationStore] (DataStore),
 * so they survive re-syncs and never touch the DB schema.
 */
class CustomizeViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val customize: CustomizationStore,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
        fun sourceIdsFor(type: MediaType): List<Long> = ActiveProfileSources(profileId, sources).sourceIdsFor(type)
    }

    // Observe the active profile's sources reactively so adding/removing a playlist refreshes the
    // customize lists immediately. Goes through activeProfileSources (like the Browse screens) so the
    // chosen "active playlist" filter also applies HERE: with playlist A selected you only see A's
    // categories, not B's. "All playlists" (default -1) still shows the merged set. Per-section Off
    // flags also hide that source's categories for the selected section.
    private val ctx: StateFlow<Ctx> = activeProfileSources(settings, sourceDao)
        .map { aps -> Ctx(aps.profileId, aps.sources) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    /** Optional PIN lock on this screen (per profile). [loaded]=false while DataStore is still read,
     *  so the screen never flashes unlocked content before the lock state is known. */
    data class PinLock(val loaded: Boolean = false, val pin: String? = null)

    val pinLock: StateFlow<PinLock> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(PinLock(loaded = true))
            else settings.customizePin(c.profileId).map { PinLock(loaded = true, pin = it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PinLock())

    /** Set (or with null: remove) the PIN lock for this profile. */
    fun setPin(pin: String?) {
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setCustomizePin(pid, pin) }
    }

    private val _section = MutableStateFlow(MediaType.LIVE)
    val section: StateFlow<MediaType> = _section.asStateFlow()

    /** What a span selection in progress will do once its end is picked. */
    enum class RangeMode { HIDE, MOVE }

    /** Which way a move (single row or whole span) shifts the affected rows. */
    enum class MoveKind { UP, DOWN, TOP, BOTTOM }

    // Range select: key of the anchor category (the long-pressed Hide/move button), or null when no
    // range is in progress. The UI highlights the anchor and shows a hint while this is set.
    private val _rangeAnchorKey = MutableStateFlow<String?>(null)
    val rangeAnchorKey: StateFlow<String?> = _rangeAnchorKey.asStateFlow()

    private val _rangeMode = MutableStateFlow(RangeMode.HIDE)
    val rangeMode: StateFlow<RangeMode> = _rangeMode.asStateFlow()

    // MOVE spans only: once the end has been picked the block stays selected, so the user can keep
    // pressing ↑/↓/⤒/⤓ to walk the whole block instead of re-selecting it for every step.
    private val _rangeEndKey = MutableStateFlow<String?>(null)
    val rangeEndKey: StateFlow<String?> = _rangeEndKey.asStateFlow()

    fun selectSection(type: MediaType) {
        _section.value = type
        // A pending range belongs to the section it was started in — switching sections cancels it.
        cancelRange()
    }

    /** Categories of the selected section, in their customized order, including hidden ones. */
    val rows: StateFlow<List<CustomizeCatRow>> = combine(_section, ctx) { s, c -> s to c }
        .flatMapLatest { (type, c) ->
            if (c.profileId < 0) flowOf(emptyList())
            else {
                val sourceIds = c.sourceIdsFor(type)
                combine(
                    categoryDao.observe(sourceIds, type),
                    customize.observe(c.profileId, type),
                    sourceDao.observeForProfile(c.profileId),
                ) { cats, cust, sources ->
                    val providerNames = sources.associate { it.id to it.name }.takeIf { sourceIds.size > 1 }
                    val orderIndex = cust.categoryOrder.withIndex().associate { (i, k) -> k to i }
                    val keyed = cats.map { it to CustomizeKeys.category(it) }
                    val (pinned, rest) = keyed.partition { (_, k) -> k in orderIndex }
                    (pinned.sortedBy { (_, k) -> orderIndex.getValue(k) } + rest).map { (cat, key) ->
                        CustomizeCatRow(
                            key = key,
                            originalName = cat.name,
                            displayName = cust.categoryNames[key] ?: cat.name,
                            hidden = key in cust.hiddenCategories,
                            renamed = key in cust.categoryNames,
                            providerName = providerNames?.get(cat.sourceId),
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hidden items of the selected section (key → label) so they can be unhidden from here. */
    val hiddenChannels: StateFlow<Map<String, String>> = combine(ctx, _section) { c, s -> c to s }
        .flatMapLatest { (c, s) ->
            if (c.profileId < 0) flowOf(emptyMap())
            else customize.observe(c.profileId, s).map { it.hiddenItems }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether a category this provider adds on a future resync should be hidden automatically, for
     *  this profile — same across Live/Movies/Series, so it doesn't follow [_section]. */
    val hideNewCategories: StateFlow<Boolean> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(false) else settings.hideNewCategoriesDefault(c.profileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setHideNewCategories(hidden: Boolean) {
        val pid = ctx.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setHideNewCategoriesDefault(pid, hidden) }
    }

    fun setCategoryHidden(row: CustomizeCatRow, hidden: Boolean) {
        viewModelScope.launch {
            customize.setCategoryHidden(ctx.value.profileId, _section.value, row.key, hidden)
        }
    }

    /** Blank name restores the provider's original. */
    fun renameCategory(row: CustomizeCatRow, name: String?) {
        viewModelScope.launch {
            customize.renameCategory(ctx.value.profileId, _section.value, row.key, name)
        }
    }

    /** Moves a category one step up/down and persists the full resulting order. */
    fun move(row: CustomizeCatRow, up: Boolean) =
        moveSingle(row, if (up) MoveKind.UP else MoveKind.DOWN)

    /** Jumps a category straight to the top or bottom of the list and persists the new order. */
    fun moveToEdge(row: CustomizeCatRow, top: Boolean) =
        moveSingle(row, if (top) MoveKind.TOP else MoveKind.BOTTOM)

    private fun moveSingle(row: CustomizeCatRow, kind: MoveKind) {
        val index = rows.value.indexOfFirst { it.key == row.key }
        if (index < 0) return
        moveBlock(index, index, kind)
    }

    /**
     * Shifts the contiguous block of rows [lo]..[hi] as one unit and persists the resulting order.
     * The block keeps its internal order; a move that would run off either end is a no-op.
     */
    private fun moveBlock(lo: Int, hi: Int, kind: MoveKind) {
        val current = rows.value
        if (lo < 0 || hi > current.lastIndex || lo > hi) return
        val target = when (kind) {
            MoveKind.UP -> if (lo == 0) return else lo - 1
            MoveKind.DOWN -> if (hi == current.lastIndex) return else lo + 1
            MoveKind.TOP -> if (lo == 0) return else 0
            MoveKind.BOTTOM -> if (hi == current.lastIndex) return else current.size - (hi - lo + 1)
        }
        val block = current.subList(lo, hi + 1).toList()
        val reordered = current.toMutableList().apply {
            subList(lo, hi + 1).clear()
            addAll(target, block)
        }
        viewModelScope.launch {
            customize.setCategoryOrder(ctx.value.profileId, _section.value, reordered.map { it.key })
        }
    }

    fun unhideChannel(key: String) {
        viewModelScope.launch {
            customize.setItemHidden(ctx.value.profileId, _section.value, key, "", false)
        }
    }

    // --- range (span) select: long-press a Hide or move button to anchor, press another to set the
    // end. A HIDE span then hides/shows the whole block; a MOVE span walks it up/down/top/bottom. ---

    /** Begins a hide/show range at [row] (its Hide button was long-pressed). */
    fun beginRange(row: CustomizeCatRow) {
        _rangeMode.value = RangeMode.HIDE
        _rangeEndKey.value = null
        _rangeAnchorKey.value = row.key
    }

    /** Begins a move range at [row] (one of its ⤒ ↑ ↓ ⤓ buttons was long-pressed). */
    fun beginMoveRange(row: CustomizeCatRow) {
        _rangeMode.value = RangeMode.MOVE
        _rangeEndKey.value = null
        _rangeAnchorKey.value = row.key
    }

    fun cancelRange() {
        _rangeAnchorKey.value = null
        _rangeEndKey.value = null
        _rangeMode.value = RangeMode.HIDE
    }

    /**
     * Keys of every category between the current anchor and [endRow], inclusive, in displayed
     * order — independent of which end was picked first. Null if there is no valid anchor.
     */
    fun keysInRange(endRow: CustomizeCatRow): List<String>? {
        val anchorKey = _rangeAnchorKey.value ?: return null
        val current = rows.value
        val anchorIndex = current.indexOfFirst { it.key == anchorKey }
        val endIndex = current.indexOfFirst { it.key == endRow.key }
        if (anchorIndex < 0 || endIndex < 0) return null
        val lo = minOf(anchorIndex, endIndex)
        val hi = maxOf(anchorIndex, endIndex)
        return current.subList(lo, hi + 1).map { it.key }
    }

    /** Applies hide/show to the whole span ending at [endRow], then clears the range. */
    fun applyRange(endRow: CustomizeCatRow, hidden: Boolean) {
        val keys = keysInRange(endRow) ?: return
        viewModelScope.launch {
            customize.setCategoriesHidden(ctx.value.profileId, _section.value, keys, hidden)
        }
        cancelRange()
    }

    /**
     * Moves the whole selected block one step [kind]. The first press after anchoring uses [endRow]
     * as the span end and locks the block in; later presses reuse that block, so ↑/↓/⤒/⤓ can be
     * pressed repeatedly to walk it. The selection survives the move — Back or Cancel clears it.
     */
    fun moveRange(endRow: CustomizeCatRow, kind: MoveKind) {
        val anchorKey = _rangeAnchorKey.value ?: return
        val endKey = _rangeEndKey.value ?: endRow.key
        val current = rows.value
        val anchorIndex = current.indexOfFirst { it.key == anchorKey }
        val endIndex = current.indexOfFirst { it.key == endKey }
        if (anchorIndex < 0 || endIndex < 0) return
        _rangeEndKey.value = endKey
        moveBlock(minOf(anchorIndex, endIndex), maxOf(anchorIndex, endIndex), kind)
    }

    /**
     * Keys of every row in the span currently selected — the anchor alone until an end is picked.
     * Drives the block highlight; empty when no range is in progress.
     */
    val rangeSelectedKeys: StateFlow<Set<String>> =
        combine(_rangeAnchorKey, _rangeEndKey, rows) { anchorKey, endKey, current ->
            if (anchorKey == null) return@combine emptySet()
            val anchorIndex = current.indexOfFirst { it.key == anchorKey }
            if (anchorIndex < 0) return@combine emptySet()
            val endIndex = endKey?.let { k -> current.indexOfFirst { it.key == k } } ?: anchorIndex
            if (endIndex < 0) return@combine setOf(anchorKey)
            current.subList(minOf(anchorIndex, endIndex), maxOf(anchorIndex, endIndex) + 1)
                .mapTo(mutableSetOf()) { it.key }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
