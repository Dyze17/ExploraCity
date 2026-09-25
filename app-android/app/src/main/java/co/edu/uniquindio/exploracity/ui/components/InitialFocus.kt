package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Para dar el foco inicial a un botón («Cancelar» en los diálogos destructivos, comentar en 13 desde 24). Un clickable
 * no toma el foco en modo táctil y `requestFocus()` no hace nada: esto lo vuelve enfocable también al tocar. Va antes del
 * clickable, en el modificador que recibe el botón.
 */
fun Modifier.initialFocus(requester: FocusRequester): Modifier = focusRequester(requester).focusProperties { canFocus = true }
