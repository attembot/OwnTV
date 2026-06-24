@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package tv.own.owntv.features.multiview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerState
import tv.own.owntv.player.SecondaryVideoSurface
import tv.own.owntv.ui.components.ChannelSwitcher
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/** Inset for on-screen chrome so it clears TV overscan (the outer ~5% a panel may crop). */
private val SafeArea = 28.dp

/** How long the control bar lingers after the last key press before it fades back to just-video. */
private const val ChromeIdleMs = 4000L

/**
 * MultiView — up to four live streams on screen at once, in either an equal **grid** or a **dominant**
 * (one large + a small strip) layout. The whole surface is built for a remote:
 *
 * - **D-pad** moves focus between tiles; the focused tile is the only audible one.
 * - **OK** on a tile opens the [ChannelSwitcher] to retune *that* tile from the playlist, live, while the
 *   others keep playing.
 * - **Hold OK** promotes a tile to the dominant (large) layout.
 * - The control bar auto-hides to an unobstructed video wall and returns on any key press.
 *
 * Each tile renders a [SecondaryVideoSurface] — the constrained second-decoder pattern from PiP.
 */
@Composable
fun MultiViewScreen(
    controller: MultiViewController,
    recentChannels: List<ChannelEntity>,
    searchChannels: suspend (String) -> List<ChannelEntity>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles by controller.tiles.collectAsStateWithLifecycle()
    val activeIndex by controller.activeIndex.collectAsStateWithLifecycle()
    val layout by controller.layout.collectAsStateWithLifecycle()

    // The channel switcher overlay. null = closed; ADD_TILE = "add a new tile"; >=0 = "retune that tile".
    var switchTarget by remember { mutableStateOf<Int?>(null) }
    val switcherOpen = switchTarget != null

    // Auto-hiding chrome. Any key press shows it and restarts the idle timer; it only fades while focus is on
    // a tile (not the bar), so a tile always owns focus when the bar leaves the composition — no stranded focus.
    var chromeVisible by remember { mutableStateOf(true) }
    var barFocused by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactions, barFocused, switcherOpen) {
        if (!barFocused && !switcherOpen) {
            delay(ChromeIdleMs)
            chromeVisible = false
        }
    }

    BackHandler(enabled = !switcherOpen) { onExit() }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Reveal chrome on any remote activity, then let the event flow on to the focused tile/button.
            .onPreviewKeyEvent { chromeVisible = true; interactions++; false },
    ) {
        when {
            tiles.isEmpty() -> Unit
            layout == MultiLayout.DOMINANT && tiles.size > 1 ->
                DominantLayout(tiles, activeIndex, controller, chromeVisible) { switchTarget = it }
            else -> GridLayout(tiles, activeIndex, controller, chromeVisible) { switchTarget = it }
        }

        // Bottom chrome: a one-line hint for the OK gestures, then the control pill. Fades together.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = SafeArea),
            ) {
                Text(
                    "OK changes the focused stream · hold OK to enlarge it",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .focusGroup()
                        .onFocusChanged { barFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val gridMode = layout == MultiLayout.GRID
                    BarButton(OwnTVIcon.ASPECT, if (gridMode) "Grid" else "Dominant") { controller.toggleLayout() }
                    if (tiles.size < controller.maxTiles) {
                        BarButton(OwnTVIcon.ADD, "Add stream") { switchTarget = ADD_TILE }
                    }
                    BarButton(OwnTVIcon.CLOSE, "Exit") { onExit() }
                }
            }
        }

        if (switcherOpen) {
            val target = switchTarget
            val adding = target == ADD_TILE
            ChannelSwitcher(
                title = if (adding) "Add a stream" else "Change this stream",
                // For "add", hide channels already on screen; for "change", the whole playlist is fair game.
                recent = if (adding) recentChannels.filter { ch -> tiles.none { it.id == ch.id } } else recentChannels,
                search = searchChannels,
                onPick = { ch ->
                    if (adding) controller.addTile(ch) else target?.let { controller.replaceTile(it, ch) }
                    switchTarget = null
                },
                onDismiss = { switchTarget = null },
            )
        }
    }
}

/** Sentinel [switchTarget] meaning "open the switcher to add a new tile" rather than retune an existing one. */
private const val ADD_TILE = -1

