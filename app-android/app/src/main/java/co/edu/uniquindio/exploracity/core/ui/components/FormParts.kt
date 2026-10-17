package co.edu.uniquindio.exploracity.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import kotlinx.coroutines.flow.drop

/** 3.c, 4, 5, 6.a y 6.b · Arriba, en ámbar: sin red no se puede seguir. [text] dice qué queda inhabilitado. */
@Composable
internal fun AccessOfflineNotice(text: String) {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        Modifier
            .fillMaxWidth()
            .background(warning.container)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), null, tint = warning.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.W600), color = warning.content)
    }
}

/** El ojo de 48 dp de los campos de contraseña: «Mostrar contraseña» / «Ocultar contraseña». */
@Composable
internal fun PasswordEye(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
            contentDescription = stringResource(if (revealed) R.string.login_conceal else R.string.login_reveal),
            tint = MaterialTheme.exploraColors.iconSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Lo escrito sube al ViewModel; lo que cambie allí por otra vía (una cuenta de prueba, el correo que trae el registro)
 * baja al campo. Lo que el ViewModel devuelve como eco de lo escrito no se vuelve a poner: llega tarde y borraría las
 * letras escritas mientras tanto. El texto con que el campo vuelve a componerse tampoco sube, para no pisar lo que el
 * ViewModel recibió mientras la pantalla no se veía.
 */
@Composable
internal fun SyncText(textState: TextFieldState, value: String, onChange: (String) -> Unit) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentValue by rememberUpdatedState(value)
    // Lo que subió y el ViewModel aún no devolvió, en orden.
    val pending = remember { ArrayDeque<String>() }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.drop(1).collect { text ->
            if (text != currentValue) pending.addLast(text)
            currentOnChange(text)
        }
    }
    LaunchedEffect(value) {
        val echo = pending.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { pending.removeFirst() }
            return@LaunchedEffect
        }
        pending.clear()
        if (textState.text.toString() != value) textState.setTextAndPlaceCursorAtEnd(value)
    }
}
