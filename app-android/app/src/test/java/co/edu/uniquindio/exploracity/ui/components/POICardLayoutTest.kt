package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.util.formatDistance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * README: «Alto mínimo 96 dp, crece con el texto», también con la fuente al 200 %. Con las tarjetas del
 * feed de prueba, ningún texto puede quedar aplastado ni salirse de la tarjeta (la tarjeta recorta).
 * Gráficos nativos: el texto se mide con las fuentes reales (Outfit, Manrope) y se parte en líneas de verdad.
 * Los cortes pueden variar un poco frente a un teléfono concreto; por eso se prueban tres anchos y tres escalas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-450dpi")
class POICardLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `ninguna tarjeta recorta texto con fontScale 1`() = assertNoCardClipsText(fontScale = 1f)

    @Test
    fun `ninguna tarjeta recorta texto con fontScale 1,3, la mayor en horizontal`() =
        assertNoCardClipsText(fontScale = 1.3f)

    @Test
    fun `ninguna tarjeta recorta texto con fontScale 2`() = assertNoCardClipsText(fontScale = 2f)

    private fun assertNoCardClipsText(fontScale: Float) {
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for (width in CardWidthsDp) for (poi in samplePois) {
                            Box(Modifier.width(width.dp).testTag(tag(poi.id, width))) {
                                POICard(
                                    title = poi.title,
                                    category = poi.category,
                                    status = poi.status,
                                    distance = formatDistance(poi.distanceMeters),
                                    votes = poi.votes,
                                    comments = poi.comments,
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        val problems = buildList {
            for (width in CardWidthsDp) for (poi in samplePois) {
                val tag = tag(poi.id, width)
                val card = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
                val cardTop = card.positionInRoot.y
                val cardBottom = cardTop + card.size.height
                val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasTextLayout, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                if (texts.isEmpty()) add("«${poi.title}» a $width dp: no se encontraron sus textos")
                for (text in texts) {
                    val layout = text.textLayout()
                    val top = text.positionInRoot.y
                    val bottom = top + layout.multiParagraph.height
                    if (layout.didOverflowHeight || top < cardTop || bottom > cardBottom) {
                        add("«${layout.layoutInput.text}» en «${poi.title}» a $width dp")
                    }
                }
            }
        }
        assertTrue("Texto recortado a fontScale $fontScale:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private fun tag(id: String, width: Int) = "$id@$width"

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private companion object {
        /** Ancho de la tarjeta (pantalla menos 2 × 16 dp del feed) con 360, 384 (el Galaxy S20+ del fallo) y 412 dp. */
        val CardWidthsDp = listOf(328, 352, 380)

        val hasTextLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
    }
}