/** A focusable icon+label button for the MultiView control bar. Labels (not bare icons) keep modes legible. */
@Composable
private fun BarButton(icon: OwnTVIcon, label: String, onClick: () -> Unit) {
    tv.own.owntv.ui.components.FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.08f,
        focusedContainerColor = OwnTVTheme.colors.primary,
        unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
        selectedContainerColor = Color.White.copy(alpha = 0.14f),
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            OwnTVIcon(
                icon,
                tint = if (focused) OwnTVTheme.colors.onPrimary else Color.White,
                filled = true,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) OwnTVTheme.colors.onPrimary else Color.White,
            )
        }
    }
}

/** Equal-size tiles: 1 = full, 2 = side-by-side, 3 = two over one, 4 = 2×2. */
@Composable
private fun GridLayout(
    tiles: List<ChannelEntity>,
    activeIndex: Int,
    controller: MultiViewController,
    chromeVisible: Boolean,
    onChange: (Int) -> Unit,
) {
    val gap = 4.dp
    when (tiles.size) {
        1 -> TileRow(tiles, 0, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxSize())
        2 -> TileRow(tiles, 0, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxSize(), count = 2)
        3 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            TileRow(tiles, 0, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxWidth().weight(1f), count = 2)
            TileRow(tiles, 2, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxWidth().weight(1f), count = 1)
        }
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            TileRow(tiles, 0, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxWidth().weight(1f), count = 2)
            TileRow(tiles, 2, activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxWidth().weight(1f), count = 2)
        }
    }
}

/** A row of [count] tiles starting at [start]. */
@Composable
private fun TileRow(
    tiles: List<ChannelEntity>,
    start: Int,
    activeIndex: Int,
    controller: MultiViewController,
    chromeVisible: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier,
    count: Int = 1,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (offset in 0 until count) {
            val index = start + offset
            if (index in tiles.indices) {
                Tile(index, tiles[index], active = index == activeIndex, controller, chromeVisible, onChange, Modifier.fillMaxHeight().weight(1f))
            }
        }
    }
}

/** Dominant layout: the active tile fills the left ~3/4; the others stack down the right ~1/4. */
@Composable
private fun DominantLayout(
    tiles: List<ChannelEntity>,
    activeIndex: Int,
    controller: MultiViewController,
    chromeVisible: Boolean,
    onChange: (Int) -> Unit,
) {
    val big = activeIndex.coerceIn(0, tiles.lastIndex) // guard against a transient tiles/active mismatch
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile(big, tiles[big], active = true, controller, chromeVisible, onChange, Modifier.fillMaxHeight().weight(3f))
        Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tiles.forEachIndexed { i, ch ->
                if (i != big) Tile(i, ch, active = false, controller, chromeVisible, onChange, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** One tile: the stream's video, a title strip, and a focus/active ring. OK → change; hold OK → enlarge. */
@Composable
private fun Tile(
    index: Int,
    channel: ChannelEntity,
    active: Boolean,
    controller: MultiViewController,
    chromeVisible: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val engine = remember(index) { controller.engineAt(index) }
    val state by engine.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    // Pull focus to the active tile when MultiView opens / the active tile changes.
    LaunchedEffect(active) { if (active) runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = modifier
            // The active tile glows in the brand color so the audible stream reads from across the room.
            .shadow(
                elevation = if (active) 18.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
                clip = false,
                ambientColor = OwnTVTheme.colors.focusGlow,
                spotColor = OwnTVTheme.colors.focusGlow,
            )
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = if (active) 4.dp else 1.5.dp,
                color = if (active) OwnTVTheme.colors.primary else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) controller.setActive(index) }
            // combinedClickable is the single focus target: OK retunes this tile, hold OK enlarges it.
            .combinedClickable(
                onClick = { onChange(index) },
                onLongClick = { controller.promoteToDominant(index) },
            ),
    ) {
        if (state == CornerState.ERROR) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't play this stream", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else {
            SecondaryVideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
        }
        if (state == CornerState.LOADING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 22)
            }
        }
        // Title strip fades with the rest of the chrome, leaving a clean video wall when idle.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (active) {
                    OwnTVIcon(OwnTVIcon.VOLUME_HIGH, tint = OwnTVTheme.colors.primary, filled = true, modifier = Modifier.size(16.dp))
                }
                Text(channel.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            }
        }
    }
}
