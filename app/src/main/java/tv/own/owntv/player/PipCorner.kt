@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.own.owntv.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Hosts the [CornerEngine]'s video on its own [SurfaceView], **z-ordered above** the main player's
 * surface (`setZOrderMediaOverlay(true)`) so the corner draws on top of the full-screen stream behind it.
 *
 * Two overlapping SurfaceViews are the right tool here: both are hardware-overlay candidates, so on a TV
 * with ≥2 overlay planes (most) neither stream falls back to GPU composition. (Both engines document that a
 * *regular* view over a video surface knocks 4K off the direct scan-out path — a second SurfaceView avoids
 * that on capable hardware; on a single-plane device the compositor blends them, which still works, warmer.)
 */
@Composable
fun SecondaryVideoSurface(engine: CornerEngine, modifier: Modifier = Modifier, keepAwake: Boolean = true) {
    // key(engine): the factory captures `engine` in the holder callback ONCE. If Compose reused this
    // AndroidView node for a different engine (e.g. MultiView's dominant pane recomposing to another tile),
    // the surface would stay bound to the old engine and the new one would decode invisibly. Keying by
    // engine identity discards the node instead, so a fresh SurfaceView binds the right engine.
    androidx.compose.runtime.key(engine) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    // Must be set before the surface is created — lifts this surface above the main one behind it.
                    setZOrderMediaOverlay(true)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = engine.setSurface(holder.surface)
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) = engine.setSurface(null)
                    })
                }
            },
            update = { it.keepScreenOn = keepAwake },
        )
    }
}

/**
 * The picture-in-picture corner window: the second stream's video in a rounded, bordered box with a title
 * and a loading spinner, plus — when [showControls] is set — a focusable D-pad control row (audio · swap ·
 * close). During the browse UI the corner carries its own controls (navigate to it with the remote, like
 * the docked mini-player); while the main player is full-screen the player HUD drives these instead, so the
 * corner is rendered video-only ([showControls] = false).
 */
@Composable
fun PipCornerWindow(
    engine: CornerEngine,
    showControls: Boolean,
    audioOnCorner: Boolean,
    onToggleAudio: () -> Unit,
    onBrowse: () -> Unit,
    onMove: () -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val meta by engine.meta.collectAsStateWithLifecycle()
    val loading = state == CornerState.LOADING

    // The channel name shows briefly when the corner opens or changes channel, then fades to just video.
    var titleVisible by remember { mutableStateOf(true) }
    LaunchedEffect(meta.title) { titleVisible = true; delay(4000); titleVisible = false }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(
                width = if (audioOnCorner) 2.dp else 1.dp,
                color = if (audioOnCorner) tv.own.owntv.ui.theme.OwnTVTheme.colors.primary else Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        if (state == CornerState.ERROR) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't play", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else {
            SecondaryVideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 22)
            }
        }

        // Title strip (top) — channel name on a scrim, with the PiP marker and an audio badge (icons, not
        // emoji, so they render consistently in the TV system font and match the rest of the app's chrome).
        // Auto-hides a few seconds after opening / changing channel so it doesn't sit over the video forever.
        AnimatedVisibility(
            visible = titleVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OwnTVIcon(OwnTVIcon.PIP, tint = Color.White.copy(alpha = 0.85f), filled = true, modifier = Modifier.size(14.dp))
                if (audioOnCorner) {
                    OwnTVIcon(OwnTVIcon.VOLUME_HIGH, tint = tv.own.owntv.ui.theme.OwnTVTheme.colors.primary, filled = true, modifier = Modifier.size(14.dp))
                }
                Text(
                    meta.title ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        if (showControls) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(8.dp).focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PipBtn(OwnTVIcon.SWAP, onClick = onToggleAudio)   // move the sound between the two windows
                Spacer(Modifier.weight(1f))
                PipBtn(OwnTVIcon.PLAYLIST, onClick = onBrowse) // retune the corner stream from the playlist
                PipBtn(OwnTVIcon.MOVE, onClick = onMove)       // cycle the corner through the four screen corners
                PipBtn(OwnTVIcon.FULLSCREEN, onClick = onSwap)  // swap the corner stream into the main window
                PipBtn(OwnTVIcon.CLOSE, onClick = onClose)
            }
        }
    }
}

@Composable
private fun PipBtn(icon: OwnTVIcon, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        focusedScale = 1.15f,
        focusedContainerColor = tv.own.owntv.ui.theme.OwnTVTheme.colors.primary,
        unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
        selectedContainerColor = Color.White.copy(alpha = 0.14f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(
            icon,
            tint = if (focused) tv.own.owntv.ui.theme.OwnTVTheme.colors.onPrimary else Color.White,
            filled = true,
            modifier = Modifier.size(19.dp),
        )
    }
}
