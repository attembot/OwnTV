package tv.own.owntv.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionHttpServer
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

/**
 * Remote restore: opens the LAN companion server in backup-upload mode and shows the PIN, a QR of the
 * URL, and the URL text so a phone on the same Wi-Fi can send an OwnTV backup file to the TV. When an
 * upload arrives, [onBackupReceived] hands the saved file off to the normal restore flow (section
 * picker, password prompt). The listener stops automatically when this screen leaves composition.
 *
 * Shared by Settings → Backup & Restore and the first-run/add-profile setup wizard.
 */
@Composable
fun RemoteBackupRestoreScreen(
    state: CompanionServerState,
    backups: Flow<File>,
    onStart: (port: Int) -> Unit,
    onStop: () -> Unit,
    onBackupReceived: (File) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // Open a fresh backup-upload session on entry; stop it when the screen leaves.
    LaunchedEffect(Unit) { onStart(CompanionLink.DEFAULT_PORT) }
    LaunchedEffect(backups) { backups.collect(onBackupReceived) }
    DisposableEffect(Unit) { onDispose { onStop() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Restore from another device", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "On a phone or laptop on the same Wi-Fi, scan the QR (or open the URL), enter this PIN, " +
                        "choose your OwnTV backup file and tap Send to TV. You then pick what to restore here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                when (state) {
                    CompanionServerState.Idle, CompanionServerState.Starting -> {
                        Text("Opening server…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    is CompanionServerState.Listening -> {
                        Text("Enter this PIN in the browser", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 8.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        state.qr?.let { qr ->
                            Image(
                                bitmap = qr,
                                contentDescription = "QR code for the companion URL",
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Or open this URL", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        state.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    is CompanionServerState.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        OwnTVButton("Try again", onClick = { onStart(CompanionLink.DEFAULT_PORT) })
                    }
                    CompanionServerState.Locked -> {
                        Text(CompanionHttpServer.LOCKOUT_MESSAGE, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                        OwnTVButton("Start again with a new PIN", onClick = { onStart(CompanionLink.DEFAULT_PORT) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Remote export: the TV has written the backup to a cache file and is serving it over the companion
 * server. Shows the PIN, a QR of the URL, and the URL text so a phone/laptop on the same Wi-Fi can
 * open it, enter the PIN and download the file. The server is started by the ViewModel (after the
 * export finishes) and stopped when this screen leaves composition.
 */
@Composable
fun RemoteBackupExportScreen(
    state: CompanionServerState,
    preparing: Boolean,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onStop() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Download on another device", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "On a phone or laptop on the same Wi-Fi, scan the QR (or open the URL), enter this PIN, " +
                        "and download your backup file to keep it safe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                val listening = state as? CompanionServerState.Listening
                when {
                    preparing || (listening == null && state !is CompanionServerState.Failed) -> {
                        Text("Preparing backup…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                    listening != null -> {
                        Text("Enter this PIN in the browser", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listening.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 8.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        listening.qr?.let { qr ->
                            Image(
                                bitmap = qr,
                                contentDescription = "QR code for the companion URL",
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Or open this URL", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        listening.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    state is CompanionServerState.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                OwnTVButton("Done", onClick = onBack, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
