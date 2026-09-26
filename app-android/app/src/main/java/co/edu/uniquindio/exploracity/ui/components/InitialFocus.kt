package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Para dar el foco inicial a un botón («Cancelar» en los diálogos destructivos, comentar en 13 desde 24). Un clickable
 * no toma el foco en modo táctil y `requestFocus()` no hace nada: esto lo vuelve enfocable también al tocar. Va antes del
 * clickable, en el modificador que recibe el botón.
 */
fun Modifier.initialFocus(requester: FocusRequester): Modifier = focusRequester(requester).focusProperties { canFocus = true }

/** Avisa cuando un campo pierde el foco (no al entrar): ahí se muestra su error (15.b, 23). */
@Composable
fun Modifier.onBlur(onBlur: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (focused && !state.isFocused) onBlur()
        focused = state.isFocused
    }
}
