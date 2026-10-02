package co.edu.uniquindio.exploracity.ui.screens.access

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
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
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

/** Estado de un requisito de la contraseña: icono y texto, nunca solo color (README 6.b). */
enum class RequirementState { PENDING, MET, MISSING }

/**
 * Un requisito de la contraseña (4 y 6.b). Pendiente: círculo vacío; cumplido: check en verde; si falta después de dejar
 * el campo: icono de error en rojo. El lector oye el requisito y su estado.
 */
@Composable
internal fun RequirementRow(text: String, state: RequirementState, modifier: Modifier = Modifier) {
    val (icon, color) = when (state) {
        RequirementState.PENDING -> R.drawable.ic_radio_button_unchecked to MaterialTheme.exploraColors.iconSecondary
        RequirementState.MET -> R.drawable.ic_check_circle to MaterialTheme.exploraColors.status.verified.content
        RequirementState.MISSING -> R.drawable.ic_error to MaterialTheme.colorScheme.error
    }
    val status = stringResource(
        when (state) {
            RequirementState.PENDING -> R.string.requirement_pending
            RequirementState.MET -> R.string.requirement_met
            RequirementState.MISSING -> R.string.requirement_missing
        },
    )
    val description = stringResource(R.string.requirement_description, text, status)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600), color = color)
    }
}

/** Icono grande en su cuadro redondeado (5, 6.a y 6C). Decorativo: el titular dice lo mismo. */
@Composable
internal fun AccessIcon(@DrawableRes icon: Int, container: Color, content: Color, size: Int = 88) {
    Box(Modifier.size(size.dp).background(container, RoundedCornerShape((size * 0.31f).dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size((size * 0.48f).dp))
    }
}

/**
 * 6.a y 6C · Mensaje centrado (icono, titular y explicación) con las acciones abajo. Todo se desplaza junto: con fuente
 * grande las acciones fijas dejaban el mensaje en una franja cortada. Sin barra superior: el lector empieza por el
 * titular.
 */
@Composable
internal fun AccessMessage(
    @DrawableRes icon: Int,
    iconContainer: Color,
    iconContent: Color,
    title: String,
    body: AnnotatedString,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        top()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Al menos el alto visible: si sobra espacio, el mensaje queda centrado sobre las acciones, que van abajo.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier)
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AccessIcon(icon, iconContainer, iconContent)
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 26.sp, lineHeight = 34.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.exploraColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 290.dp),
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions,
                )
            }
        }
    }
}

/** Titular de las pantallas de acceso con formulario (5): Outfit 24. */
@Composable
internal fun AccessHeadline(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}
