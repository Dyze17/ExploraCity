package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.ui.theme.CategoryColors
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Avatar con la foto de perfil ([photo], 28) o, sin ella, con las iniciales. El color sale de los contenedores de
 * categoría y es siempre el mismo para cada persona (en 14.a, María Paula en rosa, Juan David en verde y Ana en
 * terracota); también queda de fondo mientras la foto carga.
 *
 * Es decorativo: el nombre va escrito al lado, así que no se anuncia. Las iniciales no crecen con la fuente
 * para caber en los 40 dp; el nombre sí crece.
 */
@Composable
fun InitialsAvatar(author: Author, modifier: Modifier = Modifier, size: Dp = 40.dp, photo: String? = null) {
    val colors = avatarColors(author.id)
    val fontSize = with(LocalDensity.current) { (size * 0.35f).toSp() }
    val image = photo?.let { rememberLocalPhoto(it, size) }
    Box(
        modifier.size(size).clip(CircleShape).background(colors.container).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initialsOf(author.name),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.W700),
                color = colors.content,
            )
        }
    }
}

/** «María Paula» → «MP», «Camilo R.» → «CR», «Laura» → «L». */
internal fun initialsOf(name: String): String =
    name.split(' ').mapNotNull { word -> word.firstOrNull { it.isLetter() } }.take(2).joinToString("").uppercase()

/** Avatar de una cuenta eliminada (30): sin iniciales ni color propio, solo la silueta. Decorativo, como el otro. */
@Composable
fun DeletedUserAvatar(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_person),
            contentDescription = null,
            tint = MaterialTheme.exploraColors.iconSecondary,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
private fun avatarColors(id: String): CategoryColors {
    val palette = MaterialTheme.exploraColors.category
    // Orden elegido para que las personas de los lienzos queden con sus colores.
    val colors = listOf(palette.nature, palette.history, palette.entertainment, palette.culture, palette.gastronomy)
    return colors[id.sumOf { it.code } % colors.size]
}
