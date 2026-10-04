package tv.own.owntv.features.profiles

import androidx.compose.foundation.border
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVAvatars
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField

/** The profile dialogs' Stage popup: [title], [content], [buttons] bottom right. It scrolls, so small
 *  screens still reach the lower controls; the popup window keeps D-pad focus inside. */
@Composable
internal fun ProfileScrim(
    onDismiss: () -> Unit,
    title: String,
    width: androidx.compose.ui.unit.Dp = 864.mpx,
    buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, eyebrow = null, width = width, buttons = buttons, content = content)
}

/** Numeric PIN entry. Calls [onSubmit] with the entered digits. [compact] renders the small
 *  popup-menu treatment (narrow panel, Caladea font) used by the Customize screen. */
@Composable
internal fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean = false) {
    val dialog: @Composable () -> Unit = { PinDialogBody(title, onSubmit, onDismiss, compact) }
    if (compact) tv.own.owntv.ui.theme.PopupFontTheme(content = dialog) else dialog()
}

@Composable
private fun PinDialogBody(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Explicit start/end links: spatial D-pad search from Cancel would otherwise wander into
    // the gate's profile tiles BEHIND the dialog before reaching OK.
    val cancelFocus = remember { FocusRequester() }
    val okFocus = remember { FocusRequester() }
    ProfileScrim(
        onDismiss, title = title, width = if (compact) 560.mpx else 700.mpx,
        buttons = {
            OwnTVButton(
                stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(cancelFocus).focusProperties { end = okFocus },
            )
            OwnTVButton(
                stringResource(R.string.common_ok), onClick = { onSubmit(pin) }, enabled = pin.length >= 4,
                modifier = Modifier.focusRequester(okFocus).focusProperties { start = cancelFocus },
            )
        },
    ) {
        OwnTVTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = stringResource(R.string.profiles_pin),
            placeholder = stringResource(R.string.profiles_pin_placeholder),
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/**
 * Create / edit a profile: name, avatar, kids flag and an optional PIN. [initial] non-null = edit.
 * [onConfirm] receives (name, avatarId, isKids, pin): null = leave the PIN unchanged,
 * "" = remove the PIN lock, otherwise = set this PIN.
 *
 * [takenNames] are the OTHER profiles' names (lowercased) — profile names must be unique (they're the
 * merge key for backup restore), so a collision blocks Create/Save with an inline error.
 */
@Composable
internal fun ProfileEditorDialog(
    initial: ProfileEntity?,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?) -> Unit,
    onDismiss: () -> Unit,
    takenNames: Set<String> = emptySet(),
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: -1) } // Phase 7 — new profiles default to no-avatar
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val nameTaken = name.trim().isNotEmpty() && name.trim().lowercase() in takenNames

    ProfileScrim(
        onDismiss,
        title = stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit),
        buttons = {
            OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            OwnTVButton(
                label = stringResource(if (initial == null) R.string.profiles_create else R.string.profiles_save),
                onClick = { onConfirm(name, avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && !nameTaken && (removePin || pin.isEmpty() || pin.length >= 4),
            )
        },
    ) {
        OwnTVTextField(name, { name = it }, label = stringResource(R.string.profiles_name), placeholder = stringResource(R.string.profiles_name_hint), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        if (nameTaken) {
            Text(
                stringResource(R.string.profiles_name_taken),
                style = stageText(17, 600), color = StageColors.Danger,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
        tv.own.owntv.ui.stage.StagePopupLabel(stringResource(R.string.profiles_avatar).uppercase(), Modifier.padding(top = 18.mpx))
        val accent = stageAccent.accent
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.mpx), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.mpx)) {
            items((-1 until OwnTVAvatars.COUNT).toList()) { id -> // Phase 7 — includes "no avatar" (-1)
                tv.own.owntv.ui.stage.StageSurface(
                    onClick = { avatarId = id },
                    radius = 44.mpx,
                    modifier = Modifier.size(88.mpx),
                    focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                    idle = if (id == avatarId) Modifier.border(3.mpx, accent, CircleShape) else Modifier,
                    contentAlignment = Alignment.Center,
                ) { _ ->
                    OwnTVAvatar(avatarId = id, modifier = Modifier.size(70.mpx))
                }
            }
        }
        tv.own.owntv.ui.stage.StagePopupDivider()
        ToggleRow(label = stringResource(R.string.profiles_kids), desc = stringResource(R.string.profiles_kids_description), checked = isKids) { isKids = it }
        if (initial?.pinHash != null) {
            ToggleRow(label = stringResource(R.string.profiles_remove_pin), desc = stringResource(R.string.profiles_no_pin), checked = removePin) { removePin = it }
        }
        if (!removePin) {
            Spacer(Modifier.height(14.mpx))
            OwnTVTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = if (initial?.pinHash != null) stringResource(R.string.profiles_change_pin) else stringResource(R.string.profiles_optional_pin),
                placeholder = stringResource(R.string.profiles_pin_digits),
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** `.opt` with a switch on the right. */
@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = label, subtitle = desc, onClick = { onToggle(!checked) },
        trailing = { tv.own.owntv.ui.stage.StageSwitch(checked) },
    )
}
