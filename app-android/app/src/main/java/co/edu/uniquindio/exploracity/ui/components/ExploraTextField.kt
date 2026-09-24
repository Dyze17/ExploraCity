package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Campo del sistema de diseño: etiqueta encima, alto mínimo 56 dp, radio 12.
 *
 * - Foco: borde 2 dp primary sobre el color de tarjeta.
 * - Error ([errorMessage]): borde 2 dp error, icono y mensaje junto al campo, anunciado con liveRegion
 *   y expuesto como `error()` en semantics.
 * - Deshabilitado: borde discontinuo, candado y [disabledReason] como texto de apoyo.
 * - Sin alturas fijas: crece con la escala de fuente.
 */
@Composable
fun ExploraTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val isError = enabled && errorMessage != null
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine()
    val textColor = if (enabled) scheme.onSurface else explora.iconSecondary

    val colors = OutlinedTextFieldDefaults.colors(
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        disabledLabelColor = explora.textPlaceholder,
        errorLabelColor = scheme.error,
        focusedPlaceholderColor = explora.textPlaceholder,
        unfocusedPlaceholderColor = explora.textPlaceholder,
        disabledPlaceholderColor = explora.textPlaceholder,
        errorPlaceholderColor = explora.textPlaceholder,
        focusedTrailingIconColor = explora.iconSecondary,
        unfocusedTrailingIconColor = explora.iconSecondary,
        disabledTrailingIconColor = explora.iconSecondary,
        errorTrailingIconColor = scheme.error,
        focusedSupportingTextColor = scheme.onSurfaceVariant,
        unfocusedSupportingTextColor = scheme.onSurfaceVariant,
        disabledSupportingTextColor = scheme.onSurfaceVariant,
        errorSupportingTextColor = scheme.error,
    )

    BasicTextField(
        state = state,
        modifier = modifier.semantics { if (isError) error(errorMessage.orEmpty()) },
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
        decorator = OutlinedTextFieldDefaults.decorator(
            state = state,
            enabled = enabled,
            lineLimits = lineLimits,
            outputTransformation = null,
            interactionSource = interactionSource,
            labelPosition = TextFieldLabelPosition.Above(),
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            trailingIcon = when {
                !enabled -> {
                    { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(22.dp)) }
                }
                isError -> {
                    { Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(22.dp)) }
                }
                else -> null
            },
            supportingText = when {
                isError -> {
                    {
                        Row(
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(16.dp.scaledWithFont()))
                            Text(
                                errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600, lineHeight = 18.sp),
                            )
                        }
                    }
                }
                !enabled && disabledReason != null -> {
                    { Text(disabledReason, style = MaterialTheme.typography.bodySmall) }
                }
                supportingText != null -> {
                    { Text(supportingText, style = MaterialTheme.typography.bodySmall) }
                }
                else -> null
            },
            isError = isError,
            colors = colors,
            contentPadding = PaddingValues(14.dp),
            container = {
                val shape = MaterialTheme.shapes.medium
                when {
                    !enabled -> Box(
                        Modifier
                            .background(scheme.surfaceContainerHigh, shape)
                            .dashedBorder(1.dp, explora.outlineDisabled, 12.dp),
                    )
                    isError -> Box(Modifier.background(explora.errorFieldContainer, shape).border(2.dp, scheme.error, shape))
                    focused -> Box(Modifier.background(scheme.surfaceContainerLowest, shape).border(2.dp, scheme.primary, shape))
                    else -> Box(Modifier.background(scheme.surface, shape).border(1.dp, scheme.outline, shape))
                }
            },
        ),
    )
}
