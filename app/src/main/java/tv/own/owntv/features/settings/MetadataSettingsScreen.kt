package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * TMDB content languages (ISO 639-1, region-qualified where TMDB's coverage is meaningfully better for
 * one — e.g. pt-BR). "" keeps TMDB's own default (en-US), which is what installs used before this setting
 * existed, so an upgrade never silently changes anyone's metadata.
 *
 * Distinct from VideoPlayerSettingsScreen's LANGUAGES list, which uses 3-letter codes for audio/subtitle
 * track matching — TMDB only accepts 2-letter tags.
 */
private val TMDB_LANGUAGES = listOf(
    "" to "Default (English)",
    MetadataConfig.LANGUAGE_AUTO to "Device language",
    "ar" to "Arabic",
    "bg" to "Bulgarian",
    "zh" to "Chinese",
    "hr" to "Croatian",
    "cs" to "Czech",
    "da" to "Danish",
    "nl" to "Dutch",
    "en" to "English",
    "et" to "Estonian",
    "fi" to "Finnish",
    "fr" to "French",
    "de" to "German",
    "el" to "Greek",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "lv" to "Latvian",
    "lt" to "Lithuanian",
    "ms" to "Malay",
    "no" to "Norwegian",
    "fa" to "Persian",
    "pl" to "Polish",
    "pt-BR" to "Portuguese (Brazil)",
    "pt-PT" to "Portuguese (Portugal)",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sr" to "Serbian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "es" to "Spanish",
    "es-MX" to "Spanish (Latin America)",
    "sv" to "Swedish",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "vi" to "Vietnamese",
)

private fun tmdbLangName(code: String) =
    TMDB_LANGUAGES.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Default (English)" }

/**
 * Settings → Metadata (TMDB). Phase M1 of the enrichment plan: the master toggle and the two advanced
 * access tiers (own TMDB key / self-host URL), plus a manual "look up title" test that proves the
 * configured tier reaches TMDB end-to-end. Enrichment of actual detail screens arrives in later phases.
 *
 * Precedence (plan §4): self-host URL > own key > the default caching Worker (zero setup).
 */
