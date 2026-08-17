package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps D-pad focus INSIDE this focus group for vertical moves: when a held Up/Down outruns a lazy
 * list's composition, Compose's focus search finds nothing above/below within the pane and would
 * escape to the nearest focusable outside it (e.g. the top bar). Cancelling the vertical exit pins
 * focus to the pane's edge row instead. Left/Right/Back still leave the pane normally, and moves
 * BETWEEN children inside the group are unaffected (onExit only fires when leaving the group).
 */
fun Modifier.trapVerticalFocusExit(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
            cancelFocusChange()
        }
    }
}

/**
 * Traps D-pad focus inside this group for ALL directions (Up/Down/Left/Right). Apply to a full-screen
 * modal scrim so a directional press can never escape into the UI behind it. Back is NOT affected — it
 * must still be handled by a BackHandler above (onExit only blocks directional exits).
 *
 * Use this (not [trapVerticalFocusExit]) on modals/dialogs/overlays where every direction must stay
 * inside the dialog. Inside the group, moves between children are unaffected (onExit only fires when
 * leaving the group).
 */
fun Modifier.trapAllFocusExit(): Modifier =
    // Modal scrims are the common host for popups. Consuming the IME inset here makes centred
    // content lay out above an on-screen keyboard; dialogPanel's verticalScroll keeps tall forms
    // reachable in the reduced height.
    imePadding().focusProperties { onExit = { cancelFocusChange() } }

/**
 * Remembers which control opened a dialog, and puts focus back on it once every dialog is closed.
 *
 * Every screen that opens a modal had its own copy of this: a nullable [FocusRequester] state, plus a
 * `LaunchedEffect` that waits for the dialog to leave the composition and then re-requests focus. On a
 * TV that restore is not cosmetic — a dialog dismissed with no focus target leaves the D-pad dead, or
 * drops the user at the top of a list they had scrolled deep into.
 *
 * Set the returned state when opening a dialog:
 * ```
 * onClick = { dialogFocus.value = sortRowFocus; showSortPicker = true }
 * ```
 *
 * The delay is what makes it work: the dialog's own window still owns focus for a frame or two after
 * `anyDialogOpen` flips, and a request issued inside that window is silently dropped. Screens that also
 * restore scroll position, or that arbitrate against a second focus source, keep their own effect —
 * this covers the plain case, which is most of them.
 */
@Composable
fun rememberDialogFocusRestore(
    anyDialogOpen: Boolean,
    delayMs: Long = 60,
): MutableState<FocusRequester?> {
    val target = remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(anyDialogOpen) {
        if (anyDialogOpen) return@LaunchedEffect
        target.value?.let { opener ->
            kotlinx.coroutines.delay(delayMs)
            runCatching { opener.requestFocus() }
        }
        target.value = null
    }
    return target
}

/** The focus requesters for a −/+ stepper pair, kept usable across the ends of the range. */
class StepperFocus(val minus: FocusRequester, val plus: FocusRequester)

/**
 * Keeps the D-pad alive on a −/+ stepper pair when one side disables at the end of its range.
 *
 * A disabled button cannot take focus. Inside a dialog that traps focus, the button holding focus going
 * disabled therefore left nothing focused and the D-pad dead with only Back working — the reported
 * "+/− unreachable at the top of the range". So: land on whichever side is usable, and hand focus to
 * the other side the moment the one holding it goes disabled.
 *
 * ```
 * val steppers = rememberStepperFocus(plusEnabled = value < max, minusEnabled = value > min)
 * StepButton("–", enabled = value > min, modifier = Modifier.focusRequester(steppers.minus)) { … }
 * StepButton("+", enabled = value < max, modifier = Modifier.focusRequester(steppers.plus)) { … }
 * ```
 */
@Composable
fun rememberStepperFocus(plusEnabled: Boolean, minusEnabled: Boolean): StepperFocus {
    val focus = remember { StepperFocus(FocusRequester(), FocusRequester()) }
    // "+" is the natural landing spot, but it can't take focus while disabled.
    LaunchedEffect(Unit) { runCatching { (if (plusEnabled) focus.plus else focus.minus).requestFocus() } }
    LaunchedEffect(plusEnabled) { if (!plusEnabled && minusEnabled) runCatching { focus.minus.requestFocus() } }
    LaunchedEffect(minusEnabled) { if (!minusEnabled && plusEnabled) runCatching { focus.plus.requestFocus() } }
    return focus
}
