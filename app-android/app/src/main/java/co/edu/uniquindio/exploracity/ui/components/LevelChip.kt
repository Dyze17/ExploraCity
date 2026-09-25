package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.UserLevel
import co.edu.uniquindio.exploracity.ui.theme.ContainerColors
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

@get:StringRes
val UserLevel.labelRes: Int
    get() = when (this) {
        UserLevel.TOURIST -> R.string.level_tourist
        UserLevel.EXPLORER -> R.string.level_explorer
        UserLevel.ADVENTURER -> R.string.level_adventurer
        UserLevel.LOCAL_AMBASSADOR -> R.string.level_local_ambassador
    }

@get:DrawableRes
val UserLevel.iconRes: Int
    get() = when (this) {
        UserLevel.TOURIST -> R.drawable.ic_luggage
        UserLevel.EXPLORER -> R.drawable.ic_explore
        UserLevel.ADVENTURER -> R.drawable.ic_hiking
        UserLevel.LOCAL_AMBASSADOR -> R.drawable.ic_workspace_premium
    }

/**
 * Colores del sistema de diseño («LevelChip · 4 niveles»). Explorador usa el tertiaryContainer del tema
 * (#1F4D48 en oscuro), no el #00504A del lienzo: la misma decisión que el resto de la app.
 */
val UserLevel.colors: ContainerColors
    @Composable
    @ReadOnlyComposable
    get() {
        val scheme = MaterialTheme.colorScheme
        val explora = MaterialTheme.exploraColors
        return when (this) {
            UserLevel.TOURIST -> ContainerColors(scheme.surfaceContainer, explora.iconSecondary)
            UserLevel.EXPLORER -> ContainerColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
            UserLevel.ADVENTURER -> explora.warning
            UserLevel.LOCAL_AMBASSADOR -> ContainerColors(scheme.primary, scheme.onPrimary)
        }
    }

/**
 * LevelChip: nivel del autor con icono y nombre (nunca solo color), junto a su nombre en detalle y comentarios. [large]
 * es la cabecera del perfil (31), con el texto a 12 sp.
 */
@Composable
fun LevelChip(level: UserLevel, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = level.colors
    Row(
        modifier
            .background(colors.container, CircleShape)
            .padding(horizontal = if (large) 11.dp else 8.dp, vertical = if (large) 5.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(level.iconRes),
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size((if (large) 16.dp else 14.dp).scaledWithFont()),
        )
        Text(
            stringResource(level.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (large) 12.sp else 11.sp, fontWeight = FontWeight.W700),
            color = colors.content,
        )
    }
}

/** Para el lector: «Aventurero, nivel 3 de 4»; el último, «Embajador Local, nivel máximo» (README 31). */
@Composable
fun UserLevel.spokenDescription(): String {
    val label = stringResource(labelRes)
    val levels = UserLevel.entries
    return if (this == levels.last()) {
        stringResource(R.string.level_description_max, label)
    } else {
        stringResource(R.string.level_description, label, ordinal + 1, levels.size)
    }
}