@Composable
fun MetadataSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val storedKey by vm.tmdbApiKey.collectAsStateWithLifecycle()
    val storedUrl by vm.metadataServerUrl.collectAsStateWithLifecycle()
    val tier by vm.metadataTier.collectAsStateWithLifecycle()
    val testState by vm.metadataTest.collectAsStateWithLifecycle()
    val language by vm.metadataLanguage.collectAsStateWithLifecycle()

    var showLangPicker by remember { mutableStateOf(false) }
    var langPickerWasOpen by remember { mutableStateOf(false) }
    val langRowFocus = remember { FocusRequester() }

    // Seed the editable fields once; local edit → Save persists (same pattern as NetworkSettingsScreen).
    var seeded by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var testTitle by remember { mutableStateOf("Oppenheimer") }
    // Advanced options are hidden by default. Auto-expand if the user already has a key/URL saved, so the
    // fields aren't silently hidden when they're actually in use.
    var showAdvanced by remember { mutableStateOf(false) }
    LaunchedEffect(storedKey, storedUrl) {
        if (!seeded) {
            key = storedKey; url = storedUrl
            if (storedKey.isNotBlank() || storedUrl.isNotBlank()) showAdvanced = true
            seeded = true
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter + focusGroup: a safety net for two dispose-on-collapse paths — (1) toggling
            // "Advanced options" off while focus is on a field inside it, and (2) switching Metadata
            // mode to PROVIDER (mode.enrich=false), which disposes the whole advanced block + the row
            // the user clicked. Either path leaves focus dangling; onEnter recaptures it onto the
            // always-composed first mode row whenever directional focus re-enters the group.
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header("Metadata (TMDB)", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Metadata source")
        Text(
            "Choose where Movies & Series details come from.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        tv.own.owntv.core.metadata.MetadataMode.entries.forEachIndexed { i, m ->
            val selected = m == mode
            Row2(
                icon = if (m == tv.own.owntv.core.metadata.MetadataMode.PROVIDER) OwnTVIcon.PLAYLIST else OwnTVIcon.VIDEO,
                title = m.label,
                desc = when (m) {
                    tv.own.owntv.core.metadata.MetadataMode.PROVIDER -> "Use only what your playlist provides. No TMDB lookups."
                    tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB -> "Your playlist's info wins; TMDB fills the gaps and adds cast, genres & backdrops."
                    tv.own.owntv.core.metadata.MetadataMode.TMDB_ONLY -> "Prefer TMDB for every field; fall back to your playlist only when TMDB has nothing."
                },
                chip = if (selected) "Selected" else null, primaryChip = selected,
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { vm.setMetadataMode(m); vm.resetMetadataTest() },
            )
        }

        // The advanced TMDB tier fields only make sense when TMDB is on (mode != Provider).
        if (mode.enrich) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Active TMDB source: ${tier.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primary,
        )

        Spacer(Modifier.height(16.dp))
        Row2(
            icon = OwnTVIcon.SUBTITLE,
            title = "Metadata language",
            desc = "Language for plots, genres & titles from TMDB. Fields TMDB hasn't translated stay " +
                "in English. Changing this clears the cached details so they refetch.",
            chip = tmdbLangName(language), chevron = true,
            modifier = Modifier.focusRequester(langRowFocus),
            onClick = { showLangPicker = true },
        )

        Spacer(Modifier.height(16.dp))
        Row2(
            icon = OwnTVIcon.SETTINGS,
            title = "Advanced options",
            desc = "Use your own TMDB API key or a self-hosted server instead of the shared default.",
            chip = if (showAdvanced) "On" else "Off", primaryChip = showAdvanced,
            onClick = { showAdvanced = !showAdvanced },
        )
        if (showAdvanced) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Leave both blank to use the built-in shared server (no setup). Enter your own TMDB API key " +
                    "to call TMDB directly, or a self-hosted server URL to use your own proxy/mirror. A URL " +
                    "takes priority over a key.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OwnTVTextField(
                value = key,
                onValueChange = { key = it },
                label = "TMDB API key (v3)",
                placeholder = "optional",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OwnTVTextField(
                value = url,
                onValueChange = { url = it },
                label = "Self-host server URL",
                placeholder = "https://your-worker.example.workers.dev",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OwnTVButton("Save", onClick = {
                vm.setTmdbApiKey(key)
                vm.setMetadataServerUrl(url)
                vm.resetMetadataTest()
            })
        }

        Spacer(Modifier.height(20.dp))
        GroupLabel("Test")
        OwnTVTextField(
            value = testTitle,
            onValueChange = { testTitle = it },
            label = "Look up a movie title",
            placeholder = "Oppenheimer",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OwnTVButton(
                label = if (testState is SettingsViewModel.MetadataTestState.Testing) "Looking up…" else "Test lookup",
                onClick = { vm.testMetadataLookup(testTitle) },
                style = OwnTVButtonStyle.SECONDARY,
            )
            MetadataTestLabel(testState)
        }
        } // end if (mode.enrich)

        Spacer(Modifier.height(24.dp))
        // TMDB attribution (plan §8) — logo + line, required by TMDB's API terms.
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_tmdb_logo),
            contentDescription = "TMDB",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }

    if (showLangPicker) {
        // searchable: the list is long enough that D-pad scrolling to e.g. Ukrainian is tedious.
        PickerDialog(
            title = "Metadata language",
            options = TMDB_LANGUAGES,
            selected = language,
            searchable = true,
            onSelect = {
                if (it != language) vm.setMetadataLanguage(it)
                showLangPicker = false
            },
            onDismiss = { showLangPicker = false },
        )
    }
    // Return focus to the language row after the dialog closes, rather than letting it fall to the
    // screen's first mode row (same pattern as WeatherSettingsScreen's location dialog). Gated on
    // langPickerWasOpen so this doesn't fire on first composition and steal focus from firstFocus.
    LaunchedEffect(showLangPicker) {
        if (showLangPicker) {
            langPickerWasOpen = true
        } else if (langPickerWasOpen) {
            langPickerWasOpen = false
            kotlinx.coroutines.delay(80)
            runCatching { langRowFocus.requestFocus() }
        }
    }
}

@Composable
private fun MetadataTestLabel(state: SettingsViewModel.MetadataTestState) {
    val colors = OwnTVTheme.colors
    val (text, color) = when (state) {
        is SettingsViewModel.MetadataTestState.Ok -> "Match: ${state.summary}" to colors.primary
        is SettingsViewModel.MetadataTestState.Fail -> state.message to androidx.compose.ui.graphics.Color(0xFFEF4444)
        else -> null to colors.onSurfaceVariant
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
