package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.features.settings.data.SettingsRepository

/**
 * One action in a long-press content menu — Live channel, movie, series or episode.
 *
 * The four menus used to be hand-written columns of buttons wrapped in `if`s, which meant their order
 * was the source order and nothing could be rearranged. They now build a list of these instead, and
 * render it in list order, so a later release can sort that list without touching any menu.
 *
 * [key] is a stable identifier and is deliberately NOT derived from the label: labels are translated,
 * and several of them toggle between two texts ("Add to favourites" / "Remove from favourites").
 *
 * [group] only spaces the menu out: a divider (or a gap, depending on the menu) is drawn wherever the
 * group changes between two neighbouring actions. An empty group simply produces no divider.
 */
data class MenuAction(
    val key: String,
    val label: String,
    val icon: OwnTVIcon? = null,
    val destructive: Boolean = false,
    val group: Int = 0,
    val onClick: () -> Unit,
)

/** The four long-press menus, each with its own saved order. The name is the storage key — do not rename. */
enum class ContentMenu { LIVE, MOVIE, SERIES, EPISODE }

/**
 * Put [actions] into the user's saved [order].
 *
 * Two rules, and both matter more than they look:
 * - a saved key that no longer exists is ignored, so an order saved by an older release never drops
 *   an action or crashes;
 * - an action the saved order has never heard of keeps its shipped position relative to the other
 *   unknown ones and is appended, so an action added by a *newer* release still shows up for someone
 *   who arranged their menu months ago.
 *
 * An empty [order] returns [actions] unchanged, which is the shipped order.
 */
/**
 * [actions] in the order the user arranged [menu] into. The menus read the setting themselves rather
 * than have it threaded down through four screens and their view models.
 */
@Composable
fun arranged(menu: ContentMenu, actions: List<MenuAction>): List<MenuAction> {
    val settings: SettingsRepository = koinInject()
    val order by remember(menu) { settings.menuOrder(menu.name.lowercase()) }
        .collectAsStateWithLifecycle(emptyList())
    return applyMenuOrder(actions, order)
}

fun applyMenuOrder(actions: List<MenuAction>, order: List<String>): List<MenuAction> =
    applyMenuOrder(actions, order) { it.key }

/** [applyMenuOrder] over anything with a key — the settings screen arranges plain keys, not actions. */
fun <T> applyMenuOrder(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
    if (order.isEmpty()) return items
    val byKey = items.associateBy(key)
    return order.mapNotNull { byKey[it] } + items.filterNot { key(it) in order }
}
