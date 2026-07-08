package tv.own.owntv.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.features.live.LiveKey
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.MediaDetailsScreen
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.InAppToast
import tv.own.owntv.ui.components.rememberInAppToast
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.components.ResumeDialog
import tv.own.owntv.ui.components.SetTmdbNameDialog
import tv.own.owntv.ui.components.TrailerPlayerScreen
import tv.own.owntv.ui.components.longPressMenuGuard
import androidx.compose.foundation.layout.width
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
fun MoviesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: MovieViewModel = koinViewModel()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedMovie by vm.selectedMovie.collectAsStateWithLifecycle()
    val selectedMovieMeta by vm.selectedMovieMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    var contextMovie by remember { mutableStateOf<MovieEntity?>(null) }
    // Fullscreen TMDB details window (§11.1); null = closed.
    var detailsMovie by remember { mutableStateOf<MovieEntity?>(null) }
    // "Set TMDB name" dialog target (§11.2 U5b); null = closed.
    var setTmdbNameMovie by remember { mutableStateOf<MovieEntity?>(null) }
    // In-app trailer playback (§7.3 U4); non-null = fullscreen player open with this YouTube key.
    var trailerVideoKey by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = rememberInAppToast()
    // Id + list position of the movie the context menu was opened on. The id re-focuses the same item
    // when it survives (Favourite/Download/Cancel); when the item is REMOVED (Remove from history, or
    // un-Favourite while on the Favorites category), it's gone from the paged list, so we re-focus the
    // nearest surviving neighbour by position instead of escaping to the CategoryRail.
    var contextMovieId by remember { mutableStateOf<Long?>(null) }
    var contextMovieIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { FocusRequester() }
    val selectedProgress by vm.selectedProgress.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()
    val movies = vm.movies.collectAsLazyPagingItems()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    // Global external-player toggle: never mount the fullscreen in-app player (it spins up mpv)
    // when playback is handed to an external app.
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val goFullscreen: () -> Unit = { if (!externalPlayerOn) onFullscreen() }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)

    // Resume flow: AUTO continues silently, ASK prompts (≥10s saved), NEVER starts from zero.
    val scope = rememberCoroutineScope()
    var resumePrompt by remember { mutableStateOf<Pair<MovieEntity, Long>?>(null) }
    val startMovie: (MovieEntity) -> Unit = { m ->
        scope.launch {
            val pos = vm.savedPositionMs(m)
            when {
                resumeMode == SettingsRepository.ResumeMode.ASK && pos >= 10_000 -> resumePrompt = m to pos
                resumeMode == SettingsRepository.ResumeMode.AUTO && pos > 0 -> { vm.play(m, pos); goFullscreen() }
                else -> { vm.play(m, 0); goFullscreen() }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    // Returning from the player: scroll to and focus the movie you just played (waits for the grid to load).
    LaunchedEffect(restoreFocus, movies.itemCount) {
        if (!restoreFocus || movies.itemCount == 0) return@LaunchedEffect
        val sel = selectedMovie
        val idx = if (sel != null) movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
        if (idx >= 0) {
            runCatching { gridState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }
    // Closing the long-press context menu must return focus inside this pane, never the CategoryRail.
    //   - Item still present (Favourite toggle / Download / Cancel): re-focus the same item by id.
    //   - Item removed (Remove from history, or un-Favourite on the Favorites category): the paged
    //     list no longer contains it, so focus the NEAREST surviving neighbour by position (the item
    //     that slid into the removed slot, else the new last item, else first item). Only if the whole
    //     category is now empty do we let focus leave (there's nothing here to land on).
    LaunchedEffect(contextMovie) {
        if (contextMovie != null) return@LaunchedEffect
        // Opening the TMDB Details window or the Set TMDB name dialog closes the menu; don't yank focus
        // back to the grid — they need it (and trap it). The grid is refocused when they close (see below).
        if (detailsMovie != null) return@LaunchedEffect
        if (setTmdbNameMovie != null) return@LaunchedEffect
        if (trailerVideoKey != null) return@LaunchedEffect
        val targetId = contextMovieId
        if (targetId == null) { contextMovieIndex = -1; return@LaunchedEffect }
        val items = movies.itemSnapshotList.items
        val idx = items.indexOfFirst { it?.id == targetId }
        if (idx >= 0) {
            // Item survived — re-focus it directly.
            runCatching {
                if (viewMode == SettingsRepository.VodViewMode.LIST) listState.scrollToItem(idx)
                else gridState.scrollToItem(idx)
            }
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        } else {
            // Item was removed. Wait for the paged list to settle, then land on the nearest survivor.
            withFrameNanos { }
            val settled = movies.itemSnapshotList.items.filterNotNull()
            if (settled.isEmpty()) {
                runCatching { firstItemFocus.requestFocus() } // nothing left; firstItemFocus attaches to the next item that loads
            } else {
                val neighbor = settled.getOrNull(contextMovieIndex.coerceAtLeast(0)) ?: settled.last()
                val neighborIdx = items.indexOfFirst { it?.id == neighbor.id }.coerceAtLeast(0)
                runCatching {
                    if (viewMode == SettingsRepository.VodViewMode.LIST) listState.scrollToItem(neighborIdx)
                    else gridState.scrollToItem(neighborIdx)
                }
                // selFocus is bound to selectedMovie; reuse the generic firstItemFocus path only if that
                // fails. Here we re-purpose contextFocus by re-binding it: re-request after a frame so the
                // neighbour row (now at contextMovieIndex) receives focus.
                contextMovieId = neighbor.id
                withFrameNanos { }
                runCatching { contextFocus.requestFocus() }
            }
        }
        contextMovieIndex = -1
    }

    Row(modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
        )

        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxSize()
                .roundedPanel(fillColor = ContentPanelFill)
                // Entering this pane must land on a poster, never the search bar: prefer the
                // last-focused movie, else the first one. onEnter fires only for directional entry
                // from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                // Held Up/Down can outrun the lazy grid's composition and escape this pane
                // (landing on the top bar) — trap vertical exits; Left/Right/Back leave normally.
                .trapVerticalFocusExit()
                .focusGroup()
                .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
        ) {
            Text("Movies / ${selectedItem?.title ?: "All"}", style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedItem?.abbr ?: "ALL"} (${formatCount(count)} movies)",
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = "Search ${selectedItem?.title ?: "movies"}…",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort, playlistLabel = "Provider")
                Spacer(Modifier.width(10.dp))
                // View mode (#10): poster wall vs a compact list (more titles at once).
                tv.own.owntv.ui.components.OwnTVButton(
                    label = viewMode.label,
                    onClick = vm::toggleViewMode,
                    icon = if (viewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.MOVIES,
                    style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (movies.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No movies found for “${searchQuery.trim()}”" else "No movies here.",
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(movies.itemCount) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            MovieListRow(
                                movie = movie,
                                isFavorite = favoriteIds.contains(movie.id),
                                modifier = when {
                                    movie.id == contextMovieId -> Modifier.focusRequester(contextFocus)
                                    movie.id == selectedMovie?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                                onLongClick = { contextMovie = movie; contextMovieId = movie.id; contextMovieIndex = index },
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(movies.itemCount) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            PosterCard(
                                posterUrl = movie.posterUrl,
                                title = movie.name,
                                rating = movie.rating,
                                isFavorite = favoriteIds.contains(movie.id),
                                modifier = when {
                                    movie.id == contextMovieId -> Modifier.focusRequester(contextFocus)
                                    movie.id == selectedMovie?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                                onLongClick = { contextMovie = movie; contextMovieId = movie.id; contextMovieIndex = index },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize().roundedPanel(fillColor = PreviewPanelFill).padding(Dimens.GapLarge)) {
            MovieDetailsPane(
                movie = selectedMovie,
                meta = selectedMovieMeta?.takeIf { it.movieId == selectedMovie?.id }?.cache,
                tmdbWins = metadataMode.tmdbWins,
            )
        }
    }

    resumePrompt?.let { (m, pos) ->
        ResumeDialog(
            positionMs = pos,
            onResume = { resumePrompt = null; vm.play(m, pos); goFullscreen() },
            onStartOver = { resumePrompt = null; vm.play(m, 0); goFullscreen() },
            onDismiss = { resumePrompt = null },
        )
    }

    // Long-press a movie → context menu.
    contextMovie?.let { m ->
        val alreadyDownloaded = downloadStates[m.id] != null
        // TMDB Details is shown only when enrichment is on AND a confident match resolved for THIS movie.
        val cacheForM = selectedMovieMeta?.takeIf { it.movieId == m.id }?.cache
        MovieContextMenu(
            title = m.name,
            isFavorite = favoriteIds.contains(m.id),
            canMove = selectedKey is LiveKey.Folder || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            hasTmdbDetails = metadataMode.enrich && cacheForM != null,
            trailerKey = if (metadataMode.enrich) cacheForM?.trailerKey else null,
            canRefetchTmdb = metadataMode.enrich,
            onShowDetails = { contextMovie = null; detailsMovie = m },
            onToggleFavorite = { vm.toggleFavorite(m); contextMovie = null },
            onMove = { contextMovie = null; vm.enterMoveMode(m, selectedKey) },
            onHide = { vm.hideMovie(m); contextMovie = null },
            onRemoveFromHistory = { vm.removeFromHistory(m.id); contextMovie = null },
            onDownload = {
                contextMovie = null
                // Idempotent (§11.1): don't re-queue an existing download — nudge to the Downloads menu.
                if (alreadyDownloaded) {
                    toast.show("Already downloaded — check the Downloads menu.")
                } else vm.download(m)
            },
            onPlayExternal = { contextMovie = null; vm.playExternal(m) },
            onRefetch = {
                contextMovie = null
                toast.show("Refetching TMDB details…")
                vm.refetchMovieMeta(m)
            },
            onSetTmdbName = { contextMovie = null; setTmdbNameMovie = m },
            onPlayTrailer = { key -> contextMovie = null; trailerVideoKey = key },
            onDismiss = { contextMovie = null },
        )
    }

    // When the TMDB Details window closes, return focus to the movie it was opened from (the window
    // trapped focus, so without this it would fall to the sidebar).
    LaunchedEffect(detailsMovie) {
        if (detailsMovie == null && contextMovieId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }

    // Windowed TMDB details popup (§11.1) — read-only, Back exits.
    detailsMovie?.let { m ->
        val cache = selectedMovieMeta?.takeIf { it.movieId == m.id }?.cache
        MediaDetailsScreen(
            details = buildMovieDetails(m, cache, metadataMode.tmdbWins),
            onExit = { detailsMovie = null },
        )
    }

    // "Set TMDB name" override dialog (§11.2 U5b). Prefill once per target (saved override, else cleaned title).
    LaunchedEffect(setTmdbNameMovie) {
        if (setTmdbNameMovie == null && contextMovieId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    setTmdbNameMovie?.let { m ->
        var prefill by remember(m.id) { mutableStateOf<MovieViewModel.TmdbNamePrefill?>(null) }
        LaunchedEffect(m.id) { prefill = vm.movieTmdbNamePrefill(m) }
        prefill?.let { p ->
            SetTmdbNameDialog(
                initialTitle = p.title,
                initialYear = p.year,
                hasOverride = p.hasOverride,
                onSave = { title, year ->
                    setTmdbNameMovie = null
                    vm.setMovieTmdbName(m, title, year)
                    toast.show("Re-searching TMDB…")
                },
                onClear = {
                    setTmdbNameMovie = null
                    vm.clearMovieTmdbName(m)
                    toast.show("Re-searching TMDB…")
                },
                onDismiss = { setTmdbNameMovie = null },
            )
        }
    }

    // In-app trailer player (§7.3 U4) — fullscreen over everything; Back/Exit closes and refocuses the movie.
    LaunchedEffect(trailerVideoKey) {
        if (trailerVideoKey == null && contextMovieId != null) {
            withFrameNanos { }
            runCatching { contextFocus.requestFocus() }
        }
    }
    trailerVideoKey?.let { key ->
        TrailerPlayerScreen(videoKey = key, onExit = { trailerVideoKey = null })
    }

    // Move mode overlay.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = "Reorder movie",
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMoveUp = vm::moveUp,
            onMoveDown = vm::moveDown,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }

    InAppToast(toast)
}

@Composable
private fun MovieContextMenu(
    title: String,
    isFavorite: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    hasTmdbDetails: Boolean,
    trailerKey: String?,
    canRefetchTmdb: Boolean,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onDownload: () -> Unit,
    onPlayExternal: () -> Unit,
    onRefetch: () -> Unit,
    onSetTmdbName: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            .longPressMenuGuard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            OwnTVButton(
                if (isFavorite) "Remove from Favourites" else "Add to Favourites",
                onClick = onToggleFavorite, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.STAR,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (canMove) OwnTVButton("Move", onClick = onMove, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            if (isHistory) OwnTVButton("Remove from History", onClick = onRemoveFromHistory, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Hide", onClick = onHide, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Download", onClick = onDownload, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.fillMaxWidth())
            // Phase B: one-off external playback, independent of the global "External player" toggle.
            OwnTVButton("Play with external player", onClick = onPlayExternal, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.PLAY, modifier = Modifier.fillMaxWidth())
            // TMDB Details — only when a confident match resolved (§11.1).
            if (hasTmdbDetails) {
                Spacer(Modifier.height(4.dp))
                OwnTVButton("TMDB Details", onClick = onShowDetails, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.MENU, modifier = Modifier.fillMaxWidth())
            }
            // Play Trailer (§7.3 U4) — only when TMDB actually has a trailer for this title (§11.1 gating).
            trailerKey?.let { key ->
                OwnTVButton("Play Trailer", onClick = { onPlayTrailer(key) }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            }
            // Refetch TMDB details (§11.2 U5a) — always available when enrichment is on, so a "no match"
            // (7-day negative cache) or a stale match can be cleared and re-searched immediately.
            if (canRefetchTmdb) {
                OwnTVButton("Refetch TMDB details", onClick = onRefetch, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                // Set TMDB name (§11.2 U5b) — hand-type the exact title to override the auto-match.
                OwnTVButton("Set TMDB name", onClick = onSetTmdbName, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MovieDetailsPane(
    movie: MovieEntity?,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
) {
    val colors = OwnTVTheme.colors
    if (movie == null) {
        PreviewPane(hint = "Focus a movie to see details.")
        return
    }
    // Merge (§7.1 / §4.1). Provider+TMDB → provider wins (provider ?: tmdb); TMDB-only → tmdb wins
    // (tmdb ?: provider). TMDB fields are never written back to the content row.
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val posterArt = (if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster)
        ?: movie.backdropUrl?.takeIf { it.isNotBlank() }
        ?: tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
    val providerPlot = movie.plot?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: providerPlot else providerPlot ?: meta?.overview
    // Outer details Box carries the rounded panel (Phase 6); no clip/background here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.GapLarge),
    ) {
        // Tall portrait poster (like the list / a phone screen), centred in the pane.
        Box(modifier = Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!posterArt.isNullOrBlank()) {
                    AsyncImage(
                        model = posterArt,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(48.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(movie.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(metaLine(movie, meta, tmdbWins), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        // Genres & cast are TMDB-only (§7.1) — a whole layer the provider never had.
        val genres = jsonList(meta?.genresJson)
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(genres.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 6)
        }
        val cast = jsonList(meta?.castJson)
        if (cast.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Cast", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(cast.take(6).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.height(20.dp))
        // Display-only pane (§11.1): actions live on the poster — OK plays, long-press opens the menu
        // (Favorite / Download / TMDB Details). Keeping the pane non-focusable fixes grid→pane navigation.
        Text(
            "OK to play  ·  long-press for options",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

private fun metaLine(movie: MovieEntity, meta: tv.own.owntv.core.database.entity.MetadataCacheEntity? = null, tmdbWins: Boolean = false): String {
    val parts = mutableListOf<String>()
    // §7.1 / §4.1: precedence flips with the source mode.
    val year = if (tmdbWins) meta?.year ?: movie.year else movie.year ?: meta?.year
    val rating = if (tmdbWins) meta?.rating?.takeIf { it > 0 } ?: movie.rating?.takeIf { it > 0 }
        else movie.rating?.takeIf { it > 0 } ?: meta?.rating?.takeIf { it > 0 }
    year?.let { parts.add(it.toString()) }
    rating?.let { parts.add("★ %.1f".format(it)) }
    movie.durationSecs?.takeIf { it > 0 }?.let { secs ->
        val h = secs / 3600
        val m = (secs % 3600) / 60
        parts.add(if (h > 0) "${h}h ${m}m" else "${m}m")
    }
    return parts.joinToString("  •  ")
}

/** Build the fullscreen TMDB-details payload for a movie, applying the §7.1/§4.1 merge precedence. */
private fun buildMovieDetails(
    movie: MovieEntity,
    meta: tv.own.owntv.core.database.entity.MetadataCacheEntity?,
    tmdbWins: Boolean,
): tv.own.owntv.features.shell.components.MediaDetailsUi {
    val providerPoster = movie.posterUrl?.takeIf { it.isNotBlank() }
    val tmdbPoster = tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
    val poster = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster
    // Backdrop is TMDB-only (providers don't carry one); fall back to the provider's if it exists.
    val backdrop = tv.own.owntv.core.metadata.MetadataImages.backdrop(meta?.backdropPath)
        ?: movie.backdropUrl?.takeIf { it.isNotBlank() }
    val plot = if (tmdbWins) meta?.overview ?: movie.plot else movie.plot?.takeIf { it.isNotBlank() } ?: meta?.overview
    return tv.own.owntv.features.shell.components.MediaDetailsUi(
        title = movie.name,
        backdropUrl = backdrop,
        posterUrl = poster,
        metaLine = metaLine(movie, meta, tmdbWins),
        genres = jsonList(meta?.genresJson),
        plot = plot,
        cast = jsonList(meta?.castJson),
    )
}

/** Parse a stored JSON array of strings (genres/cast) back to a list; empty on null/blank/bad JSON. */
private fun jsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/** Compact one-line row used by the List view mode — fits many titles on screen at once (#10). */
@Composable
private fun MovieListRow(
    movie: MovieEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    movie.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val meta = metaLine(movie)
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
