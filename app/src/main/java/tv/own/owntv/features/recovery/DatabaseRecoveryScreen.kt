package tv.own.owntv.features.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/**
 * Shown instead of the shell when the database cannot be opened — a migration that failed, or a file
 * SQLite refuses. The app used to wipe itself in this situation (`fallbackToDestructiveMigration`);
 * it no longer does, so the user's data is still on the device and the fix is a code fix, not a
 * reinstall. This screen says exactly that and offers a retry, with a clearly-labelled last-resort
 * reset that only happens if the user asks for it twice.
 */
@Composable
fun DatabaseRecoveryScreen(
    message: String?,
    onRetry: () -> Unit,
    onResetData: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("OwnTV could not open its database", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Your profiles, playlists, favorites and history have NOT been deleted — this is a " +
                    "database upgrade failure, and the data is still on this device.\n\n" +
                    "Please report this so it can be fixed, and include the message below.",
                fontSize = 18.sp,
                color = Color(0xFFCFCFD6),
            )
            if (!message.isNullOrBlank()) {
                Text(message, fontSize = 14.sp, color = Color(0xFF8A8A94))
            }
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus).focusable()) {
                Text("Try again")
            }
            Text(
                "If you have a backup file you can reset the app and restore it afterwards. " +
                    "Resetting deletes everything stored on this device.",
                fontSize = 14.sp,
                color = Color(0xFF8A8A94),
            )
            Button(onClick = { if (confirmingReset) onResetData() else confirmingReset = true }) {
                Text(if (confirmingReset) "Confirm: erase all app data" else "Reset app data (last resort)")
            }
        }
    }
}
