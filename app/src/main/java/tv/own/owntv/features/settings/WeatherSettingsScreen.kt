package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.roundedPanel

/**
 * Weather settings — the top-bar weather chip: show/hide, manual location override, and °C/°F.
 * (Grew out of two loose rows on the Settings root; grouped here as its own submenu.)
 */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.weatherEnabled.collectAsStateWithLifecycle()
    val location by vm.weatherLocation.collectAsStateWithLifecycle()
    val fahrenheit by vm.weatherFahrenheit.collectAsStateWithLifecycle()

    var showLocation by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val locationRowFocus = remember { FocusRequester() }
    // The opener row for the location dialog, captured when the dialog opens and consumed when it
    // closes — so focus returns to the "Custom location" row, not the "Show weather" first row.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(showLocation) {
        // Single owner of dialogReturn: set it when opening, consume it (with a clear) when closing.
        // Previously onEnter also read+cleared it, racing this effect — whoever fired first won and
        // the other no-op'd, so the restore sometimes landed on the wrong row.
        if (showLocation) {
            dialogReturn = locationRowFocus
        } else {
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
            dialogReturn = null
        }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter handles ONLY genuine external (directional) entry — it targets the first row.
            // Programmatic dialog-close restore is owned by the LaunchedEffect above (onEnter does not
            // fire for programmatic requestFocus), so it must not consult dialogReturn.
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Header("Weather", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Top bar weather")
        Row2(
            icon = OwnTVIcon.EPG, title = "Show weather",
            desc = "Display the current weather in the top bar.",
            chip = if (enabled) "On" else "Off", primaryChip = enabled,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setWeatherEnabled(!enabled) },
        )
        Row2(
            icon = OwnTVIcon.EPG, title = "Custom location",
            desc = "Override the city used for weather. Leave blank to auto-detect, or enter a city " +
                "(e.g. London) or \"lat,lon\" (e.g. 51.5,-0.12). Useful on a VPN, where auto-detect " +
                "resolves to the server's city.",
            chip = location.ifBlank { "Auto" }, primaryChip = false, chevron = true,
            modifier = Modifier.focusRequester(locationRowFocus),
            onClick = { showLocation = true },
        )
        Row2(
            icon = OwnTVIcon.EPG, title = "Temperature unit",
            desc = "Show the temperature in Celsius or Fahrenheit.",
            chip = if (fahrenheit) "°F" else "°C", primaryChip = true,
            onClick = { vm.setWeatherFahrenheit(!fahrenheit) },
        )
    }

    if (showLocation) {
        TextInputDialog(
            title = "Custom location",
            initial = location,
            label = "City or lat,lon",
            hint = "Leave blank to auto-detect from your public IP. Enter a city (e.g. London) or \"lat,lon\" (e.g. 51.5,-0.12).",
            onConfirm = { vm.setWeatherLocation(it); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
}
