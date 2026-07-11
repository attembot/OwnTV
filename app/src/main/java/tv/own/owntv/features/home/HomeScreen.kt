package tv.own.owntv.features.home

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BlurMaskFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherWatchNextType
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.format.formatSystemTime
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.widthIn
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlayMovie: (movieId: Long, positionMs: Long) -> Unit,
    onPlayEpisode: (seriesId: Long, episodeId: Long, positionMs: Long) -> Unit,
    onPlayChannel: (channelId: Long, zapChannels: List<ChannelEntity>) -> Unit,
    onOpenGuide: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    previewEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val heroPreviewEngine = koinInject<HeroPreviewEngine>()
    val engineState by heroPreviewEngine.state.collectAsStateWithLifecycle()
    val isPreviewActive by vm.isPreviewActive.collectAsStateWithLifecycle()
    val lastInteractionMs by vm.lastHeroInteractionMs.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val homeScope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    val fallbackFocus = remember { FocusRequester() }
    val rowFirstFocusRequesters = remember {
        HomeRow.entries.associateWith { FocusRequester() }
    }
    // Nothing starts expanded: a hero card earns its wide (16:9) state only after 3s of continuous focus
    // (see the dwell effect below). Moving focus — to another card or out of the row — collapses it again.
    var expandedHeroIndex by remember { mutableStateOf(-1) }
    var focusedHeroIndex by remember { mutableStateOf(-1) }
    val orderedRows = state.config.visibleOrder
    val heroVisible = HomeRow.HERO in orderedRows
    val hasNonHeroContent = orderedRows.any { it != HomeRow.HERO && rowHasData(it, state) }
    val showHeroFallback = heroVisible && state.heroItems.isEmpty() && !hasNonHeroContent
    val renderRows = orderedRows.filter { rowCanRender(it, state, showHeroFallback) }
    val firstDataRow = renderRows.firstOrNull { it != HomeRow.HERO && rowHasData(it, state) }
    val showAllHiddenState = orderedRows.isEmpty()
    val showEmptyState = orderedRows.isNotEmpty() && renderRows.isEmpty()
    val rowFocusRequester: (HomeRow) -> FocusRequester? = { row ->
        when (row) {
            HomeRow.HERO -> when {
                state.heroItems.isNotEmpty() -> heroFocus
                showHeroFallback -> fallbackFocus
                else -> null
            }
            else -> rowFirstFocusRequesters[row]
        }
    }

    val onNonHeroFocused = remember(vm, heroPreviewEngine) {
        {
            vm.setHeroFocused(false)
            heroPreviewEngine.stop()
            expandedHeroIndex = -1
            onChildFocused()
        }
    }

    LaunchedEffect(focusedHeroIndex) {
        if (focusedHeroIndex != -1) return@LaunchedEffect
        // Focus moves between hero cards very quickly (old loses focus before new gains). Debounce the
        // "left hero row" signal so we don't flap preview state while navigating within the row.
        kotlinx.coroutines.delay(40L)
        if (focusedHeroIndex != -1) return@LaunchedEffect
        vm.setHeroFocused(false)
        heroPreviewEngine.stop()
        expandedHeroIndex = -1
    }

    // Dwell-to-expand: a card widens only after 3s of uninterrupted focus. Navigating away cancels the
    // timer (this effect restarts), so quick D-pad sweeps never expand anything.
    LaunchedEffect(focusedHeroIndex) {
        val index = focusedHeroIndex
        if (index < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000L)
        if (focusedHeroIndex == index) expandedHeroIndex = index
    }

    LaunchedEffect(previewEnabled) {
        vm.setPreviewEnabled(previewEnabled)
        if (!previewEnabled) {
            vm.stopPreview() // keep the hero expanded (poster); just stop the video
        }
    }

    // The engine is an app-scoped singleton; make sure the preview can't outlive the Home screen.
    DisposableEffect(heroPreviewEngine) {
        onDispose { heroPreviewEngine.stop() }
    }

    LaunchedEffect(orderedRows, state.heroItems, state.recentLive, state.favoriteLive, state.recentGuide, state.favoriteGuide, state.continueMovies, state.continueSeries, restoreFocus) {
        if (orderedRows.isEmpty()) {
            if (restoreFocus) onRestored()
            return@LaunchedEffect
        }

        val targetRow = when {
            restoreFocus && heroVisible && state.heroItems.isNotEmpty() -> HomeRow.HERO
            restoreFocus && showHeroFallback -> HomeRow.HERO
            restoreFocus -> firstDataRow
            else -> null
        }
        val targetIndex = targetRow?.let { renderRows.indexOf(it) } ?: 0
        runCatching { listState.scrollToItem(targetIndex.coerceAtLeast(0)) }

        // Only pull focus INTO the Home content when returning from the player (restoreFocus). On a cold
        // start or a tab switch, leave focus on the sidebar's Home item so the nav is immediately navigable.
        if (restoreFocus) {
            kotlinx.coroutines.delay(60)
            val focusTarget = when {
                heroVisible && state.heroItems.isNotEmpty() -> heroFocus
                showHeroFallback -> fallbackFocus
                firstDataRow != null -> rowFocusRequester(firstDataRow)
                else -> null
            }
            if (focusTarget != null) runCatching { focusTarget.requestFocus() }
            onRestored()
        }
    }

    // Cold-start "structure first": Home's queries are indexed and profile-scoped, but their first reads
    // still come off cold eMMC before the OS page cache warms — so there's a brief gap between the shell
    // painting and `home-data` arriving. During it we render the skeleton (instant) rather than flashing the
    // empty state, which would look wrong (and momentarily disappear) for a user who actually has history.
    // isLoading is true only for the initial state; it flips false on the first load and never goes back.
    if (state.isLoading) {
        HomeSkeleton(modifier = modifier.fillMaxSize())
        return
    }
    if (showAllHiddenState) {
        AllRowsHiddenState(modifier = modifier.fillMaxSize())
        return
    }
    if (showEmptyState) {
        EmptyHomeState(modifier = modifier.fillMaxSize())
        return
    }

    val hero = state.heroItems.getOrNull(state.activeHeroIndex)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel(fillColor = ContentPanelFill)
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
        state = listState,
        contentPadding = PaddingValues(vertical = Dimens.ScreenPaddingV),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge),
    ) {
        itemsIndexed(renderRows, key = { _, row -> row.name }) { index, row ->
            val firstItemFocusRequester = rowFocusRequester(row)
            val nextRowIndex = renderRows
                .drop(index + 1)
                .indexOfFirst { rowFocusRequester(it) != null }
                .takeIf { it >= 0 }
                ?.let { index + 1 + it }
            val onMoveToNextRow: (() -> Unit)? = nextRowIndex?.let { targetIndex ->
                val targetFocusRequester = rowFocusRequester(renderRows[targetIndex]) ?: return@let null
                {
                    homeScope.launch {
                        val targetIsVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
                        if (!targetIsVisible) {
                            listState.scrollToItem(targetIndex)
                            kotlinx.coroutines.delay(50)
                        }
                        runCatching { targetFocusRequester.requestFocus() }
                    }
                }
            }
            when (row) {
                HomeRow.HERO -> {
                    if (state.heroItems.isNotEmpty()) {
                        HeroRowSection(
                            items = state.heroItems,
                            activeHeroIndex = state.activeHeroIndex,
                            expandedIndex = expandedHeroIndex,
                            heroPreviewEngine = heroPreviewEngine,
                            engineState = engineState,
                            heroFocusRequester = heroFocus,
                            heroMetadata = state.heroMetadata,
                            onHeroFocusChanged = { index, hasFocus ->
                                if (hasFocus) {
                                    if (expandedHeroIndex != index) {
                                        heroPreviewEngine.stop() // stop the previous hero's video before switching
                                        expandedHeroIndex = -1 // collapse immediately; the dwell timer re-expands after 3s
                                    }
                                    focusedHeroIndex = index
                                    vm.onHeroUserNavigate(index)
                                    vm.setHeroFocused(true)
                                    onChildFocused()
                                } else if (focusedHeroIndex == index) {
                                    focusedHeroIndex = -1
                                }
                            },
                            onPlay = { item ->
                                when (item) {
                                    is HeroItem.MovieHero -> onPlayMovie(item.movie.id, item.positionMs)
                                    is HeroItem.SeriesHero -> onPlayEpisode(item.series.id, item.episode.id, item.positionMs)
                                    is HeroItem.LiveHero -> onPlayChannel(item.channel.id, state.recentLive)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        HeroFallbackPane(
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = fallbackFocus,
                            onChildFocused = onNonHeroFocused,
                        )
                    }
                }

                HomeRow.RECENT_CHANNELS -> if (state.recentLive.isNotEmpty()) {
                    HomeLiveRow(
                        title = row.title,
                        mode = state.config.recentLiveMode,
                        channels = state.recentLive,
                        guide = state.recentGuide,
                        onChannelClick = onPlayChannel,
                        onFocus = onNonHeroFocused,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onContainerDown = onMoveToNextRow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HomeRow.FAVORITE_CHANNELS -> if (state.favoriteLive.isNotEmpty()) {
                    HomeLiveRow(
                        title = row.title,
                        mode = state.config.favoriteLiveMode,
                        channels = state.favoriteLive,
                        guide = state.favoriteGuide,
                        onChannelClick = onPlayChannel,
                        onFocus = onNonHeroFocused,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onContainerDown = onMoveToNextRow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HomeRow.CONTINUE_MOVIES -> if (state.continueMovies.isNotEmpty()) {
                    ContinueWatchingRow(
                        title = row.title,
                        items = state.continueMovies,
                        onItemClick = { onPlayMovie(it.sourceItemId, it.positionMs) },
                        onFocus = onNonHeroFocused,
                        firstItemFocusRequester = firstItemFocusRequester,
                    )
                }

                HomeRow.CONTINUE_SERIES -> if (state.continueSeries.isNotEmpty()) {
                    ContinueWatchingRow(
                        title = row.title,
                        items = state.continueSeries,
                        posterOverrides = state.continuationArtwork,
                        landscapeTiles = true,
                        onItemClick = { onPlayEpisode(0L, it.targetItemId, it.positionMs) },
                        onFocus = onNonHeroFocused,
                        onItemFocus = {
                            onNonHeroFocused()
                            vm.resolveSeriesContinuationArtwork(it)
                        },
                        firstItemFocusRequester = firstItemFocusRequester,
                    )
                }
            }
        }
    }

    // Video preview starts after the expanded card has settled, so 4K decoder setup does not compete with
    // the width animation. Until then the expanded card stays on the poster.
    LaunchedEffect(isPreviewActive, hero, expandedHeroIndex, focusedHeroIndex, lastInteractionMs) {
        if (!isPreviewActive || hero == null || expandedHeroIndex < 0 || focusedHeroIndex != expandedHeroIndex) {
            heroPreviewEngine.stop()
            return@LaunchedEffect
        }

        val scheduledIndex = expandedHeroIndex
        val scheduledHero = hero
        val interactionStamp = lastInteractionMs

        heroPreviewEngine.stop()
        kotlinx.coroutines.delay(520L)
        if (!isPreviewActive || interactionStamp != lastInteractionMs) return@LaunchedEffect
        if (focusedHeroIndex != scheduledIndex || expandedHeroIndex != scheduledIndex) return@LaunchedEffect
        if (scheduledHero != state.heroItems.getOrNull(state.activeHeroIndex)) return@LaunchedEffect

        heroPreviewEngine.play(scheduledHero.streamUrl, scheduledHero.seekToMs)
    }
}

private fun rowHasData(row: HomeRow, state: HomeUiState): Boolean = when (row) {
    HomeRow.HERO -> state.heroItems.isNotEmpty()
    HomeRow.RECENT_CHANNELS -> when (state.config.recentLiveMode) {
        HomeLiveRowMode.CARDS -> state.recentLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.recentGuide.hasContent
    }
    HomeRow.FAVORITE_CHANNELS -> when (state.config.favoriteLiveMode) {
        HomeLiveRowMode.CARDS -> state.favoriteLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.favoriteGuide.hasContent
    }
    HomeRow.CONTINUE_MOVIES -> state.continueMovies.isNotEmpty()
    HomeRow.CONTINUE_SERIES -> state.continueSeries.isNotEmpty()
}

private fun rowCanRender(row: HomeRow, state: HomeUiState, showHeroFallback: Boolean): Boolean =
    when (row) {
        HomeRow.HERO -> state.heroItems.isNotEmpty() || showHeroFallback
        else -> rowHasData(row, state)
    }

private fun HeroItem.heroKey(): String = when (this) {
    is HeroItem.MovieHero -> "movie:${movie.id}"
    is HeroItem.SeriesHero -> "episode:${episode.id}"
    is HeroItem.LiveHero -> "live:${channel.id}"
}

private fun expandedHeroImageUrl(item: HeroItem, metadata: HomeHeroMetadata?): String? = when (item) {
    is HeroItem.MovieHero ->
        metadata?.backdropUrl
            ?: item.movie.backdropUrl?.takeIf { it.isNotBlank() }
            ?: item.movie.posterUrl?.takeIf { it.isNotBlank() }
    is HeroItem.SeriesHero ->
        metadata?.backdropUrl
            ?: item.series.backdropUrl?.takeIf { it.isNotBlank() }
            ?: item.series.posterUrl?.takeIf { it.isNotBlank() }
    is HeroItem.LiveHero -> item.channel.logoUrl?.takeIf { it.isNotBlank() }
}

private fun expandedHeroPlot(item: HeroItem, metadata: HomeHeroMetadata?): String? = when (item) {
    is HeroItem.MovieHero -> metadata?.plot ?: item.movie.plot?.takeIf { it.isNotBlank() }
    is HeroItem.SeriesHero -> metadata?.plot ?: item.episode.plot?.takeIf { it.isNotBlank() } ?: item.series.plot?.takeIf { it.isNotBlank() }
    is HeroItem.LiveHero -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroRowSection(
    items: List<HeroItem>,
    activeHeroIndex: Int,
    expandedIndex: Int,
    heroPreviewEngine: HeroPreviewEngine,
    engineState: HeroPreviewEngine.State,
    heroFocusRequester: FocusRequester,
    heroMetadata: Map<String, HomeHeroMetadata>,
    onHeroFocusChanged: (index: Int, hasFocus: Boolean) -> Unit,
    onPlay: (HeroItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val approxRowWidth = screenWidthDp - Dimens.SidebarWidthCollapsed - Dimens.HomeRowPaddingH
    val maxCardHeight = (approxRowWidth - Dimens.HeroBaseWidth - Dimens.HeroGap) * 9f / 16f
    val cardHeight = maxCardHeight.coerceIn(Dimens.HeroMinCardHeight, Dimens.HeroMaxCardHeight)
    val posterHeight = cardHeight - Dimens.HeroMetaHeight
    val expandedWidth = cardHeight * 16f / 9f
    val cardShape = RoundedCornerShape(Dimens.HeroCardCorner)
    val posterClip = RoundedCornerShape(Dimens.HeroPosterCorner)

    var rowTopLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    var previewRectInRowPx by remember { mutableStateOf<Rect?>(null) }
    var localFocusedIndex by remember { mutableStateOf(-1) }
    var rowWidthDp by remember { mutableStateOf(0.dp) }
    var alignToActiveHeroKey by remember { mutableStateOf<String?>(null) }
    val heroRowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemsSignature = remember(items) { items.joinToString(separator = "|") { it.heroKey() } }
    val activeHeroKey = items.getOrNull(activeHeroIndex)?.heroKey()

    LaunchedEffect(itemsSignature) {
        if (activeHeroIndex !in items.indices) return@LaunchedEffect

        alignToActiveHeroKey = activeHeroKey
        heroRowState.scrollToItem(activeHeroIndex)

        if (activeHeroIndex == 0 && localFocusedIndex >= 0 && localFocusedIndex != activeHeroIndex) {
            localFocusedIndex = activeHeroIndex
            runCatching { heroFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(expandedIndex, items.size) {
        // The rect is only ever written by the expanded card's onGloballyPositioned; drop it on collapse
        // so the next expansion can't flash its overlay at the previous card's stale position.
        if (expandedIndex < 0) {
            previewRectInRowPx = null
        } else if (expandedIndex < items.size) {
            heroRowState.animateScrollToItem(expandedIndex)
        }
    }

    LaunchedEffect(localFocusedIndex) {
        if (localFocusedIndex < 0) return@LaunchedEffect
        heroRowState.animateScrollToItem(localFocusedIndex)
    }

    val endPadding = (rowWidthDp - Dimens.HeroBaseWidth - Dimens.HomeRowPaddingH).coerceAtLeast(Dimens.HomeRowPaddingH)

    Column(modifier = modifier) {
        Text(
            text = "Keep Watching".uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Dimens.HomeRowPaddingH),
        )
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .onGloballyPositioned {
                    rowTopLeftInRoot = it.positionInRoot()
                    rowWidthDp = with(density) { it.size.width.toDp() }
                },
        ) {
            LazyRow(
                state = heroRowState,
                horizontalArrangement = Arrangement.spacedBy(Dimens.HeroGap),
                contentPadding = PaddingValues(start = Dimens.HomeRowPaddingH, end = endPadding),
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        onEnter = {
                            if (
                                requestedFocusDirection == FocusDirection.Down ||
                                requestedFocusDirection == FocusDirection.Up
                            ) {
                                scope.launch {
                                    heroRowState.scrollToItem(0)
                                    runCatching { heroFocusRequester.requestFocus() }
                                }
                                cancelFocusChange()
                            }
                        }
                    },
            ) {
                itemsIndexed(
                    items,
                    key = { _, item -> item.heroKey() },
                ) { index, item ->
                    val isExpanded = index == expandedIndex
                    val targetWidth = if (isExpanded) expandedWidth else Dimens.HeroBaseWidth
                    val width by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = tween(durationMillis = if (isExpanded) 500 else 150),
                        label = "heroCardWidth",
                    )

                    val imageUrl = when (item) {
                        is HeroItem.MovieHero -> item.movie.posterUrl
                        is HeroItem.SeriesHero -> item.series.posterUrl
                        is HeroItem.LiveHero -> item.channel.logoUrl
                    }
                    val itemMetadata = heroMetadata[item.heroKey()]
                    val expandedImageUrl = expandedHeroImageUrl(item, itemMetadata)

                    val heroGlowColor = colors.focusGlow
                    Box(
                        modifier = Modifier
                            .height(cardHeight)
                            .width(width)
                            .then(if (isExpanded) Modifier.zIndex(1f) else Modifier)
                            .then(
                                if (isExpanded) Modifier.onGloballyPositioned { coords ->
                                    val b = coords.boundsInRoot()
                                    previewRectInRowPx = Rect(
                                        left = b.left - rowTopLeftInRoot.x,
                                        top = b.top - rowTopLeftInRoot.y,
                                        right = b.right - rowTopLeftInRoot.x,
                                        bottom = b.bottom - rowTopLeftInRoot.y,
                                    )
                                } else Modifier
                            ),
                    ) {
                        FocusableSurface(
                            onClick = { onPlay(item) },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .width(width)
                                .height(cardHeight)
                                .then(if (index == 0) Modifier.focusRequester(heroFocusRequester) else Modifier)
                                .onFocusChanged { fs ->
                                    val redirectToActiveHero = fs.hasFocus &&
                                        activeHeroIndex == 0 &&
                                        index != activeHeroIndex &&
                                        alignToActiveHeroKey == activeHeroKey
                                    if (fs.hasFocus) {
                                        if (redirectToActiveHero) {
                                            scope.launch {
                                                heroRowState.scrollToItem(activeHeroIndex)
                                                runCatching { heroFocusRequester.requestFocus() }
                                            }
                                        } else {
                                            alignToActiveHeroKey = null
                                            localFocusedIndex = index
                                        }
                                    }
                                    if (!redirectToActiveHero) onHeroFocusChanged(index, fs.hasFocus)
                                }
                                .then(
                                    if (isExpanded) Modifier.drawBehind {
                                        val radius = 18.dp.toPx()
                                        drawIntoCanvas { canvas ->
                                            val paint = android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                color = heroGlowColor.toArgb()
                                                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                                            }
                                            canvas.nativeCanvas.drawRoundRect(
                                                0f, 0f, size.width, size.height,
                                                Dimens.HeroCardCorner.toPx(), Dimens.HeroCardCorner.toPx(),
                                                paint,
                                            )
                                        }
                                    } else Modifier
                                ),
                            shape = cardShape,
                            focusedScale = 1f,
                            glowElevation = if (isExpanded) 0 else 10,
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            selectedContainerColor = colors.surfaceContainerHigh,
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            if (isExpanded) {
                                // No blurred backdrop here: the preview overlay covers this card as soon
                                // as previewRectInRowPx is known and renders the blur itself — doubling the
                                // blur underneath just costs frames on TV hardware.
                                Box(Modifier.fillMaxSize().background(Color.Black)) {
                                    val cardImageUrl = expandedImageUrl ?: imageUrl
                                    if (!cardImageUrl.isNullOrBlank()) {
                                        if (item is HeroItem.LiveHero) {
                                            AsyncImage(
                                                model = cardImageUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().blur(20.dp),
                                                contentScale = ContentScale.Crop,
                                                alpha = 0.5f,
                                            )
                                            AsyncImage(
                                                model = cardImageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(80.dp),
                                            )
                                        } else {
                                            AsyncImage(
                                                model = cardImageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val fallback = when (item) {
                                                is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                                is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                                is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                            }
                                            OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(64.dp))
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(posterHeight)
                                            .clip(posterClip)
                                            .background(colors.surfaceContainerLowest),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!imageUrl.isNullOrBlank()) {
                                            if (item is HeroItem.LiveHero) {
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().blur(20.dp),
                                                    contentScale = ContentScale.Crop,
                                                    alpha = 0.5f,
                                                )
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(80.dp),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            } else {
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            }
                                        } else {
                                            val fallback = when (item) {
                                                is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                                is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                                is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                            }
                                            OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(42.dp))
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    val title = when (item) {
                                        is HeroItem.MovieHero -> item.item.title
                                        is HeroItem.SeriesHero -> item.item.title
                                        is HeroItem.LiveHero -> item.channel.name
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (focused) colors.primary else colors.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (item.durationMs > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        val fraction = (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(100))
                                                .background(Color.Black.copy(alpha = 0.25f)),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction)
                                                    .height(4.dp)
                                                    .background(colors.primary),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val rect = previewRectInRowPx
            if (rect != null && expandedIndex >= 0) {
                val expandedItem = items.getOrNull(expandedIndex)
                if (expandedItem != null) {
                    val ox = with(density) { rect.left.toDp() }
                    val oy = with(density) { rect.top.toDp() }
                    val ow = with(density) { rect.width.toDp() }
                    val oh = with(density) { rect.height.toDp() }

                    Box(
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .offset(x = ox, y = oy)
                            .width(ow)
                            .height(oh)
                            .clip(cardShape),
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            HeroPreviewSurface(
                                engine = heroPreviewEngine,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (engineState != HeroPreviewEngine.State.PLAYING) {
                                // Opaque cover: the SurfaceView below holds the previous preview's last
                                // frame after stop(), and the parent's black background is punched out by
                                // the SurfaceView. The loading images and fallback icon rely on this layer
                                // to hide it.
                                Box(Modifier.fillMaxSize().background(Color.Black))
                                val expandedMeta = heroMetadata[expandedItem.heroKey()]
                                val artUrl = expandedHeroImageUrl(expandedItem, expandedMeta)
                                if (!artUrl.isNullOrBlank()) {
                                    if (expandedItem is HeroItem.LiveHero) {
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().blur(20.dp),
                                            contentScale = ContentScale.Crop,
                                            alpha = 0.5f,
                                        )
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(80.dp),
                                        )
                                    } else {
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                } else {
                                    val fallback = when (expandedItem) {
                                        is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                        is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                        is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                    }
                                    OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(64.dp))
                                }
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.86f),
                                        ),
                                    ),
                                ),
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .widthIn(max = Dimens.HeroOverlayMaxWidth),
                        ) {
                            val expandedMeta = heroMetadata[expandedItem.heroKey()]
                            val title = when (expandedItem) {
                                is HeroItem.MovieHero -> expandedItem.item.title
                                is HeroItem.SeriesHero -> expandedItem.item.title
                                is HeroItem.LiveHero -> expandedItem.channel.name
                            }
                            val logoUrl = expandedMeta?.logoUrl?.takeIf { expandedItem !is HeroItem.LiveHero }
                            if (!logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.width(260.dp).height(58.dp),
                                )
                            } else {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            val subtitle = when (expandedItem) {
                                is HeroItem.MovieHero ->
                                    expandedItem.item.subtitle ?: expandedItem.movie.year?.toString().orEmpty()
                                is HeroItem.SeriesHero ->
                                    expandedItem.item.subtitle.orEmpty()
                                is HeroItem.LiveHero -> "Recently watched live"
                            }
                            if (subtitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            val statText = heroStatLabel(context, expandedItem, System.currentTimeMillis())
                            if (statText != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = statText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            val plot = expandedHeroPlot(expandedItem, expandedMeta)
                            if (!plot.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = plot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OwnTVButton(
                                label = when (expandedItem.watchNextType) {
                                    LauncherWatchNextType.NEXT -> "Play Next"
                                    LauncherWatchNextType.CONTINUE ->
                                        if (expandedItem is HeroItem.LiveHero) "Tune In" else "Resume"
                                },
                                onClick = { onPlay(expandedItem) },
                                modifier = Modifier.focusProperties { canFocus = false },
                                style = OwnTVButtonStyle.SECONDARY,
                                enabled = true,
                            )
                        }

                        if (expandedItem.durationMs > 0) {
                            val fraction = (expandedItem.positionMs.toFloat() / expandedItem.durationMs.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(Dimens.HeroProgressHeight)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(Dimens.HeroProgressHeight)
                                        .background(colors.primary),
                                )
                            }
                        }

                        if (engineState == HeroPreviewEngine.State.LOADING) {
                            OwnTVSpinner(
                                sizeDp = 18,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp)
                                    .alpha(0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun heroStatLabel(context: Context, item: HeroItem, nowMs: Long): String? =
    when (item) {
        is HeroItem.MovieHero,
        is HeroItem.SeriesHero -> finishByLabel(context, item.positionMs, item.durationMs, nowMs)
        is HeroItem.LiveHero -> relativeLastWatchedLabel(item.lastEngagementAt, nowMs)
    }

private fun finishByLabel(context: Context, positionMs: Long, durationMs: Long, nowMs: Long): String? {
    if (durationMs <= 0) return null

    val safePosition = positionMs.coerceIn(0L, durationMs)
    val remainingMs = durationMs - safePosition
    if (remainingMs <= 0L) return null

    val finishMs = roundUpToNextQuarterHour(nowMs + remainingMs)
    val time = formatSystemTime(context, finishMs)
    return "Finish by $time"
}

private fun roundUpToNextQuarterHour(ms: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = ms
    }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val millisecond = calendar.get(Calendar.MILLISECOND)
    val remainder = minute % 15
    val shouldAdvance = remainder != 0 || second != 0 || millisecond != 0

    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    if (shouldAdvance) {
        val minutesToAdd = if (remainder == 0) 15 else 15 - remainder
        calendar.add(Calendar.MINUTE, minutesToAdd)
    }

    return calendar.timeInMillis
}

private fun relativeLastWatchedLabel(lastEngagementAt: Long, nowMs: Long): String {
    val elapsedMs = nowMs - lastEngagementAt
    if (elapsedMs < 60_000L) return "Last watched just now"

    val elapsedMinutes = elapsedMs / 60_000L
    if (elapsedMinutes < 60L) {
        return "Last watched ${elapsedMinutes} ${if (elapsedMinutes == 1L) "minute" else "minutes"} ago"
    }

    val elapsedHours = elapsedMinutes / 60L
    if (elapsedHours < 24L) {
        return "Last watched ${elapsedHours} ${if (elapsedHours == 1L) "hour" else "hours"} ago"
    }

    val elapsedDays = elapsedHours / 24L
    return "Last watched ${elapsedDays} ${if (elapsedDays == 1L) "day" else "days"} ago"
}

@Composable
private fun HeroPreviewSurface(
    engine: HeroPreviewEngine,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.view.SurfaceView(ctx).apply {
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) = engine.setSurface(holder.surface)
                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) = engine.setSurface(null)
                })
            }
        },
        update = { it.keepScreenOn = false },
    )
}

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<LauncherContinuationItem>,
    posterOverrides: Map<String, String> = emptyMap(),
    landscapeTiles: Boolean = false,
    onItemClick: (LauncherContinuationItem) -> Unit,
    onFocus: () -> Unit,
    onItemFocus: (LauncherContinuationItem) -> Unit = { onFocus() },
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = OwnTVTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Dimens.HomeRowPaddingH),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                val itemModifier = when {
                    firstItemFocusRequester != null && index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                    else -> Modifier
                }
                val progress = continuationProgress(item)
                if (landscapeTiles) {
                    val overrideImageUrl = posterOverrides[item.stableKey]
                    Box(Modifier.width(275.dp)) {
                        LandscapeContinuationCard(
                            imageUrl = overrideImageUrl ?: item.posterUrl,
                            cropImage = !overrideImageUrl.isNullOrBlank(),
                            title = item.title,
                            chipText = seasonEpisodeChip(item),
                            progressFraction = progress,
                            modifier = itemModifier,
                            onFocus = { onItemFocus(item) },
                            onClick = { onItemClick(item) },
                        )
                    }
                } else {
                    Box(Modifier.width(150.dp)) {
                        PosterCard(
                            posterUrl = posterOverrides[item.stableKey] ?: item.posterUrl,
                            title = item.title,
                            progressFraction = progress,
                            modifier = itemModifier,
                            onFocus = { onItemFocus(item) },
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }
    }
}

private fun continuationProgress(item: LauncherContinuationItem): Float? =
    if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }

private fun seasonEpisodeChip(item: LauncherContinuationItem): String? {
    val season = item.seasonNumber?.takeIf { it > 0 }
    val episode = item.episodeNumber?.takeIf { it > 0 }
    return when {
        season != null && episode != null -> "S%02d E%02d".format(season, episode)
        season != null -> "S%02d".format(season)
        episode != null -> "E%02d".format(episode)
        else -> null
    }
}

@Composable
private fun LandscapeContinuationCard(
    imageUrl: String?,
    cropImage: Boolean,
    title: String,
    chipText: String?,
    progressFraction: Float?,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocus() },
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.04f,
        glowElevation = 14,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerLowest),
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = if (cropImage) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.SERIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    }
                }

                if (!chipText.isNullOrBlank()) {
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                if (progressFraction != null && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(colors.primary),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HeroFallbackPane(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onChildFocused: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 72, textSize = 42)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Nothing to preview yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your continue-watching items will appear here once playback history is available.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHomeState(
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84, textSize = 48)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Start watching to see your activity here.",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Continue watching will show up on Home once you have playback history.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AllRowsHiddenState(
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84, textSize = 48)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "All Home rows are hidden.",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Turn rows back on in Settings → Home screen.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Instant structure painted while Home's data loads on a cold start (see [HomeViewModel.HomeUiState.isLoading]).
 * Static placeholders only — no shimmer/animation, on purpose: this is a low-end-TV first paint, where an
 * animating skeleton would just compete with the cold DB reads for the same weak CPU/GPU we're trying to
 * unblock. Spacing/shape reuse the real rows' Dimens so the skeleton→content hand-off doesn't visibly jump.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface)
            .padding(vertical = Dimens.ScreenPaddingV),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapLarge),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.HomeRowPaddingH)
                .aspectRatio(21f / 9f)
                .clip(RoundedCornerShape(Dimens.HeroCardCorner))
                .background(colors.surfaceContainerLowest),
        )
        SkeletonRowPlaceholder(cardCount = 6, cardWidth = 150.dp, cardHeight = 220.dp)
        SkeletonRowPlaceholder(cardCount = 6, cardWidth = 180.dp, cardHeight = 100.dp)
    }
}

@Composable
private fun SkeletonRowPlaceholder(
    cardCount: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val placeholder = OwnTVTheme.colors.surfaceContainerLowest
    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(start = Dimens.HomeRowPaddingH)
                .width(150.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(100))
                .background(placeholder),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.HomeRowPaddingH),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(cardCount) {
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .background(placeholder),
                )
            }
        }
    }
}
