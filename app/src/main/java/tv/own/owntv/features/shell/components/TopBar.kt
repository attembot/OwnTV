package tv.own.owntv.features.shell.components

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.core.weather.WeatherInfo
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass
import tv.own.owntv.ui.theme.ownTvTween
import java.util.Date

// Top-bar chips: corner matches the nav buttons (14dp, not full-pill) and a lighter frost than the
// big panels so the small chrome reads as glass without being heavy.
private val TopBarChipCorner = 14.dp
private const val TopBarFrost = 0.45f

/** Always-on faint white glass edge for the display-only chips, only while the top bar is glassy. */
@Composable
private fun Modifier.topBarGlassRim(shape: Shape): Modifier =
    if (LocalGlass.current.isGlassy(GlassSurface.TOPBAR)) border(1.dp, Color.White.copy(alpha = 0.18f), shape) else this

@Composable
fun TopBar(
    sectionLabel: String,
    onSearchClick: () -> Unit,
    playlistName: String,
    weatherInfo: WeatherInfo? = null,
    weatherFahrenheit: Boolean = false,
    searchVisible: Boolean = true,
    playlistInteractive: Boolean = false,
    onPlaylistClick: () -> Unit = {},
    // Batch 7 — shared "Continue" chip (resume last movie/episode/channel). Null label = nothing to resume.
    continueLabel: String? = null,
    continueIcon: OwnTVIcon = OwnTVIcon.PLAY,
    onContinueClick: () -> Unit = {},
    // Audio Mode (plan §8): the now-playing bar, shown left of the weather chip while PlayerMode.AUDIO
    // is active. Null = not in Audio Mode.
    audioBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Row(
        // Left padding = 0 so the section chip's left edge lines up with panel-1 (the category
        // column) directly below it; keep the right inset for the weather/clock/playlist chips.
        modifier = modifier.fillMaxWidth().padding(start = 0.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionChip(label = sectionLabel)
            SearchPill(onClick = onSearchClick, visible = searchVisible)
            // Only focusable while the nav panel holds focus (same rule as the search pill) so it can
            // never trap D-pad focus inside a section.
            if (continueLabel != null) {
                ContinueChip(label = continueLabel, icon = continueIcon, onClick = onContinueClick, visible = searchVisible)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            audioBar?.invoke()
            if (weatherInfo != null) WeatherChip(info = weatherInfo, fahrenheit = weatherFahrenheit)
            ClockChip()
            if (playlistName.isNotBlank()) {
                PlaylistChip(label = playlistName, interactive = playlistInteractive, onClick = onPlaylistClick)
            }
        }
    }
}

@Composable
private fun SectionChip(label: String) {
    val colors = OwnTVTheme.colors
    // Keeps its accent tint (marks the current section) but frosts in glass mode like the other chips.
    val shape = RoundedCornerShape(TopBarChipCorner)
    Box(Modifier.clip(shape).glass(GlassSurface.TOPBAR, colors.primaryContainer, shape, frostScale = TopBarFrost).topBarGlassRim(shape).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, visible: Boolean) {
    val colors = OwnTVTheme.colors
    // Fade instead of remove: the pill keeps its space so the top-bar row never shifts, and it
    // becomes unfocusable while hidden so an escaping vertical focus search can never land on it.
    val alpha by animateFloatAsState(if (visible) 1f else 0f, ownTvTween(160), label = "searchPillAlpha")
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .focusProperties { canFocus = visible },
        shape = RoundedCornerShape(TopBarChipCorner),
        surface = GlassSurface.TOPBAR,
        glassFrostScale = TopBarFrost,
        glassIdleRimAlpha = 0.18f,
        // Neutral when idle; accent fill only when focused (matches the playlist selector).
        focusedContainerColor = colors.primaryContainer,
        unfocusedContainerColor = colors.surfaceContainer.copy(alpha = 0.6f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        val fg = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnTVIcon(icon = OwnTVIcon.SEARCH, tint = fg, modifier = Modifier.size(16.dp))
            Text("Search", style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
private fun ContinueChip(label: String, icon: OwnTVIcon, onClick: () -> Unit, visible: Boolean) {
    val colors = OwnTVTheme.colors
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "continueChipAlpha")
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .focusProperties { canFocus = visible },
        shape = RoundedCornerShape(TopBarChipCorner),
        surface = GlassSurface.TOPBAR,
        glassFrostScale = TopBarFrost,
        glassIdleRimAlpha = 0.18f,
        focusedContainerColor = colors.primary,
        unfocusedContainerColor = colors.primaryContainer.copy(alpha = 0.6f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fg = if (focused) colors.onPrimary else colors.onPrimaryContainer
            OwnTVIcon(icon = icon, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ClockChip() {
    val colors = OwnTVTheme.colors
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val formatted = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    // Display-only (non-focusable) and neutral (no accent), matching the weather chip. Frosts in
    // glass mode (TOPBAR surface) so it reads as glass like the focusable chips.
    val shape = RoundedCornerShape(TopBarChipCorner)
    Box(Modifier.clip(shape).glass(GlassSurface.TOPBAR, colors.surfaceContainer.copy(alpha = 0.6f), shape, frostScale = TopBarFrost).topBarGlassRim(shape).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(formatted, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistChip(label: String, interactive: Boolean = false, onClick: () -> Unit = {}) {
    val colors = OwnTVTheme.colors
    // Static badge when there's only one playlist (nothing to switch); a focusable button with a chevron
    // when there are 2+, opening the playlist quick-switcher.
    if (!interactive) {
        // Neutral display-only badge (no always-on accent), frosts in glass mode like the other chips.
        val shape = RoundedCornerShape(TopBarChipCorner)
        Box(Modifier.clip(shape).glass(GlassSurface.TOPBAR, colors.surfaceContainer.copy(alpha = 0.6f), shape, frostScale = TopBarFrost).topBarGlassRim(shape).padding(horizontal = 14.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        return
    }
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(TopBarChipCorner),
        surface = GlassSurface.TOPBAR,
        glassFrostScale = TopBarFrost,
        glassIdleRimAlpha = 0.18f,
        // Neutral when idle; accent fill only when focused (no always-on accent).
        focusedContainerColor = colors.primaryContainer,
        unfocusedContainerColor = colors.surfaceContainer.copy(alpha = 0.6f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        val fg = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
            OwnTVIcon(icon = OwnTVIcon.CHEVRON, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WeatherChip(info: WeatherInfo, fahrenheit: Boolean) {
    val colors = OwnTVTheme.colors
    val temp = if (fahrenheit) "${(info.temperatureC * 9 / 5 + 32).toInt()}°F" else "${info.temperatureC.toInt()}°C"
    val location = if (info.city.isNotBlank()) " · ${info.city}" else ""
    val shape = RoundedCornerShape(TopBarChipCorner)
    Box(Modifier.clip(shape).glass(GlassSurface.TOPBAR, colors.surfaceContainer.copy(alpha = 0.6f), shape, frostScale = TopBarFrost).topBarGlassRim(shape).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherConditionIcon(info = info, Modifier.size(16.dp))
            Text("$temp$location", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun WeatherConditionIcon(info: WeatherInfo, modifier: Modifier = Modifier) {
    val key = info.symbolKey()
    val sunC = Color(0xFFFFD166); val moonC = Color(0xFFDDF8FF)
    val cloudC = Color(0xFFDDEFE9); val rainC = Color(0xFF76A7FF)
    val snowC = Color(0xFFF0FCFF); val fogC = Color(0xFFDDF8FF)
    val thunderC = Color(0xFFFFD166)

    Canvas(modifier) {
        val s = size.minDimension / 100f
        val fill = androidx.compose.ui.graphics.drawscope.Fill
        val stk = Stroke(width = 4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        fun sun(cx: Float, cy: Float, r: Float) {
            for (i in 0 until 10) { val a = i * kotlin.math.PI.toFloat() / 5f; drawLine(sunC, o(cx + kotlin.math.cos(a) * (r + 8f), cy + kotlin.math.sin(a) * (r + 8f)), o(cx + kotlin.math.cos(a) * (r + 20f), cy + kotlin.math.sin(a) * (r + 20f)), strokeWidth = 4f * s, cap = StrokeCap.Round) }
            drawCircle(sunC, r * s, o(cx, cy))
        }
        fun moon(cx: Float, cy: Float, r: Float) {
            drawCircle(moonC, r * s, o(cx, cy))
            drawCircle(Color.Black, (r * 0.92f * s), o(cx + r * 0.45f * s, cy - r * 0.20f * s), style = fill, blendMode = BlendMode.Clear)
            listOf(-0.42f to -0.28f, -0.20f to 0.25f, 0.02f to -0.06f).forEach { (dx, dy) -> drawCircle(moonC.copy(alpha = 0.55f), 2.2f * s, o(cx + dx * r, cy + dy * r)) }
        }
        fun cloud(cx: Float, cy: Float, k: Float) {
            drawCircle(cloudC, 16f * k * s, o(cx - 19f * k, cy + 5f * k))
            drawCircle(cloudC, 23f * k * s, o(cx, cy - 9f * k))
            drawCircle(cloudC, 18f * k * s, o(cx + 24f * k, cy + 2f * k))
            drawCircle(cloudC, 13f * k * s, o(cx + 39f * k, cy + 10f * k))
        }
        fun drops(cx: Float, cy: Float, n: Int, c: Color) {
            for (i in 0 until n) drawLine(c, o(cx + i * 18f, cy), o(cx - 5f + i * 18f, cy + 18f), strokeWidth = 4f * s, cap = StrokeCap.Round)
        }
        fun snow(cx: Float, cy: Float) {
            for (i in 0 until 3) { val x = cx + i * 20f; val y = cy + (i % 2) * 4f; val w = 3f * s; val c = StrokeCap.Round; drawLine(snowC, o(x - 7f, y), o(x + 7f, y), w, c); drawLine(snowC, o(x, y - 7f), o(x, y + 7f), w, c); drawLine(snowC, o(x - 5f, y - 5f), o(x + 5f, y + 5f), w, c); drawLine(snowC, o(x + 5f, y - 5f), o(x - 5f, y + 5f), w, c) }
        }
        fun fog(cx: Float, cy: Float) {
            for (i in 0 until 4) drawLine(fogC.copy(alpha = 0.74f), o(cx - 38f, cy + i * 12f), o(cx + 38f, cy + i * 12f), strokeWidth = 5f * s, cap = StrokeCap.Round)
        }
        fun bolt(cx: Float, cy: Float) { val p = Path().apply { moveTo(cx * s, cy * s); lineTo((cx - 12f) * s, (cy + 26f) * s); lineTo((cx + 1f) * s, (cy + 23f) * s); lineTo((cx - 8f) * s, (cy + 48f) * s); lineTo((cx + 18f) * s, (cy + 15f) * s); lineTo((cx + 4f) * s, (cy + 18f) * s); close() }; drawPath(p, thunderC, style = fill) }

        when (key) {
            "sunny" -> sun(50f, 50f, 23f)
            "clearNight" -> moon(50f, 50f, 28f)
            "partlyDay" -> { sun(36f, 36f, 17f); cloud(56f, 60f, 1f) }
            "partlyNight" -> { moon(36f, 35f, 20f); cloud(56f, 60f, 1f) }
            "cloudy" -> { cloud(46f, 48f, 1.15f); cloud(60f, 62f, 0.82f) }
            "fog" -> { cloud(50f, 36f, 0.9f); fog(50f, 58f) }
            "drizzle" -> { cloud(50f, 38f, 1f); drops(35f, 62f, 3, rainC.copy(alpha = 0.72f)) }
            "rain" -> { cloud(50f, 36f, 1.05f); drops(30f, 60f, 4, rainC) }
            "snow" -> { cloud(50f, 36f, 1.05f); snow(32f, 65f) }
            "thunder" -> { cloud(50f, 35f, 1.05f); bolt(52f, 48f); drops(28f, 66f, 2, rainC) }
            else -> cloud(46f, 48f, 1.15f)
        }
    }
}
