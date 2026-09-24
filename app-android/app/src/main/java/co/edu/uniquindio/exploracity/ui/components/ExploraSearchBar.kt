package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Buscador del feed: 52 dp, píldora sobre surfaceContainer, lupa, placeholder y borrar cuando hay texto.
 * [trailing] aloja la acción extra (botón de filtros, 9).
 */
@Composable
fun ExploraSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedPlaceholderColor = explora.textPlaceholder,
        unfocusedPlaceholderColor = explora.textPlaceholder,
        disabledPlaceholderColor = explora.textPlaceholder,
        focusedLeadingIconColor = scheme.onSurfaceVariant,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
        disabledLeadingIconColor = explora.textPlaceholder,
        focusedTrailingIconColor = scheme.onSurfaceVariant,
        unfocusedTrailingIconColor = scheme.onSurfaceVariant,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(22.dp)) },
                trailingIcon = when {
                    value.isNotEmpty() -> {
                        {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.search_clear),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    trailing != null -> trailing
                    else -> null
                },
                colors = colors,
                contentPadding = PaddingValues(vertical = 12.dp),
                container = { Box(Modifier.background(scheme.surfaceContainer, MaterialTheme.shapes.extraLarge)) },
            )
        },
    )
}
