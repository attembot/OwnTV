package tv.own.owntv.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The docked mini-player (dock-to-corner model). The mpv surface is rendered behind this by the shell;
 * this just overlays a title and a small focusable control row (play/pause · expand · close). Navigate
 * to it with the D-pad to use it; expand returns to fullscreen.
 */
@Composable
fun MiniPlayer(
    player: PlaybackEngine,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onCycleSize: () -> Unit = {},
    onCyclePosition: () -> Unit = {},
    onAudioMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    // Show the title + controls only while the mini-player has D-pad focus; otherwise fade to just the
    // video. The controls stay composed (and focusable) even when hidden, so pressing toward the
    // mini-player lands focus on a button and reveals them.
    var focused by remember { mutableStateOf(false) }
    val controlsAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "miniControls")
    Box(modifier = modifier.onFocusChanged { focused = it.hasFocus }.focusGroup()) {
        // Title (top-left, on a slight scrim). Padded left to clear the Audio-Mode button and right so
        // it never sits under the window controls in the top-right corner.
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().alpha(controlsAlpha)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .padding(start = 48.dp, end = 96.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(meta.title ?: "", style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
        }

        // Audio Mode (top-left): switch to audio-only and surface the top-bar now-playing bar.
        Row(
            modifier = Modifier.align(Alignment.TopStart).alpha(controlsAlpha).padding(6.dp).focusGroup(),
        ) {
            MiniBtn(OwnTVIcon.HEADPHONES, onClick = onAudioMode)
        }

        // Window controls (top-right): resize (cycles size) and move (cycles the 6 positions). Kept up
        // here so the bottom row stays short enough to fit even the smallest mini-player size.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).alpha(controlsAlpha).padding(6.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniBtn(OwnTVIcon.ZOOM, onClick = onCycleSize)
            MiniBtn(OwnTVIcon.PIP, onClick = onCyclePosition)
        }

        // Playback controls (bottom): play/pause, volume (single mute/unmute toggle), fullscreen, close.
        // Horizontally scrollable so the buttons are always reachable (never clipped) at tiny sizes.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().alpha(controlsAlpha)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(8.dp).horizontalScroll(rememberScrollState()).focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniBtn(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY) { player.togglePlayPause() }
            // Single mute/unmute toggle — volume icon (not the audio-track icon).
            MiniBtn(if (volume <= 0) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH, onClick = { player.toggleMute() })
            MiniBtn(OwnTVIcon.FULLSCREEN, onClick = onExpand)
            MiniBtn(OwnTVIcon.CLOSE, onClick = onClose)
        }
    }
}

@Composable
private fun MiniBtn(icon: OwnTVIcon, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        focusedScale = 1.12f,
        focusedContainerColor = Color.White.copy(alpha = 0.28f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
        selectedContainerColor = Color.White.copy(alpha = 0.12f),
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVIcon(icon, tint = Color.White, filled = true, modifier = Modifier.size(18.dp))
    }
}
