package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * Inline search field for a section, TV-style: the pill itself takes D-pad focus like any other
 * control — the keyboard only opens when the user presses OK on it (the inner text field is not
 * focusable until then), so focus can pass through / land on search without an IME popup.
 *
 * Liquid Glass: when [surface] is in scope the pill frosts (translucent fill + specular highlight)
 * and the focus ring becomes a bright white glass rim; otherwise it keeps its solid tonal fill +
 * accent/outline border. Defaults to [GlassSurface.CARDS] (most search bars live on a content panel);
 * pass [GlassSurface.DIALOGS] for one inside a popup.
 *
 * @param surface glass surface to frost with, or null to stay flat.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    surface: GlassSurface? = GlassSurface.CARDS,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pillFocused by interaction.collectIsFocusedAsState()
    var editing by remember { mutableStateOf(false) }
    val pillFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(50)
    val focused = pillFocused || editing
    // Glassy only when a surface is given and it's in the active glass scope (matches FocusableSurface).
    val glassy = surface != null && LocalGlass.current.isGlassy(surface)
    // Liquid glass reads as glass because a bright hairline lenses the whole edge at all times, not
    // just on focus. So when glassy: a faint white rim always, brightening on focus.
    val borderColor = when {
        glassy && focused -> Color.White.copy(alpha = 0.5f)
        glassy -> Color.White.copy(alpha = 0.22f)
        focused -> colors.primary
        else -> colors.outlineVariant
    }

    // Enter edit mode after recomposition has made the field focusable (canFocus = editing).
    LaunchedEffect(editing) {
        if (editing) runCatching {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .then(
                // Frosted glass pill when this surface is glassy; plain tonal fill otherwise. glass()
                // early-returns on a transparent fill, but our fill is always opaque here.
                if (surface != null) Modifier.glass(surface = surface, baseFill = colors.surfaceContainerHigh, shape = shape, frostScale = 0.6f)
                else Modifier.background(colors.surfaceContainerHigh)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .focusRequester(pillFocus)
            .clickable(interactionSource = interaction, indication = null) { editing = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(
                icon = OwnTVIcon.SEARCH,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (editing && !it.isFocused) editing = false }
                        .onPreviewKeyEvent {
                            // After the IME closed itself, Back hands focus back to the pill
                            // instead of bubbling to the screen's BackHandler.
                            if (it.key == Key.Back) {
                                if (it.type == KeyEventType.KeyUp) {
                                    editing = false
                                    runCatching { pillFocus.requestFocus() }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        editing = false
                        runCatching { pillFocus.requestFocus() }
                    }),
                )
            }
        }
    }
}
